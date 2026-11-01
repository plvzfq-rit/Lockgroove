package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedLyricsTest {
    private val lines = listOf(LyricLine(7_600, "first"), LyricLine(55_460, ""), LyricLine(83_190, "二人で"))

    @Test fun `the date and the lines come back as they went in`() {
        val saved = SavedLyrics.parse(SavedLyrics.format(1_730_000_000_000L, lines))
        assertEquals(1_730_000_000_000L, saved.fetchedAtMs)
        assertEquals(lines, saved.lines)
    }

    @Test fun `the header is not a lyric line`() {
        val text = SavedLyrics.format(5L, lines)
        assertEquals(lines, LrcParser.parse(text))
    }

    @Test fun `a file from before the header still reads, with no date`() {
        val saved = SavedLyrics.parse(LrcWriter.format(lines))
        assertNull(saved.fetchedAtMs)
        assertEquals(lines, saved.lines)
    }

    @Test fun `lyrics expire after ninety days`() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(SavedLyrics.isExpired(0, 90 * day))
        assertTrue(SavedLyrics.isExpired(0, 90 * day + 1))
        assertFalse(SavedLyrics.isExpired(0, 10 * day))
    }

    @Test fun `a date in the future is not expired`() {
        assertFalse(SavedLyrics.isExpired(fetchedAtMs = 10_000_000L, nowMs = 1_000L))
    }
}
