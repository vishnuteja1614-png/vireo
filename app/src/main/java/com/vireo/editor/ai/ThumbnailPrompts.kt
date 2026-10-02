package com.vireo.editor.ai

/** Ready-made thumbnail art directions that reliably get clicks. */
object ThumbnailPrompts {

    data class Preset(val id: String, val label: String, val recipe: String)

    val PRESETS = listOf(
        Preset("mrbeast", "High-Energy", "ultra saturated colours, shocked facial expression, huge bold arrow, glowing outline, dramatic rim light, high contrast, 4k, YouTube thumbnail"),
        Preset("clean_pro", "Clean Pro", "minimal composition, single subject, soft studio lighting, lots of negative space on the left for title text, muted premium palette"),
        Preset("cinematic", "Cinematic", "anamorphic lens look, teal and orange grade, shallow depth of field, volumetric light, film grain, movie poster framing"),
        Preset("tech", "Tech Review", "product centred on a dark gradient backdrop, neon purple and cyan rim lighting, reflective surface, crisp studio product photography"),
        Preset("tutorial", "Tutorial", "clear split composition, before and after halves, bright even lighting, simple readable layout, bold directional arrow between halves"),
        Preset("vlog", "Vlog", "candid lifestyle shot, golden hour sunlight, warm tones, authentic documentary feel, shallow depth of field"),
        Preset("gaming", "Gaming", "dynamic action pose, explosive particle effects, neon rim light, dark background with glowing accents, hyper detailed"),
        Preset("food", "Food", "overhead flat lay, vivid fresh ingredients, natural window light, steam rising, appetising macro detail"),
        Preset("finance", "Finance", "clean infographic style, rising green chart, dark navy background, gold accents, professional and trustworthy"),
        Preset("shock", "Shock / Drama", "extreme close-up, dramatic hard shadows, red warning accents, intense mood, high contrast, attention grabbing"),
        Preset("minimal_text", "Typographic", "bold typographic poster, giant condensed sans-serif, two-colour palette, geometric shapes, swiss design"),
        Preset("3d", "3D Render", "glossy 3d render, soft global illumination, pastel gradient background, floating objects, octane render style")
    )

    fun build(topic: String, preset: Preset, aspect: String, extra: String = ""): String =
        buildString {
            append("A scroll-stopping thumbnail image about: $topic. ")
            append("Style: ${preset.recipe}. ")
            append("Composition: subject off-centre, clear focal point, readable at small size on a phone. ")
            append("Aspect ratio $aspect. ")
            append("No watermark, no gibberish lettering. ")
            if (extra.isNotBlank()) append(extra)
        }
}
