package com.vireo.editor.ai

import android.content.Context
import android.graphics.Bitmap

/**
 * Chooses where an AI request goes.
 *
 *  1. PUTER  — default. Free for the developer, the signed-in user's own Puter
 *              allocation pays. No API key anywhere in the app.
 *  2. KEY    — power-user fallback: the user's own OpenRouter / OpenAI / Gemini / Groq key.
 *
 * Keeping both means the app never dies if one provider changes its pricing.
 */
enum class AiBackend(val label: String) {
    PUTER("Puter (free, no key)"),
    KEY("My own API key")
}

class AiRouter(
    context: Context,
    private val config: AiConfig
) {
    val puter = PuterClient(context)
    private val keyClient = OpenRouterClient(config)
    private val imageAi = ImageAi(context, config)

    /** Effective backend: honour the user's choice, but fall back sensibly. */
    fun backend(): AiBackend = when (config.backend) {
        AiBackend.KEY -> if (config.hasAnyKey) AiBackend.KEY else AiBackend.PUTER
        AiBackend.PUTER -> AiBackend.PUTER
    }

    val activeLabel: String
        get() = if (backend() == AiBackend.PUTER) "Puter · ${config.puterModel}"
                else "${keyClient.providerName} · ${config.model}"

    suspend fun chat(
        system: String,
        user: String,
        temperature: Double = 0.8,
        maxTokens: Int = 1600,
        jsonMode: Boolean = false
    ): Result<String> = when (backend()) {
        AiBackend.PUTER -> puter.chat(system, user, config.puterModel)
        AiBackend.KEY -> keyClient.chat(system, user, temperature, maxTokens, jsonMode)
    }

    suspend fun image(prompt: String, aspect: String): Result<Bitmap> = when (backend()) {
        AiBackend.PUTER -> puter.image(prompt)
        AiBackend.KEY -> imageAi.generate(prompt, config.imageModel, aspect)
    }

    fun saveImage(bitmap: Bitmap) = imageAi.saveToGallery(bitmap)

    fun release() = puter.release()
}
