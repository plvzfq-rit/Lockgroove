package io.github.plvzfqrit.lockgroove.lyrics

/** Reads a line of Japanese (kanji and kana) as romaji. Only the "withLyrics" build has one. */
interface JapaneseReader {
    /** The line in Latin letters, or null if it can't be read. May take a moment the first time (it loads a dictionary): never call it while drawing. */
    fun romaji(text: String): String?

    /** Free the memory the reader holds (the dictionary). It is loaded again if needed. */
    fun release()
}
