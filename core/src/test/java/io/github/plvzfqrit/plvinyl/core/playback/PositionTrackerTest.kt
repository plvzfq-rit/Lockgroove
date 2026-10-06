package io.github.plvzfqrit.plvinyl.core.playback

import io.github.plvzfqrit.plvinyl.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositionTrackerTest {
    @Test fun `the position moves on from the last report while playing`() {
        val s = snapshot(positionMs = 10_000, at = 1_000)
        assertEquals(15_000, PositionTracker.positionAt(s, now = 6_000))
    }

    @Test fun `a paused song stays where it was`() {
        val s = snapshot(sounding = false, positionMs = 10_000, at = 1_000)
        assertEquals(10_000, PositionTracker.positionAt(s, now = 60_000))
    }

    @Test fun `progress is null when the player doesn't say how long the song is`() {
        assertNull(PositionTracker.progress(snapshot(durationMs = 0), now = 0))
    }

    @Test fun `progress never runs past the end`() {
        val s = snapshot(positionMs = 170_000, at = 1, durationMs = 180_000)
        assertEquals(1f, PositionTracker.progress(s, now = 60_000))
    }

    @Test fun `a player going quiet for 20 s is not a seek if the positions line up`() {
        val a = snapshot(positionMs = 30_000, at = 100_000)
        val b = snapshot(positionMs = 50_000, at = 120_000)
        assertFalse(PositionTracker.isSeek(a, b))
    }

    @Test fun `a jump in position is a seek`() {
        val a = snapshot(positionMs = 30_000, at = 100_000)
        val b = snapshot(positionMs = 90_000, at = 101_000)
        assertTrue(PositionTracker.isSeek(a, b))
    }

    @Test fun `a different song is never a seek`() {
        val a = snapshot(title = "A", positionMs = 30_000, at = 100_000)
        val b = snapshot(title = "B", positionMs = 0, at = 101_000)
        assertFalse(PositionTracker.isSeek(a, b))
    }

    @Test fun `the run-out still counts down when Spotify freezes its position near the end`() {
        // Sounding (buffering) with speed 0, position stuck 5 s before the end.
        val s = snapshot(sounding = true, speed = 0f, positionMs = 175_000, at = 1_000, durationMs = 180_000)
        assertEquals(2_000, PositionTracker.remaining(s, now = 4_000))
    }
}
