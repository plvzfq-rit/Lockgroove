package io.github.plvzfqrit.lockgroove.lyrics

/** The "withLyrics" build: lyrics come from LRCLIB. */
object LyricsFeature {
    val available = true
    private val lrclib by lazy { LrcLibSource() }
    fun source(): LyricsSource? = lrclib
}
