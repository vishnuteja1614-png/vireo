package com.vireo.editor.ai

/** Comparison chart of AI models available through OpenRouter, for the in-app guide. */
object ModelsChart {

    data class Row(
        val model: String,
        val vendor: String,
        val bestFor: String,
        val speed: Int,      // 1-5
        val quality: Int,    // 1-5
        val costPerM: String,
        val free: Boolean = false,
        val images: Boolean = false
    )

    val ROWS = listOf(
        Row("google/gemini-2.0-flash-001", "Google", "Titles, descriptions, bulk work", 5, 4, "$0.10 / $0.40"),
        Row("google/gemini-2.5-flash-image-preview", "Google", "Thumbnails & image generation", 4, 5, "~$0.03 / image", images = true),
        Row("anthropic/claude-3.5-sonnet", "Anthropic", "Best writing, scripts, hooks", 3, 5, "$3.00 / $15.00"),
        Row("anthropic/claude-3.5-haiku", "Anthropic", "Fast quality writing", 5, 4, "$0.80 / $4.00"),
        Row("openai/gpt-4o", "OpenAI", "Strong all-rounder, reasoning", 3, 5, "$2.50 / $10.00"),
        Row("openai/gpt-4o-mini", "OpenAI", "Cheap everyday generation", 5, 3, "$0.15 / $0.60"),
        Row("meta-llama/llama-3.3-70b-instruct", "Meta", "Open weights, good value", 4, 4, "$0.12 / $0.30"),
        Row("deepseek/deepseek-chat", "DeepSeek", "Cheapest solid quality", 4, 4, "$0.14 / $0.28"),
        Row("qwen/qwen-2.5-72b-instruct", "Alibaba", "Multilingual, Indian languages", 4, 4, "$0.23 / $0.40"),
        Row("mistralai/mistral-large", "Mistral", "EU hosted, reliable", 3, 4, "$2.00 / $6.00"),
        Row("google/gemma-2-9b-it:free", "Google", "Free tier experiments", 5, 2, "FREE", free = true),
        Row("meta-llama/llama-3.1-8b-instruct:free", "Meta", "Free tier experiments", 5, 2, "FREE", free = true)
    )

    val NOTES = listOf(
        "Prices are per 1 million tokens (input / output) and change often — check openrouter.ai/models.",
        "A full publish pack costs roughly 2,000 tokens: under ₹0.50 on Gemini Flash, free on the :free models.",
        "Free models are rate-limited and lower quality, but perfect for testing the app.",
        "Text-to-speech in Vireo runs on-device and is always free, no key needed.",
        "Best time to upload also works fully offline with no key."
    )
}
