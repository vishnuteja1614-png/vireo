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
 * Green-screen / chroma key as a real Media3 GPU effect.
 *
 * Media3 ships colour filters, matrix transforms and overlays, but has no
 * colour-keying effect - the documented route is to supply your own
 * [GlEffect] wrapping a [androidx.media3.effect.GlShaderProgram], which is what
 * this does. [BaseGlShaderProgram] handles output texture allocation.
 *
 * The shader keys in YUV chrominance space so the matte is unaffected by how
 * brightly the subject is lit, and includes spill suppression for the green
 * bounce that lands on hair and shoulders.
 *
 * **Important limitation:** MediaCodec strips alpha when encoding, so a keyed
 * region cannot be exported as transparency in a single sequence. The shader
 * therefore composites the keyed area against [backColorArgb]. Set that to the
 * colour you want behind the subject; true transparency only works when the
 * clip is layered over another sequence.
 */
@UnstableApi
class ChromaKeyEffect(
    /** Colour to remove, as 0xRRGGBB. Pure green by default. */
    private val keyColorRgb: Int = 0x00FF00,
    /** Colour painted where the key was removed, as 0xRRGGBB. */
    private val backColorArgb: Int = 0x000000,
    /** 0..1 - how close a pixel must be to the key colour. Typical 0.3-0.45. */
    private val similarity: Float = 0.40f,
    /** 0..1 - matte edge softness. Typical 0.05-0.12. */
    private val smoothness: Float = 0.08f,
    /** 0..1 - how aggressively to desaturate spill. Typical 0.1-0.3. */
    private val spill: Float = 0.15f
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        ChromaKeyShaderProgram(
            context = context,
            useHdr = useHdr,
            keyColor = keyColorRgb.toRgbFloats(),
            backColor = backColorArgb.toRgbFloats(),
            similarity = similarity.coerceIn(0f, 1f),
            smoothness = smoothness.coerceIn(0.001f, 1f),
            spill = spill.coerceIn(0.001f, 1f)
        )

    /** No-op when the key is effectively disabled, so the frame skips the GPU pass. */
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = similarity <= 0f

    private fun Int.toRgbFloats(): FloatArray = floatArrayOf(
        ((this shr 16) and 0xFF) / 255f,
        ((this shr 8) and 0xFF) / 255f,
        (this and 0xFF) / 255f
    )
}

@UnstableApi
private class ChromaKeyShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val keyColor: FloatArray,
    private val backColor: FloatArray,
    private val similarity: Float,
    private val smoothness: Float,
    private val spill: Float
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(context, VERTEX_PATH, FRAGMENT_PATH)
    } catch (e: Exception) {
        throw VideoFrameProcessingException("Could not compile the chroma key shader", e)
    }

    init {
        program.setFloatsUniform("uKeyColor", keyColor)
        program.setFloatsUniform("uBackColor", backColor)
        program.setFloatUniform("uSimilarity", similarity)
        program.setFloatUniform("uSmoothness", smoothness)
        program.setFloatUniform("uSpill", spill)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size =
        Size(inputWidth, inputHeight)

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
        const val FRAGMENT_PATH = "shaders/fragment_chroma.glsl"
    }
}
