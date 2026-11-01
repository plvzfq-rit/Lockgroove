package io.github.plvzfqrit.lockgroove.core.lyrics

import java.util.Locale

/** Writes lyric lines back out in the `.lrc` format, so they can be saved and read again by [LrcParser]. */
object LrcWriter {
    /** One `[mm:ss.xx]text` line each. Times are kept to hundredths of a second. */
    fun format(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
        val hundredths = line.timeMs / 10
        val minutes = hundredths / 6000
        val seconds = (hundredths / 100) % 60
        val fraction = hundredths % 100
        "[%02d:%02d.%02d]%s".format(Locale.ROOT, minutes, seconds, fraction, line.text.replace('\n', ' '))   // plain digits, whatever the phone's language
    }
}
