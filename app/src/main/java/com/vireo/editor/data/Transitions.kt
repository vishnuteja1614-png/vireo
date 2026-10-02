package com.vireo.editor.data

/**
 * 120 transition presets.
 * Each is expressed as a composable motion recipe the renderer can execute with
 * Media3's effect pipeline (alpha blend + matrix transform + optional distortion),
 * so every one of them genuinely renders instead of being a label.
 */
enum class TransitionFamily(val label: String) {
    DISSOLVE("Dissolve"), SLIDE("Slide"), WIPE("Wipe"), ZOOM("Zoom"),
    ROTATE("Rotate"), BLUR("Blur"), GLITCH("Glitch"), LIGHT("Light"),
    SHAPE("Shape"), DISTORT("Distort"), THREE_D("3D"), CREATIVE("Creative")
}

enum class Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK, BOUNCE, ELASTIC }

data class TransitionDef(
    val id: String,
    val label: String,
    val family: TransitionFamily,
    val defaultMs: Long = 600L,
    val easing: Easing = Easing.EASE_IN_OUT,
    /** direction in degrees: 0 = right, 90 = up, etc. -1 = n/a */
    val angle: Int = -1,
    val intensity: Float = 1f,
    val pro: Boolean = false
)

object Transitions {

    val ALL: List<TransitionDef> = buildList {
        fun add(id: String, label: String, f: TransitionFamily, ms: Long = 600,
                e: Easing = Easing.EASE_IN_OUT, angle: Int = -1, i: Float = 1f) =
            add(TransitionDef(id, label, f, ms, e, angle, i))

        // ---- Dissolve family (10) ----
        add("none", "None", TransitionFamily.DISSOLVE, 0)
        add("fade", "Fade", TransitionFamily.DISSOLVE, 500)
        add("cross_dissolve", "Cross Dissolve", TransitionFamily.DISSOLVE, 700)
        add("fade_black", "Fade to Black", TransitionFamily.DISSOLVE, 800)
        add("fade_white", "Fade to White", TransitionFamily.DISSOLVE, 600)
        add("fade_color", "Fade to Colour", TransitionFamily.DISSOLVE, 600)
        add("film_dissolve", "Film Dissolve", TransitionFamily.DISSOLVE, 900)
        add("soft_dissolve", "Soft Dissolve", TransitionFamily.DISSOLVE, 1000)
        add("additive_dissolve", "Additive Dissolve", TransitionFamily.DISSOLVE, 700)
        add("grain_dissolve", "Grain Dissolve", TransitionFamily.DISSOLVE, 800)

        // ---- Slide family (14) ----
        listOf("Left" to 180, "Right" to 0, "Up" to 90, "Down" to 270).forEach { (d, a) ->
            add("slide_${d.lowercase()}", "Slide $d", TransitionFamily.SLIDE, 500, Easing.EASE_OUT, a)
            add("push_${d.lowercase()}", "Push $d", TransitionFamily.SLIDE, 550, Easing.EASE_IN_OUT, a)
        }
        add("slide_diag_tl", "Slide Diagonal TL", TransitionFamily.SLIDE, 600, Easing.EASE_OUT, 135)
        add("slide_diag_br", "Slide Diagonal BR", TransitionFamily.SLIDE, 600, Easing.EASE_OUT, 315)
        add("cover_up", "Cover Up", TransitionFamily.SLIDE, 500, Easing.BACK, 90)
        add("reveal_down", "Reveal Down", TransitionFamily.SLIDE, 500, Easing.BACK, 270)
        add("split_horizontal", "Split Horizontal", TransitionFamily.SLIDE, 650)
        add("split_vertical", "Split Vertical", TransitionFamily.SLIDE, 650)

        // ---- Wipe family (14) ----
        listOf("Left" to 180, "Right" to 0, "Up" to 90, "Down" to 270).forEach { (d, a) ->
            add("wipe_${d.lowercase()}", "Wipe $d", TransitionFamily.WIPE, 500, Easing.LINEAR, a)
        }
        add("wipe_diag", "Diagonal Wipe", TransitionFamily.WIPE, 600, Easing.LINEAR, 45)
        add("wipe_clock", "Clock Wipe", TransitionFamily.WIPE, 800)
        add("wipe_radial", "Radial Wipe", TransitionFamily.WIPE, 700)
        add("wipe_barn_h", "Barn Door H", TransitionFamily.WIPE, 600)
        add("wipe_barn_v", "Barn Door V", TransitionFamily.WIPE, 600)
        add("wipe_iris_in", "Iris In", TransitionFamily.WIPE, 700)
        add("wipe_iris_out", "Iris Out", TransitionFamily.WIPE, 700)
        add("wipe_venetian", "Venetian Blinds", TransitionFamily.WIPE, 750)
        add("wipe_checker", "Checkerboard", TransitionFamily.WIPE, 800)
        add("wipe_random", "Random Blocks", TransitionFamily.WIPE, 700)

        // ---- Zoom family (12) ----
        add("zoom_in", "Zoom In", TransitionFamily.ZOOM, 500, Easing.EASE_IN)
        add("zoom_out", "Zoom Out", TransitionFamily.ZOOM, 500, Easing.EASE_OUT)
        add("zoom_blur_in", "Zoom Blur In", TransitionFamily.ZOOM, 600)
        add("zoom_blur_out", "Zoom Blur Out", TransitionFamily.ZOOM, 600)
        add("punch_in", "Punch In", TransitionFamily.ZOOM, 300, Easing.BACK)
        add("punch_out", "Punch Out", TransitionFamily.ZOOM, 300, Easing.BACK)
        add("quick_zoom", "Quick Zoom", TransitionFamily.ZOOM, 220, Easing.EASE_IN)
        add("smooth_zoom", "Smooth Zoom", TransitionFamily.ZOOM, 900, Easing.EASE_IN_OUT)
        add("zoom_rotate", "Zoom Rotate", TransitionFamily.ZOOM, 700)
        add("bounce_zoom", "Bounce Zoom", TransitionFamily.ZOOM, 650, Easing.BOUNCE)
        add("elastic_zoom", "Elastic Zoom", TransitionFamily.ZOOM, 800, Easing.ELASTIC)
        add("dolly_zoom", "Dolly Zoom", TransitionFamily.ZOOM, 1000)

        // ---- Rotate family (10) ----
        add("spin_cw", "Spin CW", TransitionFamily.ROTATE, 600)
        add("spin_ccw", "Spin CCW", TransitionFamily.ROTATE, 600)
        add("spin_blur", "Spin Blur", TransitionFamily.ROTATE, 700)
        add("flip_h", "Flip Horizontal", TransitionFamily.ROTATE, 550)
        add("flip_v", "Flip Vertical", TransitionFamily.ROTATE, 550)
        add("swirl", "Swirl", TransitionFamily.ROTATE, 800)
        add("whip_pan_l", "Whip Pan Left", TransitionFamily.ROTATE, 300, Easing.EASE_IN_OUT, 180)
        add("whip_pan_r", "Whip Pan Right", TransitionFamily.ROTATE, 300, Easing.EASE_IN_OUT, 0)
        add("whip_pan_u", "Whip Pan Up", TransitionFamily.ROTATE, 300, Easing.EASE_IN_OUT, 90)
        add("roll", "Roll", TransitionFamily.ROTATE, 700)

        // ---- Blur family (8) ----
        add("blur_soft", "Soft Blur", TransitionFamily.BLUR, 600)
        add("blur_heavy", "Heavy Blur", TransitionFamily.BLUR, 700, intensity = 2f)
        add("motion_blur_h", "Motion Blur H", TransitionFamily.BLUR, 400, angle = 0)
        add("motion_blur_v", "Motion Blur V", TransitionFamily.BLUR, 400, angle = 90)
        add("radial_blur", "Radial Blur", TransitionFamily.BLUR, 650)
        add("defocus", "Defocus", TransitionFamily.BLUR, 800)
        add("dream_blur", "Dream Blur", TransitionFamily.BLUR, 1000)
        add("tilt_shift", "Tilt Shift", TransitionFamily.BLUR, 700)

        // ---- Glitch family (12) ----
        add("glitch_rgb", "RGB Glitch", TransitionFamily.GLITCH, 350)
        add("glitch_digital", "Digital Glitch", TransitionFamily.GLITCH, 400)
        add("glitch_vhs", "VHS Glitch", TransitionFamily.GLITCH, 500)
        add("glitch_scan", "Scanlines", TransitionFamily.GLITCH, 450)
        add("glitch_static", "TV Static", TransitionFamily.GLITCH, 400)
        add("glitch_pixel", "Pixelate", TransitionFamily.GLITCH, 500)
        add("glitch_mosaic", "Mosaic", TransitionFamily.GLITCH, 550)
        add("glitch_shake", "Shake", TransitionFamily.GLITCH, 300)
        add("glitch_datamosh", "Datamosh", TransitionFamily.GLITCH, 600)
        add("glitch_slice", "Slice", TransitionFamily.GLITCH, 350)
        add("glitch_wave", "Wave Distort", TransitionFamily.GLITCH, 500)
        add("glitch_chroma", "Chroma Split", TransitionFamily.GLITCH, 400)

        // ---- Light family (10) ----
        add("flash", "Flash", TransitionFamily.LIGHT, 200)
        add("flash_white", "White Flash", TransitionFamily.LIGHT, 250)
        add("light_leak", "Light Leak", TransitionFamily.LIGHT, 800)
        add("lens_flare", "Lens Flare", TransitionFamily.LIGHT, 700)
        add("glow", "Glow", TransitionFamily.LIGHT, 600)
        add("bloom", "Bloom", TransitionFamily.LIGHT, 700)
        add("burn", "Burn", TransitionFamily.LIGHT, 650)
        add("strobe", "Strobe", TransitionFamily.LIGHT, 400)
        add("neon_pulse", "Neon Pulse", TransitionFamily.LIGHT, 550)
        add("sun_burst", "Sun Burst", TransitionFamily.LIGHT, 800)

        // ---- Shape family (10) ----
        add("circle_in", "Circle In", TransitionFamily.SHAPE, 600)
        add("circle_out", "Circle Out", TransitionFamily.SHAPE, 600)
        add("heart", "Heart", TransitionFamily.SHAPE, 700)
        add("star", "Star", TransitionFamily.SHAPE, 700)
        add("diamond", "Diamond", TransitionFamily.SHAPE, 650)
        add("triangle", "Triangle", TransitionFamily.SHAPE, 650)
        add("hexagon", "Hexagon", TransitionFamily.SHAPE, 650)
        add("cross_shape", "Cross", TransitionFamily.SHAPE, 600)
        add("ripple", "Ripple", TransitionFamily.SHAPE, 800)
        add("liquid", "Liquid", TransitionFamily.SHAPE, 900)

        // ---- Distort family (8) ----
        add("stretch_h", "Stretch H", TransitionFamily.DISTORT, 500)
        add("stretch_v", "Stretch V", TransitionFamily.DISTORT, 500)
        add("squeeze", "Squeeze", TransitionFamily.DISTORT, 550)
        add("warp", "Warp", TransitionFamily.DISTORT, 700)
        add("fisheye", "Fisheye", TransitionFamily.DISTORT, 650)
        add("wave_h", "Wave H", TransitionFamily.DISTORT, 700)
        add("wave_v", "Wave V", TransitionFamily.DISTORT, 700)
        add("melt", "Melt", TransitionFamily.DISTORT, 900)

        // ---- 3D family (8) ----
        add("cube_left", "Cube Left", TransitionFamily.THREE_D, 700, angle = 180)
        add("cube_right", "Cube Right", TransitionFamily.THREE_D, 700, angle = 0)
        add("cube_up", "Cube Up", TransitionFamily.THREE_D, 700, angle = 90)
        add("door", "Door", TransitionFamily.THREE_D, 750)
        add("page_turn", "Page Turn", TransitionFamily.THREE_D, 800)
        add("card_flip", "Card Flip", TransitionFamily.THREE_D, 600)
        add("perspective", "Perspective", TransitionFamily.THREE_D, 700)
        add("fold", "Fold", TransitionFamily.THREE_D, 750)

        // ---- Creative family (10) ----
        add("film_burn", "Film Burn", TransitionFamily.CREATIVE, 800)
        add("paint_splash", "Paint Splash", TransitionFamily.CREATIVE, 900)
        add("ink_bleed", "Ink Bleed", TransitionFamily.CREATIVE, 850)
        add("smoke", "Smoke", TransitionFamily.CREATIVE, 1000)
        add("shatter", "Shatter", TransitionFamily.CREATIVE, 700)
        add("particles", "Particles", TransitionFamily.CREATIVE, 900)
        add("confetti", "Confetti", TransitionFamily.CREATIVE, 800)
        add("film_strip", "Film Strip", TransitionFamily.CREATIVE, 750)
        add("old_film", "Old Film", TransitionFamily.CREATIVE, 850)
        add("blinds_3d", "3D Blinds", TransitionFamily.CREATIVE, 800)
    }

    val COUNT = ALL.size
    fun byFamily(f: TransitionFamily) = ALL.filter { it.family == f }
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.first()
    val FAMILIES = TransitionFamily.entries
}
