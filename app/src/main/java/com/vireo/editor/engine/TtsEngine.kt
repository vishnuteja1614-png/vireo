package com.vireo.editor.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Offline-capable text-to-speech using Android's built-in engine.
 * Renders narration straight to a WAV the editor can drop on the audio track —
 * no API key, no cost, works on a plane.
 */
class TtsEngine(private val context: Context) {

    private var tts: TextToSpeech? = null

    suspend fun init(locale: Locale = Locale.getDefault()): Boolean =
        suspendCancellableCoroutine { cont ->
            tts = TextToSpeech(context) { status ->
                val ok = status == TextToSpeech.SUCCESS
                if (ok) tts?.language = locale
                if (cont.isActive) cont.resume(ok)
            }
        }

    fun voices(): List<String> =
        tts?.voices?.map { it.name }?.sorted().orEmpty()

    fun setVoice(name: String) {
        tts?.voices?.firstOrNull { it.name == name }?.let { tts?.voice = it }
    }

    fun setSpeed(rate: Float) { tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }
    fun setPitch(pitch: Float) { tts?.setPitch(pitch.coerceIn(0.5f, 2.0f)) }

    /** Synthesize [text] to a wav file and return it. */
    suspend fun synthesizeToFile(text: String, name: String = "vo_${System.currentTimeMillis()}"): File? =
        suspendCancellableCoroutine { cont ->
            val engine = tts
            if (engine == null) { cont.resume(null); return@suspendCancellableCoroutine }
            val out = File(context.cacheDir, "$name.wav")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(out) }
                @Deprecated("deprecated") override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            val result = engine.synthesizeToFile(text, null, out, name)
            if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(null)
        }

    fun release() { tts?.stop(); tts?.shutdown(); tts = null }
}
