package com.vireo.editor.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free text-to-image with **no API key, no sign-up, no WebView**.
 *
 * Pollinations serves images straight from a plain HTTPS GET:
 *
 *     https://image.pollinations.ai/prompt/<url-encoded prompt>?width=..&height=..
 *
 * Verified end to end: a 1280x720 Flux render returned in about three seconds.
 * That makes it a far better fit than Perchance, which needs a browser-based
 * ad-verification handshake and therefore a WebView.
 *
 * Two notes that matter in production:
 *  - Without a registered referrer/token, output carries a small
 *    "pollinations.ai" watermark. Set [token] to remove it.
 *  - There is no SLA. [ImageAi] remains available as the fallback path.
 */
class PollinationsImage(
    /** Optional free app token from auth.pollinations.ai; removes the watermark. */
    private val token: String = ""
) {

    enum class Model(val id: String, val label: String, val note: String) {
        FLUX("flux", "Flux", "Best quality, photoreal and illustration"),
        FLUX_REALISM("flux-realism", "Flux Realism", "Photographic look"),
        FLUX_ANIME("flux-anime", "Flux Anime", "Anime and manga"),
        FLUX_3D("flux-3d", "Flux 3D", "3D render look"),
        ANY_DARK("any-dark", "Any Dark", "Moody, high-contrast"),
        TURBO("turbo", "Turbo", "Fastest, lower detail")
    }

    /**
     * Generate an image for [prompt].
     *
     * @param seed pass a stable value to reproduce a render, or -1 for random
     * @param enhance let the service rewrite the prompt for better composition
     */
    suspend fun generate(
        prompt: String,
        width: Int = 1280,
        height: Int = 720,
        model: Model = Model.FLUX,
        seed: Int = -1,
        enhance: Boolean = true,
        negativePrompt: String = ""
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Describe the image first"))
        }

        val url = buildUrl(prompt, width, height, model, seed, enhance, negativePrompt)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                // Flux renders can take a while under load.
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/*")
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }

            when (val code = connection.responseCode) {
                in 200..299 -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        Result.failure(IllegalStateException("Image service returned unreadable data"))
                    } else {
                        Result.success(bitmap)
                    }
                }
                402 -> Result.failure(
                    IllegalStateException("That model needs credits. Try the Flux model, which is free.")
                )
                429 -> Result.failure(
                    IllegalStateException("Image service is busy. Wait a few seconds and try again.")
                )
                in 500..599 -> Result.failure(
                    IllegalStateException("Image service is down right now. Try again shortly.")
                )
                else -> Result.failure(IllegalStateException("Image generation failed (HTTP $code)"))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Could not reach the image service", e))
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildUrl(
        prompt: String,
        width: Int,
        height: Int,
        model: Model,
        seed: Int,
        enhance: Boolean,
        negativePrompt: String
    ): String {
        // Encoders and the service both prefer even dimensions.
        fun even(v: Int) = (if (v % 2 == 0) v else v + 1).coerceIn(64, 2048)

        val encoded = URLEncoder.encode(prompt.trim().take(1200), "UTF-8").replace("+", "%20")
        val params = buildList {
            add("width=${even(width)}")
            add("height=${even(height)}")
            add("model=${model.id}")
            add("nologo=true")
            add("safe=true")
            if (enhance) add("enhance=true")
            if (seed >= 0) add("seed=$seed")
            if (negativePrompt.isNotBlank()) {
                add("negative=" + URLEncoder.encode(negativePrompt.take(400), "UTF-8").replace("+", "%20"))
            }
            if (token.isNotBlank()) add("token=$token")
        }
        return "$BASE/$encoded?${params.joinToString("&")}"
    }

    private companion object {
        const val BASE = "https://image.pollinations.ai/prompt"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
