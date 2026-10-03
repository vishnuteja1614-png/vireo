package com.vireo.editor.engine

import android.graphics.Color
import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.SingleColorLut
import com.vireo.editor.data.Clip
import com.vireo.editor.data.PanZoom

/**
 * Motion and framing effects: Ken Burns pan/zoom, crop, and colour LUTs.
 *
 * These are the "desktop editor" basics Vireo was missing. All three are built
 * on effects Media3 already provides, so they render in the export with no
 * extra dependencies:
 *
 *  - pan/zoom animates a [MatrixTransformation] over the clip's duration
 *  - crop uses [Crop], which operates in normalised device coordinates
 *  - LUTs use [SingleColorLut], Media3's own colour lookup implementation
 */
@UnstableApi
object MotionEffects {

    /**
     * Ken Burns move: smoothly interpolates scale and position across the clip.
     *
     * Needs the clip's own duration to pace the animation, and a [ClipClock]
     * because frame timestamps are cumulative across a sequence rather than
     * restarting per clip.
     */
    fun panZoom(preset: PanZoom, clipDurationMs: Long, clock: ClipClock): List<Effect> {
        if (preset == PanZoom.NONE || clipDurationMs <= 0) return emptyList()
        val durUs = clipDurationMs * 1_000L

        // start scale, end scale, start offset, end offset (NDC units)
        val (sz, ez, so, eo) = when (preset) {
            PanZoom.ZOOM_IN -> Spec(1f, 1.25f, 0f to 0f, 0f to 0f)
            PanZoom.ZOOM_OUT -> Spec(1.25f, 1f, 0f to 0f, 0f to 0f)
            PanZoom.PAN_LEFT -> Spec(1.2f, 1.2f, 0.18f to 0f, -0.18f to 0f)
            PanZoom.PAN_RIGHT -> Spec(1.2f, 1.2f, -0.18f to 0f, 0.18f to 0f)
            PanZoom.PAN_UP -> Spec(1.2f, 1.2f, 0f to 0.18f, 0f to -0.18f)
            PanZoom.PAN_DOWN -> Spec(1.2f, 1.2f, 0f to -0.18f, 0f to 0.18f)
            PanZoom.ZOOM_IN_LEFT -> Spec(1f, 1.3f, 0.12f to 0f, -0.1f to 0f)
            PanZoom.ZOOM_OUT_RIGHT -> Spec(1.3f, 1f, -0.12f to 0f, 0.1f to 0f)
            PanZoom.NONE -> return emptyList()
        }

        return listOf(
            MatrixTransformation { presentationTimeUs ->
                val t = smooth((clock.localUs(presentationTimeUs).toFloat() / durUs).coerceIn(0f, 1f))
                val scale = sz + (ez - sz) * t
                val dx = so.first + (eo.first - so.first) * t
                val dy = so.second + (eo.second - so.second) * t
                Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
            }
        )
    }

    /**
     * Crop to a rectangle expressed as 0..1 fractions of the source frame.
     * Media3's [Crop] works in NDC (-1..1), so the fractions are remapped here.
     */
    fun crop(left: Float, top: Float, right: Float, bottom: Float): List<Effect> {
        // Nothing to do for a full-frame crop.
        if (left <= 0f && top <= 0f && right >= 1f && bottom >= 1f) return emptyList()

        val l = (left.coerceIn(0f, 1f) * 2f) - 1f
        val r = (right.coerceIn(0f, 1f) * 2f) - 1f
        // Fractions run top-down, NDC runs bottom-up.
        val t = 1f - (top.coerceIn(0f, 1f) * 2f)
        val b = 1f - (bottom.coerceIn(0f, 1f) * 2f)

        // Crop rejects an inverted or zero-area rectangle.
        if (r - l < 0.02f || t - b < 0.02f) return emptyList()
        return listOf(Crop(l, r, b, t))
    }

    /**
     * Cinematic colour grade as a true 3D lookup table.
     *
     * Generated procedurally rather than shipping .cube files, so the APK stays
     * small. Each preset maps the RGB cube through a tone curve plus a
     * shadow/highlight tint - the same structure a .cube file encodes.
     */
    fun lut(preset: LutPreset, size: Int = 17): List<Effect> {
        if (preset == LutPreset.NONE) return emptyList()
        val cube = Array(size) { r ->
            Array(size) { g ->
                IntArray(size) { b ->
                    val rf = r / (size - 1f)
                    val gf = g / (size - 1f)
                    val bf = b / (size - 1f)
                    preset.map(rf, gf, bf)
                }
            }
        }
        return listOf(SingleColorLut.createFromCube(cube))
    }

    /** All motion/colour effects for a clip, in render order. */
    fun effectsFor(clip: Clip, clock: ClipClock): List<Effect> {
        val out = mutableListOf<Effect>()
        out += crop(clip.cropLeft, clip.cropTop, clip.cropRight, clip.cropBottom)
        out += lut(runCatching { LutPreset.valueOf(clip.lutId) }.getOrDefault(LutPreset.NONE))
        out += panZoom(clip.panZoom, clip.outputDurationMs, clock)
        if (clip.chromaKey) {
            out += ChromaKeyEffect(
                keyColorRgb = clip.chromaColorRgb,
                backColorArgb = clip.chromaBackRgb,
                similarity = clip.chromaSimilarity,
                smoothness = clip.chromaSmoothness,
                spill = clip.chromaSpill
            )
        }
        return out
    }

    /** Ease-in-out so a Ken Burns move starts and ends gently. */
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private data class Spec(
        val startScale: Float,
        val endScale: Float,
        val startOffset: Pair<Float, Float>,
        val endOffset: Pair<Float, Float>
    )
}

/**
 * Film-style colour grades, generated as 3D LUTs at runtime.
 * Each entry maps a normalised RGB triple to a graded colour.
 */
enum class LutPreset(val label: String) {
    NONE("None"),
    TEAL_ORANGE("Teal & Orange"),
    BLEACH_BYPASS("Bleach Bypass"),
    GOLDEN_HOUR("Golden Hour"),
    MOODY_BLUE("Moody Blue"),
    VINTAGE_FILM("Vintage Film"),
    CYBERPUNK("Cyberpunk"),
    MATTE_BLACK("Matte Black"),
    SUNNY_POP("Sunny Pop");

    fun map(r: Float, g: Float, b: Float): Int {
        val lum = r * 0.2126f + g * 0.7152f + b * 0.0722f
        var nr = r; var ng = g; var nb = b

        when (this) {
            NONE -> Unit
            TEAL_ORANGE -> {
                // Warm the highlights, cool the shadows - the blockbuster look.
                nr = r + (lum - 0.5f) * 0.22f
                nb = b - (lum - 0.5f) * 0.22f
                ng = g + (lum - 0.5f) * 0.03f
            }
            BLEACH_BYPASS -> {
                val d = lum * 0.55f
                nr = r * 0.45f + d; ng = g * 0.45f + d; nb = b * 0.45f + d
                nr = contrast(nr, 1.3f); ng = contrast(ng, 1.3f); nb = contrast(nb, 1.3f)
            }
            GOLDEN_HOUR -> {
                nr = contrast(r * 1.12f + 0.04f, 1.05f)
                ng = g * 1.02f + 0.02f
                nb = b * 0.86f
            }
            MOODY_BLUE -> {
                nr = r * 0.88f
                ng = g * 0.95f
                nb = contrast(b * 1.15f + 0.03f, 1.08f)
            }
            VINTAGE_FILM -> {
                // Lifted blacks and a faded, slightly yellow cast.
                nr = r * 0.9f + 0.09f
                ng = g * 0.88f + 0.08f
                nb = b * 0.82f + 0.11f
            }
            CYBERPUNK -> {
                nr = contrast(r * 1.1f + (1f - lum) * 0.12f, 1.15f)
                ng = g * 0.92f
                nb = contrast(b * 1.25f + lum * 0.08f, 1.15f)
            }
            MATTE_BLACK -> {
                nr = r * 0.82f + 0.12f; ng = g * 0.82f + 0.12f; nb = b * 0.84f + 0.13f
            }
            SUNNY_POP -> {
                val s = 1.28f
                nr = contrast(lum + (r - lum) * s, 1.1f)
                ng = contrast(lum + (g - lum) * s, 1.1f)
                nb = contrast(lum + (b - lum) * s, 1.1f)
            }
        }
        return Color.rgb(to255(nr), to255(ng), to255(nb))
    }

    private fun contrast(v: Float, k: Float) = ((v - 0.5f) * k) + 0.5f
    private fun to255(v: Float) = (v.coerceIn(0f, 1f) * 255f).toInt()
}
