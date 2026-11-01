package io.github.plvzfqrit.lockgroove.core.lyrics

/**
 * Reads synced lyrics in the `.lrc` format:
 *
 *     [00:12.34] First line
 *     [00:15.00][01:30.50] A line sung twice
 *     [offset:+300]
 *
 * Lines without a time are ignored, except `[offset:ms]`: a positive offset makes the lyrics
 * come that much earlier. Word-by-word `<00:12.34>` marks inside a line are dropped.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val timedLine = Regex("""^\s*((?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+)(.*)$""")
    private val offsetLine = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(text: String): List<LyricLine> {
        val lines = text.lines()
        val offset = lines.firstNotNullOfOrNull { offsetLine.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val parsed = ArrayList<LyricLine>()
        for (raw in lines) {
            val match = timedLine.find(raw) ?: continue
            val words = wordTag.replace(match.groupValues[2], "").trim()
            for (tag in timeTag.findAll(match.groupValues[1])) {
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                // ".34" is hundredths, ".345" is thousandths: pad the fraction to three digits.
                val millis = tag.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val at = (minutes * 60 + seconds) * 1000 + millis - offset
                parsed += LyricLine(at.coerceAtLeast(0L), words)
            }
        }
        return parsed.sortedBy { it.timeMs }                          // stable: equal times keep their order
    }
}
