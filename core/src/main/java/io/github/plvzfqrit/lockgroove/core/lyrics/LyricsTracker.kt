package io.github.plvzfqrit.lockgroove.core.lyrics

/** Finds which lyric line is being sung. */
object LyricsTracker {
    /**
     * The index of the last line that has started by [positionMs], or -1 before the first
     * line (or with no lyrics). [lines] must be sorted by time, as [LrcParser] returns them.
     */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {                                       // binary search: lyrics can be long
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}
