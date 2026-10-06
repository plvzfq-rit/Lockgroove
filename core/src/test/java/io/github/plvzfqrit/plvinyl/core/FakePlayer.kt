package io.github.plvzfqrit.plvinyl.core

import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import io.github.plvzfqrit.plvinyl.core.playback.PlayerSnapshot

/**
 * A music player that does what it's told after a delay, like Spotify over a real
 * connection. Songs are a playlist of titles, all by "Artist".
 */
class FakePlayer(
    private val clock: FakeClock,
    private val songs: List<String>,
    var index: Int = 0,
    /** How long the player takes to act on a command (and report it). */
    var latencyMs: Long = 300L,
    private val durationMs: Long = 180_000L,
    var sounding: Boolean = true,
    startAtMs: Long = 0L,
) {
    private var positionMs = startAtMs
    private var positionAt = clock.nowMs()
    private val pending = ArrayList<Pair<Long, PlayerCommand>>()

    val title get() = songs[index]
    val key get() = SongKey.of(title, "Artist")

    fun snapshot() = PlayerSnapshot(
        key, title, "Artist", "com.spotify.music", sounding, positionMs, positionAt,
        if (sounding) 1f else 0f, durationMs, hasArt = true,
    )

    fun receive(command: PlayerCommand) {
        pending += (clock.nowMs() + latencyMs) to command
    }

    /** Acts on the commands that are due. Returns true if there's a new report. */
    fun tick(): Boolean {
        val now = clock.nowMs()
        val due = pending.filter { it.first <= now }
        if (due.isEmpty()) return false
        pending.removeAll(due)
        for ((_, command) in due) {
            // Bring the position up to now before changing anything.
            if (sounding) positionMs += now - positionAt
            positionAt = now
            when (command) {
                PlayerCommand.Play -> sounding = true
                PlayerCommand.Pause -> sounding = false
                is PlayerCommand.SeekTo -> positionMs = command.ms
                PlayerCommand.Next -> { index = (index + 1).coerceAtMost(songs.lastIndex); positionMs = 0 }
                // Like real players: a few seconds in, "previous" restarts the song.
                PlayerCommand.Previous -> {
                    if (positionMs <= 3_000L) index = (index - 1).coerceAtLeast(0)
                    positionMs = 0
                }
            }
        }
        return true
    }
}
