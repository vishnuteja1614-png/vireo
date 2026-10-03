package com.vireo.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.vireo.editor.data.CaptionAnim
import com.vireo.editor.data.CaptionStyle
import com.vireo.editor.data.TextOverlay as VTextOverlay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Burns a styled caption into the exported video.
 *
 * Every one of the 116 [CaptionStyle] presets is drawn here with a real Android
 * [Canvas] - stroke, drop shadow, rounded background plate, letter spacing,
 * all-caps, word wrapping and per-word highlighting - then handed to Media3 as a
 * texture. The 12 [CaptionAnim] modes are evaluated per frame from the
 * presentation timestamp, so pops, typewriters and karaoke fills genuinely
 * animate in the output file rather than only in the preview.
 */
class CaptionOverlay(
    private val cue: VTextOverlay,
    private val style: CaptionStyle,
    private val frameWidth: Int,
    private val frameHeight: Int,
    /** Timeline offset of the clip this overlay is attached to. */
    private val clipStartMs: Long
) : BitmapOverlay() {

    private val startUs = (cue.startMs - clipStartMs).coerceAtLeast(0L) * 1_000L
    private val endUs = (cue.endMs - clipStartMs).coerceAtLeast(0L) * 1_000L
    private val durationUs = (endUs - startUs).coerceAtLeast(1L)

    private val words: List<String> = cue.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Rendered frames are expensive; animation is quantised and cached. */
    private val cache = HashMap<Int, Bitmap>()
    private var blank: Bitmap? = null

    private val scale = frameHeight / 720f

    // --------------------------------------------------------------- overlay

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (presentationTimeUs < startUs || presentationTimeUs > endUs) return blankBitmap()
        val t = ((presentationTimeUs - startUs).toFloat() / durationUs).coerceIn(0f, 1f)
        // 60 animation steps is smoother than the eye resolves, and caps memory.
        val step = (t * 60f).toInt().coerceIn(0, 60)
        return cache.getOrPut(step) { render(step / 60f) }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val visible = presentationTimeUs in startUs..endUs
        val t = if (visible) ((presentationTimeUs - startUs).toFloat() / durationUs).coerceIn(0f, 1f) else 0f

        var alpha = if (visible) cue.opacity.coerceIn(0f, 1f) else 0f
        var scaleX = 1f
        var scaleY = 1f
        var dy = 0f

        if (visible) {
            // Entry occupies the first 300 ms, exit the last 200 ms.
            val inT = ((presentationTimeUs - startUs) / 300_000f).coerceIn(0f, 1f)
            val outT = ((endUs - presentationTimeUs) / 200_000f).coerceIn(0f, 1f)

            when (style.anim) {
                CaptionAnim.FADE -> alpha *= inT * outT
                CaptionAnim.POP -> {
                    val s = overshoot(inT)
                    scaleX = s; scaleY = s; alpha *= outT
                }
                CaptionAnim.BOUNCE -> {
                    val s = 1f + (1f - inT).pow(2) * 0.35f * sin(inT * 12f)
                    scaleX = s; scaleY = s; alpha *= inT.coerceAtMost(1f) * outT
                }
                CaptionAnim.SLIDE_UP -> {
                    dy = -(1f - easeOut(inT)) * 0.25f
                    alpha *= inT * outT
                }
                CaptionAnim.ZOOM_PULSE -> {
                    val s = 1f + 0.06f * sin(t * 18f)
                    scaleX = s; scaleY = s; alpha *= inT * outT
                }
                CaptionAnim.SHAKE -> {
                    alpha *= inT * outT
                }
                CaptionAnim.FLIP -> {
                    scaleX = abs(sin(inT * (Math.PI.toFloat() / 2f))).coerceAtLeast(0.02f)
                    alpha *= outT
                }
                CaptionAnim.WAVE -> {
                    dy = sin(t * 10f) * 0.012f
                    alpha *= inT * outT
                }
                else -> alpha *= inT * outT // NONE, TYPEWRITER, WORD_BY_WORD, KARAOKE_FILL
            }
        }

        // Media3 overlay space is -1..1 with 0 at the centre.
        val x = (cue.xFraction * 2f) - 1f
        val y = 1f - (cue.yFraction * 2f) + dy

        val shakeX = if (visible && style.anim == CaptionAnim.SHAKE)
            sin(presentationTimeUs * 0.00045).toFloat() * 0.012f else 0f

        return OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor((x + shakeX).coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
            .setScale(scaleX, scaleY)
            .setAlphaScale(alpha.coerceIn(0f, 1f))
            .build()
    }

    // ---------------------------------------------------------------- drawing

    private fun render(t: Float): Bitmap {
        val textPaint = buildPaint(fill = true)
        val strokePaint = if (style.strokeWidth > 0f) buildPaint(fill = false) else null

        val visibleWords = when (style.anim) {
            CaptionAnim.WORD_BY_WORD -> words.take((t * words.size).toInt().coerceAtLeast(1))
            else -> words
        }
        val lines = wrap(visibleWords, style.maxWordsPerLine)
        val display = if (style.anim == CaptionAnim.TYPEWRITER) {
            typewriter(lines, t)
        } else lines

        val lineHeight = textPaint.fontSpacing * style.lineHeight
        val padH = 26f * scale
        val padV = 14f * scale

        val widest = display.maxOfOrNull { textPaint.measureText(it) } ?: 1f
        val w = (widest + padH * 2).toInt().coerceIn(2, frameWidth)
        val h = (lineHeight * display.size + padV * 2).toInt().coerceIn(2, frameHeight)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background plate
        if (Color.alpha(style.backgroundColor.toInt()) > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.backgroundColor.toInt() }
            val r = style.cornerRadius * scale
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, bg)
        }

        // Karaoke fill: the highlight colour sweeps across the plate.
        val karaokeEdge = if (style.anim == CaptionAnim.KARAOKE_FILL) w * t else -1f

        var baseline = padV - textPaint.fontMetrics.top
        for (line in display) {
            val lineWidth = textPaint.measureText(line)
            val x = (w - lineWidth) / 2f

            strokePaint?.let { canvas.drawText(line, x, baseline, it) }

            if (karaokeEdge >= 0f) {
                // Unfilled text first, then clip the filled portion over it.
                canvas.drawText(line, x, baseline, textPaint)
                canvas.save()
                canvas.clipRect(0f, 0f, karaokeEdge, h.toFloat())
                val fill = Paint(textPaint).apply { color = style.highlightColor.toInt() }
                canvas.drawText(line, x, baseline, fill)
                canvas.restore()
            } else if (style.wordHighlight) {
                drawHighlighted(canvas, line, x, baseline, textPaint, t)
            } else {
                canvas.drawText(line, x, baseline, textPaint)
            }
            baseline += lineHeight
        }
        return bmp
    }

    /** Colour the "current" word, the trick behind viral caption styles. */
    private fun drawHighlighted(
        canvas: Canvas,
        line: String,
        startX: Float,
        baseline: Float,
        paint: Paint,
        t: Float
    ) {
        val parts = line.split(" ")
        val active = (t * parts.size).toInt().coerceIn(0, parts.size - 1)
        var x = startX
        val space = paint.measureText(" ")
        parts.forEachIndexed { i, word ->
            val p = if (i == active) Paint(paint).apply { color = style.highlightColor.toInt() } else paint
            canvas.drawText(word, x, baseline, p)
            x += paint.measureText(word) + space
        }
    }

    private fun typewriter(lines: List<String>, t: Float): List<String> {
        val full = lines.joinToString("\n")
        val shown = (full.length * t).toInt().coerceAtLeast(1)
        return full.take(shown).split("\n")
    }

    private fun buildPaint(fill: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = style.sizeSp * scale
        typeface = Typeface.create(
            mapFont(style.fontFamily),
            if (style.fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL
        )
        letterSpacing = style.letterSpacing
        if (fill) {
            this.style = Paint.Style.FILL
            color = style.textColor.toInt()
            if (style.shadowRadius > 0f) {
                setShadowLayer(
                    style.shadowRadius * scale,
                    0f,
                    style.shadowRadius * 0.4f * scale,
                    style.shadowColor.toInt()
                )
            }
        } else {
            this.style = Paint.Style.STROKE
            strokeWidth = style.strokeWidth * scale
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = style.strokeColor.toInt()
        }
    }

    /** Map catalogue font names onto families guaranteed to exist on Android. */
    private fun mapFont(family: String): Typeface = when (family.lowercase()) {
        "mono", "courier", "jetbrains mono", "roboto mono" -> Typeface.MONOSPACE
        "serif", "playfair", "georgia", "times" -> Typeface.SERIF
        else -> Typeface.SANS_SERIF
    }

    private fun wrap(words: List<String>, perLine: Int): List<String> {
        if (words.isEmpty()) return listOf(" ")
        val n = perLine.coerceAtLeast(1)
        return words.chunked(n).map { chunk ->
            val joined = chunk.joinToString(" ")
            if (style.allCaps) joined.uppercase() else joined
        }
    }

    private fun blankBitmap(): Bitmap =
        blank ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also { blank = it }

    private fun overshoot(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return (1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)).coerceIn(0.02f, 1.6f)
    }

    private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

    override fun release() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        blank?.let { if (!it.isRecycled) it.recycle() }
        blank = null
        super.release()
    }
}
