package io.github.plvzfqrit.lockgroove.core.stats

import io.github.plvzfqrit.lockgroove.core.history.PlayEntry
import io.github.plvzfqrit.lockgroove.core.model.RecordSize
import io.github.plvzfqrit.lockgroove.core.model.SongKey
import java.util.Calendar
import java.util.TimeZone

enum class StatsPeriod(val label: String) { Month("This month"), Year("This year"), All("All time") }

data class TopRecord(val key: SongKey, val title: String?, val artist: String?, val plays: Int, val firstAt: Long)

data class Stats(
    val plays: Int,
    val listenedMs: Long,
    val records: Int,
    val top: List<TopRecord>,
    /** Name (most-seen spelling) and plays. */
    val topArtist: Pair<String, Int>?,
    val topArtistRecords: Int,
    /** Plays per hour of the day, 0..23. */
    val byHour: List<Int>,
    /** Player package → plays, most first. */
    val players: List<Pair<String, Int>>,
    /** Record, and times in a row. */
    val mostRepeats: Pair<TopRecord, Int>?,
    val longestStreakDays: Int,
    /** 7" share of plays. */
    val singlesShare: Float,
    /** Calendar.SUNDAY..SATURDAY. */
    val busiestDay: Int?,
    val peakHour: Int?,
)

/** "Liner notes": the numbers behind the stats screen, from the play log. */
object StatsCalculator {
    /** Band names that contain a comma, so they aren't split into "artists". */
    private val CommaBands = listOf(
        "Tyler, The Creator", "Earth, Wind & Fire", "Crosby, Stills, Nash & Young",
        "Crosby, Stills & Nash", "Emerson, Lake & Palmer", "Blood, Sweat & Tears",
        "Peter, Paul and Mary",
    )

    /**
     * Splits a player's artist line into the individual artists: "A, B" → [A, B].
     * Only commas split (the way music apps list several artists); "&" and "and" stay,
     * since they're often part of one name ("Simon & Garfunkel"). Known band names
     * with commas are kept whole. One spelling per name: the first one seen wins.
     */
    fun splitArtists(line: String?): List<String> {
        if (line.isNullOrBlank()) return emptyList()
        var text: String = line
        val kept = ArrayList<String>()
        for (band in CommaBands) {
            val i = text.indexOf(band, ignoreCase = true)
            if (i >= 0) {
                kept += text.substring(i, i + band.length)
                text = text.removeRange(i, i + band.length)
            }
        }
        val parts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return (kept + parts).distinctBy { it.lowercase() }
    }

    private fun Calendar.startOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }

    fun periodStart(p: StatsPeriod, now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
        return when (p) {
            StatsPeriod.All -> 0L
            StatsPeriod.Month -> c.apply { set(Calendar.DAY_OF_MONTH, 1) }.startOfDay()
            StatsPeriod.Year -> c.apply { set(Calendar.DAY_OF_YEAR, 1) }.startOfDay()
        }
    }

    fun compute(all: List<PlayEntry>, period: StatsPeriod, now: Long, zone: TimeZone = TimeZone.getDefault()): Stats {
        val from = periodStart(period, now, zone)
        val plays = all.filter { it.at >= from }
        val cal = Calendar.getInstance(zone)

        val byKey = plays.groupBy { it.key }
        val top = byKey.map { (k, list) ->
            TopRecord(k, list.last().title, list.last().artist, list.size, list.first().at)
        }.sortedWith(compareByDescending<TopRecord> { it.plays }.thenBy { it.firstAt }).take(5)

        // "A, B" is two artists (that's how Spotify and YT Music list features), so each
        // one gets the play. Counted per person, case-insensitively.
        val credited = plays.flatMap { e -> splitArtists(e.artist).map { it to e } }
        val displayName = credited.groupBy({ it.first.lowercase() }, { it.first })
            .mapValues { (_, names) -> names.groupingBy { it }.eachCount().maxBy { it.value }.key }
        val artists = credited.groupBy({ it.first.lowercase() }, { it.second })
        val topArtistEntry = artists.maxByOrNull { it.value.size }

        val byHour = IntArray(24)
        val byDay = IntArray(8)
        val days = sortedSetOf<Long>()
        for (e in plays) {
            cal.timeInMillis = e.at
            byHour[cal.get(Calendar.HOUR_OF_DAY)]++
            byDay[cal.get(Calendar.DAY_OF_WEEK)]++
            // Day number in local time: counts calendar days, not 24-hour spans.
            days += cal.get(Calendar.YEAR) * 400L + cal.get(Calendar.DAY_OF_YEAR)
        }

        // Longest run of consecutive days with at least one play.
        var best = 0; var run = 0; var prev: Long? = null
        for (d in days) {
            run = if (prev != null && isNextDay(prev, d)) run + 1 else 1
            best = maxOf(best, run); prev = d
        }

        // Most times in a row: the same record straight after itself (within its length + 10 min).
        var repeatBest: Pair<SongKey, Int>? = null
        var streakKey: SongKey? = null; var streak = 0; var lastAt = 0L; var lastLen = 0L
        for (e in plays) {
            val continues = e.key == streakKey && e.at - lastAt <= lastLen + 10 * 60_000L
            streak = if (continues) streak + 1 else 1
            streakKey = e.key; lastAt = e.at; lastLen = maxOf(e.durationMs, 60_000L)
            if (streak >= 2 && streak > (repeatBest?.second ?: 0)) repeatBest = e.key to streak
        }
        val repeatRecord = repeatBest?.let { (k, n) ->
            val list = byKey[k].orEmpty()
            TopRecord(k, list.lastOrNull()?.title, list.lastOrNull()?.artist, list.size, list.firstOrNull()?.at ?: 0L) to n
        }

        val singles = plays.count { it.durationMs in 1..RecordSize.SINGLE_MAX_MS }
        return Stats(
            plays = plays.size,
            listenedMs = plays.sumOf { it.timeMs },
            records = byKey.size,
            top = top,
            topArtist = topArtistEntry?.let { (displayName[it.key] ?: it.key) to it.value.size },
            topArtistRecords = topArtistEntry?.value?.map { it.key }?.distinct()?.size ?: 0,
            byHour = byHour.toList(),
            players = plays.groupingBy { it.player }.eachCount().toList().sortedByDescending { it.second },
            mostRepeats = repeatRecord,
            longestStreakDays = best,
            singlesShare = if (plays.isEmpty()) 0f else singles.toFloat() / plays.size,
            busiestDay = (1..7).maxByOrNull { byDay[it] }?.takeIf { byDay[it] > 0 },
            peakHour = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 },
        )
    }

    /** Day numbers are year * 400 + day of year. */
    private fun isNextDay(a: Long, b: Long): Boolean {
        if (b == a + 1) return true
        // 31 Dec → 1 Jan
        val (ya, da) = a / 400 to a % 400
        val (yb, db) = b / 400 to b % 400
        return yb == ya + 1 && db == 1L && da >= 365
    }
}
