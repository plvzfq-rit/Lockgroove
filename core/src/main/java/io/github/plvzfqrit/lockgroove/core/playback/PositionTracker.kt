package io.github.plvzfqrit.lockgroove.core.playback

import kotlin.math.abs

/** Where a song is right now, worked out from the player's last report. */
object PositionTracker {
    /** Position mismatch (vs. where the song should be) that counts as a seek. */
    const val SEEK_DETECT_MS = 3000L

    private fun sinceReport(s: PlayerSnapshot, now: Long, speed: Float): Long =
        if (s.at > 0L) ((now - s.at) * speed).toLong() else 0L

    /** Where the player is in the song right now (ms). */
    fun positionAt(s: PlayerSnapshot, now: Long): Long = s.positionMs + sinceReport(s, now, s.speed)

    /**
     * How far through the song the player is, 0..1, or null if the player doesn't
     * report a length. While playing, the position moves on from the last report.
     */
    fun progress(s: PlayerSnapshot, now: Long): Float? {
        if (s.durationMs <= 0L) return null
        return positionAt(s, now).coerceIn(0L, s.durationMs).toFloat() / s.durationMs
    }

    /**
     * Milliseconds left in the song, or null if the player doesn't report a length.
     *
     * While the song is sounding, time keeps moving even if the player froze its
     * reported position. Spotify does this for the last second of a track while it
     * prepares the next one (it reports "buffering" with the position stuck), which
     * used to stop the run-out from ever firing, so the next song started first.
     */
    fun remaining(s: PlayerSnapshot, now: Long): Long? {
        if (s.durationMs <= 0L) return null
        val speed = if (s.sounding && s.speed == 0f) 1f else s.speed
        return (s.durationMs - s.positionMs - sinceReport(s, now, speed)).coerceAtLeast(0L)
    }

    /**
     * Did the position jump between two reports of the same song? Compares the new
     * report with where the previous one says the song should be by now.
     */
    fun isSeek(prev: PlayerSnapshot, next: PlayerSnapshot): Boolean {
        if (prev.key != next.key) return false
        if (prev.at <= 0L || next.at <= 0L) return false
        val expected = prev.positionMs + ((next.at - prev.at) * prev.speed).toLong()
        return abs(next.positionMs - expected) > SEEK_DETECT_MS
    }
}
