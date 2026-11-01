package io.github.plvzfqrit.lockgroove.lyrics

/** The standard build has no lyrics: no internet permission, and no code that could use one. */
object LyricsFeature {
    val available = false
    fun source(): LyricsSource? = null
}
