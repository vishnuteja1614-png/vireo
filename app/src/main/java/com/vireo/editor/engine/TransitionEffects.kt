package com.vireo.editor.engine

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import com.vireo.editor.data.Easing
import com.vireo.editor.data.TransitionDef
import com.vireo.editor.data.TransitionFamily
import com.vireo.editor.data.Transitions
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns a [TransitionDef] into real Media3 GL effects so the 125 presets
 * actually render into the exported file instead of only existing in the picker.
 *
 * Two primitives cover every family, and both are frame-accurate because
 * Media3 hands us the presentation timestamp for each frame:
 *
 *  - [MatrixTransformation] drives geometry (slide, zoom, rotate, 3D, shape)
 *    in normalised device coordinates where the frame spans -1..1.
 *  - [RgbMatrix] drives colour (dissolve, blur-substitute, light flash, glitch)
 *    by scaling or shifting the RGB channels over time.
 *
 * A transition is applied as an *in* animation at the head of the clip it is
 * attached to, which is how slide/zoom/spin transitions behave in every mobile
 * editor. Media3 1.3.1 cannot sample two decoders at once, so a true A-to-B
 * cross-dissolve is emulated as a fade up from black over the incoming clip.
 */
object TransitionEffects {

    /**
     * Build the effect list for [transitionId] running for [durationMs]
     * at the start of a clip. Returns empty when there is nothing to do.
     */
    fun effectsFor(transitionId: String, durationMs: Long): List<Effect> {
        if (transitionId.isBlank() || transitionId == "none") return emptyList()
        val def = Transitions.ALL.firstOrNull { it.id == transitionId } ?: return emptyList()
        val durUs = (if (durationMs > 0) durationMs else def.defaultMs) * 1_000L
        if (durUs <= 0) return emptyList()
        return effectsFor(def, durUs)
    }

    private fun effectsFor(def: TransitionDef, durUs: Long): List<Effect> {
        val out = mutableListOf<Effect>()
        when (def.family) {

            TransitionFamily.DISSOLVE ->
                out += fade(durUs, def)

            TransitionFamily.SLIDE -> {
                out += slide(durUs, def)
                if (def.id.contains("fade")) out += fade(durUs, def)
            }

            TransitionFamily.WIPE -> {
                // A wipe reads as a fast directional push with a hard edge;
                // approximated by a short, snappier slide.
                out += slide(durUs, def, overshoot = 0f)
            }

            TransitionFamily.ZOOM -> {
                out += zoom(durUs, def)
                if (def.id.contains("blur") || def.id.contains("fade")) out += fade(durUs, def)
            }

            TransitionFamily.ROTATE -> out += spin(durUs, def)

            TransitionFamily.BLUR -> {
                // No cheap separable blur in 1.3.1: sell it as a soft, slightly
                // scaled fade, which reads as a defocus on playback.
                out += zoom(durUs, def, from = 1.12f)
                out += fade(durUs, def)
            }

            TransitionFamily.GLITCH -> {
                out += glitchShake(durUs, def)
                out += channelSplit(durUs, def)
            }

            TransitionFamily.LIGHT -> out += flash(durUs, def)

            TransitionFamily.SHAPE -> {
                out += zoom(durUs, def, from = 0f)
                out += fade(durUs, def)
            }

            TransitionFamily.DISTORT -> {
                out += stretch(durUs, def)
                out += fade(durUs, def)
            }

            TransitionFamily.THREE_D -> out += perspective(durUs, def)

            TransitionFamily.CREATIVE -> {
                out += spin(durUs, def)
                out += zoom(durUs, def, from = 0.45f)
                out += fade(durUs, def)
            }
        }
        return out
    }

    // ------------------------------------------------------------- primitives

    /** Fade up from black across the transition window. */
    private fun fade(durUs: Long, def: TransitionDef): RgbMatrix =
        RgbMatrix { presentationTimeUs, _ ->
            val t = ease(progress(presentationTimeUs, durUs), def.easing)
            scaleMatrix(t)
        }

    /** Hard flash to white then settle - the classic "light leak" hit. */
    private fun flash(durUs: Long, def: TransitionDef): RgbMatrix =
        RgbMatrix { presentationTimeUs, _ ->
            val t = progress(presentationTimeUs, durUs)
            // Peak brightness at the very start, decaying to normal.
            val boost = 1f + (1.9f * def.intensity) * (1f - t).pow(2.2f)
            scaleMatrix(boost)
        }

    /** RGB channel separation that decays - reads as digital corruption. */
    private fun channelSplit(durUs: Long, def: TransitionDef): RgbMatrix =
        RgbMatrix { presentationTimeUs, _ ->
            val t = progress(presentationTimeUs, durUs)
            val k = (1f - t) * 0.55f * def.intensity
            // Column-major 4x4: push red up, green down, leave blue.
            floatArrayOf(
                1f + k, 0f, 0f, 0f,
                0f, 1f - k * 0.5f, 0f, 0f,
                0f, 0f, 1f + k * 0.3f, 0f,
                0f, 0f, 0f, 1f
            )
        }

    /** Translate the frame in from [TransitionDef.angle] degrees. */
    private fun slide(durUs: Long, def: TransitionDef, overshoot: Float = 1f): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val raw = progress(presentationTimeUs, durUs)
            val t = ease(raw, if (overshoot == 0f) Easing.EASE_OUT else def.easing)
            val rad = Math.toRadians((if (def.angle >= 0) def.angle else 0).toDouble())
            // distance remaining, in NDC units (2.0 = a full frame width)
            val d = (1f - t) * 2f * def.intensity
            Matrix().apply {
                postTranslate((-cos(rad) * d).toFloat(), (-sin(rad) * d).toFloat())
            }
        }

    /** Scale in (or out) toward the resting frame. */
    private fun zoom(durUs: Long, def: TransitionDef, from: Float = -1f): MatrixTransformation {
        val start = if (from >= 0f) from else if (def.id.contains("out")) 1.8f else 0.35f
        return MatrixTransformation { presentationTimeUs ->
            val t = ease(progress(presentationTimeUs, durUs), def.easing)
            val s = lerp(start, 1f, t).coerceAtLeast(0.001f)
            Matrix().apply { postScale(s, s) }
        }
    }

    /** Rotate into place, optionally scaling up at the same time. */
    private fun spin(durUs: Long, def: TransitionDef): MatrixTransformation {
        val turns = if (def.id.contains("360") || def.id.contains("whip")) 1f else 0.5f
        val dir = if (def.id.contains("ccw") || def.id.contains("left")) -1f else 1f
        return MatrixTransformation { presentationTimeUs ->
            val t = ease(progress(presentationTimeUs, durUs), def.easing)
            val angle = dir * turns * 360f * (1f - t) * def.intensity
            val s = lerp(0.6f, 1f, t)
            Matrix().apply {
                postScale(s, s)
                postRotate(angle)
            }
        }
    }

    /** Non-uniform squash/stretch that settles to square. */
    private fun stretch(durUs: Long, def: TransitionDef): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val t = ease(progress(presentationTimeUs, durUs), def.easing)
            val k = (1f - t) * def.intensity
            val sx = lerp(1f + k * 1.4f, 1f, t)
            val sy = lerp(1f - k * 0.55f, 1f, t).coerceAtLeast(0.05f)
            Matrix().apply { postScale(sx, sy) }
        }

    /** Fake 3D card flip: horizontal squeeze through zero plus a slight tilt. */
    private fun perspective(durUs: Long, def: TransitionDef): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val t = ease(progress(presentationTimeUs, durUs), def.easing)
            val angle = (1f - t) * 90f * def.intensity
            val sx = abs(cos(Math.toRadians(angle.toDouble())).toFloat()).coerceAtLeast(0.02f)
            Matrix().apply {
                postScale(sx, lerp(0.88f, 1f, t))
                postRotate((1f - t) * 4f * def.intensity)
            }
        }

    /** Decaying random-feeling jitter driven by the timestamp. */
    private fun glitchShake(durUs: Long, def: TransitionDef): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val t = progress(presentationTimeUs, durUs)
            val decay = (1f - t).pow(1.5f) * 0.08f * def.intensity
            // Deterministic pseudo-noise so the render is reproducible.
            val n1 = sin(presentationTimeUs * 0.00037).toFloat()
            val n2 = cos(presentationTimeUs * 0.00051).toFloat()
            Matrix().apply { postTranslate(n1 * decay, n2 * decay * 0.5f) }
        }

    // ----------------------------------------------------------------- helpers

    /** 0f at the clip start, 1f once the transition window has elapsed. */
    private fun progress(presentationTimeUs: Long, durUs: Long): Float =
        (presentationTimeUs.toFloat() / durUs).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Column-major 4x4 RGB scale (brightness) matrix. */
    private fun scaleMatrix(v: Float): FloatArray {
        val s = v.coerceIn(0f, 4f)
        return floatArrayOf(
            s, 0f, 0f, 0f,
            0f, s, 0f, 0f,
            0f, 0f, s, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun ease(t: Float, easing: Easing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            Easing.LINEAR -> x
            Easing.EASE_IN -> x * x * x
            Easing.EASE_OUT -> 1f - (1f - x).pow(3)
            Easing.EASE_IN_OUT -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            Easing.BACK -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
            }
            Easing.BOUNCE -> bounce(x)
            Easing.ELASTIC -> {
                if (x == 0f || x == 1f) x
                else {
                    val c4 = (2f * Math.PI.toFloat()) / 3f
                    2f.pow(-10f * x) * sin((x * 10f - 0.75f) * c4) + 1f
                }
            }
        }
    }

    private fun bounce(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { val v = x - 1.5f / d1; n1 * v * v + 0.75f }
            x < 2.5f / d1 -> { val v = x - 2.25f / d1; n1 * v * v + 0.9375f }
            else -> { val v = x - 2.625f / d1; n1 * v * v + 0.984375f }
        }
    }
}
