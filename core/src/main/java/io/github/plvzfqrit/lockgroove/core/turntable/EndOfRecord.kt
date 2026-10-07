package io.github.plvzfqrit.lockgroove.core.turntable

import io.github.plvzfqrit.lockgroove.core.model.SongKey
import io.github.plvzfqrit.lockgroove.core.playback.PlayerSnapshot
import io.github.plvzfqrit.lockgroove.core.playback.PositionTracker

/** What happens at the end of a record. */
sealed interface EndEffect {
    /** The music is over: silence, and the needle rides the lead-out into the locked groove. */
    data object EnterLeadOut : EndEffect
    /** Something else happened in the lead-out (STOP, a skip, the arm grabbed): normal rules again. */
    data object LeadOutCancelled : EndEffect
    /** ↻1: lift, swing back to the START of the same record, drop. */
    data class Repeat(val key: SongKey) : EndEffect
    /** On to the next record. */
    data class NextRecord(val key: SongKey) : EndEffect
}

/*
 * Like a semi-automatic turntable: when the needle reaches the end, it glides into the
 * locked groove by the label, circles there silently for a moment, THEN the arm does
 * its thing. It all starts a moment BEFORE the player would auto-advance, so the next
 * song never plays ahead of the record swap.
 */
class EndOfRecord {
    private data class LeadOut(val key: SongKey, val repeat: Boolean, val since: Long)
    private var leadOut: LeadOut? = null

    /** The record the needle is on and when it ends (monotonic ms). */
    private var ending: Pair<SongKey, Long>? = null

    val inLeadOut get() = leadOut != null

    fun reset() {
        leadOut = null
    }

    /**
     * A song change within a few seconds of the record's end is the player's natural
     * hand-off, not a glitch: it gets paused at once.
     */
    fun isNaturalHandOff(needleOn: SongKey?, now: Long): Boolean {
        val e = ending ?: return false
        return needleOn != null && e.first == needleOn && now >= e.second - NATURAL_END_WINDOW_MS
    }

    /**
     * @param audible the needle is on [song]'s record and it's sounding
     * @param leadMs how early to stop: the margin plus the player's learned crossfade
     */
    fun tick(
        now: Long, song: PlayerSnapshot?, needleOn: SongKey?, audible: Boolean,
        browsing: Boolean, wantPlaying: Boolean, grabbing: Boolean, leadMs: Long, repeatOne: Boolean,
    ): EndEffect? {
        leadOut?.let { lo ->
            if (!wantPlaying || browsing || grabbing || song?.key != lo.key) {
                leadOut = null
                return EndEffect.LeadOutCancelled
            }
            if (now - lo.since < LEAD_OUT_MS) return null
            leadOut = null
            return if (lo.repeat) EndEffect.Repeat(lo.key) else EndEffect.NextRecord(lo.key)
        }
        if (!audible || song == null) return null
        val left = PositionTracker.remaining(song, now) ?: return null      // length unknown: can't
        ending = song.key to (now + left)
        if (left > leadMs || needleOn != song.key || browsing) return null
        leadOut = LeadOut(song.key, repeatOne, now)
        return EndEffect.EnterLeadOut
    }

    companion object {
        /** Glide into the locked groove (~1.5 s) plus a couple of silent turns there. */
        const val LEAD_OUT_MS = 3500L
        /** A song change within this long of the record's end is a natural hand-off. */
        const val NATURAL_END_WINDOW_MS = 5000L
    }
}
