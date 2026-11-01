package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTrackerTest {
    private val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))

    @Test fun `before the first line there is no current line`() {
        assertEquals(-1, LyricsTracker.currentIndex(lines, 0))
        assertEquals(-1, LyricsTracker.currentIndex(lines, 999))
    }

    @Test fun `a line is current from its time until the next one starts`() {
        assertEquals(0, LyricsTracker.currentIndex(lines, 1_000))
        assertEquals(0, LyricsTracker.currentIndex(lines, 4_999))
        assertEquals(1, LyricsTracker.currentIndex(lines, 5_000))
        assertEquals(2, LyricsTracker.currentIndex(lines, 600_000))
    }

    @Test fun `no lyrics means no current line`() {
        assertEquals(-1, LyricsTracker.currentIndex(emptyList(), 5_000))
    }
}
