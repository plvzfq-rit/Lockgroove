package io.github.plvzfqrit.lockgroove.core.turntable

/** What the listener does. The keys and gestures own no logic: they send these. */
sealed interface UserIntent {
    /** ▶■: start (needle onto the record) or stop (the arm goes home). */
    data object StartStop : UserIntent

    /** ▲▼: the cue lever. Lifts or lowers the arm WHERE IT IS (pause in place). */
    data object Cue : UserIntent

    /** |◀◀ / ▶▶| or a swipe on the record: -1 previous, +1 next. Repeats queue up. */
    data class Flick(val dir: Int) : UserIntent

    /** ↻1: at the end, the same record plays again. */
    data object ToggleRepeat : UserIntent

    /** A finger holds the arm at this angle (sent as it moves). */
    data class HoldArm(val angleDeg: Float) : UserIntent

    /** The finger lets go of the arm. */
    data object LetGoOfArm : UserIntent
}
