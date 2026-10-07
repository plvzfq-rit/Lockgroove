package io.github.plvzfqrit.lockgroove.core.turntable

import io.github.plvzfqrit.lockgroove.core.lighting.DayPreview
import io.github.plvzfqrit.lockgroove.core.lighting.RoomLight

/** Everything the screen shows, apart from the moving parts (those follow the target). */
data class TurntableUiState(
    /** The song under the record: the player's, which can run ahead of the record. */
    val title: String = "Nothing playing",
    val artist: String? = "Play something in any music app",
    val durationMs: Long = 0L,
    /** What the listener wants (the ▶■ key's light). */
    val playing: Boolean = false,
    /** The needle is on the playing song's record and the player is sounding. */
    val audible: Boolean = false,
    /** The player says music is coming out. */
    val sounding: Boolean = false,
    /** The needle rests on a record. */
    val needleDown: Boolean = false,
    /** The arm can be picked up. */
    val canGrab: Boolean = false,
    /** Flicking through songs (the arm waits parked). */
    val browsing: Boolean = false,
    /** The needle is circling the locked groove after the music ended. */
    val inLeadOut: Boolean = false,
    /** ↻1 is on (the key's light). */
    val repeatOne: Boolean = false,
    /** The playing app (package name). */
    val player: String? = null,
    /** How early the needle lifts for this player (its learned crossfade). */
    val learnedCrossfadeMs: Long = 0L,
    /** The light in the room right now. */
    val roomLight: RoomLight = RoomLight(),
    /** A sped-up day playing (the screen works out its light every frame), or null. */
    val dayPreview: DayPreview? = null,
)
