package com.vireo.editor.engine

import android.content.Context
import android.net.Uri
import com.vireo.editor.data.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import kotlin.coroutines.resume

/**
 * Offline auto-captions with word-level timing.
 *
 * Speech is transcribed on the device by Vosk, which returns a start and end
 * time for **every word**. That matters: captions timed per sentence drift
 * visibly against the speaker, whereas per-word timing lets cues be grouped
 * into short, tightly synced chunks - the style short-form video relies on.
 *
 * **Why Vosk rather than Whisper/ONNX.** Whisper is more accurate, but running
 * it on ONNX Runtime means implementing the encoder/decoder loop, the
 * tokenizer and beam search by hand, and the base model's own output has no
 * word timings without a separate forced-alignment pass (WhisperX). Vosk ships
 * a real Android artifact, emits word timings directly, and runs comfortably
 * on a phone. Both are fully offline: no API key, no account, nothing leaves
 * the device.
 */
object AutoCaptions {

    private const val ASSET_DIR = "model-en-us"
    private const val TARGET_DIR = "vosk-model"
    private const val SAMPLE_RATE = 16_000f

    @Volatile private var cached: Model? = null

    /**
     * Transcribe a clip and return caption cues ready to drop on the timeline.
     *
     * @param wordsPerCue words shown at once; 3-5 reads best on a phone
     * @param onProgress 0..100
     */
    suspend fun generate(
        context: Context,
        input: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        timelineOffsetMs: Long = 0L,
        wordsPerCue: Int = 4,
        onProgress: (Int) -> Unit = {}
    ): Result<List<TextOverlay>> = withContext(Dispatchers.IO) {
        val durationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        if (durationMs <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Clip has no audio to transcribe"))
        }

        onProgress(5)

        // 1. Vosk needs 16 kHz mono 16-bit PCM. Anything else silently
        //    transcribes to nonsense, so the conversion is not optional.
        val wav = File(context.cacheDir, "asr_${System.currentTimeMillis()}.wav")
        val extract = FfmpegEngine.runRaw(context, input) { src ->
            "-y -ss ${sec(trimStartMs)} -t ${sec(durationMs)} -i \"$src\" " +
                "-vn -ac 1 -ar 16000 -f wav -acodec pcm_s16le \"${wav.absolutePath}\""
        }
        if (extract.isFailure) {
            return@withContext Result.failure(
                IllegalStateException("Could not read the audio track - does this clip have sound?")
            )
        }
        onProgress(20)

        val model = loadModel(context)
            ?: return@withContext Result.failure(IllegalStateException("Speech model failed to load"))

        // 2. Stream the PCM through the recogniser, collecting word timings.
        val words = mutableListOf<Word>()
        try {
            Recognizer(model, SAMPLE_RATE).use { rec ->
                rec.setWords(true)
                wav.inputStream().buffered().use { stream ->
                    stream.skip(44)                      // WAV header
                    val total = (wav.length() - 44).coerceAtLeast(1)
                    val buffer = ByteArray(8192)
                    var read: Int
                    var consumed = 0L
                    while (stream.read(buffer).also { read = it } > 0) {
                        if (rec.acceptWaveForm(buffer, read)) {
                            words += parseWords(rec.result)
                        }
                        consumed += read
                        onProgress(20 + ((consumed * 70) / total).toInt().coerceIn(0, 70))
                    }
                }
                words += parseWords(rec.finalResult)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("Transcription failed: ${e.message}"))
        } finally {
            runCatching { wav.delete() }
        }

        onProgress(95)

        if (words.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No speech detected in this clip")
            )
        }

        // 3. Group words into short cues, breaking on natural pauses so a cue
        //    never straddles a sentence boundary.
        val cues = mutableListOf<TextOverlay>()
        var bucket = mutableListOf<Word>()

        fun flush() {
            if (bucket.isEmpty()) return
            cues += TextOverlay(
                text = bucket.joinToString(" ") { it.text },
                startMs = timelineOffsetMs + (bucket.first().startSec * 1000).toLong(),
                endMs = timelineOffsetMs + (bucket.last().endSec * 1000).toLong(),
                xFraction = 0.5f,
                yFraction = 0.78f
            )
            bucket = mutableListOf()
        }

        words.forEachIndexed { i, w ->
            bucket += w
            val gapToNext = words.getOrNull(i + 1)?.let { it.startSec - w.endSec } ?: 0f
            // Break on a full bucket, or on a pause long enough to be a breath.
            if (bucket.size >= wordsPerCue || gapToNext > 0.6f) flush()
        }
        flush()

        onProgress(100)
        Result.success(cues.filter { it.endMs > it.startMs })
    }

    // ---------------------------------------------------------------- helpers

    private data class Word(val text: String, val startSec: Float, val endSec: Float)

    private fun parseWords(json: String?): List<Word> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONObject(json).optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("word").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Word(text, o.optDouble("start").toFloat(), o.optDouble("end").toFloat())
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Unpacks the bundled model to internal storage on first use and keeps it
     * in memory afterwards - loading costs seconds, so doing it per clip would
     * dominate the runtime.
     */
    private suspend fun loadModel(context: Context): Model? {
        cached?.let { return it }
        return suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context, ASSET_DIR, TARGET_DIR,
                { model ->
                    cached = model
                    if (cont.isActive) cont.resume(model)
                },
                { _ ->
                    if (cont.isActive) cont.resume(null)
                }
            )
        }
    }

    private fun sec(ms: Long) = String.format(java.util.Locale.US, "%.3f", ms / 1000.0)
}
