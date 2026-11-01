package io.github.plvzfqrit.lockgroove.lyrics

import android.icu.text.Transliterator
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricScripts

/**
 * Writes a lyric line in plain Latin letters (pinyin, Korean romanization, romaji, Arabic and
 * Cyrillic transliteration...) using the transliterator built into Android. Nothing is downloaded.
 * Call it from one thread (the screen's): the transliterator isn't thread-safe.
 */
object Romanizer {
    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }

    private val simplifier: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Hant-Hans") }.getOrNull()
    }

    /** Traditional Chinese characters as simplified ones (the text itself if it can't be converted). */
    fun simplify(text: String): String =
        runCatching { simplifier?.transliterate(text) }.getOrNull() ?: text

    /** The line in Latin letters, or null when it already is, or can't be read reliably. */
    fun romanize(text: String): String? {
        if (!LyricScripts.needsRomanization(text) || LyricScripts.isMixedJapanese(text)) return null
        val out = runCatching { transliterator?.transliterate(text) }.getOrNull()?.trim()
        return out?.takeIf { it.isNotEmpty() && it != text }
    }
}
