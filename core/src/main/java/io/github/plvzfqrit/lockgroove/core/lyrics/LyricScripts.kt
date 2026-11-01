package io.github.plvzfqrit.lockgroove.core.lyrics

/** Which writing system a lyric line uses, to decide whether to show it in Latin letters too. */
object LyricScripts {
    /** The line has letters outside the Latin alphabet: Chinese, Korean, Japanese, Arabic, Cyrillic, Thai... */
    fun needsRomanization(text: String): Boolean = scripts(text).any { it !in LATIN_LIKE }

    /**
     * Japanese that mixes kana with kanji. A general-purpose transliterator reads those kanji as
     * Mandarin, which would be wrong for a Japanese song, so such lines are better left alone.
     */
    fun isMixedJapanese(text: String): Boolean {
        val found = scripts(text)
        val kana = Character.UnicodeScript.HIRAGANA in found || Character.UnicodeScript.KATAKANA in found
        return kana && Character.UnicodeScript.HAN in found
    }

    /** The line has hiragana or katakana: it is Japanese (kanji alone can't be told from Chinese). */
    fun isJapanese(text: String): Boolean {
        val found = scripts(text)
        return Character.UnicodeScript.HIRAGANA in found || Character.UnicodeScript.KATAKANA in found
    }

    /**
     * Chinese: Han characters with no kana (Japanese) or Hangul (Korean) beside them. Only these
     * lines can be switched between traditional and simplified characters.
     */
    fun isChinese(text: String): Boolean {
        val found = scripts(text)
        return Character.UnicodeScript.HAN in found && found.none {
            it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA ||
                it == Character.UnicodeScript.HANGUL
        }
    }

    /** The scripts of the letters in [text]. */
    private fun scripts(text: String): Set<Character.UnicodeScript> {
        val found = HashSet<Character.UnicodeScript>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) found += Character.UnicodeScript.of(cp)
            i += Character.charCount(cp)
        }
        return found
    }

    private val LATIN_LIKE = setOf(
        Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED,
    )
}
