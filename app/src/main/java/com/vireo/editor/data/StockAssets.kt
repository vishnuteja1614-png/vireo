package com.vireo.editor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Online royalty-free asset browser, Kdenlive style.
 *
 * Rather than shipping thousands of sound files inside the APK, Vireo searches
 * Openverse live. Openverse is the Creative Commons search index: it federates
 * Freesound, Jamendo, Wikimedia and others behind one API that needs **no key,
 * no signup and no captcha**.
 *
 * Every query is pinned to `license_type=commercial,modification`, so results
 * are always safe to use in a monetised app and safe to edit. That filter is
 * not cosmetic -- it is the difference between a legal product and a takedown.
 */
object StockAssets {

    /** What kind of asset a search is after. */
    enum class Kind { AUDIO, IMAGE }

    data class Asset(
        val id: String,
        val title: String,
        val creator: String,
        val license: String,
        val provider: String,
        val url: String,
        val thumb: String?,
        val durationMs: Long,
        val kind: Kind
    ) {
        /** "CC0 . freesound" -- short enough for a one-line list row. */
        val credit: String get() = "${license.uppercase()} · $provider"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://api.openverse.org/v1"

    /**
     * Search Openverse. Returns an empty list rather than throwing, because a
     * flaky network must never take the editor down mid-edit.
     */
    suspend fun search(
        query: String,
        kind: Kind,
        page: Int = 1,
        pageSize: Int = 30
    ): List<Asset> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query.ifBlank { "music" }, "UTF-8")
        val path = if (kind == Kind.AUDIO) "audio" else "images"
        val url = "$BASE/$path/?q=$q&page=$page&page_size=$pageSize" +
            "&license_type=commercial,modification"

        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "Vireo/1.0").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val arr = root.optJSONArray("results") ?: return@withContext emptyList()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val direct = o.optString("url").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        Asset(
                            id = o.optString("id", direct),
                            title = o.optString("title").ifBlank { "Untitled" },
                            creator = o.optString("creator").ifBlank { "Unknown" },
                            license = o.optString("license", "cc"),
                            provider = o.optString("provider", "openverse"),
                            url = direct,
                            thumb = o.optString("thumbnail").takeIf { it.isNotBlank() },
                            // Openverse reports audio length in milliseconds already.
                            durationMs = o.optLong("duration", 0L),
                            kind = kind
                        )
                    }
                }
        }.getOrDefault(emptyList())
    }

    /**
     * Download an asset into the app cache and hand back a real [File].
     *
     * Everything downstream -- FFmpeg, ExoPlayer, the exporter -- wants a real
     * path, not a remote URL, so the fetch happens once here and the file is
     * then treated exactly like a clip picked from the gallery.
     */
    suspend fun download(context: Context, asset: Asset): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "stock").apply { mkdirs() }
        val ext = when {
            asset.kind == Kind.IMAGE -> "jpg"
            asset.url.contains(".wav", true) -> "wav"
            asset.url.contains(".ogg", true) -> "ogg"
            else -> "mp3"
        }
        val safe = asset.title.replace(Regex("[^A-Za-z0-9 ._-]"), "").take(40).trim()
            .ifBlank { "asset" }
        val out = File(dir, "${safe}_${asset.id.take(8)}.$ext")
        if (out.exists() && out.length() > 1024) return@withContext out

        runCatching {
            http.newCall(
                Request.Builder().url(asset.url).header("User-Agent", "Vireo/1.0").build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
            }
            if (out.length() > 1024) out else null.also { out.delete() }
        }.getOrNull()
    }
}
