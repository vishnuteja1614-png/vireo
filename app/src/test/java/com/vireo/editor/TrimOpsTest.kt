package com.vireo.editor

import com.vireo.editor.data.Clip
import com.vireo.editor.data.MediaItem
import com.vireo.editor.data.MediaKind
import com.vireo.editor.data.TrimOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trimming is pure arithmetic over an immutable list, so unlike the GPU
 * pipeline it can be proved properly here. These tests exist because an
 * off-by-one in a ripple cut silently desynchronises every downstream clip.
 */
class TrimOpsTest {

    private fun media(durMs: Long) = MediaItem(
        uri = org.mockito.Mockito.mock(android.net.Uri::class.java),
        kind = MediaKind.VIDEO,
        durationMs = durMs,
        name = "t.mp4",
        sizeBytes = 1L
    )

    /** A clip of [durMs] starting at source zero. */
    private fun clip(durMs: Long, speed: Float = 1f) =
        Clip(media = media(durMs), trimStartMs = 0L, trimEndMs = durMs, speed = speed)

    private fun total(clips: List<Clip>) = clips.sumOf { it.outputDurationMs }

    // ---------------- hit testing ----------------

    @Test fun hitTest_findsTheRightClipAndOffset() {
        val clips = listOf(clip(1_000), clip(2_000), clip(3_000))
        assertEquals(0, TrimOps.hitTest(clips, 500)!!.index)
        assertEquals(1, TrimOps.hitTest(clips, 1_500)!!.index)
        assertEquals(500L, TrimOps.hitTest(clips, 1_500)!!.offsetInClip)
        assertEquals(2, TrimOps.hitTest(clips, 3_500)!!.index)
    }

    @Test fun hitTest_boundaryBelongsToTheLaterClip() {
        val clips = listOf(clip(1_000), clip(1_000))
        assertEquals(1, TrimOps.hitTest(clips, 1_000)!!.index)
        assertEquals(0L, TrimOps.hitTest(clips, 1_000)!!.offsetInClip)
    }

    @Test fun hitTest_emptyTimelineIsNull() {
        assertEquals(null, TrimOps.hitTest(emptyList(), 0))
    }

    // ---------------- trim left ----------------

    @Test fun trimLeft_movesInPointAndShortensTimeline() {
        val clips = listOf(clip(5_000))
        val r = TrimOps.trimLeft(clips, 2_000)
        assertTrue(r.changed)
        assertEquals(2_000L, r.clips[0].trimStartMs)
        assertEquals(5_000L, r.clips[0].trimEndMs)
        assertEquals(3_000L, total(r.clips))
    }

    @Test fun trimLeft_atZeroIsRejected() {
        val r = TrimOps.trimLeft(listOf(clip(5_000)), 0)
        assertFalse(r.changed)
    }

    @Test fun trimLeft_refusesToLeaveASliver() {
        val r = TrimOps.trimLeft(listOf(clip(1_000)), 900)
        assertFalse(r.changed)
    }

    @Test fun trimLeft_onSecondClipLeavesTheFirstAlone() {
        val clips = listOf(clip(2_000), clip(4_000))
        val r = TrimOps.trimLeft(clips, 3_000)
        assertTrue(r.changed)
        assertEquals(0L, r.clips[0].trimStartMs)
        assertEquals(1_000L, r.clips[1].trimStartMs)
        assertEquals(5_000L, total(r.clips))
    }

    /** At 2x a clip occupies half the timeline, so the source cut doubles. */
    @Test fun trimLeft_respectsSpeed() {
        val r = TrimOps.trimLeft(listOf(clip(4_000, speed = 2f)), 1_000)
        assertTrue(r.changed)
        assertEquals(2_000L, r.clips[0].trimStartMs)
    }

    // ---------------- trim right ----------------

    @Test fun trimRight_movesOutPoint() {
        val r = TrimOps.trimRight(listOf(clip(5_000)), 2_000)
        assertTrue(r.changed)
        assertEquals(0L, r.clips[0].trimStartMs)
        assertEquals(2_000L, r.clips[0].trimEndMs)
        assertEquals(2_000L, total(r.clips))
    }

    @Test fun trimRight_atTheEndIsRejected() {
        assertFalse(TrimOps.trimRight(listOf(clip(5_000)), 5_000).changed)
    }

    @Test fun trimRight_refusesToLeaveASliver() {
        assertFalse(TrimOps.trimRight(listOf(clip(5_000)), 100).changed)
    }

    // ---------------- ripple middle cut ----------------

    @Test fun trimMiddle_insideOneClipSplitsItInTwo() {
        val r = TrimOps.trimMiddle(listOf(clip(10_000)), 3_000, 6_000)
        assertTrue(r.changed)
        assertEquals(2, r.clips.size)
        assertEquals(0L, r.clips[0].trimStartMs)
        assertEquals(3_000L, r.clips[0].trimEndMs)
        assertEquals(6_000L, r.clips[1].trimStartMs)
        assertEquals(10_000L, r.clips[1].trimEndMs)
        // Exactly the marked range disappeared.
        assertEquals(7_000L, total(r.clips))
    }

    @Test fun trimMiddle_splitGivesTheTailAFreshId() {
        val r = TrimOps.trimMiddle(listOf(clip(10_000)), 3_000, 6_000)
        assertNotEquals(r.clips[0].id, r.clips[1].id)
    }

    @Test fun trimMiddle_deletesClipsFullyInsideTheRange() {
        val clips = listOf(clip(2_000), clip(2_000), clip(2_000))
        val r = TrimOps.trimMiddle(clips, 2_000, 4_000)
        assertTrue(r.changed)
        assertEquals(2, r.clips.size)
        assertEquals(4_000L, total(r.clips))
    }

    @Test fun trimMiddle_spanningThreeClipsTrimsEdgesAndDropsTheMiddle() {
        val clips = listOf(clip(3_000), clip(3_000), clip(3_000))
        val r = TrimOps.trimMiddle(clips, 1_000, 8_000)
        assertTrue(r.changed)
        // Head of clip 0 (1s) plus tail of clip 2 (1s).
        assertEquals(2_000L, total(r.clips))
        assertEquals(2, r.clips.size)
    }

    /** The whole point of a ripple: the gap closes, it is not left empty. */
    @Test fun trimMiddle_closesTheGapExactly() {
        val clips = listOf(clip(4_000), clip(4_000))
        val before = total(clips)
        val r = TrimOps.trimMiddle(clips, 1_000, 3_000)
        assertEquals(before - 2_000L, total(r.clips))
    }

    @Test fun trimMiddle_tinyRangeIsRejected() {
        assertFalse(TrimOps.trimMiddle(listOf(clip(5_000)), 1_000, 1_050).changed)
    }

    @Test fun trimMiddle_invertedMarksAreNormalised() {
        val a = TrimOps.trimMiddle(listOf(clip(10_000)), 6_000, 3_000)
        val b = TrimOps.trimMiddle(listOf(clip(10_000)), 3_000, 6_000)
        assertTrue(a.changed)
        assertEquals(total(b.clips), total(a.clips))
    }

    @Test fun trimMiddle_rangeCoveringEverythingIsRefused() {
        val r = TrimOps.trimMiddle(listOf(clip(3_000)), 0, 3_000)
        assertFalse(r.changed)
        assertEquals(1, r.clips.size)
    }

    @Test fun trimMiddle_rangePastTheEndChangesNothing() {
        val clips = listOf(clip(2_000))
        val r = TrimOps.trimMiddle(clips, 5_000, 9_000)
        assertFalse(r.changed)
        assertEquals(2_000L, total(r.clips))
    }

    @Test fun allOps_neverMutateTheInputList() {
        val clips = listOf(clip(5_000), clip(5_000))
        val snapshot = clips.map { it.trimStartMs to it.trimEndMs }
        TrimOps.trimLeft(clips, 1_000)
        TrimOps.trimRight(clips, 4_000)
        TrimOps.trimMiddle(clips, 2_000, 7_000)
        assertEquals(snapshot, clips.map { it.trimStartMs to it.trimEndMs })
    }

    /** Repeated head trims must converge, never produce negative spans. */
    @Test fun trimLeft_repeatedStaysValid() {
        var clips = listOf(clip(10_000))
        repeat(30) {
            val r = TrimOps.trimLeft(clips, 300)
            if (r.changed) clips = r.clips
            assertTrue(clips[0].trimEndMs - clips[0].trimStartMs >= 0)
            assertTrue(total(clips) >= 0)
        }
    }
}
