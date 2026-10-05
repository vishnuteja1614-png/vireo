package com.vireo.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * AI background removal - no green screen required.
 *
 * Uses MediaPipe's Selfie Segmenter (a 249 KB on-device TFLite model) to
 * produce a per-pixel person mask for every frame, then composites the subject
 * over a chosen colour. Everything runs locally: no API key, no account, no
 * network, nothing leaves the phone.
 *
 * **Why this is a render-to-file operation rather than a live GL effect.**
 * The segmenter works on bitmaps, so using it inside Media3's GPU pipeline
 * would mean reading every frame back off the GPU, running inference, and
 * re-uploading the mask - a round trip that destroys realtime playback. Doing
 * it offline, once, produces a normal video file that then flows through the
 * ordinary preview and export path at full speed. It is slower to apply but
 * honest about the cost, and the result is editable like any other clip.
 *
 * The pipeline is: FFmpeg extracts frames -> MediaPipe masks each one ->
 * FFmpeg re-encodes with the original audio.
 */
object AiBackgroundRemover {

    private const val MODEL = "models/selfie_segmenter.tflite"

    /** Longer clips are refused rather than left to grind for many minutes. */
    private const val MAX_DURATION_MS = 15_000L

    /**
     * @param backgroundRgb colour painted where the background was removed.
     *   Video codecs cannot store transparency, so a fill is required.
     * @param onProgress 0..100, called on a background thread
     */
    suspend fun removeBackground(
        context: Context,
        input: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        backgroundRgb: Int = 0x000000,
        fps: Int = 24,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val durationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        if (durationMs <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to process"))
        }
        if (durationMs > MAX_DURATION_MS) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "AI background removal is limited to ${MAX_DURATION_MS / 1000}s per clip - " +
                        "it runs a neural network on every frame. Split the clip and try again."
                )
            )
        }

        val workDir = File(context.cacheDir, "matte_${System.currentTimeMillis()}").apply { mkdirs() }
        val framesIn = File(workDir, "in").apply { mkdirs() }
        val framesOut = File(workDir, "out").apply { mkdirs() }

        try {
            // 1. Frames out of the source segment.
            val extract = FfmpegEngine.runRaw(
                context, input,
                { src ->
                    "-y -ss ${sec(trimStartMs)} -t ${sec(durationMs)} -i \"$src\" " +
                        "-vf fps=$fps -q:v 2 \"${framesIn.absolutePath}/f_%05d.jpg\""
                }
            )
            if (extract.isFailure) return@withContext Result.failure(extract.exceptionOrNull()!!)

            val frames = framesIn.listFiles()?.sortedBy { it.name }.orEmpty()
            if (frames.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No frames could be read"))
            }

            // 2. Segment each frame.
            val segmenter = buildSegmenter(context)
                ?: return@withContext Result.failure(IllegalStateException("Could not load the AI model"))

            segmenter.use { seg ->
                frames.forEachIndexed { index, frameFile ->
                    val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath) ?: return@forEachIndexed
                    val composited = applyMask(seg, bitmap, backgroundRgb)
                    File(framesOut, frameFile.name).outputStream().use { out ->
                        composited.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    if (composited !== bitmap) composited.recycle()
                    bitmap.recycle()
                    onProgress(((index + 1) * 95) / frames.size)
                }
            }

            // 3. Re-encode, carrying the original audio across.
            val out = File(context.cacheDir, "nobg_${System.currentTimeMillis()}.mp4")
            val encode = FfmpegEngine.runRaw(
                context, input,
                { src ->
                    "-y -framerate $fps -i \"${framesOut.absolutePath}/f_%05d.jpg\" " +
                        "-ss ${sec(trimStartMs)} -t ${sec(durationMs)} -i \"$src\" " +
                        "-map 0:v -map 1:a? -c:v mpeg4 -q:v 3 -c:a aac -shortest " +
                        "\"${out.absolutePath}\""
                }
            )
            onProgress(100)
            encode.map { out }
        } finally {
            // Frame dumps are large; never leave them behind.
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun buildSegmenter(context: Context): ImageSegmenter? = runCatching {
        val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            // A confidence mask gives soft edges around hair, which a binary
            // category mask would turn into a hard, obviously-cut-out outline.
            .setOutputConfidenceMasks(true)
            .setOutputCategoryMask(false)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.getOrNull()

    /** Composites the subject over [backgroundRgb] using the confidence mask. */
    private fun applyMask(segmenter: ImageSegmenter, frame: Bitmap, backgroundRgb: Int): Bitmap {
        val result = runCatching {
            segmenter.segment(BitmapImageBuilder(frame).build())
        }.getOrNull() ?: return frame

        val masks = result.confidenceMasks().orElse(null) ?: return frame
        if (masks.isEmpty()) return frame

        // Index 0 is background confidence for the selfie model.
        val mask = masks[0]
        val buffer = runCatching {
            com.google.mediapipe.framework.image.ByteBufferExtractor.extract(mask)
        }.getOrNull() ?: return frame

        val w = mask.width
        val h = mask.height
        if (w <= 0 || h <= 0) return frame

        val scaled = if (frame.width != w || frame.height != h) {
            Bitmap.createScaledBitmap(frame, w, h, true)
        } else frame

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val bgR = (backgroundRgb shr 16) and 0xFF
        val bgG = (backgroundRgb shr 8) and 0xFF
        val bgB = backgroundRgb and 0xFF

        val floats = buffer.asFloatBuffer()
        for (i in pixels.indices) {
            if (i >= floats.limit()) break
            // Confidence that this pixel is BACKGROUND.
            val bg = floats.get(i).coerceIn(0f, 1f)
            val keep = 1f - bg
            if (keep > 0.98f) continue              // clearly subject, leave alone
            val p = pixels[i]
            if (keep < 0.02f) {                      // clearly background
                pixels[i] = Color.rgb(bgR, bgG, bgB)
                continue
            }
            // Feathered edge: blend so hair and shoulders do not look cut out.
            val r = (((p shr 16) and 0xFF) * keep + bgR * bg).toInt().coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) * keep + bgG * bg).toInt().coerceIn(0, 255)
            val b = ((p and 0xFF) * keep + bgB * bg).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(r, g, b)
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== frame) scaled.recycle()

        // Restore the original resolution so the clip keeps its geometry.
        return if (output.width == frame.width && output.height == frame.height) {
            output
        } else {
            val restored = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            Canvas(restored).drawBitmap(
                output,
                android.graphics.Rect(0, 0, output.width, output.height),
                android.graphics.Rect(0, 0, frame.width, frame.height),
                null
            )
            output.recycle()
            restored
        }
    }

    private fun sec(ms: Long) = String.format(java.util.Locale.US, "%.3f", ms / 1000.0)
}
