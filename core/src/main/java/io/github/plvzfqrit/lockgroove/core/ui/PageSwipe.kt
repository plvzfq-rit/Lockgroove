package io.github.plvzfqrit.lockgroove.core.ui

import kotlin.math.abs

/*
 * The turntable ⇄ stats swipe. One progress value, 0 = turntable, 1 = stats, in
 * three beats:
 *   0.00–0.45  the whole deck slides off to the left, under your finger
 *   0.45–0.60  just the background (it never moves)
 *   0.60–1.00  the stats sections pop in one after another
 * Let go past the commit point (or flick) and it finishes by itself; going back plays
 * it in reverse.
 */
object PageSwipe {
    /** The deck has slid off by here. */
    const val FADE_OUT_END = 0.45f
    /** The stats start popping in from here. */
    const val REVEAL_START = 0.60f
    /** Full turntable → stats (or back) when it plays by itself. */
    const val TRANSITION_MS = 1100
    /** How far a swipe must go (of the screen width) before letting go finishes it. */
    const val COMMIT_FRACTION = 0.12f
    /** A quick flick this fast (dp/s) finishes the swipe whatever the distance. */
    const val FLING_DP_S = 350f

    /** A swipe that starts here begins on the stats side. */
    fun startsOpen(progress: Float) = progress >= REVEAL_START

    /**
     * Progress while dragging, [dx] px from where the swipe began at [startProgress].
     * Turntable side: the deck follows the finger 1:1. Stats side: half a screen takes
     * the sections away.
     */
    fun dragged(startProgress: Float, dx: Float, widthPx: Float): Float =
        if (!startsOpen(startProgress)) (startProgress - dx / widthPx * FADE_OUT_END).coerceIn(0f, FADE_OUT_END)
        else (startProgress - dx / (widthPx * 0.5f) * (1f - REVEAL_START)).coerceIn(REVEAL_START, 1f)

    /**
     * The finger lifted: should the stats end up open? A flick decides on its own; a slow
     * drag counts once it went a little way; a tiny wobble goes back to where it started.
     * Left (negative) opens, right closes.
     */
    fun settle(startedOpen: Boolean, dx: Float, velocityX: Float, widthPx: Float, flingPxPerS: Float): Boolean {
        val dir = when {
            abs(velocityX) > flingPxPerS -> if (velocityX < 0f) -1 else 1
            abs(dx) >= widthPx * COMMIT_FRACTION -> if (dx < 0f) -1 else 1
            else -> 0
        }
        return when (dir) {
            -1 -> true
            1 -> false
            else -> startedOpen
        }
    }

    /** How long the rest of the way takes, from [from] to [to]. */
    fun durationMs(from: Float, to: Float): Int = (TRANSITION_MS * abs(to - from)).toInt().coerceAtLeast(200)

    /** The stats' own 0..1 reveal for an overall [progress]. */
    fun reveal(progress: Float): Float = ((progress - REVEAL_START) / (1f - REVEAL_START)).coerceIn(0f, 1f)
}
