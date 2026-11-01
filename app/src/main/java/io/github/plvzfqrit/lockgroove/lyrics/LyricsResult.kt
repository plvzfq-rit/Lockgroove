package io.github.plvzfqrit.lockgroove.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine

/** What a lyrics lookup came back with. The three "no" answers mean different things to the listener. */
sealed interface LyricsResult {
    data class Found(val lines: List<LyricLine>) : LyricsResult

    /** The service has no synced lyrics under this song's name at all. */
    data object NotFound : LyricsResult

    /** It has entries, but none fit THIS recording (another version, a translation, another song). */
    data object NoFit : LyricsResult

    /** The question couldn't be asked: offline, or the service was too busy. */
    data object Unreachable : LyricsResult
}
