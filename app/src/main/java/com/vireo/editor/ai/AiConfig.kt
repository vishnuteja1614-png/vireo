package com.vireo.editor.ai

import android.content.Context
import androidx.core.content.edit

/** Stores the user's AI provider keys locally (never leaves the device except to the provider). */
class AiConfig(context: Context) {
    private val prefs = context.getSharedPreferences("vireo_ai", Context.MODE_PRIVATE)

    var openRouterKey: String
        get() = prefs.getString("openrouter_key", "") ?: ""
        set(v) = prefs.edit { putString("openrouter_key", v.trim()) }

    var openAiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) = prefs.edit { putString("openai_key", v.trim()) }

    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) = prefs.edit { putString("gemini_key", v.trim()) }

    var groqKey: String
        get() = prefs.getString("groq_key", "") ?: ""
        set(v) = prefs.edit { putString("groq_key", v.trim()) }

    /** Which backend to use. Puter is the default so the app works with zero setup. */
    var backend: AiBackend
        get() = runCatching { AiBackend.valueOf(prefs.getString("backend", AiBackend.PUTER.name)!!) }
            .getOrDefault(AiBackend.PUTER)
        set(v) = prefs.edit { putString("backend", v.name) }

    var puterModel: String
        get() = prefs.getString("puter_model", PuterClient.DEFAULT_MODEL) ?: PuterClient.DEFAULT_MODEL
        set(v) = prefs.edit { putString("puter_model", v) }

    var imageModel: String
        get() = prefs.getString("image_model", ImageAi.IMAGE_MODELS.first().id)
            ?: ImageAi.IMAGE_MODELS.first().id
        set(v) = prefs.edit { putString("image_model", v) }

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit { putString("model", v) }

    val hasAnyKey: Boolean
        get() = openRouterKey.isNotBlank() || openAiKey.isNotBlank() ||
                geminiKey.isNotBlank() || groqKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "google/gemini-2.0-flash-001"

        /** Popular OpenRouter model ids, including free tiers. */
        val SUGGESTED_MODELS = listOf(
            "google/gemini-2.0-flash-001"      to "Gemini 2.0 Flash · fast, cheap",
            "anthropic/claude-3.5-sonnet"      to "Claude 3.5 Sonnet · best writing",
            "openai/gpt-4o-mini"               to "GPT-4o mini · balanced",
            "openai/gpt-4o"                    to "GPT-4o · strong all-round",
            "meta-llama/llama-3.3-70b-instruct" to "Llama 3.3 70B · open weights",
            "deepseek/deepseek-chat"           to "DeepSeek V3 · very cheap",
            "qwen/qwen-2.5-72b-instruct"       to "Qwen 2.5 72B · multilingual",
            "mistralai/mistral-large"          to "Mistral Large · EU hosted",
            "google/gemma-2-9b-it:free"        to "Gemma 2 9B · FREE",
            "meta-llama/llama-3.1-8b-instruct:free" to "Llama 3.1 8B · FREE"
        )
    }

    /** Last neural voice the creator picked. */
    var voiceId: String
        get() = prefs.getString("voice_id", com.vireo.editor.engine.EdgeVoices.DEFAULT)
            ?: com.vireo.editor.engine.EdgeVoices.DEFAULT
        set(v) = prefs.edit { putString("voice_id", v) }
}
