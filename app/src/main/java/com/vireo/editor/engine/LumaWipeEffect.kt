package com.vireo.editor.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * Luma-wipe transitions, modelled on how MLT (Shotcut, Kdenlive) and
 * libopenshot (OpenShot) implement them.
 *
 * Those editors do not write one shader per transition. They keep a folder of
 * grayscale "luma map" images and sweep a **threshold** across the map: pixels
 * darker than the threshold show the incoming frame, lighter ones still show
 * the outgoing frame, and a **softness** value feathers the boundary. Change
 * the map and you change the shape of the wipe - bars, iris, clock, blinds,
 * spiral - with no new code.
 *
 * Vireo adopts the same architecture but generates the maps procedurally in
 * the fragment shader, so 16 wipe shapes cost a few hundred bytes instead of
 * shipping 1920x1080 PGM files.
 *
 * Unlike [TransitionEffects], which moves the whole frame, a luma wipe
 * *reveals* the frame through a shape. Media3 can only sample one decoder at a
 * time, so the reveal happens from black rather than from the previous clip.
 */
@UnstableApi
class LumaWipeEffect(
    private val pattern: LumaPattern,
    /** Transition length in microseconds. */
    private val durationUs: Long,
    /** Shared with the clip's other effects so timestamps share an origin. */
    private val clock: ClipClock,
    /** 0 = razor-sharp edge, 1 = extremely soft. MLT uses the same range. */
    private val softness: Float = 0.12f,
    /** Reverse the reveal order, equivalent to Shotcut's "invert wipe". */
    private val invert: Boolean = false
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        LumaWipeShaderProgram(
            context = context,
            useHdr = useHdr,
            patternIndex = pattern.ordinal.toFloat(),
            durationUs = durationUs.coerceAtLeast(1L),
            clock = clock,
            softness = softness.coerceIn(0f, 1f),
            invert = if (invert) 1f else 0f
        )

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = durationUs <= 0
}

/**
 * The 16 procedurally generated luma maps.
 * Order must match the `lumaMap()` branches in `fragment_luma_wipe.glsl`.
 */
enum class LumaPattern(val label: String) {
    LINEAR_X("Wipe Right"),
    LINEAR_Y("Wipe Down"),
    BARN_H("Barn Door H"),
    BARN_V("Barn Door V"),
    IRIS("Iris Round"),
    BOX("Iris Box"),
    DIAMOND("Diamond"),
    CLOCK("Clock Wipe"),
    CLOCK_SYM("Clock Split"),
    SPIRAL("Spiral"),
    BLINDS_H("Blinds H"),
    BLINDS_V("Blinds V"),
    CHECKER("Checkerboard"),
    BURST("Star Burst"),
    CLOUD("Cloud Dissolve"),
    DIAGONAL("Diagonal");

    companion object {
        fun byId(id: String): LumaPattern? = entries.firstOrNull { it.name == id }
    }
}

@UnstableApi
private class LumaWipeShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val patternIndex: Float,
    private val durationUs: Long,
    private val clock: ClipClock,
    private val softness: Float,
    private val invert: Float
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(context, VERTEX_PATH, FRAGMENT_PATH)
    } catch (e: Exception) {
        throw VideoFrameProcessingException("Could not compile the luma wipe shader", e)
    }

    init {
        program.setFloatUniform("uPattern", patternIndex)
        program.setFloatUniform("uSoftness", softness)
        program.setFloatUniform("uInvert", invert)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // Threshold is recomputed every frame; the clock keeps the sweep
            // anchored to this clip rather than to the whole composition.
            val progress = (clock.localUs(presentationTimeUs).toFloat() / durationUs)
                .coerceIn(0f, 1f)

            program.use()
            program.setFloatUniform("uThreshold", progress)
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_PATH = "shaders/vertex_chroma.glsl"
        const val FRAGMENT_PATH = "shaders/fragment_luma_wipe.glsl"
    }
}
