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
 * Fits a frame into the project's aspect ratio and fills the letterbox bars.
 *
 * Media3's `Presentation` can letterbox, but the bars are always solid black
 * and by the time it has run the pixels outside the fit region no longer
 * exist. The blurred-background look that social editors use therefore has to
 * be produced in the same pass that performs the fit, which is what this does.
 *
 * Modes:
 *  - [FillMode.COLOR] - flat colour bars
 *  - [FillMode.BLUR]  - a blurred, zoomed-to-cover copy of the frame itself
 *
 * The shader program reports the **target** size from `configure`, so this
 * effect replaces `Presentation` rather than running alongside it.
 */
@UnstableApi
class AspectFillEffect(
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val mode: FillMode = FillMode.BLUR,
    /** Flat fill colour as 0xRRGGBB. Ignored in [FillMode.BLUR]. */
    private val fillColorRgb: Int = 0x000000,
    /** Blur spread in source UV units. 0.02-0.06 reads well. */
    private val blurAmount: Float = 0.035f
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        AspectFillShaderProgram(
            context = context,
            useHdr = useHdr,
            targetWidth = targetWidth.coerceAtLeast(2),
            targetHeight = targetHeight.coerceAtLeast(2),
            mode = mode,
            fillColorRgb = fillColorRgb,
            blurAmount = blurAmount.coerceIn(0.001f, 0.2f)
        )
}

enum class FillMode { COLOR, BLUR }

@UnstableApi
private class AspectFillShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val mode: FillMode,
    private val fillColorRgb: Int,
    private val blurAmount: Float
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(context, VERTEX_PATH, FRAGMENT_PATH)
    } catch (e: Exception) {
        throw VideoFrameProcessingException("Could not compile the aspect fill shader", e)
    }

    init {
        program.setFloatUniform("uDstAspect", targetWidth.toFloat() / targetHeight.toFloat())
        program.setFloatUniform("uMode", if (mode == FillMode.BLUR) 1f else 0f)
        program.setFloatUniform("uBlurAmount", blurAmount)
        program.setFloatsUniform(
            "uFillColor",
            floatArrayOf(
                ((fillColorRgb shr 16) and 0xFF) / 255f,
                ((fillColorRgb shr 8) and 0xFF) / 255f,
                (fillColorRgb and 0xFF) / 255f
            )
        )
    }

    /**
     * Reports the canvas size rather than the input size: this effect is what
     * changes the frame's shape, so downstream effects see the final geometry.
     */
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        program.setFloatUniform(
            "uSrcAspect",
            inputWidth.coerceAtLeast(1).toFloat() / inputHeight.coerceAtLeast(1).toFloat()
        )
        return Size(targetWidth, targetHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
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
        const val FRAGMENT_PATH = "shaders/fragment_aspect_fill.glsl"
    }
}
