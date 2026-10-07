package io.github.plvzfqrit.lockgroove.core.playback

/**
 * Players hand songs over early by different amounts (Spotify's crossfade, YT Music
 * being YT Music). When a song changes by itself a few seconds before its end, with
 * the needle on it, that's the player's crossfade: remember how early, per player,
 * and lift the needle that much sooner next time.
 */
class CrossfadeLearner(initial: Map<String, Long> = emptyMap()) {
    /** Learned early hand-off per player app (package name → ms). */
    var learned: Map<String, Long> = initial
        private set

    /**
     * A new report arrived. Returns true if something new was learned.
     * @param needleWasOnPrev the needle was on [prev]'s record
     * @param flicking the change came from our own skips, not the player
     */
    fun observe(prev: PlayerSnapshot, next: PlayerSnapshot, needleWasOnPrev: Boolean, flicking: Boolean, now: Long): Boolean {
        if (prev.key == next.key || !needleWasOnPrev || flicking) return false
        val left = PositionTracker.remaining(prev, now) ?: return false
        // Only a few seconds early counts: a change 40 s early was you skipping.
        if (left !in MIN_MS..MAX_MS) return false
        learned = learned + (prev.player to left)
        return true
    }

    /** How early to lift the needle for [player]. */
    fun leadFor(player: String): Long = learned[player] ?: 0L

    fun forget(player: String) {
        learned = learned - player
    }

    companion object {
        const val MIN_MS = 1_000L
        const val MAX_MS = 12_500L
    }
}
