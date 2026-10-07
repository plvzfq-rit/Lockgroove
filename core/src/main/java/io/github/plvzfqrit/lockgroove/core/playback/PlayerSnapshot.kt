package io.github.plvzfqrit.lockgroove.core.playback

import io.github.plvzfqrit.lockgroove.core.model.SongKey

/** One report from the music player we follow. */
data class PlayerSnapshot(
    val key: SongKey,
    val title: String?,
    val artist: String?,
    /** Which app is playing (package name), e.g. "com.spotify.music". */
    val player: String,
    /**
     * Music is (or is about to be) coming out of the speaker: playing, buffering or
     * connecting. Buffering counts, so a network hiccup isn't mistaken for a pause.
     */
    val sounding: Boolean,
    /** Playback position (ms) at the moment [at] was taken. */
    val positionMs: Long,
    /** Monotonic time (ms) when [positionMs] was measured; 0 = unknown. */
    val at: Long,
    /** How fast the position is moving right now (1 = normal, 0 = not moving). */
    val speed: Float,
    /** Song length (ms); 0 if the player doesn't say. */
    val durationMs: Long,
    /** The player is on "repeat one". */
    val repeatOne: Boolean = false,
    /** The player sent album art for this song. */
    val hasArt: Boolean = false,
)

/** What we can tell the player to do. */
sealed interface PlayerCommand {
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data object Next : PlayerCommand
    data object Previous : PlayerCommand
    data class SeekTo(val ms: Long) : PlayerCommand
}
