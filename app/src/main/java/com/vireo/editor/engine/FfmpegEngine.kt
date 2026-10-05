package com.vireo.editor.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FFmpeg-backed operations that AndroidX Media3 cannot perform.
 *
 * Media3's Transformer is excellent at the common path - trim, concatenate,
 * apply GPU effects, re-encode - but a few things are simply outside its
 * model:
 *
 *  - **Reverse playback.** Transformer streams frames forward through a
 *    decoder. Playing backwards needs the whole GOP structure rebuilt, so it
 *    has to be pre-rendered to a new file.
 *  - **Arbitrary containers and codec choices** beyond what MediaCodec and the
 *    muxer expose.
 *  - **Audio extraction** without a video re-encode.
 *
 * Everything here writes a **new file** and leaves the original untouched,
 * preserving the non-destructive editing model: the timeline still references
 * source media, and these outputs become ordinary media of their own.
 *
 * Commands run off the main thread and report a plain [Result] so callers can
 * surface the real FFmpeg failure instead of a silent no-op.
 */
object FfmpegEngine {

    /**
     * Reverse a clip, video and audio together.
     *
     * The `reverse` filter buffers every frame in memory, so this is applied to
     * a trimmed segment rather than a whole source file, and long clips are
     * rejected up front instead of being allowed to exhaust RAM.
     *
     * @param trimStartMs/trimEndMs the segment to reverse, in source time
     * @return the reversed file, or a failure carrying FFmpeg's own message
     */
    suspend fun reverse(
        context: Context,
        input: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        maxDurationMs: Long = 30_000L
    ): Result<File> = withContext(Dispatchers.IO) {
        val durationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        if (durationMs <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to reverse"))
        }
        if (durationMs > maxDurationMs) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Reverse is limited to ${maxDurationMs / 1000}s - the filter holds every " +
                        "frame in memory. Trim the clip shorter and try again."
                )
            )
        }

        val source = resolveToFile(context, input)
            ?: return@withContext Result.failure(IllegalStateException("Could not read the source media"))

        val out = File(context.cacheDir, "reverse_${System.currentTimeMillis()}.mp4")
        val startSec = trimStartMs / 1000.0
        val durSec = durationMs / 1000.0

        // -ss before -i seeks quickly; reverse and areverse must run after the
        // segment is cut or FFmpeg would buffer the entire file.
        val cmd = buildString {
            append("-y ")
            append("-ss ").append(fmt(startSec)).append(' ')
            append("-t ").append(fmt(durSec)).append(' ')
            append("-i \"").append(source.absolutePath).append("\" ")
            append("-vf reverse -af areverse ")
            append("-c:v mpeg4 -q:v 3 -c:a aac -b:a 128k ")
            append("\"").append(out.absolutePath).append("\"")
        }

        runFfmpeg(cmd).map { out }
    }

    /** Extract the audio track without touching the video, for waveform work or voiceover mixing. */
    suspend fun extractAudio(context: Context, input: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            val source = resolveToFile(context, input)
                ?: return@withContext Result.failure(IllegalStateException("Could not read the source media"))
            val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            runFfmpeg("-y -i \"${source.absolutePath}\" -vn -c:a aac -b:a 192k \"${out.absolutePath}\"")
                .map { out }
        }

    /**
     * Change speed with pitch correction.
     *
     * `atempo` only accepts 0.5-2.0 per instance, so larger changes are built
     * by chaining filters - the standard trick, and the reason a naive
     * single-filter implementation fails outside that range.
     */
    suspend fun changeSpeed(context: Context, input: Uri, speed: Float): Result<File> =
        withContext(Dispatchers.IO) {
            if (speed <= 0.05f || speed > 10f) {
                return@withContext Result.failure(IllegalArgumentException("Speed must be 0.1x-10x"))
            }
            val source = resolveToFile(context, input)
                ?: return@withContext Result.failure(IllegalStateException("Could not read the source media"))
            val out = File(context.cacheDir, "speed_${System.currentTimeMillis()}.mp4")

            val chain = StringBuilder()
            var remaining = speed
            while (remaining > 2f) { chain.append("atempo=2.0,"); remaining /= 2f }
            while (remaining < 0.5f) { chain.append("atempo=0.5,"); remaining *= 2f }
            chain.append("atempo=").append(fmt(remaining.toDouble()))

            val cmd = "-y -i \"${source.absolutePath}\" " +
                "-filter:v \"setpts=${fmt(1.0 / speed)}*PTS\" " +
                "-filter:a \"$chain\" " +
                "-c:v mpeg4 -q:v 3 -c:a aac \"${out.absolutePath}\""
            runFfmpeg(cmd).map { out }
        }

    /**
     * Run an arbitrary command against a source URI.
     *
     * The URI is materialised to a real path first (FFmpeg cannot open
     * content:// strings), then handed to [build] so callers can compose any
     * filter graph they need without duplicating that plumbing.
     */
    suspend fun runRaw(
        context: Context,
        input: Uri,
        build: (sourcePath: String) -> String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val source = resolveToFile(context, input)
            ?: return@withContext Result.failure(IllegalStateException("Could not read the source media"))
        runFfmpeg(build(source.absolutePath))
    }

    // ---------------------------------------------------------------- helpers

    private fun runFfmpeg(command: String): Result<Unit> {
        val session = FFmpegKit.execute(command)
        return if (ReturnCode.isSuccess(session.returnCode)) {
            Result.success(Unit)
        } else {
            // Surface FFmpeg's own diagnostics; a bare exit code is useless.
            val tail = session.allLogsAsString?.takeLast(400)?.trim().orEmpty()
            Result.failure(IllegalStateException("FFmpeg failed (${session.returnCode}): $tail"))
        }
    }

    /**
     * FFmpeg needs a real path. Content URIs from the picker are copied into
     * the cache first rather than attempting to pass a content:// string,
     * which FFmpeg cannot open.
     */
    private fun resolveToFile(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.takeIf { it.exists() }
        return runCatching {
            val temp = File(context.cacheDir, "src_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return null
            temp
        }.getOrNull()
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.4f", v)
}
