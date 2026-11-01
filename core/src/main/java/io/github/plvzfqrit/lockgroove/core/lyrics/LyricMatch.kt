package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.math.abs

/**
 * Chooses which of several lyric files belongs to THIS recording. Community lyric databases hold
 * many versions of a song, some for another recording, a translation, or even another song set to
 * the same tune, and their stated lengths can be wrong.
 */
object LyricMatch {
    /** A candidate: the length its entry claims, and its parsed lyrics. */
    class Candidate(val durationS: Double, val lines: List<LyricLine>)

    /** A candidate this much longer or shorter than the song is another recording. */
    const val MAX_LENGTH_GAP_S = 3.0

    /** Lyrics that stop before this share of the song are probably timed for a shorter recording. */
    const val MIN_COVERAGE = 0.6

    /**
     * The index of the best candidate for a song [seconds] long (0 = length unknown), or null.
     * 1. Its claimed length must be close to the song's.
     * 2. Its lyrics must not run past the end of the song.
     * 3. Of the rest, lyrics that cover most of the song come first, then the closest length.
     * (Lyrics that stop early are kept as a last resort: a long instrumental ending is possible.)
     */
    fun pick(candidates: List<Candidate>, seconds: Long): Int? {
        var best: Int? = null
        var bestScore = Double.MAX_VALUE
        candidates.forEachIndexed { index, candidate ->
            if (candidate.lines.isEmpty()) return@forEachIndexed
            val gap = if (seconds > 0) abs(candidate.durationS - seconds) else 0.0
            if (seconds > 0 && gap > MAX_LENGTH_GAP_S) return@forEachIndexed
            val lastLineS = candidate.lines.last().timeMs / 1000.0
            if (seconds > 0 && lastLineS > seconds + MAX_LENGTH_GAP_S) return@forEachIndexed
            val stopsEarly = seconds > 0 && lastLineS < seconds * MIN_COVERAGE
            val score = gap + if (stopsEarly) EARLY_PENALTY else 0.0
            if (score < bestScore) {
                best = index
                bestScore = score
            }
        }
        return best
    }

    private const val EARLY_PENALTY = 1000.0
}
