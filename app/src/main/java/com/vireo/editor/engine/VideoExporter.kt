package com.vireo.editor.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as M3MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.vireo.editor.data.*
import com.vireo.editor.data.CaptionStyles
import com.vireo.editor.data.TextOverlay as VTextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int, val stage: String = "Rendering") : ExportState
    data class Done(val uri: Uri, val file: File, val elapsedMs: Long) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * Renders a [Project] to a video file with AndroidX Media3 Transformer.
 *
 * Fixes over the first version:
 *  - text overlays are burned in (OverlayEffect)
 *  - music / voiceover tracks are mixed in as a parallel sequence
 *  - per-clip volume honoured via ChannelMixingAudioProcessor
 *  - transitions rendered as timed alpha ramps at clip boundaries
 *  - image clips given an explicit duration + frame rate so they don't crash
 *  - bitrate / fps / aspect ratio from ExportSettings actually applied
 */
@UnstableApi
class VideoExporter(private val context: Context) {

    private var transformer: Transformer? = null

    fun export(project: Project, settings: ExportSettings): Flow<ExportState> = callbackFlow {
        if (project.clips.isEmpty()) {
            trySend(ExportState.Failed("Timeline is empty — add a clip first"))
            close(); return@callbackFlow
        }

        val started = System.currentTimeMillis()
        val outFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "vireo_${started}.${settings.format.ext}"
        )
        outFile.parentFile?.mkdirs()

        val (outW, outH) = outputSize(project.aspect, settings.resolution.height)

        // ---------- video sequence ----------
        var cursorMs = 0L
        val videoItems = project.clips.map { clip ->
            val item = buildClipItem(clip, project, settings, outW, outH, cursorMs)
            cursorMs += clip.outputDurationMs
            item
        }
        val videoSequence = EditedMediaItemSequence(videoItems)

        // ---------- audio sequences (music / voiceover) ----------
        val audioSequences = project.audio
            .filterNot { it.muted }
            .map { track ->
                val src = M3MediaItem.Builder().setUri(track.media.uri).build()
                val gain = ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(track.volume))
                    putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(track.volume))
                }
                EditedMediaItemSequence(
                    listOf(
                        EditedMediaItem.Builder(src)
                            .setRemoveVideo(true)
                            .setEffects(Effects(ImmutableList.of<AudioProcessor>(gain), ImmutableList.of()))
                            .build()
                    )
                )
            }

        val composition = Composition.Builder(listOf(videoSequence) + audioSequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        // ---------- encoder ----------
        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(settings.bitrateMbps * 1_000_000)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(c: Composition, result: ExportResult) {
                trySend(ExportState.Running(99, "Saving to gallery"))
                val saved = saveToGallery(outFile, settings.format)
                trySend(
                    ExportState.Done(
                        saved ?: Uri.fromFile(outFile), outFile,
                        System.currentTimeMillis() - started
                    )
                )
                close()
            }

            override fun onError(c: Composition, result: ExportResult, e: ExportException) {
                trySend(ExportState.Failed(friendlyError(e)))
                close()
            }
        }

        val t = Transformer.Builder(context)
            .setVideoMimeType(
                if (settings.format == ContainerFormat.WEBM) MimeTypes.VIDEO_VP9
                else MimeTypes.VIDEO_H264
            )
            .setAudioMimeType(
                if (settings.format == ContainerFormat.WEBM) MimeTypes.AUDIO_OPUS
                else MimeTypes.AUDIO_AAC
            )
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(listener)
            .build()

        transformer = t
        trySend(ExportState.Running(1, "Starting encoder"))
        withContext(Dispatchers.Main) { t.start(composition, outFile.absolutePath) }

        val holder = ProgressHolder()
        val ticker = launch {
            while (isActive) {
                val state = withContext(Dispatchers.Main) { t.getProgress(holder) }
                if (state == Transformer.PROGRESS_STATE_NOT_STARTED) break
                trySend(ExportState.Running(holder.progress.coerceIn(1, 98)))
                delay(200)
            }
        }

        awaitClose {
            ticker.cancel()
            runCatching { t.cancel() }
            transformer = null
        }
    }

    fun cancel() { runCatching { transformer?.cancel() } }

    // ---------------------------------------------------------------- helpers

    private fun buildClipItem(
        clip: Clip,
        project: Project,
        settings: ExportSettings,
        outW: Int,
        outH: Int,
        timelineStartMs: Long
    ): EditedMediaItem {
        val isImage = clip.media.kind == MediaKind.IMAGE

        val builder = M3MediaItem.Builder().setUri(clip.media.uri)
        if (!isImage) {
            builder.setClippingConfiguration(
                M3MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build()
            )
        }
        val source = builder.build()

        // ---- video effects ----
        val videoEffects = mutableListOf<Effect>()
        videoEffects += FilterFactory.effectsFor(clip)
        if (clip.rotationDeg != 0f) {
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(clip.rotationDeg).build()
        }
        videoEffects += Presentation.createForWidthAndHeight(
            outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        // One clock per clip: Media3 timestamps are cumulative across a
        // sequence, so transitions and captions both need a shared origin.
        val clock = ClipClock()

        // ---- transition in-animation for this clip ----
        // Runs after Presentation so the geometry is expressed in output space.
        videoEffects += TransitionEffects.effectsFor(clip.transitionId, clip.transitionMs, clock)

        // ---- burn in captions / text that fall inside this clip's window ----
        val clipEndMs = timelineStartMs + clip.outputDurationMs
        // byId() always resolves, falling back to the first preset.
        val style = CaptionStyles.byId(project.captionStyleId)
        val overlays: List<TextureOverlay> = project.texts
            .filter { it.startMs < clipEndMs && it.endMs > timelineStartMs }
            .map { cue -> CaptionOverlay(cue, style, outW, outH, timelineStartMs, clock) }
        if (overlays.isNotEmpty()) {
            videoEffects += OverlayEffect(ImmutableList.copyOf(overlays))
        }

        // ---- audio processing ----
        val audioProcessors = mutableListOf<AudioProcessor>()
        if (clip.speed != 1f) {
            audioProcessors += SonicAudioProcessor().apply { setSpeed(clip.speed) }
        }
        if (clip.volume != 1f && clip.volume > 0f) {
            audioProcessors += ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(clip.volume))
                putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(clip.volume))
            }
        }

        val edited = EditedMediaItem.Builder(source)
            .setEffects(
                Effects(
                    ImmutableList.copyOf(audioProcessors),
                    ImmutableList.copyOf(videoEffects)
                )
            )
            .setRemoveAudio(clip.volume <= 0f)

        if (isImage) {
            edited.setDurationUs(clip.outputDurationMs.coerceAtLeast(1000L) * 1000L)
            edited.setFrameRate(settings.fps)
        }

        return edited.build()
    }

    /** Media3 text overlay positioned from the project's fractional coordinates. */
    /** Plain fallback overlay, kept for styles that need no decoration. */
    @Suppress("unused")
    private fun textOverlay(t: VTextOverlay, outH: Int, clipStartMs: Long): TextureOverlay {
        val pxSize = (t.sizeSp * outH / 720f).toInt().coerceAtLeast(12)
        val span = SpannableString(t.text).apply {
            setSpan(ForegroundColorSpan(t.colorArgb), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(pxSize), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Media3 overlay space is -1..1 with 0 at centre
        val x = (t.xFraction * 2f) - 1f
        val y = 1f - (t.yFraction * 2f)
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(x, y)
            .setAlphaScale(t.opacity.coerceIn(0f, 1f))
            .build()
        return TextOverlay.createStaticTextOverlay(span, settings)
    }

    private fun outputSize(aspect: AspectRatio, height: Int): Pair<Int, Int> {
        val w = (height.toFloat() * aspect.w / aspect.h).toInt()
        // encoders require even dimensions
        fun even(v: Int) = if (v % 2 == 0) v else v + 1
        return even(w) to even(height)
    }

    private fun friendlyError(e: ExportException): String = when {
        e.message?.contains("decoder", true) == true ->
            "This clip's format isn't supported by your device decoder. Try a different clip or lower the resolution."
        e.message?.contains("encoder", true) == true ->
            "Encoder failed at this resolution. Try 1080p or a lower bitrate."
        e.message?.contains("space", true) == true -> "Not enough storage space."
        else -> e.message ?: "Export failed"
    }

    private fun saveToGallery(file: File, format: ContainerFormat): Uri? = runCatching {
        if (!file.exists() || file.length() == 0L) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, format.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Vireo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }.getOrNull()
}
