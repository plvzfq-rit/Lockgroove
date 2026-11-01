package io.github.plvzfqrit.lockgroove.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine

/** What the lyrics area knows about the song being played. */
sealed interface LyricsState {
    /** Not asked for yet: the area offers a "Get lyrics" button. Nothing is fetched until it is tapped. */
    data object Idle : LyricsState

    /** Asking. */
    data object Looking : LyricsState

    /** The lyrics database has nothing under this song's name. */
    data object None : LyricsState

    /** It has lyrics under that name, but none fit this recording. */
    data object NoFit : LyricsState

    /** The question couldn't be asked: offline, or the service was too busy. */
    data object Unreachable : LyricsState

    /** [readings] has one entry per line: its Latin-letter reading, or null when it needs none. */
    data class Found(val lines: List<LyricLine>, val readings: List<String?>) : LyricsState
}
