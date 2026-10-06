package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.Disc
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.math.abs

/*
 * Semi-automatic turntable choreography.
 *
 * Physical rules this enforces:
 *  - The arm only moves sideways while RAISED. Lift, swing and lower never
 *    overlap. (The one exception is the groove slowly pulling the needle inward
 *    while playing, which is what a real arm does.)
 *  - A record is only lifted off or placed on the platter while the arm is parked
 *    in its rest clip.
 *  - The platter never stops. Pausing lifts the needle (cue lever up) and the record
 *    keeps turning underneath it.
 *
 * The planner looks at where things ARE (the deck) and where they SHOULD be (the
 * target) and names exactly one small physical move. The animator performs it, then
 * asks again. Each move finishes before the next is chosen, so spamming Next or Play
 * never leaves the arm half-way or dragging.
 */

/** Where the turntable physically is. */
data class Deck(
    /** The record on the platter. */
    val disc: Disc?,
    val armDeg: Float = ArmGeometry.REST_DEG,
    /** The arm is raised (cue lever up). */
    val armUp: Boolean = false,
    /** The record the needle rests on, or null if the needle is up or parked. */
    val needleOn: SongKey? = null,
) {
    val armParked get() = abs(armDeg - ArmGeometry.REST_DEG) < 0.05f
    val armDown get() = !armUp
}

/** Where the turntable should be. */
data class TurntableTarget(
    val disc: Disc? = null,
    val playing: Boolean = false,
    /** Where the needle belongs on the record (from song progress). */
    val playAngle: Float = ArmGeometry.REST_DEG,
    /** Park the arm and hold it there (while flicking through songs). */
    val holdArm: Boolean = false,
    /** Bumped each time the player's position jumps: re-cue the needle (lift, move, drop). */
    val seekId: Int = 0,
    /** Set while a finger holds the arm: the angle it's held at. */
    val grabAngle: Float? = null,
    /** When not playing: true = arm goes home (STOP), false = needle hovers (cue ▲). */
    val parkWhenPaused: Boolean = false,
    /** Bumped to jump straight to the target with no animation (opening / coming back). */
    val snapId: Int = 0,
)

/** One physical move. */
sealed interface Move {
    /** Raise the arm. If the needle was on the record, it leaves it now. A hand lifts it quicker. */
    data class Lift(val reason: String, val byHand: Boolean = false) : Move
    /** Lower the arm without touching a record (into the rest clip, or hovering down). */
    data object Lower : Move
    /** Lower the needle onto [on]: the music starts. */
    data class DropNeedle(val on: SongKey) : Move
    /** Move sideways while raised. */
    data class Swing(val toDeg: Float) : Move
    /** The groove pulls the needle inward while it plays (needle stays down). */
    data class Track(val toDeg: Float) : Move
    /** A finger is holding the raised arm here. */
    data class FollowHand(val toDeg: Float) : Move
    data object RemoveRecord : Move
    data class PlaceRecord(val disc: Disc) : Move
    /** Same record, new details (e.g. its art arrived): swap them in silently. */
    data class UpdateDisc(val disc: Disc) : Move
    /** Be there at once, no animation (opening the app, coming back to it). */
    data class SnapTo(val deck: Deck) : Move
}

object MovePlanner {
    /** A position change bigger than this is a seek: re-cue instead of dragging the needle. */
    const val SEEK_JUMP_DEG = 1.5f

    /** How far the needle may lag before the groove pulls the arm inward again. */
    const val TRACK_STEP_DEG = 0.15f

    /**
     * The next physical move, or null if the deck already matches the target.
     * @param seeked the player's position jumped since the last move
     * @param snap jump straight to the target
     */
    fun next(deck: Deck, target: TurntableTarget, seeked: Boolean = false, snap: Boolean = false): Move? {
        val t = target
        if (snap) {
            val play = t.playing && t.disc != null
            return Move.SnapTo(
                Deck(
                    disc = t.disc,
                    armDeg = if (play) t.playAngle else ArmGeometry.REST_DEG,
                    armUp = false,
                    needleOn = if (play) t.disc?.key else null,
                )
            )
        }

        // Same song, new details (art finished loading): swap them in silently.
        if (t.disc != null && deck.disc?.key == t.disc.key && deck.disc != t.disc) return Move.UpdateDisc(t.disc)

        // A finger holds the arm: lift the needle once, then follow the finger.
        t.grabAngle?.let { g ->
            if (deck.disc == null) return null
            if (deck.armDown) return Move.Lift("picked up by hand", byHand = true)
            if (abs(deck.armDeg - g) > 0.01f) return Move.FollowHand(g)
            return null
        }

        // Flicking through songs: park and wait. No record swaps until you land,
        // so only the song you stop on gets the full animation.
        if (t.holdArm) return if (deck.armParked && deck.armDown) null else park(deck, "flicking through songs")

        // Wrong record (or no record wanted): park the arm, then swap.
        if (deck.disc?.key != t.disc?.key) {
            if (!(deck.armParked && deck.armDown)) return park(deck, "record change")
            if (deck.disc != null) return Move.RemoveRecord
            if (t.disc != null) return Move.PlaceRecord(t.disc)
            return null
        }

        // No record at all: the arm belongs in the rest clip.
        val disc = deck.disc ?: return if (deck.armParked && deck.armDown) null else park(deck, "no record")

        // Right record, playing: needle down at the play position.
        if (t.playing) {
            // While the needle is down it only re-tracks once it lags a little behind:
            // invisible drift, and the arm isn't redrawn nonstop.
            val off = abs(deck.armDeg - t.playAngle)
            val onSpot = off < (if (deck.armDown) TRACK_STEP_DEG else 0.05f)
            return when {
                deck.armDown && deck.armParked -> Move.Lift("leaving rest")
                deck.armDown && onSpot -> null
                // Small lag: the groove pulls the arm inward. A big jump (a seek in the
                // player, or "previous" restarting the song): lift, move, drop again.
                deck.armDown -> if (seeked && off > SEEK_JUMP_DEG) Move.Lift("seek") else Move.Track(t.playAngle)
                !onSpot -> Move.Swing(t.playAngle)
                else -> Move.DropNeedle(disc.key)
            }
        }

        // Right record, paused: cue up. The needle hovers where it was, the record turns.
        return when {
            // STOP: the arm goes all the way home (a cue ▲ just hovers instead).
            t.parkWhenPaused && !(deck.armParked && deck.armDown) -> park(deck, "stop")
            deck.armDown && !deck.armParked -> Move.Lift("pause wanted")
            // Hovering outside the record (let go of the arm over the edge): back to rest.
            deck.armUp && !deck.armParked && deck.armDeg < disc.size.leadInDeg - 1f -> park(deck, "put back to rest")
            deck.armUp && deck.armParked -> Move.Lower                 // settle into the clip
            else -> null
        }
    }

    /**
     * Should [current], still under way from [deck], give way to what [target] wants now?
     * Only the groove glide is ever cut short (it's the slow one: up to 2 s), and only
     * for something that matters: a lift (pause, grab, seek, song change, flicking) or
     * the needle's destination jumping (the lead-out). A target that merely crept on
     * lets the glide finish, so the arm keeps moving smoothly.
     */
    fun interrupts(current: Move, deck: Deck, target: TurntableTarget, seeked: Boolean = false, snap: Boolean = false): Boolean {
        if (current !is Move.Track) return false
        return when (val next = next(deck, target, seeked, snap)) {
            is Move.Lift, is Move.SnapTo -> true
            is Move.Track -> abs(next.toDeg - current.toDeg) >= SEEK_JUMP_DEG
            else -> false
        }
    }

    /** Lift → swing to rest → lower, one move per call. */
    private fun park(deck: Deck, why: String): Move = when {
        deck.armDown -> Move.Lift(why)
        !deck.armParked -> Move.Swing(ArmGeometry.REST_DEG)
        else -> Move.Lower
    }
}
