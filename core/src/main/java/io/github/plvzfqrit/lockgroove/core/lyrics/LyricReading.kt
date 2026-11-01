package io.github.plvzfqrit.lockgroove.core.lyrics

/** Puts the pieces of a romanized lyric line back together as readable text. */
object LyricReading {
    private const val NO_SPACE_BEFORE = ",.!?;:)]}"
    private const val NO_SPACE_AFTER = "([{"

    /** Japanese and other full-width punctuation, as plain punctuation (the quote brackets just go). */
    private val punctuation = mapOf(
        '、' to ",", '，' to ",", '。' to ".", '．' to ".", '！' to "!", '？' to "?", '：' to ":", '；' to ";",
        '（' to "(", '）' to ")", '・' to " ", '「' to "", '」' to "", '『' to "", '』' to "",
    )

    /**
     * Words separated by one space, except that nothing goes before , . ! ? ; : ) and nothing
     * after an opening bracket. Empty pieces are skipped.
     */
    fun join(parts: List<String>): String {
        val out = StringBuilder()
        for (raw in parts) {
            val part = tidy(raw).trim()
            if (part.isEmpty()) continue
            val glued = part.first() in NO_SPACE_BEFORE || (out.isNotEmpty() && out.last() in NO_SPACE_AFTER)
            if (out.isNotEmpty() && !glued) out.append(' ')
            out.append(part)
        }
        return out.toString()
    }

    private fun tidy(text: String): String = buildString {
        for (c in text) append(punctuation[c] ?: c.toString())
    }
}
