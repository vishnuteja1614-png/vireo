package com.vireo.editor.ai

import org.json.JSONObject

enum class Platform(val label: String, val maxTitle: Int, val maxDesc: Int, val tagStyle: String) {
    YOUTUBE("YouTube", 100, 5000, "3-5 broad + 10-15 niche tags"),
    YT_SHORTS("YouTube Shorts", 100, 1000, "3-5 short punchy #tags"),
    INSTAGRAM("Instagram", 125, 2200, "20-30 mixed-reach #tags"),
    IG_REELS("Instagram Reels", 125, 2200, "15-25 #tags, trending first"),
    FACEBOOK("Facebook", 120, 5000, "3-5 #tags only"),
    TIKTOK("TikTok", 100, 2200, "4-6 niche #tags"),
    X("X / Twitter", 100, 280, "1-3 #tags"),
    LINKEDIN("LinkedIn", 150, 3000, "3-5 professional #tags"),
    PINTEREST("Pinterest", 100, 500, "5-8 keyword #tags")
}

data class ContentPack(
    val titles: List<String> = emptyList(),
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val hook: String = "",
    val thumbnailText: String = "",
    val bestTimes: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val raw: String = ""
)

data class VideoIdea(
    val title: String,
    val angle: String,
    val hook: String,
    val whyItWorks: String
)

class ContentAi(private val client: OpenRouterClient) {

    private val persona = """
        You are an elite social-media growth strategist and video editor's assistant.
        You know YouTube, Shorts, Instagram Reels, Facebook, TikTok, X, LinkedIn and
        Pinterest algorithms deeply: watch time, retention curves, CTR, hook strength,
        saves/shares weighting, SEO keyword placement and hashtag reach tiers.
        You write like a human creator, never corporate. Be specific and punchy.
        Always answer with VALID JSON only, no markdown fences.
    """.trimIndent()

    /** Full content pack: titles, description, hashtags, keywords, best posting times. */
    suspend fun contentPack(
        topic: String,
        platform: Platform,
        audience: String,
        language: String,
        region: String
    ): Result<ContentPack> {
        val prompt = """
            Topic / video content: "$topic"
            Platform: ${platform.label}
            Target audience: ${audience.ifBlank { "general creators" }}
            Output language: $language
            Creator region/timezone: $region

            Produce a complete publishing pack optimised for ${platform.label}.
            Title max ${platform.maxTitle} chars. Description max ${platform.maxDesc} chars.
            Hashtags: ${platform.tagStyle}.

            Research mentally against how this platform ranks content today, plus what
            similar high-performing videos, Google search intent and Wikipedia-level
            topical authority suggest. Mix high-volume and low-competition keywords.

            Return JSON exactly:
            {
              "titles": ["8 scroll-stopping title options, best first"],
              "hook": "the first 3 seconds of spoken/text hook",
              "thumbnailText": "3-5 words max for the thumbnail",
              "description": "full optimised description with keywords in the first 2 lines, line breaks as \\n, CTA, and a timestamp placeholder section if useful",
              "hashtags": ["#tag", "..."],
              "keywords": ["15 SEO keywords/search phrases ranked by opportunity"],
              "bestTimes": ["3-5 concrete posting slots in $region local time with weekday, e.g. 'Tue 7:30-9:00 PM — peak commute + evening scroll'"],
              "tips": ["5 platform-specific tips to maximise reach for THIS video"]
            }
        """.trimIndent()

        return client.chat(persona, prompt, temperature = 0.85, maxTokens = 2200, jsonMode = true)
            .mapCatching { raw -> parsePack(raw) }
    }

    /** Video idea generator. */
    suspend fun ideas(niche: String, platform: Platform, count: Int = 10): Result<List<VideoIdea>> {
        val prompt = """
            Niche: "$niche". Platform: ${platform.label}.
            Give $count fresh video ideas that could realistically go viral in the next 30 days.
            Avoid generic advice; use specific angles, contrarian takes, or trending formats.
            JSON: {"ideas":[{"title":"","angle":"","hook":"","whyItWorks":""}]}
        """.trimIndent()
        return client.chat(persona, prompt, 0.95, 2000, jsonMode = true).mapCatching { raw ->
            val arr = JSONObject(clean(raw)).getJSONArray("ideas")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VideoIdea(
                    o.optString("title"), o.optString("angle"),
                    o.optString("hook"), o.optString("whyItWorks")
                )
            }
        }
    }

    /** Script / voiceover writer. */
    suspend fun script(topic: String, seconds: Int, platform: Platform, tone: String): Result<String> {
        val prompt = """
            Write a ${seconds}-second ${platform.label} video script about "$topic".
            Tone: $tone. Roughly ${(seconds * 2.4).toInt()} words.
            Structure: HOOK (0-3s), VALUE beats with timestamps, CTA.
            Plain narration text I can feed to text-to-speech, with [bracketed] b-roll notes on their own lines.
            JSON: {"script":"..."}
        """.trimIndent()
        return client.chat(persona, prompt, 0.9, 2000, jsonMode = true)
            .mapCatching { JSONObject(clean(it)).optString("script") }
    }

    /** Turn narration into timed caption chunks for the caption renderer. */
    suspend fun captionChunks(text: String, wordsPerChunk: Int = 3): Result<List<String>> {
        val prompt = """
            Split this narration into caption chunks of about $wordsPerChunk words,
            breaking at natural speech pauses so each chunk reads well on screen.
            Narration: "$text"
            JSON: {"chunks":["...","..."]}
        """.trimIndent()
        return client.chat(persona, prompt, 0.3, 2000, jsonMode = true).mapCatching { raw ->
            val arr = JSONObject(clean(raw)).getJSONArray("chunks")
            (0 until arr.length()).map { arr.getString(it) }
        }
    }

    /** Competitive/trend analysis for a topic. */
    suspend fun trendAnalysis(topic: String, platform: Platform, region: String): Result<String> {
        val prompt = """
            Analyse the topic "$topic" for ${platform.label} in $region.
            Cover: current trend direction, audience intent, saturation level,
            3 content gaps to exploit, the format that's working right now,
            and the single biggest mistake creators make on this topic.
            JSON: {"analysis":"markdown text"}
        """.trimIndent()
        return client.chat(persona, prompt, 0.7, 1800, jsonMode = true)
            .mapCatching { JSONObject(clean(it)).optString("analysis") }
    }

    private fun clean(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) s = s.substringAfter('\n').substringBeforeLast("```").trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun parsePack(raw: String): ContentPack {
        val o = JSONObject(clean(raw))
        fun list(k: String): List<String> {
            val a = o.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        return ContentPack(
            titles = list("titles"),
            description = o.optString("description"),
            hashtags = list("hashtags"),
            keywords = list("keywords"),
            hook = o.optString("hook"),
            thumbnailText = o.optString("thumbnailText"),
            bestTimes = list("bestTimes"),
            tips = list("tips"),
            raw = raw
        )
    }
}
