package io.github.plvzfqrit.lockgroove.core.lyrics

/** One line of synced lyrics: sung from [timeMs] on. An empty [text] is a pause (an instrumental break). */
data class LyricLine(val timeMs: Long, val text: String)
