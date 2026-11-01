package io.github.plvzfqrit.lockgroove.lyrics

import android.icu.text.Transliterator
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricScripts

/**
 * Writes a lyric line in plain Latin letters (pinyin, Korean romanization, romaji, Arabic and
 * Cyrillic transliteration...) using the transliterator built into Android. Nothing is downloaded.
 * The transliterators aren't thread-safe, so every call takes a lock.
 */
object Romanizer {
    /** Plain letters, no accents: right for Korean, Arabic, Cyrillic... */
    private val plain: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }

    /** Keeps the accents, so Chinese comes out as pinyin with its tone marks (nǐ hǎo). NFC joins each mark to its letter. */
    private val toned: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Any-Latin; NFC") }.getOrNull()
    }

    private val simplifier: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Hant-Hans") }.getOrNull()
    }

    /** Traditional Chinese characters as simplified ones (the text itself if it can't be converted). */
    fun simplify(text: String): String = synchronized(this) {
        runCatching { simplifier?.transliterate(text) }.getOrNull() ?: text
    }

    /** The line in Latin letters, or null when it already is, or can't be read reliably. */
    fun romanize(text: String): String? {
        if (!LyricScripts.needsRomanization(text)) return null
        // Japanese needs a dictionary (kanji are read in context); without one there is no reading.
        if (LyricScripts.isJapanese(text)) {
            return JapaneseReaders.reader()?.romaji(text)?.takeIf { it != text }
        }
        val transliterator = if (LyricScripts.isChinese(text)) toned else plain
        val out = synchronized(this) { runCatching { transliterator?.transliterate(text) }.getOrNull() }?.trim()
        return out?.takeIf { it.isNotEmpty() && it != text }
    }
}
