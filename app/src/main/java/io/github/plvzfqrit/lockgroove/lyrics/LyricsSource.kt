package io.github.plvzfqrit.lockgroove.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine

/** Somewhere to look up a song's synced lyrics. Only the "withLyrics" build has one. */
interface LyricsSource {
    /** Who gets asked: named in the notice the listener sees first. */
    val serviceName: String

    /** Synced lyrics for the song, an empty list when it has none, or null when the lookup failed. */
    suspend fun find(title: String, artist: String?, durationMs: Long): List<LyricLine>?
}
