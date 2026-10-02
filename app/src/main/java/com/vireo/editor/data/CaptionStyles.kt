package com.vireo.editor.data

/**
 * 120+ caption / subtitle styles.
 *
 * Design references are the popular open-source auto-caption projects
 * (ass-subtitle presets used by captacity, auto-subtitle, whisper-burn and
 * the "Hormozi style" karaoke renderers), re-implemented here natively so
 * each style is drawn by our own Compose + Media3 overlay renderer.
 */
enum class CaptionCategory(val label: String) {
    VIRAL("Viral"), CLEAN("Clean"), KARAOKE("Karaoke"), BOLD("Bold"),
    NEON("Neon"), RETRO("Retro"), CINEMATIC("Cinematic"), PLAYFUL("Playful"),
    MINIMAL("Minimal"), SOCIAL("Social")
}

enum class CaptionAnim {
    NONE, FADE, POP, BOUNCE, SLIDE_UP, TYPEWRITER, WORD_BY_WORD,
    KARAOKE_FILL, SHAKE, ZOOM_PULSE, WAVE, FLIP
}

data class CaptionStyle(
    val id: String,
    val label: String,
    val category: CaptionCategory,
    val fontFamily: String = "Inter",
    val fontWeight: Int = 800,
    val sizeSp: Float = 34f,
    val allCaps: Boolean = false,
    val textColor: Long = 0xFFFFFFFF,
    val highlightColor: Long = 0xFFFFD60A,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 6f,
    val shadowRadius: Float = 8f,
    val shadowColor: Long = 0xBF000000,
    val backgroundColor: Long = 0x00000000,
    val cornerRadius: Float = 10f,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.15f,
    val maxWordsPerLine: Int = 4,
    val anim: CaptionAnim = CaptionAnim.POP,
    val wordHighlight: Boolean = false,
    val emojiBoost: Boolean = false
)

object CaptionStyles {

    val ALL: List<CaptionStyle> = buildList {

        // ---------- VIRAL (18) ----------
        add(CaptionStyle("hormozi", "Hormozi", CaptionCategory.VIRAL, sizeSp = 40f, allCaps = true,
            highlightColor = 0xFF00FF00, strokeWidth = 9f, maxWordsPerLine = 3,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("hormozi_yellow", "Hormozi Yellow", CaptionCategory.VIRAL, sizeSp = 40f,
            allCaps = true, highlightColor = 0xFFFFD60A, strokeWidth = 9f, maxWordsPerLine = 3,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("beast", "Beast Mode", CaptionCategory.VIRAL, sizeSp = 44f, allCaps = true,
            textColor = 0xFFFFFFFF, highlightColor = 0xFFFF2D55, strokeWidth = 10f,
            anim = CaptionAnim.ZOOM_PULSE, wordHighlight = true))
        add(CaptionStyle("pop_green", "Pop Green", CaptionCategory.VIRAL, highlightColor = 0xFF39FF14,
            anim = CaptionAnim.BOUNCE, wordHighlight = true))
        add(CaptionStyle("shock", "Shock", CaptionCategory.VIRAL, sizeSp = 42f, allCaps = true,
            highlightColor = 0xFFFF0000, anim = CaptionAnim.SHAKE, wordHighlight = true))
        add(CaptionStyle("money", "Money", CaptionCategory.VIRAL, highlightColor = 0xFF2ECC71,
            emojiBoost = true, anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("podcast", "Podcast", CaptionCategory.VIRAL, sizeSp = 32f,
            backgroundColor = 0x99000000, anim = CaptionAnim.WORD_BY_WORD))
        add(CaptionStyle("hook", "Hook", CaptionCategory.VIRAL, sizeSp = 46f, allCaps = true,
            strokeWidth = 11f, anim = CaptionAnim.ZOOM_PULSE))
        add(CaptionStyle("reels_pop", "Reels Pop", CaptionCategory.VIRAL, anim = CaptionAnim.POP,
            backgroundColor = 0xFFFFFFFF, textColor = 0xFF000000, strokeWidth = 0f))
        add(CaptionStyle("tiktok_classic", "TikTok Classic", CaptionCategory.VIRAL, sizeSp = 30f,
            backgroundColor = 0xCC000000, strokeWidth = 0f, anim = CaptionAnim.FADE))
        add(CaptionStyle("mr_beast_bold", "Mega Bold", CaptionCategory.VIRAL, sizeSp = 48f,
            allCaps = true, strokeWidth = 12f, anim = CaptionAnim.BOUNCE, wordHighlight = true))
        add(CaptionStyle("viral_blue", "Viral Blue", CaptionCategory.VIRAL, highlightColor = 0xFF00A8FF,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("viral_purple", "Viral Purple", CaptionCategory.VIRAL, highlightColor = 0xFF8B5CF6,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("viral_orange", "Viral Orange", CaptionCategory.VIRAL, highlightColor = 0xFFFF7A00,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("storytime", "Storytime", CaptionCategory.VIRAL, sizeSp = 34f,
            anim = CaptionAnim.WORD_BY_WORD, maxWordsPerLine = 5))
        add(CaptionStyle("fact_drop", "Fact Drop", CaptionCategory.VIRAL, allCaps = true,
            backgroundColor = 0xFF111111, cornerRadius = 4f, anim = CaptionAnim.SLIDE_UP))
        add(CaptionStyle("quiz", "Quiz", CaptionCategory.VIRAL, highlightColor = 0xFFFFD60A,
            anim = CaptionAnim.FLIP, wordHighlight = true))
        add(CaptionStyle("countdown", "Countdown", CaptionCategory.VIRAL, sizeSp = 50f, allCaps = true,
            anim = CaptionAnim.ZOOM_PULSE))

        // ---------- CLEAN (12) ----------
        add(CaptionStyle("clean_white", "Clean White", CaptionCategory.CLEAN, fontWeight = 600,
            sizeSp = 30f, strokeWidth = 3f, anim = CaptionAnim.FADE))
        add(CaptionStyle("clean_black_bar", "Black Bar", CaptionCategory.CLEAN, fontWeight = 600,
            backgroundColor = 0xCC000000, strokeWidth = 0f, anim = CaptionAnim.FADE))
        add(CaptionStyle("clean_shadow", "Soft Shadow", CaptionCategory.CLEAN, fontWeight = 600,
            strokeWidth = 0f, shadowRadius = 14f, anim = CaptionAnim.FADE))
        add(CaptionStyle("netflix", "Netflix", CaptionCategory.CLEAN, fontWeight = 500, sizeSp = 28f,
            strokeWidth = 0f, shadowRadius = 10f, anim = CaptionAnim.NONE))
        add(CaptionStyle("youtube_default", "YouTube Default", CaptionCategory.CLEAN, fontWeight = 500,
            backgroundColor = 0xB3000000, strokeWidth = 0f, cornerRadius = 2f, anim = CaptionAnim.NONE))
        add(CaptionStyle("broadcast", "Broadcast", CaptionCategory.CLEAN, fontWeight = 700,
            backgroundColor = 0xFF003366, strokeWidth = 0f, anim = CaptionAnim.SLIDE_UP))
        add(CaptionStyle("news_ticker", "News Ticker", CaptionCategory.CLEAN, fontWeight = 700,
            allCaps = true, backgroundColor = 0xFFCC0000, strokeWidth = 0f, cornerRadius = 0f))
        add(CaptionStyle("interview", "Interview", CaptionCategory.CLEAN, fontWeight = 500, sizeSp = 26f,
            strokeWidth = 2f, maxWordsPerLine = 7, anim = CaptionAnim.FADE))
        add(CaptionStyle("doc_clean", "Documentary", CaptionCategory.CLEAN, fontWeight = 400,
            sizeSp = 27f, strokeWidth = 2f, letterSpacing = 0.5f))
        add(CaptionStyle("clean_outline", "Outline Only", CaptionCategory.CLEAN, strokeWidth = 5f,
            shadowRadius = 0f))
        add(CaptionStyle("clean_rounded", "Rounded Box", CaptionCategory.CLEAN, fontWeight = 600,
            backgroundColor = 0xE6FFFFFF, textColor = 0xFF111111, strokeWidth = 0f, cornerRadius = 22f))
        add(CaptionStyle("clean_grey", "Grey Box", CaptionCategory.CLEAN, backgroundColor = 0xCC2A2A33,
            strokeWidth = 0f, cornerRadius = 12f))

        // ---------- KARAOKE (12) ----------
        add(CaptionStyle("karaoke_yellow", "Karaoke Yellow", CaptionCategory.KARAOKE,
            highlightColor = 0xFFFFD60A, anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("karaoke_green", "Karaoke Green", CaptionCategory.KARAOKE,
            highlightColor = 0xFF39FF14, anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("karaoke_pink", "Karaoke Pink", CaptionCategory.KARAOKE,
            highlightColor = 0xFFFF2D9B, anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("karaoke_cyan", "Karaoke Cyan", CaptionCategory.KARAOKE,
            highlightColor = 0xFF22D3EE, anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("karaoke_box", "Karaoke Box", CaptionCategory.KARAOKE,
            backgroundColor = 0xCC000000, highlightColor = 0xFFFFD60A,
            anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("lyric_fade", "Lyric Fade", CaptionCategory.KARAOKE, fontWeight = 600,
            anim = CaptionAnim.FADE, maxWordsPerLine = 6))
        add(CaptionStyle("lyric_bounce", "Lyric Bounce", CaptionCategory.KARAOKE,
            anim = CaptionAnim.BOUNCE, wordHighlight = true))
        add(CaptionStyle("lyric_wave", "Lyric Wave", CaptionCategory.KARAOKE, anim = CaptionAnim.WAVE))
        add(CaptionStyle("sing_along", "Sing Along", CaptionCategory.KARAOKE, sizeSp = 38f,
            highlightColor = 0xFFFF7A00, anim = CaptionAnim.KARAOKE_FILL, wordHighlight = true))
        add(CaptionStyle("duet", "Duet", CaptionCategory.KARAOKE, highlightColor = 0xFF8B5CF6,
            anim = CaptionAnim.WORD_BY_WORD, wordHighlight = true))
        add(CaptionStyle("rap_fast", "Rap Fast", CaptionCategory.KARAOKE, sizeSp = 42f, allCaps = true,
            maxWordsPerLine = 2, anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("beat_sync", "Beat Sync", CaptionCategory.KARAOKE, sizeSp = 40f,
            anim = CaptionAnim.ZOOM_PULSE, wordHighlight = true))

        // ---------- BOLD (12) ----------
        add(CaptionStyle("impact", "Impact", CaptionCategory.BOLD, fontWeight = 900, sizeSp = 44f,
            allCaps = true, strokeWidth = 10f))
        add(CaptionStyle("meme_top", "Meme", CaptionCategory.BOLD, fontWeight = 900, sizeSp = 42f,
            allCaps = true, strokeWidth = 12f, anim = CaptionAnim.NONE))
        add(CaptionStyle("heavy_black", "Heavy Black", CaptionCategory.BOLD, fontWeight = 900,
            sizeSp = 46f, letterSpacing = -1f))
        add(CaptionStyle("stencil", "Stencil", CaptionCategory.BOLD, fontWeight = 800, allCaps = true,
            letterSpacing = 3f))
        add(CaptionStyle("sports", "Sports", CaptionCategory.BOLD, fontWeight = 900, allCaps = true,
            textColor = 0xFFFFFFFF, highlightColor = 0xFFFF2D55, anim = CaptionAnim.SLIDE_UP))
        add(CaptionStyle("gym", "Gym", CaptionCategory.BOLD, fontWeight = 900, sizeSp = 44f,
            allCaps = true, highlightColor = 0xFFFF7A00, anim = CaptionAnim.SHAKE, wordHighlight = true))
        add(CaptionStyle("alert", "Alert", CaptionCategory.BOLD, fontWeight = 900, allCaps = true,
            backgroundColor = 0xFFFF0000, strokeWidth = 0f, anim = CaptionAnim.SHAKE))
        add(CaptionStyle("warning", "Warning", CaptionCategory.BOLD, fontWeight = 900, allCaps = true,
            textColor = 0xFF000000, backgroundColor = 0xFFFFD60A, strokeWidth = 0f))
        add(CaptionStyle("bold_underline", "Bold Underline", CaptionCategory.BOLD, fontWeight = 800,
            highlightColor = 0xFF22D3EE, wordHighlight = true))
        add(CaptionStyle("double_stroke", "Double Stroke", CaptionCategory.BOLD, strokeWidth = 14f))
        add(CaptionStyle("mega_caps", "Mega Caps", CaptionCategory.BOLD, sizeSp = 52f, allCaps = true,
            maxWordsPerLine = 2, anim = CaptionAnim.ZOOM_PULSE))
        add(CaptionStyle("headline", "Headline", CaptionCategory.BOLD, fontWeight = 900, sizeSp = 38f,
            backgroundColor = 0xFF000000, strokeWidth = 0f, cornerRadius = 0f))

        // ---------- NEON (10) ----------
        add(CaptionStyle("neon_cyan", "Neon Cyan", CaptionCategory.NEON, textColor = 0xFF22D3EE,
            shadowColor = 0xFF22D3EE, shadowRadius = 24f, strokeWidth = 0f))
        add(CaptionStyle("neon_pink", "Neon Pink", CaptionCategory.NEON, textColor = 0xFFFF2D9B,
            shadowColor = 0xFFFF2D9B, shadowRadius = 24f, strokeWidth = 0f))
        add(CaptionStyle("neon_green", "Neon Green", CaptionCategory.NEON, textColor = 0xFF39FF14,
            shadowColor = 0xFF39FF14, shadowRadius = 24f, strokeWidth = 0f))
        add(CaptionStyle("neon_purple", "Neon Purple", CaptionCategory.NEON, textColor = 0xFFB388FF,
            shadowColor = 0xFF8B5CF6, shadowRadius = 26f, strokeWidth = 0f))
        add(CaptionStyle("cyberpunk", "Cyberpunk", CaptionCategory.NEON, allCaps = true,
            textColor = 0xFF00FFF0, shadowColor = 0xFFFF00AA, shadowRadius = 20f, letterSpacing = 2f))
        add(CaptionStyle("synthwave", "Synthwave", CaptionCategory.NEON, allCaps = true,
            textColor = 0xFFFF6EC7, shadowColor = 0xFF7A5CFF, shadowRadius = 22f))
        add(CaptionStyle("hologram", "Hologram", CaptionCategory.NEON, textColor = 0xFFAEEBFF,
            shadowColor = 0xFF00A8FF, shadowRadius = 18f, anim = CaptionAnim.WAVE))
        add(CaptionStyle("laser", "Laser", CaptionCategory.NEON, textColor = 0xFFFF2D55,
            shadowColor = 0xFFFF2D55, shadowRadius = 28f, anim = CaptionAnim.ZOOM_PULSE))
        add(CaptionStyle("glow_white", "Glow White", CaptionCategory.NEON, shadowColor = 0xFFFFFFFF,
            shadowRadius = 22f, strokeWidth = 0f))
        add(CaptionStyle("electric", "Electric", CaptionCategory.NEON, textColor = 0xFFFFD60A,
            shadowColor = 0xFF00A8FF, shadowRadius = 20f, anim = CaptionAnim.SHAKE))

        // ---------- RETRO (10) ----------
        add(CaptionStyle("vhs", "VHS", CaptionCategory.RETRO, fontWeight = 700, allCaps = true,
            textColor = 0xFFE8E8E8, shadowColor = 0xFFFF00AA, shadowRadius = 6f))
        add(CaptionStyle("crt", "CRT", CaptionCategory.RETRO, textColor = 0xFF39FF14, strokeWidth = 0f,
            shadowColor = 0xFF39FF14, shadowRadius = 10f, letterSpacing = 2f))
        add(CaptionStyle("eighties", "80s", CaptionCategory.RETRO, allCaps = true, textColor = 0xFFFFD60A,
            strokeColor = 0xFFFF2D9B, strokeWidth = 8f))
        add(CaptionStyle("nineties", "90s", CaptionCategory.RETRO, allCaps = true, textColor = 0xFF00FFF0,
            strokeColor = 0xFF7A5CFF, strokeWidth = 8f))
        add(CaptionStyle("arcade", "Arcade", CaptionCategory.RETRO, fontWeight = 900, allCaps = true,
            letterSpacing = 4f, textColor = 0xFFFFD60A))
        add(CaptionStyle("typewriter_old", "Typewriter", CaptionCategory.RETRO, fontWeight = 500,
            anim = CaptionAnim.TYPEWRITER, letterSpacing = 1.5f))
        add(CaptionStyle("film_noir", "Film Noir", CaptionCategory.RETRO, fontWeight = 600,
            textColor = 0xFFEDEDED, strokeWidth = 0f, shadowRadius = 16f))
        add(CaptionStyle("sepia", "Sepia", CaptionCategory.RETRO, textColor = 0xFFF0D9A8,
            strokeColor = 0xFF4A2F10, strokeWidth = 5f))
        add(CaptionStyle("polaroid", "Polaroid", CaptionCategory.RETRO, fontWeight = 500,
            backgroundColor = 0xFFFFFDF5, textColor = 0xFF222222, strokeWidth = 0f, cornerRadius = 4f))
        add(CaptionStyle("grunge", "Grunge", CaptionCategory.RETRO, fontWeight = 800, allCaps = true,
            textColor = 0xFFE0E0E0, strokeWidth = 7f, anim = CaptionAnim.SHAKE))

        // ---------- CINEMATIC (10) ----------
        add(CaptionStyle("cinema_bar", "Cinema Bar", CaptionCategory.CINEMATIC, fontWeight = 500,
            sizeSp = 26f, letterSpacing = 2f, strokeWidth = 0f, shadowRadius = 12f))
        add(CaptionStyle("trailer", "Trailer", CaptionCategory.CINEMATIC, fontWeight = 700,
            allCaps = true, letterSpacing = 6f, sizeSp = 30f, anim = CaptionAnim.FADE))
        add(CaptionStyle("epic", "Epic", CaptionCategory.CINEMATIC, fontWeight = 800, allCaps = true,
            letterSpacing = 8f, sizeSp = 34f, anim = CaptionAnim.ZOOM_PULSE))
        add(CaptionStyle("whisper", "Whisper", CaptionCategory.CINEMATIC, fontWeight = 300,
            sizeSp = 24f, letterSpacing = 3f, anim = CaptionAnim.FADE))
        add(CaptionStyle("credits", "Credits", CaptionCategory.CINEMATIC, fontWeight = 400,
            sizeSp = 22f, letterSpacing = 4f, anim = CaptionAnim.SLIDE_UP))
        add(CaptionStyle("lower_third", "Lower Third", CaptionCategory.CINEMATIC, fontWeight = 600,
            backgroundColor = 0xB3111111, strokeWidth = 0f, cornerRadius = 6f, anim = CaptionAnim.SLIDE_UP))
        add(CaptionStyle("quote", "Quote", CaptionCategory.CINEMATIC, fontWeight = 400, sizeSp = 28f,
            anim = CaptionAnim.FADE, maxWordsPerLine = 6))
        add(CaptionStyle("title_card", "Title Card", CaptionCategory.CINEMATIC, fontWeight = 700,
            allCaps = true, sizeSp = 40f, letterSpacing = 5f))
        add(CaptionStyle("subtitle_pro", "Subtitle Pro", CaptionCategory.CINEMATIC, fontWeight = 500,
            sizeSp = 27f, strokeWidth = 3f, maxWordsPerLine = 8))
        add(CaptionStyle("noir_caps", "Noir Caps", CaptionCategory.CINEMATIC, fontWeight = 700,
            allCaps = true, letterSpacing = 4f, textColor = 0xFFD9D9D9))

        // ---------- PLAYFUL (10) ----------
        add(CaptionStyle("bubble", "Bubble", CaptionCategory.PLAYFUL, fontWeight = 800,
            backgroundColor = 0xFFFF2D9B, strokeWidth = 0f, cornerRadius = 28f, anim = CaptionAnim.BOUNCE))
        add(CaptionStyle("candy", "Candy", CaptionCategory.PLAYFUL, textColor = 0xFFFF7AC6,
            strokeColor = 0xFFFFFFFF, strokeWidth = 7f, anim = CaptionAnim.BOUNCE))
        add(CaptionStyle("comic", "Comic", CaptionCategory.PLAYFUL, fontWeight = 900, allCaps = true,
            textColor = 0xFFFFD60A, strokeWidth = 9f, anim = CaptionAnim.POP))
        add(CaptionStyle("kids", "Kids", CaptionCategory.PLAYFUL, fontWeight = 800,
            textColor = 0xFF39FF14, strokeWidth = 8f, anim = CaptionAnim.WAVE))
        add(CaptionStyle("emoji_pop", "Emoji Pop", CaptionCategory.PLAYFUL, emojiBoost = true,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("sticker", "Sticker", CaptionCategory.PLAYFUL, fontWeight = 800,
            backgroundColor = 0xFFFFFFFF, textColor = 0xFF111111, strokeWidth = 0f,
            cornerRadius = 16f, anim = CaptionAnim.FLIP))
        add(CaptionStyle("rainbow", "Rainbow", CaptionCategory.PLAYFUL, wordHighlight = true,
            anim = CaptionAnim.WAVE))
        add(CaptionStyle("wobble", "Wobble", CaptionCategory.PLAYFUL, anim = CaptionAnim.SHAKE))
        add(CaptionStyle("balloon", "Balloon", CaptionCategory.PLAYFUL, fontWeight = 800,
            backgroundColor = 0xFF22D3EE, strokeWidth = 0f, cornerRadius = 30f, anim = CaptionAnim.BOUNCE))
        add(CaptionStyle("doodle", "Doodle", CaptionCategory.PLAYFUL, fontWeight = 600,
            strokeWidth = 5f, anim = CaptionAnim.TYPEWRITER))

        // ---------- MINIMAL (10) ----------
        add(CaptionStyle("mono", "Mono", CaptionCategory.MINIMAL, fontWeight = 400, sizeSp = 24f,
            letterSpacing = 1f, strokeWidth = 0f, shadowRadius = 6f))
        add(CaptionStyle("thin", "Thin", CaptionCategory.MINIMAL, fontWeight = 200, sizeSp = 28f,
            strokeWidth = 0f, shadowRadius = 8f))
        add(CaptionStyle("light_caps", "Light Caps", CaptionCategory.MINIMAL, fontWeight = 300,
            allCaps = true, letterSpacing = 5f, strokeWidth = 0f))
        add(CaptionStyle("underline_min", "Underline", CaptionCategory.MINIMAL, fontWeight = 500,
            strokeWidth = 0f, shadowRadius = 4f))
        add(CaptionStyle("left_align", "Left Align", CaptionCategory.MINIMAL, fontWeight = 500,
            maxWordsPerLine = 5, strokeWidth = 2f))
        add(CaptionStyle("tiny", "Tiny", CaptionCategory.MINIMAL, fontWeight = 500, sizeSp = 20f,
            strokeWidth = 2f))
        add(CaptionStyle("ghost", "Ghost", CaptionCategory.MINIMAL, fontWeight = 400,
            textColor = 0xB3FFFFFF, strokeWidth = 0f))
        add(CaptionStyle("pill", "Pill", CaptionCategory.MINIMAL, fontWeight = 600, sizeSp = 24f,
            backgroundColor = 0x99000000, strokeWidth = 0f, cornerRadius = 40f))
        add(CaptionStyle("edge", "Edge", CaptionCategory.MINIMAL, fontWeight = 600, strokeWidth = 3f,
            shadowRadius = 0f))
        add(CaptionStyle("clean_serif", "Serif", CaptionCategory.MINIMAL, fontFamily = "Serif",
            fontWeight = 500, sizeSp = 28f, strokeWidth = 2f))

        // ---------- SOCIAL (12) ----------
        add(CaptionStyle("ig_reels", "IG Reels", CaptionCategory.SOCIAL, fontWeight = 800, sizeSp = 32f,
            backgroundColor = 0xFFFFFFFF, textColor = 0xFF000000, strokeWidth = 0f,
            cornerRadius = 8f, anim = CaptionAnim.WORD_BY_WORD))
        add(CaptionStyle("ig_story", "IG Story", CaptionCategory.SOCIAL, fontWeight = 700,
            backgroundColor = 0xFFFF2D9B, strokeWidth = 0f, cornerRadius = 6f))
        add(CaptionStyle("yt_shorts", "YT Shorts", CaptionCategory.SOCIAL, fontWeight = 800,
            sizeSp = 36f, allCaps = true, highlightColor = 0xFFFF0000,
            anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("yt_long", "YouTube Long", CaptionCategory.SOCIAL, fontWeight = 600,
            sizeSp = 28f, backgroundColor = 0xB3000000, strokeWidth = 0f, maxWordsPerLine = 7))
        add(CaptionStyle("fb_reels", "FB Reels", CaptionCategory.SOCIAL, fontWeight = 800,
            highlightColor = 0xFF1877F2, anim = CaptionAnim.POP, wordHighlight = true))
        add(CaptionStyle("x_post", "X Post", CaptionCategory.SOCIAL, fontWeight = 600,
            backgroundColor = 0xFF000000, strokeWidth = 0f, cornerRadius = 10f))
        add(CaptionStyle("linkedin", "LinkedIn", CaptionCategory.SOCIAL, fontWeight = 600, sizeSp = 28f,
            backgroundColor = 0xFF0A66C2, strokeWidth = 0f, cornerRadius = 6f))
        add(CaptionStyle("snap", "Snap", CaptionCategory.SOCIAL, fontWeight = 700,
            backgroundColor = 0xFFFFFC00, textColor = 0xFF000000, strokeWidth = 0f))
        add(CaptionStyle("whatsapp_status", "WA Status", CaptionCategory.SOCIAL, fontWeight = 600,
            backgroundColor = 0xFF25D366, strokeWidth = 0f, cornerRadius = 14f))
        add(CaptionStyle("threads", "Threads", CaptionCategory.SOCIAL, fontWeight = 600,
            backgroundColor = 0xFF101010, strokeWidth = 0f, cornerRadius = 18f))
        add(CaptionStyle("pinterest", "Pinterest", CaptionCategory.SOCIAL, fontWeight = 700,
            backgroundColor = 0xFFE60023, strokeWidth = 0f, cornerRadius = 16f))
        add(CaptionStyle("shorts_hook", "Shorts Hook", CaptionCategory.SOCIAL, fontWeight = 900,
            sizeSp = 44f, allCaps = true, maxWordsPerLine = 3, strokeWidth = 10f,
            anim = CaptionAnim.ZOOM_PULSE, wordHighlight = true))
    }

    val COUNT = ALL.size
    fun byCategory(c: CaptionCategory) = ALL.filter { it.category == c }
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.first()
    val CATEGORIES = CaptionCategory.entries
}
