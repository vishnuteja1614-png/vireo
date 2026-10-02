package com.vireo.editor.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun loadVideos(): List<MediaItem> = query(MediaKind.VIDEO)
    suspend fun loadImages(): List<MediaItem> = query(MediaKind.IMAGE)
    suspend fun loadAudio(): List<MediaItem> = query(MediaKind.AUDIO)

    private suspend fun query(kind: MediaKind): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val durationCol = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.DURATION
            MediaKind.AUDIO -> MediaStore.Audio.Media.DURATION
            MediaKind.IMAGE -> null
        }
        val projection = listOfNotNull(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            durationCol
        ).toTypedArray()

        val out = mutableListOf<MediaItem>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val durIdx = durationCol?.let { c.getColumnIndex(it) } ?: -1
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                out += MediaItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    kind = kind,
                    durationMs = if (durIdx >= 0) c.getLong(durIdx) else 3000L,
                    name = c.getString(nameIdx) ?: "",
                    sizeBytes = c.getLong(sizeIdx)
                )
            }
        }
        out
    }
}
