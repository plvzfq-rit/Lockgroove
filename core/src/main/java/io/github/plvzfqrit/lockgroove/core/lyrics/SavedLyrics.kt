package io.github.plvzfqrit.lockgroove.core.lyrics

/**
 * Lyrics saved on the phone: the `.lrc` text, preceded by a header line saying when they were
 * fetched (`[x-fetched:<ms since 1970>]`). The header is how saved lyrics expire. It is not a
 * timed line, so [LrcParser] skips it, and an older file with no header still reads fine.
 */
object SavedLyrics {
    /** Saved lyrics are fetched again after this long. */
    const val MAX_AGE_DAYS = 90
    const val MAX_AGE_MS = MAX_AGE_DAYS * 24L * 60 * 60 * 1000

    class Saved(
        /** When they were fetched, or null for a file written before there was a header. */
        val fetchedAtMs: Long?,
        val lines: List<LyricLine>,
    )

    private val header = Regex("""^\s*\[x-fetched:(\d+)]""")

    fun format(fetchedAtMs: Long, lines: List<LyricLine>): String =
        "[x-fetched:$fetchedAtMs]\n" + LrcWriter.format(lines)

    fun parse(text: String): Saved {
        val fetched = header.find(text.lineSequence().firstOrNull().orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        return Saved(fetched, LrcParser.parse(text))
    }

    /** Past its age. A date in the future (the phone's clock was moved back) doesn't count as expired. */
    fun isExpired(fetchedAtMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean =
        nowMs - fetchedAtMs > maxAgeMs
}
