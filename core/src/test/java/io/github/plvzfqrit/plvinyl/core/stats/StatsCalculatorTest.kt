package io.github.plvzfqrit.plvinyl.core.stats

import io.github.plvzfqrit.plvinyl.core.history.PlayEntry
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsCalculatorTest {
    private val zone = TimeZone.getTimeZone("Asia/Manila")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.OCTOBER, day, hour, minute) }.timeInMillis

    private var id = 0L
    private fun play(title: String, at: Long, artist: String = "Artist", durationMs: Long = 180_000) =
        PlayEntry(++id, SongKey.of(title, artist), title, artist, "p", at, durationMs, 0)

    @Test fun `plays on Monday, Tuesday and Thursday make a 2-day streak`() {
        // 5 Oct 2026 is a Monday.
        val plays = listOf(play("A", at(5, 10)), play("B", at(6, 22)), play("C", at(8, 9)))
        assertEquals(2, StatsCalculator.compute(plays, StatsPeriod.All, at(9, 0), zone).longestStreakDays)
    }

    @Test fun `the same song three times back to back is 3 in a row`() {
        val plays = listOf(play("A", at(5, 10, 0)), play("A", at(5, 10, 3)), play("A", at(5, 10, 6)), play("B", at(5, 10, 9)))
        val stats = StatsCalculator.compute(plays, StatsPeriod.All, at(9, 0), zone)
        assertEquals(3, stats.mostRepeats?.second)
        assertEquals("A", stats.mostRepeats?.first?.title)
    }

    @Test fun `featured artists each get the play`() {
        assertEquals(listOf("Kendrick Lamar", "SZA"), StatsCalculator.splitArtists("Kendrick Lamar, SZA"))
    }

    @Test fun `ampersands and comma band names stay one artist`() {
        assertEquals(listOf("Simon & Garfunkel"), StatsCalculator.splitArtists("Simon & Garfunkel"))
        assertEquals(listOf("Tyler, The Creator"), StatsCalculator.splitArtists("Tyler, The Creator"))
    }

    @Test fun `the same artist in different capitals is one artist`() {
        val plays = listOf(play("A", at(5, 1), artist = "SZA"), play("B", at(5, 2), artist = "Sza"))
        val stats = StatsCalculator.compute(plays, StatsPeriod.All, at(9, 0), zone)
        assertEquals(2, stats.topArtist?.second)
    }

    @Test fun `this month only counts plays since the 1st`() {
        val plays = listOf(play("A", at(1, 0) - 60_000), play("B", at(2, 12)))
        assertEquals(1, StatsCalculator.compute(plays, StatsPeriod.Month, at(9, 0), zone).plays)
    }
}
