package com.vireo.editor.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI image + thumbnail generation through OpenRouter's image-capable models.
 * Returns a decoded Bitmap and can save it to the gallery.
 */
class ImageAi(private val context: Context, private val config: AiConfig) {

    data class ImageModel(val id: String, val label: String, val note: String)

    companion object {
        val IMAGE_MODELS = listOf(
            ImageModel("google/gemini-2.5-flash-image-preview", "Gemini 2.5 Flash Image", "fast, good text rendering"),
            ImageModel("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash Exp", "FREE tier"),
            ImageModel("openai/gpt-4o", "GPT-4o vision", "image understanding + edit prompts")
        )
    }

    /** Generate an image from a prompt. Returns bitmap or an error message. */
    suspend fun generate(
        prompt: String,
        model: String = IMAGE_MODELS.first().id,
        aspect: String = "16:9"
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val key = config.openRouterKey
        if (key.isBlank()) return@withContext Result.failure(
            IllegalStateException("Add an OpenRouter key in Settings to generate images.")
        )

        runCatching {
            val body = JSONObject().apply {
                put("model", model)
                put("modalities", JSONArray().put("image").put("text"))
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("content", "Generate an image, aspect ratio $aspect. $prompt")
                ))
            }.toString()

            val conn = (URL("https://openrouter.ai/api/v1/chat/completions")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 30_000; readTimeout = 180_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("HTTP-Referer", "https://github.com/vishnuteja1614-png/vireo")
                setRequestProperty("X-Title", "Vireo Video Editor")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                        .getOrElse { "HTTP $code" }
                )
            }

            val message = JSONObject(text).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message")

            val dataUrl = extractImageUrl(message)
                ?: throw IllegalStateException("This model returned no image. Try Gemini 2.5 Flash Image.")

            decode(dataUrl) ?: throw IllegalStateException("Could not decode the generated image")
        }
    }

    private fun extractImageUrl(message: JSONObject): String? {
        message.optJSONArray("images")?.let { imgs ->
            if (imgs.length() > 0) {
                val first = imgs.getJSONObject(0)
                first.optJSONObject("image_url")?.optString("url")?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                first.optString("url").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        val content = message.opt("content")
        if (content is JSONArray) {
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                part.optJSONObject("image_url")?.optString("url")
                    ?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private fun decode(url: String): Bitmap? = runCatching {
        if (url.startsWith("data:")) {
            val b64 = url.substringAfter(",")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            URL(url).openStream().use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** Save a bitmap to Pictures/Vireo. */
    fun saveToGallery(bitmap: Bitmap, name: String = "vireo_${System.currentTimeMillis()}"): Uri? =
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Vireo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
}
