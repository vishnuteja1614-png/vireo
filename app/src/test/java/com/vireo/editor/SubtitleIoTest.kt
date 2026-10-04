package com.vireo.editor

import com.vireo.editor.data.SubtitleIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real tests for the subtitle engine.
 *
 * Subtitle files in the wild are inconsistent, so these lock in the awkward
 * cases rather than the happy path: WebVTT dots, missing indices, CRLF,
 * inline HTML, short millisecond fractions and inverted cue times.
 */
class SubtitleIoTest {

    @Test
    fun parsesStandardSrt() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello world

            2
            00:00:04,000 --> 00:00:06,000
            Second line
        """.trimIndent()

        val cues = SubtitleIo.parse(srt)
        assertEquals(2, cues.size)
        assertEquals("Hello world", cues[0].text)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals(4_000L, cues[1].startMs)
    }

    @Test
    fun parsesWebVttWithDotsAndNoIndices() {
        val vtt = "WEBVTT\n\n00:00:02.250 --> 00:00:05.000\nVtt cue\n"
        val cues = SubtitleIo.parse(vtt)
        assertEquals(1, cues.size)
        assertEquals(2_250L, cues[0].startMs)
        assertEquals("Vtt cue", cues[0].text)
    }

    @Test
    fun handlesCrLfBomAndHtmlTags() {
        val messy = "\uFEFF1\r\n00:00:00,500 --> 00:00:02,000\r\n<i>Italic</i> <b>bold</b>\r\n"
        val cues = SubtitleIo.parse(messy)
        assertEquals(1, cues.size)
        assertEquals("Italic bold", cues[0].text)
        assertEquals(500L, cues[0].startMs)
    }

    @Test
    fun padsShortMillisecondFractions() {
        // "5" must mean 500 ms, not 5 ms.
        val cues = SubtitleIo.parse("00:00:01.5 --> 00:00:02.0\nx\n")
        assertEquals(1_500L, cues[0].startMs)
    }

    @Test
    fun joinsMultiLineCues() {
        val cues = SubtitleIo.parse("1\n00:00:01,000 --> 00:00:02,000\nline one\nline two\n")
        assertEquals("line one line two", cues[0].text)
    }

    @Test
    fun repairsInvertedCueTimes() {
        val cues = SubtitleIo.parse("1\n00:00:05,000 --> 00:00:02,000\nbad\n")
        assertTrue("end must follow start", cues[0].endMs > cues[0].startMs)
    }

    @Test
    fun srtRoundTripsLosslessly() {
        val original = "1\n00:01:02,345 --> 00:01:04,000\nRound trip\n"
        val once = SubtitleIo.parse(original)
        val twice = SubtitleIo.parse(SubtitleIo.toSrt(once))
        assertEquals(once.size, twice.size)
        assertEquals(once[0].startMs, twice[0].startMs)
        assertEquals(once[0].endMs, twice[0].endMs)
        assertEquals(once[0].text, twice[0].text)
    }

    @Test
    fun vttOutputCarriesHeaderAndDots() {
        val cues = SubtitleIo.parse("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
        val vtt = SubtitleIo.toVtt(cues)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:00:01.000 --> 00:00:02.000"))
    }

    @Test
    fun shiftNeverProducesNegativeTimes() {
        val cues = SubtitleIo.parse("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
        val shifted = SubtitleIo.shift(cues, -10_000L)
        assertEquals(0L, shifted[0].startMs)
        assertTrue(shifted[0].endMs >= 1L)
    }

    @Test
    fun scriptTimingCoversTheWholeDuration() {
        val cues = SubtitleIo.fromScript("one two three four five six seven eight", 8_000L, 4)
        assertEquals(2, cues.size)
        assertEquals(0L, cues[0].startMs)
        // Cues must be contiguous and finish at roughly the clip end.
        assertEquals(cues[0].endMs, cues[1].startMs)
        assertTrue(cues.last().endMs in 7_000L..9_000L)
    }

    @Test
    fun emptyAndGarbageInputAreSafe() {
        assertTrue(SubtitleIo.parse("").isEmpty())
        assertTrue(SubtitleIo.parse("not a subtitle file at all").isEmpty())
        assertTrue(SubtitleIo.fromScript("", 5_000L).isEmpty())
    }
}
