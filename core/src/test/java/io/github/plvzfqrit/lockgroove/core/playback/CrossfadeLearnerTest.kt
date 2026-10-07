package io.github.plvzfqrit.lockgroove.core.playback

import io.github.plvzfqrit.lockgroove.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossfadeLearnerTest {
    private val learner = CrossfadeLearner()

    @Test fun `a song handed over 3 s early is learned for that player`() {
        val prev = snapshot(title = "A", positionMs = 177_000, at = 0, durationMs = 180_000)
        assertTrue(learner.observe(prev, snapshot(title = "B"), needleWasOnPrev = true, flicking = false, now = 0))
        assertEquals(3_000, learner.leadFor("com.spotify.music"))
    }

    @Test fun `a change 40 s early was a real skip and teaches nothing`() {
        val prev = snapshot(title = "A", positionMs = 140_000, at = 0, durationMs = 180_000)
        assertFalse(learner.observe(prev, snapshot(title = "B"), needleWasOnPrev = true, flicking = false, now = 0))
        assertEquals(0, learner.leadFor("com.spotify.music"))
    }

    @Test fun `what's learned on Spotify doesn't change YT Music`() {
        val prev = snapshot(title = "A", positionMs = 177_000, at = 0, durationMs = 180_000)
        learner.observe(prev, snapshot(title = "B"), needleWasOnPrev = true, flicking = false, now = 0)
        assertEquals(0, learner.leadFor("com.google.android.apps.youtube.music"))
    }

    @Test fun `our own skips teach nothing`() {
        val prev = snapshot(title = "A", positionMs = 177_000, at = 0, durationMs = 180_000)
        assertFalse(learner.observe(prev, snapshot(title = "B"), needleWasOnPrev = true, flicking = true, now = 0))
    }

    @Test fun `a change with the needle up teaches nothing`() {
        val prev = snapshot(title = "A", positionMs = 177_000, at = 0, durationMs = 180_000)
        assertFalse(learner.observe(prev, snapshot(title = "B"), needleWasOnPrev = false, flicking = false, now = 0))
    }
}
