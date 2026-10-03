package com.vireo.editor.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists projects to disk so the Home screen's "Recent" list survives the app
 * being closed.
 *
 * Previously `MainActivity` held the list in a Compose `remember`, which meant
 * every project vanished on process death and nothing was ever written to
 * storage - a repo-wide search for Room, DataStore or SharedPreferences found
 * no project persistence at all.
 *
 * Deliberately plain JSON in `filesDir` rather than Room: the dataset is a
 * handful of records, it needs no queries, and this avoids carrying schema
 * migrations for the life of the app. Media is referenced by URI, so projects
 * stay small; a missing URI is tolerated on load.
 */
@UnstableApi
class ProjectStore(context: Context) {

    private val file = File(context.filesDir, "projects.json")

    /** Most recently updated first. */
    fun load(): List<Project> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length())
            .mapNotNull { i -> runCatching { arr.getJSONObject(i).toProject() }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    /** Insert or replace [project], keeping the [limit] most recent. */
    fun save(project: Project, limit: Int = 20) {
        if (project.clips.isEmpty()) return // nothing worth remembering
        runCatching {
            val existing = load().filterNot { it.id == project.id }
            val updated = (listOf(project.copy(updatedAt = System.currentTimeMillis())) + existing)
                .take(limit)
            val arr = JSONArray()
            updated.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        }
    }

    fun delete(id: String) = runCatching {
        val arr = JSONArray()
        load().filterNot { it.id == id }.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    fun clear() = runCatching { if (file.exists()) file.delete() }

    // ------------------------------------------------------------ serialisation

    private fun Project.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("aspect", aspect.name)
        put("captionStyleId", captionStyleId)
        put("updatedAt", updatedAt)
        put("clips", JSONArray().also { a -> clips.forEach { a.put(it.toJson()) } })
        put("texts", JSONArray().also { a -> texts.forEach { a.put(it.toJson()) } })
        put("audio", JSONArray().also { a -> audio.forEach { a.put(it.toJson()) } })
    }

    private fun JSONObject.toProject(): Project = Project(
        id = optString("id"),
        name = optString("name", "Untitled Project"),
        aspect = runCatching { AspectRatio.valueOf(optString("aspect")) }
            .getOrDefault(AspectRatio.R16_9),
        captionStyleId = optString("captionStyleId", "hormozi"),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        clips = optJSONArray("clips").mapObjects { it.toClip() },
        texts = optJSONArray("texts").mapObjects { it.toText() },
        audio = optJSONArray("audio").mapObjects { it.toAudio() }
    )

    private fun Clip.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", media.uri.toString())
        put("name", media.name)
        put("durationMs", media.durationMs)
        put("kind", media.kind.name)
        put("sizeBytes", media.sizeBytes)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("speed", speed)
        put("volume", volume)
        put("rotationDeg", rotationDeg)
        put("filter", filter.name)
        put("brightness", brightness)
        put("contrast", contrast)
        put("saturation", saturation)
        put("transitionIn", transitionIn.name)
        put("transitionId", transitionId)
        put("transitionMs", transitionMs)
    }

    private fun JSONObject.toClip(): Clip = Clip(
        id = optString("id"),
        media = MediaItem(
            uri = Uri.parse(optString("uri")),
            kind = runCatching { MediaKind.valueOf(optString("kind")) }
                .getOrDefault(MediaKind.VIDEO),
            durationMs = optLong("durationMs"),
            name = optString("name"),
            sizeBytes = optLong("sizeBytes")
        ),
        trimStartMs = optLong("trimStartMs"),
        trimEndMs = optLong("trimEndMs"),
        speed = optDouble("speed", 1.0).toFloat(),
        volume = optDouble("volume", 1.0).toFloat(),
        rotationDeg = optDouble("rotationDeg", 0.0).toFloat(),
        filter = runCatching { FilterPreset.valueOf(optString("filter")) }
            .getOrDefault(FilterPreset.NONE),
        brightness = optDouble("brightness", 0.0).toFloat(),
        contrast = optDouble("contrast", 0.0).toFloat(),
        saturation = optDouble("saturation", 1.0).toFloat(),
        transitionIn = runCatching { TransitionType.valueOf(optString("transitionIn")) }
            .getOrDefault(TransitionType.NONE),
        transitionId = optString("transitionId", "none"),
        transitionMs = optLong("transitionMs", 600L)
    )

    private fun TextOverlay.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("startMs", startMs)
        put("endMs", endMs)
        put("xFraction", xFraction)
        put("yFraction", yFraction)
        put("sizeSp", sizeSp)
        put("colorArgb", colorArgb)
        put("style", style)
        put("anim", anim.name)
        put("opacity", opacity)
    }

    private fun JSONObject.toText(): TextOverlay = TextOverlay(
        id = optString("id"),
        text = optString("text"),
        startMs = optLong("startMs"),
        endMs = optLong("endMs", 3000L),
        xFraction = optDouble("xFraction", 0.5).toFloat(),
        yFraction = optDouble("yFraction", 0.5).toFloat(),
        sizeSp = optDouble("sizeSp", 32.0).toFloat(),
        colorArgb = optInt("colorArgb", 0xFFFFFFFF.toInt()),
        style = optString("style", "Neon"),
        anim = runCatching { TextAnim.valueOf(optString("anim")) }
            .getOrDefault(TextAnim.FADE_IN),
        opacity = optDouble("opacity", 1.0).toFloat()
    )

    private fun AudioTrack.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", media.uri.toString())
        put("name", media.name)
        put("durationMs", media.durationMs)
        put("startMs", startMs)
        put("volume", volume)
        put("muted", muted)
        put("isVoiceover", isVoiceover)
    }

    private fun JSONObject.toAudio(): AudioTrack = AudioTrack(
        id = optString("id"),
        media = MediaItem(
            uri = Uri.parse(optString("uri")),
            kind = MediaKind.AUDIO,
            durationMs = optLong("durationMs"),
            name = optString("name")
        ),
        startMs = optLong("startMs"),
        volume = optDouble("volume", 0.85).toFloat(),
        muted = optBoolean("muted"),
        isVoiceover = optBoolean("isVoiceover")
    )

    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            runCatching { block(getJSONObject(i)) }.getOrNull()
        }
    }
}
