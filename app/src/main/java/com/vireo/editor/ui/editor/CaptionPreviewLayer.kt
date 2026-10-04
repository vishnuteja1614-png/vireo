package com.vireo.editor.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import com.vireo.editor.data.CaptionAnim
import com.vireo.editor.data.CaptionStyle
import com.vireo.editor.data.CaptionStyles
import com.vireo.editor.data.TextOverlay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Draws text overlays on top of the preview player using the project's chosen
 * caption style.
 *
 * Why this exists: captions and text were only ever rendered by the exporter.
 * The editor's preview showed raw video, so picking a caption style appeared to
 * do nothing and users had to run a full export to discover whether their text
 * was right. This mirrors [com.vireo.editor.engine.CaptionOverlay] - the same
 * stroke, shadow, plate, word-wrap and animation maths - so the preview is an
 * honest representation of the final file.
 *
 * Kept as a Compose Canvas rather than a GL overlay: it costs nothing when no
 * cue is on screen, and it survives devices that reject GL effects on the
 * preview path.
 */
@Composable
fun CaptionPreviewLayer(
    texts: List<TextOverlay>,
    styleId: String,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val style = CaptionStyles.byId(styleId)
    val active = texts.filter { playheadMs in it.startMs..it.endMs }
    if (active.isEmpty()) return

    Canvas(modifier) {
        val frameH = size.height
        val frameW = size.width
        // Caption sizes are authored against a 720p frame, as in the exporter.
        val scale = frameH / 720f

        active.forEach { cue ->
            val span = (cue.endMs - cue.startMs).coerceAtLeast(1L)
            val t = ((playheadMs - cue.startMs).toFloat() / span).coerceIn(0f, 1f)

            val fill = buildPaint(style, scale, fill = true)
            val stroke = if (style.strokeWidth > 0f) buildPaint(style, scale, fill = false) else null

            // Word-by-word and typewriter reveal progressively.
            val words = cue.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return@forEach
            val shown = when (style.anim) {
                CaptionAnim.WORD_BY_WORD -> words.take((t * words.size).toInt().coerceAtLeast(1))
                else -> words
            }
            var lines = shown.chunked(style.maxWordsPerLine.coerceAtLeast(1)).map { chunk ->
                chunk.joinToString(" ").let { if (style.allCaps) it.uppercase() else it }
            }
            if (style.anim == CaptionAnim.TYPEWRITER) {
                val full = lines.joinToString("\n")
                lines = full.take((full.length * t).toInt().coerceAtLeast(1)).split("\n")
            }

            // Entry / exit shaping, matching the exporter's 300 ms / 200 ms windows.
            val inT = ((playheadMs - cue.startMs) / 300f).coerceIn(0f, 1f)
            val outT = ((cue.endMs - playheadMs) / 200f).coerceIn(0f, 1f)
            var alpha = cue.opacity.coerceIn(0f, 1f)
            var scaleAnim = 1f
            var dy = 0f
            var dx = 0f

            when (style.anim) {
                CaptionAnim.FADE -> alpha *= inT * outT
                CaptionAnim.POP -> { scaleAnim = overshoot(inT); alpha *= outT }
                CaptionAnim.BOUNCE -> {
                    scaleAnim = 1f + (1f - inT).pow(2) * 0.35f * sin(inT * 12f)
                    alpha *= inT * outT
                }
                CaptionAnim.SLIDE_UP -> { dy = (1f - easeOut(inT)) * frameH * 0.12f; alpha *= inT * outT }
                CaptionAnim.ZOOM_PULSE -> { scaleAnim = 1f + 0.06f * sin(t * 18f); alpha *= inT * outT }
                CaptionAnim.SHAKE -> { dx = sin(playheadMs * 0.05f) * 6f * scale; alpha *= inT * outT }
                CaptionAnim.FLIP -> {
                    scaleAnim = abs(sin(inT * (Math.PI.toFloat() / 2f))).coerceAtLeast(0.05f)
                    alpha *= outT
                }
                CaptionAnim.WAVE -> { dy = sin(t * 10f) * 8f * scale; alpha *= inT * outT }
                else -> alpha *= inT * outT
            }
            if (alpha <= 0.01f) return@forEach

            val lineHeight = fill.fontSpacing * style.lineHeight
            val blockH = lineHeight * lines.size
            val widest = lines.maxOfOrNull { fill.measureText(it) } ?: 0f

            val cx = frameW * cue.xFraction + dx
            val cy = frameH * cue.yFraction + dy

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val save = native.save()
                native.scale(scaleAnim, scaleAnim, cx, cy)

                val padH = 22f * scale
                val padV = 12f * scale
                var baseline = cy - blockH / 2f - fill.fontMetrics.top / 1f - lineHeight / 2f

                // Background plate
                if (android.graphics.Color.alpha(style.backgroundColor.toInt()) > 0) {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = style.backgroundColor.toInt()
                        this.alpha = (android.graphics.Color.alpha(style.backgroundColor.toInt()) * alpha).toInt()
                    }
                    val r = style.cornerRadius * scale
                    native.drawRoundRect(
                        cx - widest / 2f - padH, cy - blockH / 2f - padV,
                        cx + widest / 2f + padH, cy + blockH / 2f + padV,
                        r, r, bg
                    )
                }

                lines.forEach { line ->
                    val w = fill.measureText(line)
                    val x = cx - w / 2f

                    stroke?.let {
                        it.alpha = (255 * alpha).toInt()
                        native.drawText(line, x, baseline, it)
                    }

                    if (style.wordHighlight) {
                        val parts = line.split(" ")
                        val activeWord = (t * parts.size).toInt().coerceIn(0, parts.size - 1)
                        var wx = x
                        val space = fill.measureText(" ")
                        parts.forEachIndexed { i, word ->
                            val p = if (i == activeWord) {
                                Paint(fill).apply { color = style.highlightColor.toInt() }
                            } else fill
                            p.alpha = (255 * alpha).toInt()
                            native.drawText(word, wx, baseline, p)
                            wx += fill.measureText(word) + space
                        }
                    } else {
                        fill.alpha = (255 * alpha).toInt()
                        native.drawText(line, x, baseline, fill)
                    }
                    baseline += lineHeight
                }
                native.restoreToCount(save)
            }
        }
    }
}

private fun buildPaint(style: CaptionStyle, scale: Float, fill: Boolean): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = style.sizeSp * scale
        typeface = Typeface.create(
            when (style.fontFamily.lowercase()) {
                "mono", "courier", "jetbrains mono", "roboto mono" -> Typeface.MONOSPACE
                "serif", "playfair", "georgia", "times" -> Typeface.SERIF
                else -> Typeface.SANS_SERIF
            },
            if (style.fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL
        )
        letterSpacing = style.letterSpacing
        if (fill) {
            this.style = Paint.Style.FILL
            color = style.textColor.toInt()
            if (style.shadowRadius > 0f) {
                setShadowLayer(
                    style.shadowRadius * scale, 0f,
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

private fun overshoot(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return (1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)).coerceIn(0.05f, 1.6f)
}

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
