package com.vireo.editor.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbMatrix
import com.vireo.editor.data.Clip
import com.vireo.editor.data.FilterPreset

/**
 * Builds GPU colour effects for a clip using Media3's open-source effect pipeline.
 * Each preset is expressed as a 4x4 RGB matrix plus brightness/contrast/HSL tweaks.
 */
@UnstableApi
object FilterFactory {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    private fun presetMatrix(p: FilterPreset): FloatArray = when (p) {
        FilterPreset.NONE -> identity
        FilterPreset.VIVID -> floatArrayOf(
            1.18f, -0.06f, -0.06f, 0f,
            -0.06f, 1.18f, -0.06f, 0f,
            -0.06f, -0.06f, 1.18f, 0f,
            0f, 0f, 0f, 1f
        )
        FilterPreset.NOIR -> floatArrayOf(
            0.299f, 0.299f, 0.299f, 0f,
            0.587f, 0.587f, 0.587f, 0f,
            0.114f, 0.114f, 0.114f, 0f,
            0f, 0f, 0f, 1f
        )
        FilterPreset.FILM35 -> floatArrayOf(
            1.06f, 0.02f, 0.0f, 0f,
            0.02f, 1.0f, 0.02f, 0f,
            0.0f, 0.02f, 0.94f, 0f,
            0.02f, 0.01f, -0.01f, 1f
        )
        FilterPreset.TEAL -> floatArrayOf(
            0.92f, 0.0f, 0.06f, 0f,
            0.0f, 1.02f, 0.08f, 0f,
            0.04f, 0.06f, 1.12f, 0f,
            0f, 0f, 0.02f, 1f
        )
        FilterPreset.WARM -> floatArrayOf(
            1.14f, 0.0f, 0.0f, 0f,
            0.0f, 1.02f, 0.0f, 0f,
            0.0f, 0.0f, 0.88f, 0f,
            0.03f, 0.01f, -0.02f, 1f
        )
        FilterPreset.CYBER -> floatArrayOf(
            1.05f, -0.1f, 0.2f, 0f,
            -0.08f, 1.0f, 0.12f, 0f,
            0.18f, 0.08f, 1.22f, 0f,
            -0.02f, 0f, 0.04f, 1f
        )
        FilterPreset.FADE -> floatArrayOf(
            0.88f, 0.03f, 0.03f, 0f,
            0.03f, 0.88f, 0.03f, 0f,
            0.03f, 0.03f, 0.88f, 0f,
            0.08f, 0.08f, 0.09f, 1f
        )
    }

    /** Effects applied to a clip, in render order. */
    @UnstableApi
    fun effectsFor(clip: Clip): List<androidx.media3.common.Effect> {
        val out = mutableListOf<androidx.media3.common.Effect>()
        if (clip.filter != FilterPreset.NONE) {
            val m = presetMatrix(clip.filter)
            out += RgbMatrix { _, _ -> m }
        }
        if (clip.brightness != 0f) out += Brightness(clip.brightness.coerceIn(-1f, 1f))
        if (clip.contrast != 0f) out += Contrast(clip.contrast.coerceIn(-1f, 1f))
        if (clip.saturation != 1f) {
            out += HslAdjustment.Builder()
                .adjustSaturation(((clip.saturation - 1f) * 100f).coerceIn(-100f, 100f))
                .build()
        }
        return out
    }
}
