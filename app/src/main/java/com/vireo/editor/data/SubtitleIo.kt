package com.vireo.editor.data

import java.util.Locale

/**
 * Subtitle import and export, modelled on the parsing rules Subtitle Edit uses.
 *
 * Supports the two formats that actually matter for social video:
 *
 *  - **SubRip (.srt)** - index, `HH:MM:SS,mmm --> HH:MM:SS,mmm`, text, blank line
 *  - **WebVTT (.vtt)** - `WEBVTT` header, optional cue ids, `.` instead of `,`
 *
 * The parser is deliberately forgiving, because real-world subtitle files are
 * messy. It tolerates: BOM markers, CRLF or LF, missing index numbers, extra
 * blank lines, both `,` and `.` as the millisecond separator, 1-2 digit hours,
 * cue settings trailing the timestamp (`line:90%`), and basic HTML tags
 * (`<i>`, `<b>`, `<font>`) which are stripped rather than rejected.
 *
 * Imported cues become [TextOverlay]s, so they immediately pick up whichever
 * of the 116 caption styles the project is using and render in both the
 * preview and the export.
 */
object SubtitleIo {

    private val TIME_LINE = Regex(
        """(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val TAG = Regex("""</?[a-zA-Z][^>]*>""")

    /** Parse SRT or WebVTT. Format is detected automatically. */
    fun parse(raw: String): List<TextOverlay> {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val out = mutableListOf<TextOverlay>()

        var pendingStart = -1L
        var pendingEnd = -1L
        val buffer = StringBuilder()

        fun flush() {
            if (pendingStart >= 0 && buffer.isNotBlank()) {
                val body = TAG.replace(buffer.toString(), "").trim()
                if (body.isNotEmpty()) {
                    out += TextOverlay(
                        text = body,
                        startMs = pendingStart,
                        // Guard against zero-length or inverted cues.
                        endMs = if (pendingEnd > pendingStart) pendingEnd else pendingStart + 1500L,
                        xFraction = 0.5f,
                        // Subtitles sit low in frame by convention.
                        yFraction = 0.80f
                    )
                }
            }
            pendingStart = -1L
            pendingEnd = -1L
            buffer.setLength(0)
        }

        for (line in text.split("\n")) {
            val trimmed = line.trim()

            // Headers and metadata blocks carry no cue text.
            if (trimmed.startsWith("WEBVTT", ignoreCase = true) ||
                trimmed.startsWith("NOTE", ignoreCase = true) ||
                trimmed.startsWith("STYLE", ignoreCase = true) ||
                trimmed.startsWith("REGION", ignoreCase = true)
            ) continue

            val match = TIME_LINE.find(trimmed)
            if (match != null) {
                // A new timestamp always closes the previous cue.
                flush()
                val g = match.groupValues
                pendingStart = toMs(g[1], g[2], g[3], g[4])
                pendingEnd = toMs(g[5], g[6], g[7], g[8])
                continue
            }

            if (trimmed.isEmpty()) {
                flush()
                continue
            }

            // A bare number immediately before a timestamp is the SRT index.
            if (pendingStart < 0 && trimmed.toIntOrNull() != null) continue

            if (pendingStart >= 0) {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(trimmed)
            }
        }
        flush()
        return out.sortedBy { it.startMs }
    }

    /** Serialise overlays as SubRip. */
    fun toSrt(cues: List<TextOverlay>): String = buildString {
        cues.sortedBy { it.startMs }.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(stamp(cue.startMs, ',')).append(" --> ").append(stamp(cue.endMs, ',')).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    /** Serialise overlays as WebVTT. */
    fun toVtt(cues: List<TextOverlay>): String = buildString {
        append("WEBVTT\n\n")
        cues.sortedBy { it.startMs }.forEach { cue ->
            append(stamp(cue.startMs, '.')).append(" --> ").append(stamp(cue.endMs, '.')).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    /**
     * Split a block of script into timed cues.
     *
     * Used when there is no subtitle file - for example captioning an
     * AI-written script. Time is distributed by word count rather than evenly
     * per sentence, so a long line is held on screen longer than a short one,
     * which is how a human would time it.
     *
     * @param wordsPerCue words shown at once; 3-5 suits vertical video
     */
    fun fromScript(
        script: String,
        totalDurationMs: Long,
        wordsPerCue: Int = 4,
        startAtMs: Long = 0L
    ): List<TextOverlay> {
        val words = script.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || totalDurationMs <= 0) return emptyList()

        val groups = words.chunked(wordsPerCue.coerceAtLeast(1))
        val totalWords = words.size.toFloat()

        var cursor = startAtMs
        return groups.map { group ->
            val share = group.size / totalWords
            val span = (totalDurationMs * share).toLong().coerceAtLeast(250L)
            val cue = TextOverlay(
                text = group.joinToString(" "),
                startMs = cursor,
                endMs = cursor + span,
                xFraction = 0.5f,
                yFraction = 0.78f
            )
            cursor += span
            cue
        }
    }

    /** Shift every cue, for fixing a subtitle file that is out of sync. */
    fun shift(cues: List<TextOverlay>, deltaMs: Long): List<TextOverlay> =
        cues.map {
            it.copy(
                startMs = (it.startMs + deltaMs).coerceAtLeast(0L),
                endMs = (it.endMs + deltaMs).coerceAtLeast(1L)
            )
        }

    // ---------------------------------------------------------------- helpers

    private fun toMs(h: String, m: String, s: String, frac: String): Long {
        // WebVTT allows 1-2 digit fractions; pad so "5" means 500 ms not 5 ms.
        val millis = frac.padEnd(3, '0').take(3).toLong()
        return h.toLong() * 3_600_000L + m.toLong() * 60_000L + s.toLong() * 1_000L + millis
    }

    private fun stamp(ms: Long, separator: Char): String {
        val safe = ms.coerceAtLeast(0L)
        val h = safe / 3_600_000
        val m = (safe % 3_600_000) / 60_000
        val s = (safe % 60_000) / 1_000
        val milli = safe % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d%c%03d", h, m, s, separator, milli)
    }
}
