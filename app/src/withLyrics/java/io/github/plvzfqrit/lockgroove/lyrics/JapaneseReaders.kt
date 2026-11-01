package io.github.plvzfqrit.lockgroove.lyrics

/** The "withLyrics" build reads Japanese with the Kuromoji dictionary. */
object JapaneseReaders {
    fun reader(): JapaneseReader? = KuromojiReader
}
