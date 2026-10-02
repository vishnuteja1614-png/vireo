package com.vireo.editor.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem as M3MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.vireo.editor.data.ContainerFormat
import com.vireo.editor.data.ExportSettings
import com.vireo.editor.data.MediaKind
import com.vireo.editor.data.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int) : ExportState
    data class Done(val uri: Uri, val file: File) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * Renders a [Project] to a single video file using AndroidX Media3 Transformer
 * (open source, Apache-2.0, hardware accelerated on device).
 */
@UnstableApi
class VideoExporter(private val context: Context) {

    private var transformer: Transformer? = null

    fun export(project: Project, settings: ExportSettings): Flow<ExportState> = callbackFlow {
        if (project.clips.isEmpty()) {
            trySend(ExportState.Failed("Timeline is empty"))
            close(); return@callbackFlow
        }

        val outFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "vireo_${System.currentTimeMillis()}.${settings.format.ext}"
        )

        val editedItems = project.clips.map { clip ->
            val clipping = M3MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.trimStartMs)
                .setEndPositionMs(clip.trimEndMs)
                .build()

            val source = M3MediaItem.Builder()
                .setUri(clip.media.uri)
                .setClippingConfiguration(clipping)
                .build()

            val videoEffects = buildList {
                addAll(FilterFactory.effectsFor(clip))
                if (clip.rotationDeg != 0f) {
                    add(ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(clip.rotationDeg).build())
                }
                add(Presentation.createForHeight(settings.resolution.height))
            }

            val audioProcessors = buildList {
                if (clip.speed != 1f) add(SonicAudioProcessor().apply { setSpeed(clip.speed) })
            }

            EditedMediaItem.Builder(source)
                .setEffects(Effects(audioProcessors, videoEffects))
                .setRemoveAudio(clip.volume == 0f)
                .apply { if (clip.media.kind == MediaKind.IMAGE) setDurationUs(3_000_000L) }
                .build()
        }

        val composition = Composition.Builder(EditedMediaItemSequence(editedItems))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(c: Composition, result: ExportResult) {
                val saved = saveToGallery(outFile, settings.format)
                trySend(ExportState.Done(saved ?: Uri.fromFile(outFile), outFile))
                close()
            }

            override fun onError(c: Composition, result: ExportResult, e: ExportException) {
                trySend(ExportState.Failed(e.message ?: "Export failed"))
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
            .addListener(listener)
            .build()

        transformer = t
        withContext(Dispatchers.Main) { t.start(composition, outFile.absolutePath) }

        // poll progress
        val holder = androidx.media3.transformer.ProgressHolder()
        val ticker = launch {
            while (isActive) {
                val state = withContext(Dispatchers.Main) { t.getProgress(holder) }
                if (state == Transformer.PROGRESS_STATE_NOT_STARTED) break
                trySend(ExportState.Running(holder.progress))
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

    private fun saveToGallery(file: File, format: ContainerFormat): Uri? = runCatching {
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
