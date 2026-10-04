package com.vireo.editor

import com.vireo.editor.engine.ClipClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fix for the bug that silently broke every transition and caption
 * after the first clip: Media3 timestamps are cumulative across a sequence, so
 * each clip needs its own origin.
 */
class ClipClockTest {

    @Test
    fun firstTimestampBecomesTheOrigin() {
        val clock = ClipClock()
        assertEquals(0L, clock.localUs(5_000_000L))
        assertEquals(1_000_000L, clock.localUs(6_000_000L))
    }

    @Test
    fun clipStartingAtZeroStillWorks() {
        val clock = ClipClock()
        assertEquals(0L, clock.localUs(0L))
        assertEquals(250_000L, clock.localUs(250_000L))
    }

    @Test
    fun neverReturnsNegative() {
        val clock = ClipClock()
        clock.localUs(10_000_000L)
        assertTrue("out-of-order frames must clamp", clock.localUs(9_000_000L) >= 0L)
    }

    @Test
    fun separateClipsDoNotShareAnOrigin() {
        val a = ClipClock()
        val b = ClipClock()
        a.localUs(1_000_000L)
        b.localUs(30_000_000L)
        // Both clips are 500 ms into their own media.
        assertEquals(a.localUs(1_500_000L), b.localUs(30_500_000L))
    }
}
