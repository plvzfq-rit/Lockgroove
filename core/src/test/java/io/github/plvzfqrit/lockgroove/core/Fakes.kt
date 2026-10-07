package io.github.plvzfqrit.lockgroove.core

import io.github.plvzfqrit.lockgroove.core.model.Clock

/** A clock that only moves when the test says so. */
class FakeClock(var t: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    var minute: Float = 12 * 60f
    var zone: String = "Etc/UTC"
    override fun nowMs() = t
    override fun wallMs() = wall
    override fun minuteOfDay() = minute
    override fun zoneId() = zone
    override fun utcOffsetMs() = 0
    fun advance(ms: Long) { t += ms; wall += ms }
}

/** A player report with sensible defaults; override what the test is about. */
fun snapshot(
    title: String = "Song A",
    sounding: Boolean = true,
    positionMs: Long = 0L,
    at: Long = 0L,
    durationMs: Long = 180_000L,
    repeatOne: Boolean = false,
    player: String = "com.spotify.music",
    speed: Float = if (sounding) 1f else 0f,
    hasArt: Boolean = true,
) = io.github.plvzfqrit.lockgroove.core.playback.PlayerSnapshot(
    io.github.plvzfqrit.lockgroove.core.model.SongKey.of(title, "Artist"), title, "Artist", player,
    sounding, positionMs, at, speed, durationMs, repeatOne, hasArt,
)
