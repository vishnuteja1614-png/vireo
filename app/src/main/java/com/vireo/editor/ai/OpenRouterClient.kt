package com.vireo.editor.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal chat-completions client. Works with OpenRouter (hundreds of models),
 * and the same wire format covers OpenAI and Groq, so one client serves all three.
 */
class OpenRouterClient(private val config: AiConfig) {

    data class Provider(val baseUrl: String, val key: String, val name: String)

    private fun provider(): Provider? = when {
        config.openRouterKey.isNotBlank() ->
            Provider("https://openrouter.ai/api/v1/chat/completions", config.openRouterKey, "OpenRouter")
        config.openAiKey.isNotBlank() ->
            Provider("https://api.openai.com/v1/chat/completions", config.openAiKey, "OpenAI")
        config.groqKey.isNotBlank() ->
            Provider("https://api.groq.com/openai/v1/chat/completions", config.groqKey, "Groq")
        config.geminiKey.isNotBlank() ->
            Provider("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                config.geminiKey, "Gemini")
        else -> null
    }

    val providerName: String get() = provider()?.name ?: "none"

    suspend fun chat(
        system: String,
        user: String,
        temperature: Double = 0.8,
        maxTokens: Int = 1600,
        jsonMode: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val p = provider() ?: return@withContext Result.failure(
            IllegalStateException("No AI key set. Open Settings → AI Keys and paste an OpenRouter key.")
        )

        runCatching {
            val body = JSONObject().apply {
                put("model", config.model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                })
            }.toString()

            val conn = (URL(p.baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${p.key}")
                if (p.name == "OpenRouter") {
                    setRequestProperty("HTTP-Referer", "https://github.com/vishnuteja1614-png/vireo")
                    setRequestProperty("X-Title", "Vireo Video Editor")
                }
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrElse { "HTTP $code" }
                throw IllegalStateException(msg)
            }

            JSONObject(text)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }
}
