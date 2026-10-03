package com.vireo.editor.engine

/**
 * Catalogue of free Microsoft neural voices available to [EdgeTts].
 * No API key, no sign-up, no quota page. Covers 10 Indian languages including Telugu.
 */
data class EdgeVoice(
    val id: String,
    val name: String,
    val locale: String,
    val gender: String,
    val style: String
) {
    val language: String get() = EdgeVoices.LANGUAGE_NAMES[locale] ?: locale
    val label: String get() = "$name — $language"
}

object EdgeVoices {

    val LANGUAGE_NAMES = mapOf(
        "en-US" to "English (US)", "en-GB" to "English (UK)", "en-IN" to "English (India)",
        "en-AU" to "English (Australia)", "en-CA" to "English (Canada)", "en-IE" to "English (Ireland)",
        "en-NZ" to "English (NZ)", "en-ZA" to "English (South Africa)",
        "hi-IN" to "Hindi", "te-IN" to "Telugu", "ta-IN" to "Tamil", "kn-IN" to "Kannada",
        "ml-IN" to "Malayalam", "mr-IN" to "Marathi", "gu-IN" to "Gujarati", "bn-IN" to "Bengali",
        "pa-IN" to "Punjabi", "ur-IN" to "Urdu",
        "es-ES" to "Spanish", "es-MX" to "Spanish (Mexico)", "fr-FR" to "French", "de-DE" to "German",
        "it-IT" to "Italian", "pt-BR" to "Portuguese (Brazil)", "ja-JP" to "Japanese", "ko-KR" to "Korean",
        "zh-CN" to "Chinese", "ar-SA" to "Arabic", "ru-RU" to "Russian", "id-ID" to "Indonesian",
        "tr-TR" to "Turkish", "vi-VN" to "Vietnamese", "th-TH" to "Thai", "nl-NL" to "Dutch",
        "pl-PL" to "Polish", "fil-PH" to "Filipino"
    )

    const val DEFAULT = "en-US-AndrewNeural"

    val FEATURED = listOf(
        "en-US-AndrewNeural", "en-US-AvaNeural", "en-US-BrianNeural", "en-US-EmmaNeural",
        "en-IN-PrabhatNeural", "en-IN-NeerjaExpressiveNeural",
        "hi-IN-MadhurNeural", "hi-IN-SwaraNeural",
        "te-IN-MohanNeural", "te-IN-ShrutiNeural",
        "en-GB-RyanNeural", "en-GB-SoniaNeural"
    )

    val ALL: List<EdgeVoice> = listOf(
        EdgeVoice("en-US-AnaNeural", "Ana", "en-US", "Female", "Cute"),
        EdgeVoice("en-US-AndrewNeural", "Andrew", "en-US", "Male", "Warm, Confident, Authentic, Honest"),
        EdgeVoice("en-US-AndrewMultilingualNeural", "Andrew Multilingual", "en-US", "Male", "Warm, Confident, Authentic, Honest"),
        EdgeVoice("en-US-AriaNeural", "Aria", "en-US", "Female", "Positive, Confident"),
        EdgeVoice("en-US-AvaNeural", "Ava", "en-US", "Female", "Expressive, Caring, Pleasant, Friendly"),
        EdgeVoice("en-US-AvaMultilingualNeural", "Ava Multilingual", "en-US", "Female", "Expressive, Caring, Pleasant, Friendly"),
        EdgeVoice("en-US-BrianNeural", "Brian", "en-US", "Male", "Approachable, Casual, Sincere"),
        EdgeVoice("en-US-BrianMultilingualNeural", "Brian Multilingual", "en-US", "Male", "Approachable, Casual, Sincere"),
        EdgeVoice("en-US-ChristopherNeural", "Christopher", "en-US", "Male", "Reliable, Authority"),
        EdgeVoice("en-US-EmmaNeural", "Emma", "en-US", "Female", "Cheerful, Clear, Conversational"),
        EdgeVoice("en-US-EmmaMultilingualNeural", "Emma Multilingual", "en-US", "Female", "Cheerful, Clear, Conversational"),
        EdgeVoice("en-US-EricNeural", "Eric", "en-US", "Male", "Rational"),
        EdgeVoice("en-US-GuyNeural", "Guy", "en-US", "Male", "Passion"),
        EdgeVoice("en-US-JennyNeural", "Jenny", "en-US", "Female", "Friendly, Considerate, Comfort"),
        EdgeVoice("en-US-MichelleNeural", "Michelle", "en-US", "Female", "Friendly, Pleasant"),
        EdgeVoice("en-US-RogerNeural", "Roger", "en-US", "Male", "Lively"),
        EdgeVoice("en-US-SteffanNeural", "Steffan", "en-US", "Male", "Rational"),
        EdgeVoice("en-GB-LibbyNeural", "Libby", "en-GB", "Female", "Friendly, Positive"),
        EdgeVoice("en-GB-MaisieNeural", "Maisie", "en-GB", "Female", "Friendly, Positive"),
        EdgeVoice("en-GB-RyanNeural", "Ryan", "en-GB", "Male", "Friendly, Positive"),
        EdgeVoice("en-GB-SoniaNeural", "Sonia", "en-GB", "Female", "Friendly, Positive"),
        EdgeVoice("en-GB-ThomasNeural", "Thomas", "en-GB", "Male", "Friendly, Positive"),
        EdgeVoice("en-IN-NeerjaNeural", "Neerja", "en-IN", "Female", "Friendly, Positive"),
        EdgeVoice("en-IN-NeerjaExpressiveNeural", "Neerja Expressive", "en-IN", "Female", "Friendly, Positive"),
        EdgeVoice("en-IN-PrabhatNeural", "Prabhat", "en-IN", "Male", "Friendly, Positive"),
        EdgeVoice("en-AU-NatashaNeural", "Natasha", "en-AU", "Female", "Friendly, Positive"),
        EdgeVoice("en-AU-WilliamMultilingualNeural", "William Multilingual", "en-AU", "Male", "Friendly, Positive"),
        EdgeVoice("en-CA-ClaraNeural", "Clara", "en-CA", "Female", "Friendly, Positive"),
        EdgeVoice("en-CA-LiamNeural", "Liam", "en-CA", "Male", "Friendly, Positive"),
        EdgeVoice("en-IE-ConnorNeural", "Connor", "en-IE", "Male", "Friendly, Positive"),
        EdgeVoice("en-IE-EmilyNeural", "Emily", "en-IE", "Female", "Friendly, Positive"),
        EdgeVoice("en-NZ-MitchellNeural", "Mitchell", "en-NZ", "Male", "Friendly, Positive"),
        EdgeVoice("en-NZ-MollyNeural", "Molly", "en-NZ", "Female", "Friendly, Positive"),
        EdgeVoice("en-ZA-LeahNeural", "Leah", "en-ZA", "Female", "Friendly, Positive"),
        EdgeVoice("en-ZA-LukeNeural", "Luke", "en-ZA", "Male", "Friendly, Positive"),
        EdgeVoice("hi-IN-MadhurNeural", "Madhur", "hi-IN", "Male", "Friendly, Positive"),
        EdgeVoice("hi-IN-SwaraNeural", "Swara", "hi-IN", "Female", "Friendly, Positive"),
        EdgeVoice("te-IN-MohanNeural", "Mohan", "te-IN", "Male", "Friendly, Positive"),
        EdgeVoice("te-IN-ShrutiNeural", "Shruti", "te-IN", "Female", "Friendly, Positive"),
        EdgeVoice("ta-IN-PallaviNeural", "Pallavi", "ta-IN", "Female", "Friendly, Positive"),
        EdgeVoice("ta-IN-ValluvarNeural", "Valluvar", "ta-IN", "Male", "Friendly, Positive"),
        EdgeVoice("kn-IN-GaganNeural", "Gagan", "kn-IN", "Male", "Friendly, Positive"),
        EdgeVoice("kn-IN-SapnaNeural", "Sapna", "kn-IN", "Female", "Friendly, Positive"),
        EdgeVoice("ml-IN-MidhunNeural", "Midhun", "ml-IN", "Male", "Friendly, Positive"),
        EdgeVoice("ml-IN-SobhanaNeural", "Sobhana", "ml-IN", "Female", "Friendly, Positive"),
        EdgeVoice("mr-IN-AarohiNeural", "Aarohi", "mr-IN", "Female", "Friendly, Positive"),
        EdgeVoice("mr-IN-ManoharNeural", "Manohar", "mr-IN", "Male", "Friendly, Positive"),
        EdgeVoice("gu-IN-DhwaniNeural", "Dhwani", "gu-IN", "Female", "Friendly, Positive"),
        EdgeVoice("gu-IN-NiranjanNeural", "Niranjan", "gu-IN", "Male", "Friendly, Positive"),
        EdgeVoice("bn-IN-BashkarNeural", "Bashkar", "bn-IN", "Male", "Friendly, Positive"),
        EdgeVoice("bn-IN-TanishaaNeural", "Tanishaa", "bn-IN", "Female", "Friendly, Positive"),
        EdgeVoice("ur-IN-GulNeural", "Gul", "ur-IN", "Female", "Friendly, Positive"),
        EdgeVoice("ur-IN-SalmanNeural", "Salman", "ur-IN", "Male", "Friendly, Positive"),
        EdgeVoice("es-ES-AlvaroNeural", "Alvaro", "es-ES", "Male", "Friendly, Positive"),
        EdgeVoice("es-ES-ElviraNeural", "Elvira", "es-ES", "Female", "Friendly, Positive"),
        EdgeVoice("es-ES-XimenaNeural", "Ximena", "es-ES", "Female", "Friendly, Positive"),
        EdgeVoice("es-MX-DaliaNeural", "Dalia", "es-MX", "Female", "Friendly, Positive"),
        EdgeVoice("es-MX-JorgeNeural", "Jorge", "es-MX", "Male", "Friendly, Positive"),
        EdgeVoice("fr-FR-DeniseNeural", "Denise", "fr-FR", "Female", "Friendly, Positive"),
        EdgeVoice("fr-FR-EloiseNeural", "Eloise", "fr-FR", "Female", "Friendly, Positive"),
        EdgeVoice("fr-FR-HenriNeural", "Henri", "fr-FR", "Male", "Friendly, Positive"),
        EdgeVoice("fr-FR-RemyMultilingualNeural", "Remy Multilingual", "fr-FR", "Male", "Friendly, Positive"),
        EdgeVoice("fr-FR-VivienneMultilingualNeural", "Vivienne Multilingual", "fr-FR", "Female", "Friendly, Positive"),
        EdgeVoice("de-DE-AmalaNeural", "Amala", "de-DE", "Female", "Friendly, Positive"),
        EdgeVoice("de-DE-ConradNeural", "Conrad", "de-DE", "Male", "Friendly, Positive"),
        EdgeVoice("de-DE-FlorianMultilingualNeural", "Florian Multilingual", "de-DE", "Male", "Friendly, Positive"),
        EdgeVoice("de-DE-KatjaNeural", "Katja", "de-DE", "Female", "Friendly, Positive"),
        EdgeVoice("de-DE-KillianNeural", "Killian", "de-DE", "Male", "Friendly, Positive"),
        EdgeVoice("de-DE-SeraphinaMultilingualNeural", "Seraphina Multilingual", "de-DE", "Female", "Friendly, Positive"),
        EdgeVoice("it-IT-DiegoNeural", "Diego", "it-IT", "Male", "Friendly, Positive"),
        EdgeVoice("it-IT-ElsaNeural", "Elsa", "it-IT", "Female", "Friendly, Positive"),
        EdgeVoice("it-IT-GiuseppeMultilingualNeural", "Giuseppe Multilingual", "it-IT", "Male", "Friendly, Positive"),
        EdgeVoice("it-IT-IsabellaNeural", "Isabella", "it-IT", "Female", "Friendly, Positive"),
        EdgeVoice("pt-BR-AntonioNeural", "Antonio", "pt-BR", "Male", "Friendly, Positive"),
        EdgeVoice("pt-BR-FranciscaNeural", "Francisca", "pt-BR", "Female", "Friendly, Positive"),
        EdgeVoice("pt-BR-ThalitaMultilingualNeural", "Thalita Multilingual", "pt-BR", "Female", "Friendly, Positive"),
        EdgeVoice("ja-JP-KeitaNeural", "Keita", "ja-JP", "Male", "Friendly, Positive"),
        EdgeVoice("ja-JP-NanamiNeural", "Nanami", "ja-JP", "Female", "Friendly, Positive"),
        EdgeVoice("ko-KR-HyunsuMultilingualNeural", "Hyunsu Multilingual", "ko-KR", "Male", "Friendly, Positive"),
        EdgeVoice("ko-KR-InJoonNeural", "InJoon", "ko-KR", "Male", "Friendly, Positive"),
        EdgeVoice("ko-KR-SunHiNeural", "SunHi", "ko-KR", "Female", "Friendly, Positive"),
        EdgeVoice("zh-CN-XiaoxiaoNeural", "Xiaoxiao", "zh-CN", "Female", "Warm"),
        EdgeVoice("zh-CN-XiaoyiNeural", "Xiaoyi", "zh-CN", "Female", "Lively"),
        EdgeVoice("zh-CN-YunjianNeural", "Yunjian", "zh-CN", "Male", "Passion"),
        EdgeVoice("zh-CN-YunxiNeural", "Yunxi", "zh-CN", "Male", "Lively, Sunshine"),
        EdgeVoice("zh-CN-YunxiaNeural", "Yunxia", "zh-CN", "Male", "Cute"),
        EdgeVoice("zh-CN-YunyangNeural", "Yunyang", "zh-CN", "Male", "Professional, Reliable"),
        EdgeVoice("ar-SA-HamedNeural", "Hamed", "ar-SA", "Male", "Friendly, Positive"),
        EdgeVoice("ar-SA-ZariyahNeural", "Zariyah", "ar-SA", "Female", "Friendly, Positive"),
        EdgeVoice("ru-RU-DmitryNeural", "Dmitry", "ru-RU", "Male", "Friendly, Positive"),
        EdgeVoice("ru-RU-SvetlanaNeural", "Svetlana", "ru-RU", "Female", "Friendly, Positive"),
        EdgeVoice("id-ID-ArdiNeural", "Ardi", "id-ID", "Male", "Friendly, Positive"),
        EdgeVoice("id-ID-GadisNeural", "Gadis", "id-ID", "Female", "Friendly, Positive"),
        EdgeVoice("tr-TR-AhmetNeural", "Ahmet", "tr-TR", "Male", "Friendly, Positive"),
        EdgeVoice("tr-TR-EmelNeural", "Emel", "tr-TR", "Female", "Friendly, Positive"),
        EdgeVoice("vi-VN-HoaiMyNeural", "HoaiMy", "vi-VN", "Female", "Friendly, Positive"),
        EdgeVoice("vi-VN-NamMinhNeural", "NamMinh", "vi-VN", "Male", "Friendly, Positive"),
        EdgeVoice("th-TH-NiwatNeural", "Niwat", "th-TH", "Male", "Friendly, Positive"),
        EdgeVoice("th-TH-PremwadeeNeural", "Premwadee", "th-TH", "Female", "Friendly, Positive"),
        EdgeVoice("nl-NL-ColetteNeural", "Colette", "nl-NL", "Female", "Friendly, Positive"),
        EdgeVoice("nl-NL-FennaNeural", "Fenna", "nl-NL", "Female", "Friendly, Positive"),
        EdgeVoice("nl-NL-MaartenNeural", "Maarten", "nl-NL", "Male", "Friendly, Positive"),
        EdgeVoice("pl-PL-MarekNeural", "Marek", "pl-PL", "Male", "Friendly, Positive"),
        EdgeVoice("pl-PL-ZofiaNeural", "Zofia", "pl-PL", "Female", "Friendly, Positive"),
        EdgeVoice("fil-PH-AngeloNeural", "Angelo", "fil-PH", "Male", "Friendly, Positive"),
        EdgeVoice("fil-PH-BlessicaNeural", "Blessica", "fil-PH", "Female", "Friendly, Positive"),
    )

    fun byId(id: String): EdgeVoice? = ALL.firstOrNull { it.id == id }
    fun featured(): List<EdgeVoice> = FEATURED.mapNotNull { id -> byId(id) }
    fun locales(): List<String> = ALL.map { it.locale }.distinct()
    fun forLocale(locale: String): List<EdgeVoice> = ALL.filter { it.locale == locale }
    fun indian(): List<EdgeVoice> = ALL.filter { it.locale.endsWith("-IN") }
}
