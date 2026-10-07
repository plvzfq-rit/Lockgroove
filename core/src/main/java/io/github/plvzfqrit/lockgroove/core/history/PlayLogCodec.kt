package io.github.plvzfqrit.lockgroove.core.history

import io.github.plvzfqrit.lockgroove.core.model.SongKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The play log file, as JSON lines:
 *   {"t":"play","id":…,"key":…,"title":…,"artist":…,"player":…,"at":…,"durationMs":…,"listenedMs":…}
 *   {"t":"listen","id":…,"listenedMs":…}      ← later update of the same play
 * Appending lines (instead of rewriting a file) keeps every write tiny.
 */
object PlayLogCodec {
    @Serializable
    private data class Line(
        val t: String = "play",
        val id: Long? = null,
        val key: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val player: String = "",
        val at: Long = 0L,
        val durationMs: Long = 0L,
        val listenedMs: Long = 0L,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** All plays in [lines], oldest first. Lines that can't be read are skipped. */
    fun parse(lines: Sequence<String>): List<PlayEntry> {
        val plays = LinkedHashMap<Long, PlayEntry>()
        for (raw in lines) {
            if (raw.isBlank()) continue
            val line = runCatching { json.decodeFromString<Line>(raw) }.getOrNull() ?: continue
            when (line.t) {
                "listen" -> {
                    val id = line.id ?: continue
                    plays[id]?.let { plays[id] = it.copy(listenedMs = line.listenedMs) }
                }
                else -> {
                    val key = line.key ?: continue
                    // The first version of the log had no ids: the time was the id.
                    val id = line.id ?: line.at
                    plays[id] = PlayEntry(id, SongKey.parse(key), line.title, line.artist, line.player,
                        line.at, line.durationMs, line.listenedMs)
                }
            }
        }
        return plays.values.sortedBy { it.at }
    }

    fun playLine(e: PlayEntry): String = json.encodeToString(
        Line.serializer(),
        Line("play", e.id, e.key.value, e.title, e.artist, e.player, e.at, e.durationMs, e.listenedMs),
    )

    fun listenLine(id: Long, listenedMs: Long): String =
        """{"t":"listen","id":$id,"listenedMs":$listenedMs}"""
}
