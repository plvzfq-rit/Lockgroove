package io.github.plvzfqrit.lockgroove.lyrics

/** Somewhere to look up a song's synced lyrics. Only the "withLyrics" build has one. */
interface LyricsSource {
    /** Who gets asked: named in the notice the listener sees first. */
    val serviceName: String

    /**
     * Synced lyrics for the song, or the reason there are none (see [LyricsResult]).
     * @param fresh ask again even if an earlier answer was kept (the listener pressed Retry)
     */
    suspend fun find(title: String, artist: String?, durationMs: Long, fresh: Boolean = false): LyricsResult
}
