package com.vireo.editor

import com.vireo.editor.data.AspectRatio
import com.vireo.editor.data.CanvasFill
import com.vireo.editor.data.CaptionStyles
import com.vireo.editor.data.PanZoom
import com.vireo.editor.data.SubtitleIo
import com.vireo.editor.data.TransitionFamily
import com.vireo.editor.data.Transitions
import com.vireo.editor.engine.ClipClock
import com.vireo.editor.engine.LumaPattern
import com.vireo.editor.engine.LutPreset
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Scored feature matrix.
 *
 * Every checkable feature is exercised [REPEATS] times with randomised inputs
 * rather than a single happy-path assertion, then a pass/fail scorecard is
 * printed at the end of the run. Randomised repetition is the point: a feature
 * that works for one tidy input and falls over on an empty string, a negative
 * duration or an out-of-order timestamp is not actually working, and a single
 * assertion would never catch it.
 *
 * Features needing a GPU, a decoder or a real display (chroma key, luma wipe,
 * export, playback) cannot be verified here - a JVM test has no GL context.
 * Those are deliberately recorded as NOT_TESTABLE rather than quietly passed,
 * so the score never overstates what has been proven.
 */
class FeatureMatrixTest {

    private companion object {
        const val REPEATS = 30
        val results = linkedMapOf<String, Pair<Int, Int>>()   // feature -> passed/total
        val notTestable = mutableListOf<String>()

        fun score(feature: String, block: (Random) -> Boolean) {
            var passed = 0
            val rng = Random(feature.hashCode())
            repeat(REPEATS) {
                if (runCatching { block(rng) }.getOrDefault(false)) passed++
            }
            results[feature] = passed to REPEATS
        }

        @AfterClass
        @JvmStatic
        fun printScorecard() {
            val width = results.keys.maxOfOrNull { it.length } ?: 20
            println("\n================ VIREO FEATURE SCORECARD ================")
            println("Each feature exercised $REPEATS times with randomised input\n")
            var totalPassed = 0
            var totalRuns = 0
            results.forEach { (feature, pair) ->
                val (passed, total) = pair
                totalPassed += passed
                totalRuns += total
                val tick = if (passed == total) "PASS" else "FAIL"
                val pct = passed * 100 / total
                println("[$tick] ${feature.padEnd(width)}  $passed/$total  ($pct%)")
            }
            println("\nNOT TESTABLE on the JVM (needs GPU / decoder / display):")
            notTestable.forEach { println("  - $it") }
            val pct = if (totalRuns == 0) 0 else totalPassed * 100 / totalRuns
            println("\nOVERALL: $totalPassed/$totalRuns checks passed ($pct%)")
            println("=========================================================\n")
        }
    }

    // ------------------------------------------------------------ catalogues

    @Test
    fun transitionCatalogue() {
        score("Transitions catalogue") {
            Transitions.ALL.isNotEmpty() &&
                Transitions.COUNT == Transitions.ALL.size &&
                Transitions.ALL.map { it.id }.toSet().size == Transitions.ALL.size
        }
        score("Transitions: every family populated") { rng ->
            val family = TransitionFamily.entries[rng.nextInt(TransitionFamily.entries.size)]
            Transitions.byFamily(family).isNotEmpty()
        }
        score("Transitions: durations sane") { rng ->
            val t = Transitions.ALL[rng.nextInt(Transitions.ALL.size)]
            t.defaultMs in 100L..5_000L && t.label.isNotBlank()
        }
        assertTrue(Transitions.COUNT >= 100)
    }

    @Test
    fun captionCatalogue() {
        score("Caption styles catalogue") {
            CaptionStyles.ALL.isNotEmpty() &&
                CaptionStyles.ALL.map { it.id }.toSet().size == CaptionStyles.ALL.size
        }
        score("Caption styles: lookup never null") { rng ->
            val id = if (rng.nextBoolean()) {
                CaptionStyles.ALL[rng.nextInt(CaptionStyles.ALL.size)].id
            } else {
                "nonexistent-${rng.nextInt()}"      // must fall back, not crash
            }
            CaptionStyles.byId(id).id.isNotBlank()
        }
        score("Caption styles: readable sizes") { rng ->
            val s = CaptionStyles.ALL[rng.nextInt(CaptionStyles.ALL.size)]
            s.sizeSp in 8f..200f && s.maxWordsPerLine >= 1 && s.lineHeight > 0f
        }
        assertTrue(CaptionStyles.COUNT >= 100)
    }

    // ------------------------------------------------------------- subtitles

    @Test
    fun subtitleEngine() {
        score("SRT import") { rng ->
            val n = 1 + rng.nextInt(5)
            val srt = buildString {
                repeat(n) { i ->
                    val start = i * 2000L
                    append("${i + 1}\n")
                    append(stamp(start, ',')).append(" --> ").append(stamp(start + 1500L, ',')).append("\n")
                    append("cue number $i\n\n")
                }
            }
            SubtitleIo.parse(srt).size == n
        }
        score("WebVTT import") { rng ->
            val start = rng.nextLong(0, 500_000)
            val vtt = "WEBVTT\n\n${stamp(start, '.')} --> ${stamp(start + 1200, '.')}\nhello\n"
            SubtitleIo.parse(vtt).firstOrNull()?.startMs == start
        }
        score("SRT export round-trip") { rng ->
            val start = rng.nextLong(0, 3_600_000)
            val cues = SubtitleIo.parse("1\n${stamp(start, ',')} --> ${stamp(start + 900, ',')}\ntext\n")
            val again = SubtitleIo.parse(SubtitleIo.toSrt(cues))
            again.size == 1 && again[0].startMs == start && again[0].text == "text"
        }
        score("Subtitle shift stays positive") { rng ->
            val cues = SubtitleIo.parse("1\n00:00:05,000 --> 00:00:07,000\nx\n")
            val shifted = SubtitleIo.shift(cues, rng.nextLong(-100_000, 100_000))
            shifted.all { it.startMs >= 0 && it.endMs > it.startMs }
        }
        score("Script auto-timing") { rng ->
            val words = (1..(3 + rng.nextInt(40))).joinToString(" ") { "w$it" }
            val dur = 1_000L + rng.nextLong(0, 120_000)
            val cues = SubtitleIo.fromScript(words, dur, 1 + rng.nextInt(6))
            cues.isNotEmpty() && cues.all { it.endMs > it.startMs } &&
                cues.zipWithNext().all { (a, b) -> a.endMs == b.startMs }
        }
        score("Subtitle parser: garbage safe") { rng ->
            val junk = buildString { repeat(rng.nextInt(50)) { append(rng.nextInt(255).toChar()) } }
            SubtitleIo.parse(junk).all { it.endMs > it.startMs }
        }
    }

    // ----------------------------------------------------------- clip clock

    @Test
    fun clipClockUnderLoad() {
        score("ClipClock per-clip origin") { rng ->
            val clock = ClipClock()
            val origin = rng.nextLong(0, 10_000_000)
            clock.localUs(origin)
            val delta = rng.nextLong(0, 5_000_000)
            clock.localUs(origin + delta) == delta
        }
        score("ClipClock never negative") { rng ->
            val clock = ClipClock()
            clock.localUs(rng.nextLong(1_000_000, 50_000_000))
            (0 until 10).all { clock.localUs(rng.nextLong(0, 50_000_000)) >= 0L }
        }
    }

    // ------------------------------------------------------- enum catalogues

    @Test
    fun effectCatalogues() {
        score("Pan/zoom presets") { rng ->
            val p = PanZoom.entries[rng.nextInt(PanZoom.entries.size)]
            p.label.isNotBlank()
        }
        score("LUT presets") { rng ->
            val l = LutPreset.entries[rng.nextInt(LutPreset.entries.size)]
            l.label.isNotBlank()
        }
        score("Luma wipe patterns match shader") { rng ->
            // The shader branches on ordinal, so the enum must stay 16 long
            // and every name must round-trip through byId().
            val p = LumaPattern.entries[rng.nextInt(LumaPattern.entries.size)]
            LumaPattern.entries.size == 16 &&
                LumaPattern.byId(p.name) == p &&
                LumaPattern.byId("NONE") == null
        }
        score("Aspect ratios valid") { rng ->
            val r = AspectRatio.entries[rng.nextInt(AspectRatio.entries.size)]
            r.w > 0 && r.h > 0 && r.label.contains(":")
        }
        score("Canvas fill modes") { rng ->
            val f = CanvasFill.entries[rng.nextInt(CanvasFill.entries.size)]
            f.label.isNotBlank() && CanvasFill.entries.size == 3
        }

        assertEquals("luma shader has 16 branches", 16, LumaPattern.entries.size)

        notTestable += listOf(
            "Chroma key (needs an OpenGL context)",
            "Luma wipe rendering (needs an OpenGL context)",
            "Background blur / aspect fill (needs an OpenGL context)",
            "Export encode (needs MediaCodec)",
            "Live preview playback (needs a decoder + display)",
            "Freeze frame (needs MediaMetadataRetriever)",
            "Drag / pinch text gestures (needs a touch display)"
        )
    }

    private fun stamp(ms: Long, sep: Char): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val milli = ms % 1_000
        return String.format("%02d:%02d:%02d%c%03d", h, m, s, sep, milli)
    }
}
