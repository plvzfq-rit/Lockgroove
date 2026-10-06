package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand

/** Something the skip queue needs done. */
sealed interface SkipEffect {
    data class Send(val command: PlayerCommand) : SkipEffect
    /** A pause right behind a skip, in case the player auto-plays (goes through the arbiter). */
    data object Silence : SkipEffect
}

/*
 * Flicking through songs. Tap Next/Previous (or hold for fast-forward) as many times
 * as you like: the needle lifts once, the skips go to the player silently while the
 * arm is up, and only the song you LAND on gets the record swap + needle drop.
 *
 * A reducer: flick() and runOut() queue skips, tick() sends them one at a time while
 * the needle is off the record, and says when you've landed.
 */
class SkipQueue {
    /** Skips not sent yet: + next, - previous. */
    var queued = 0
        private set

    /** Flicking: the arm parks and waits, no record swaps until you land. */
    var browsing = false
        private set

    private var lastFlickAt = 0L

    /** An automatic skip decided at the end of a record: which record it was for. */
    private var runOutFor: SongKey? = null

    private sealed interface Phase {
        data object Idle : Phase
        /** A real previous: rewinding to 0:00 first. */
        data class Rewinding(val before: SongKey?, val until: Long) : Phase
        /** Rewound: a moment for the player, then "previous". */
        data class Rewound(val before: SongKey?, val sendAt: Long) : Phase
        /** Sent a skip: wait for the player to switch songs (titles update as you flick). */
        data class AwaitingChange(val before: SongKey?, val until: Long) : Phase
        /** Minimum spacing between skips sent to the player. */
        data class Gap(val until: Long) : Phase
    }
    private var phase: Phase = Phase.Idle

    fun flick(dir: Int, now: Long) {
        queued += dir
        browsing = true
        lastFlickAt = now
    }

    /** The record ended: on to the next one (unless the player already went there). */
    fun runOut(key: SongKey, now: Long) {
        runOutFor = key
        flick(+1, now)
    }

    /** Coming back to the app: forget everything in flight. */
    fun reset() {
        queued = 0
        browsing = false
        runOutFor = null
        phase = Phase.Idle
    }

    /**
     * @param needleUp the needle is off the record (skips are only sent then, so nothing is heard)
     * @param key the player's current song
     * @param positionMs where the player is in it right now
     * @param settledKey the record the turntable has settled on
     */
    fun tick(now: Long, needleUp: Boolean, key: SongKey?, positionMs: Long?, settledKey: SongKey?): List<SkipEffect> {
        // Landed: no skips left, no new taps for a moment, AND the record has caught up
        // with the song you're on. (Without that last check the arm headed back to the
        // OLD record, then turned around for the new one.)
        if (browsing && queued == 0 && phase == Phase.Idle && now - lastFlickAt >= LAND_AFTER_MS) {
            val caughtUp = key != null && settledKey == key
            if (caughtUp || now - lastFlickAt >= LAND_AFTER_MS + LAND_SETTLE_TIMEOUT_MS) browsing = false
        }

        return when (val p = phase) {
            Phase.Idle -> {
                if (queued == 0 || !needleUp) return emptyList()
                val dir = if (queued > 0) 1 else -1
                // Automatic run-out skip: make sure the player hasn't already moved on by
                // itself (auto-advance, crossfade, repeat-one looping). Sending it anyway
                // would skip a song.
                runOutFor?.let { ended ->
                    runOutFor = null
                    val alreadyMoved = key != ended ||
                            (dir < 0 && positionMs != null && positionMs < RESTART_WINDOW_MS)
                    if (alreadyMoved) {
                        queued -= dir
                        return emptyList()
                    }
                }
                // A REAL previous: players treat "previous" as "restart this song" once
                // you're a few seconds in. Rewind to 0:00 first, then "previous" goes back.
                if (dir < 0 && positionMs != null && positionMs >= PREVIOUS_RESTART_MS) {
                    phase = Phase.Rewinding(key, now + REWIND_WAIT_MS)
                    return listOf(SkipEffect.Send(PlayerCommand.SeekTo(0L)))
                }
                send(dir, key, now)
            }
            is Phase.Rewinding -> {
                if ((positionMs != null && positionMs < PREVIOUS_RESTART_MS) || now >= p.until) {
                    phase = Phase.Rewound(p.before, now + REWOUND_PAUSE_MS)
                }
                emptyList()
            }
            is Phase.Rewound -> if (now >= p.sendAt) send(-1, p.before, now) else emptyList()
            is Phase.AwaitingChange -> {
                if (key != p.before || now >= p.until) phase = Phase.Gap(now + SKIP_GAP_MS)
                emptyList()
            }
            is Phase.Gap -> {
                if (now >= p.until) phase = Phase.Idle
                emptyList()
            }
        }
    }

    private fun send(dir: Int, before: SongKey?, now: Long): List<SkipEffect> {
        queued -= dir
        phase = Phase.AwaitingChange(before, now + SKIP_WAIT_MS)
        val skip = if (dir > 0) PlayerCommand.Next else PlayerCommand.Previous
        return listOf(SkipEffect.Send(skip), SkipEffect.Silence)
    }

    companion object {
        /** How long after the last tap you count as "landed" on a song. */
        const val LAND_AFTER_MS = 900L
        /** Max extra wait for the landed song to settle before playing anyway. */
        const val LAND_SETTLE_TIMEOUT_MS = 2500L
        /** Max wait for the player to switch songs after each skip. */
        const val SKIP_WAIT_MS = 400L
        /** Minimum spacing between skips sent to the player. */
        const val SKIP_GAP_MS = 120L
        /** Players restart the song (instead of going back) when "previous" comes after this. */
        const val PREVIOUS_RESTART_MS = 2000L
        /** Within this long of a song's start, the player has just moved on by itself. */
        const val RESTART_WINDOW_MS = 3000L
        /** Max wait for the rewind to 0:00 before "previous". */
        const val REWIND_WAIT_MS = 1000L
        /** Pause between the rewind and "previous". */
        const val REWOUND_PAUSE_MS = 150L
    }
}
