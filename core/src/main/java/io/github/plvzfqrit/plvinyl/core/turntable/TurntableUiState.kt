package io.github.plvzfqrit.plvinyl.core.turntable

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
    /** The arm can be picked up. */
    val canGrab: Boolean = false,
    /** While a finger holds the arm: its angle. */
    val grabAngle: Float? = null,
    /** ↻1 is on (the key's light). */
    val repeatOne: Boolean = false,
)
