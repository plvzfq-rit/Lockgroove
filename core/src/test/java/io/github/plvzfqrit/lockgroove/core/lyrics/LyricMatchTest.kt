package io.github.plvzfqrit.lockgroove.core.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LyricMatch.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricMatchTest {
    /** Lyrics whose last line is sung at [lastS] seconds. */
    private fun lyrics(lastS: Int) = listOf(LyricLine(5_000, "first"), LyricLine(lastS * 1000L, "last"))

    @Test fun `the closest length wins`() {
        val pick = LyricMatch.pick(
            listOf(Candidate(150.0, lyrics(140)), Candidate(147.0, lyrics(140)), Candidate(144.0, lyrics(140))), 146,
        )
        assertEquals(1, pick)
    }

    @Test fun `a length too far off is another recording`() {
        assertNull(LyricMatch.pick(listOf(Candidate(155.0, lyrics(150)), Candidate(139.0, lyrics(130))), 146))
    }

    @Test fun `lyrics that run past the end of the song are another song`() {
        // The Italian words to the same tune: the right length on paper, but sung until 181 s.
        val pick = LyricMatch.pick(listOf(Candidate(147.0, lyrics(181)), Candidate(148.0, lyrics(130))), 146)
        assertEquals(1, pick)
    }

    @Test fun `lyrics that cover the song beat a closer length that stops far too early`() {
        // Katyusha: the claimed 146 s entry stops at 83 s (timed for a 93 s recording).
        val pick = LyricMatch.pick(listOf(Candidate(146.0, lyrics(83)), Candidate(148.0, lyrics(130))), 146)
        assertEquals(1, pick)
    }

    @Test fun `lyrics that stop early are still used when nothing better exists`() {
        assertEquals(0, LyricMatch.pick(listOf(Candidate(146.0, lyrics(83))), 146))
    }

    @Test fun `entries with no lyrics are skipped`() {
        assertEquals(1, LyricMatch.pick(listOf(Candidate(146.0, emptyList()), Candidate(146.0, lyrics(140))), 146))
    }

    @Test fun `with an unknown song length the first entry with lyrics is taken`() {
        assertEquals(0, LyricMatch.pick(listOf(Candidate(300.0, lyrics(100)), Candidate(146.0, lyrics(100))), 0))
    }
}
