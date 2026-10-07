package io.github.plvzfqrit.lockgroove.core.playback

import io.github.plvzfqrit.lockgroove.core.model.Clock
import io.github.plvzfqrit.lockgroove.core.model.SongKey

/*
 * Rule: music plays if and only if the needle is on the record.
 *
 * The app doesn't mirror the player; the NEEDLE drives the player:
 *   - needle touches down → Play
 *   - needle starts lifting → Pause
 *
 * The keys only change what you WANT (wantPlaying). The turntable then does the
 * physical moves, and the music follows the needle.
 *
 * If music starts from somewhere else while the needle isn't on the current record
 * (notification, headphones button, the player auto-playing after a skip), that
 * counts as "the user wants to play": it's paused right away, and the needle drop
 * starts it for real. If music is stopped from somewhere else while the needle is
 * down, the needle lifts to match.
 *
 * Nothing here sleeps. Grace periods are timestamps compared with the clock on each
 * call, so they can't race each other.
 */
class PlaybackArbiter(private val clock: Clock) {
    /** What the listener wants. Drives the turntable, and the ▶■ key's light. */
    var wantPlaying = false

    /**
     * The needle has run off the music into the lead-out: still ON the record, but
     * there's nothing more to hear. The one place where "needle down" and "silence"
     * go together, exactly like on a real record.
     */
    var inLeadOut = false
        private set

    // A command we sent that the player hasn't confirmed yet. While it's in flight,
    // contradicting reports are ignored (players take a moment to react).
    private var pending: Boolean? = null
    private var pendingSince = 0L

    /** Coming back to the app: ignore the player until this time while we snap into place. */
    private var resumeGraceUntil = 0L

    // The latest player report, since when it's been like that, and whether we acted on it.
    private data class Seen(val playing: Boolean, val key: SongKey?, val touching: Boolean, val needleDown: Boolean)
    private var seen: Seen? = null
    private var seenSince = 0L
    private var graceMs = 0L
    private var acted = true
    private var lastSafetyCheck = 0L

    /** Take over the player's current state as-is (app just opened). Sends nothing. */
    fun adopt(playing: Boolean) {
        wantPlaying = playing
        pending = null
        inLeadOut = false
        seen = null
    }

    /** Back in the app: give the snap a moment before reacting to the player. */
    fun startResumeGrace() {
        resumeGraceUntil = clock.nowMs() + RESUME_GRACE_MS
    }

    fun onNeedleDown(): List<PlayerCommand> {
        inLeadOut = false
        return command(true)
    }

    fun onNeedleUp(): List<PlayerCommand> {
        inLeadOut = false
        return command(false)
    }

    /** The needle glides past the last note into the lead-out: silence, needle stays down. */
    fun enterLeadOut(): List<PlayerCommand> {
        inLeadOut = true
        return command(false)
    }

    /** Leaving the lead-out without lifting (cancelled). */
    fun leaveLeadOut() {
        inLeadOut = false
    }

    /** A pause right behind a skip, so a player that auto-plays is silenced at once. */
    fun silence(): List<PlayerCommand> = command(false)

    /**
     * A player report. A mismatch with the needle gets a moment before it counts:
     * if it goes back to normal by itself, nothing happens.
     * @param touching the needle rests on THIS song's record
     * @param naturalHandOff the song changed right at the end of the record: the
     *        player's own hand-off, not a glitch, so it counts at once
     */
    fun onPlayer(
        playing: Boolean, key: SongKey?, touching: Boolean, needleDown: Boolean, naturalHandOff: Boolean = false,
    ): List<PlayerCommand> {
        val now = Seen(playing, key, touching, needleDown)
        if (now != seen) {
            seen = now
            seenSince = clock.nowMs()
            acted = false
            graceMs = when {
                needleDown && !playing -> SELF_PAUSE_GRACE_MS
                needleDown && !touching -> if (naturalHandOff) 0L else SONG_CHANGE_SETTLE_MS
                else -> 0L
            }
        }
        return onTick()
    }

    /** Grace periods expire here; call it regularly. */
    fun onTick(): List<PlayerCommand> {
        val now = clock.nowMs()
        val s = seen ?: return emptyList()
        // Just back in the app: let the snap happen first.
        if (now < resumeGraceUntil) return emptyList()
        if (!acted) {
            // The grace counts from the report, or from the end of the resume grace.
            if (now - maxOf(seenSince, resumeGraceUntil) < graceMs) return emptyList()
            acted = true
            lastSafetyCheck = now
            return evaluate(s.playing, s.touching)
        }
        // Safety net: music playing while the needle is UP (a player that ignored a
        // pause, e.g. mid-transition). Re-check every second and pause it again.
        if (s.playing && !s.needleDown && now - lastSafetyCheck >= SAFETY_CHECK_MS) {
            lastSafetyCheck = now
            return evaluate(playing = true, touching = false)
        }
        return emptyList()
    }

    private fun evaluate(playing: Boolean, touching: Boolean): List<PlayerCommand> {
        pending?.let { p ->
            if (playing == p) {
                pending = null                                   // confirmed: this report is
                return emptyList()                               // our own command, not the user
            } else if (clock.nowMs() - pendingSince < PENDING_TIMEOUT_MS) {
                return emptyList()                               // still settling, ignore
            } else {
                pending = null                                   // player ignored us; re-evaluate
            }
        }
        // In the lead-out, silence with the needle down is correct: don't "fix" it.
        if (inLeadOut && touching && !playing) return emptyList()
        return when {
            // Sound without the needle on this record: hold it, and go drop the needle.
            playing && !touching -> {
                wantPlaying = true
                command(false)
            }
            // Stopped from outside while the needle is down: lift the needle.
            !playing && touching -> {
                wantPlaying = false
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun command(playNow: Boolean): List<PlayerCommand> {
        pending = playNow
        pendingSince = clock.nowMs()
        return listOf(if (playNow) PlayerCommand.Play else PlayerCommand.Pause)
    }

    companion object {
        /** A pause the player does by itself must last this long before the needle lifts. */
        const val SELF_PAUSE_GRACE_MS = 1500L

        /** A song change must stick this long before the turntable treats it as real. */
        const val SONG_CHANGE_SETTLE_MS = 700L

        /** Coming back to the app: ignore the player's state this long while we snap into place. */
        const val RESUME_GRACE_MS = 1500L

        /** How long a command may go unconfirmed before reports count again. */
        const val PENDING_TIMEOUT_MS = 2000L

        /** How often the safety net re-checks music playing with the needle up. */
        const val SAFETY_CHECK_MS = 1000L
    }
}
