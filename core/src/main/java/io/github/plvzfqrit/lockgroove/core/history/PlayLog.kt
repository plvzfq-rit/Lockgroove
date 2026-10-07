package io.github.plvzfqrit.lockgroove.core.history

import io.github.plvzfqrit.lockgroove.core.model.SongKey
import kotlin.math.ln

/** One play. */
data class PlayEntry(
    val id: Long,
    val key: SongKey,
    val title: String?,
    val artist: String?,
    /** Package name of the player app. */
    val player: String,
    /** When it counted (wall clock ms). */
    val at: Long,
    val durationMs: Long,
    /** Real listening time (0 for plays logged before this was tracked). */
    val listenedMs: Long,
) {
    /** Listening time for totals: the real one, else the song's length. */
    val timeMs: Long get() = if (listenedMs > 0L) listenedMs else durationMs
}

/** The song a play is counted for. */
data class PlayedSong(
    val key: SongKey,
    val title: String?,
    val artist: String?,
    val player: String,
    val durationMs: Long,
)

/**
 * Every play, in memory. The app's store loads it from the log file, and writes back
 * what changes. A "play" counts after 30 seconds of listening (or most of the song,
 * if it's shorter); each play also keeps how long you REALLY listened.
 */
class PlayLog(entries: List<PlayEntry> = emptyList()) {
    /** All plays, oldest first. */
    var entries: List<PlayEntry> = entries.sortedBy { it.at }
        private set

    /** Plays per record. */
    var counts: Map<SongKey, Int> = this.entries.groupingBy { it.key }.eachCount()
        private set

    /**
     * Count a play at [now]. Returns the play (new, or the existing one). If this is
     * the same play already counted (the app and the background counter both saw it),
     * it isn't counted twice: the existing play comes back instead.
     */
    fun countPlay(song: PlayedSong, listenedMs: Long, now: Long): Pair<PlayEntry, Boolean> {
        val last = entries.lastOrNull { it.key == song.key }
        val samePlayWindow = maxOf(song.durationMs * 9 / 10, COUNT_AFTER_MS)
        if (last != null && now - last.at < samePlayWindow) return last to false
        // Ids are the wall time; bump on the rare tie so they stay unique.
        val id = maxOf(now, (entries.maxOfOrNull { it.id } ?: 0L) + 1)
        val entry = PlayEntry(id, song.key, song.title, song.artist, song.player, now, song.durationMs, listenedMs)
        entries = entries + entry
        counts = counts + (song.key to ((counts[song.key] ?: 0) + 1))
        return entry to true
    }

    /** The record came off: save how long it was really listened to. Returns true if it changed. */
    fun updateListened(id: Long, listenedMs: Long): Boolean {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0 || entries[i].listenedMs >= listenedMs) return false
        entries = entries.toMutableList().also { it[i] = it[i].copy(listenedMs = listenedMs) }
        return true
    }

    /** Forget every play (stats and record wear start over). */
    fun clear() {
        entries = emptyList()
        counts = emptyMap()
    }

    /** How worn [key]'s record looks, 0..1. */
    fun wearFor(key: SongKey?): Float = if (key == null) 0f else Wear.of(counts[key] ?: 0)

    companion object {
        /** Listening before a song counts as a play. */
        const val COUNT_AFTER_MS = 30_000L

        /** How much of a song must be heard to count, for a song of [durationMs]. */
        fun countAfter(durationMs: Long): Long =
            if (durationMs in 1 until COUNT_AFTER_MS) durationMs * 9 / 10 else COUNT_AFTER_MS
    }
}

/** Record wear from play counts. */
object Wear {
    /**
     * How worn a record looks, 0 (fresh) to 1 (much loved), in steps so the record
     * isn't re-drawn after every single play. A couple of plays leave no trace; it
     * builds up slowly and tops out around 100 plays.
     */
    fun of(plays: Int): Float {
        if (plays < 3) return 0f
        val w = (ln(plays / 2f) / ln(50f)).coerceIn(0f, 1f)
        return (w * 5f).toInt() / 5f
    }
}
