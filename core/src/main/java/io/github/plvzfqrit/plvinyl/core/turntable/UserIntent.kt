package io.github.plvzfqrit.plvinyl.core.turntable

/** What the listener does. The keys and gestures own no logic: they send these. */
sealed interface UserIntent {
    /** A finger holds the arm at this angle (sent as it moves). */
    data class HoldArm(val angleDeg: Float) : UserIntent

    /** The finger lets go of the arm. */
    data object LetGoOfArm : UserIntent
}
