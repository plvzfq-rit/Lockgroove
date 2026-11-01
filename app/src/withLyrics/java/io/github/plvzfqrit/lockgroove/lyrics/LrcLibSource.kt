package io.github.plvzfqrit.lockgroove.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LrcParser
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Synced lyrics from lrclib.net, a free community service. It asks for one request at a time
 * (so a lock), so this keeps a small cache and never asks twice for the same song.
 */
class LrcLibSource : LyricsSource {
    override val serviceName = "lrclib.net"

    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>) = size > CACHE_SIZE
    }

    override suspend fun find(title: String, artist: String?, durationMs: Long): List<LyricLine>? {
        val key = "$title|$artist|${durationMs / 1000}"
        synchronized(cache) { cache[key] }?.let { return it }
        return lock.withLock {
            synchronized(cache) { cache[key] }?.let { return@withLock it }       // fetched while we waited
            val found = withContext(Dispatchers.IO) { fetch(title, artist, durationMs) }
            if (found != null) synchronized(cache) { cache[key] = found }          // a failed lookup is not remembered
            found
        }
    }

    private fun fetch(title: String, artist: String?, durationMs: Long): List<LyricLine>? = try {
        // Players join several artists with commas; the first one is the one the service knows.
        val lead = artist?.substringBefore(", ")?.takeIf { it.isNotBlank() }
        val seconds = durationMs / 1000
        val exact = request("get", title, lead, seconds)
        when {
            exact == null -> null
            exact.first == 200 -> synced(JSONObject(exact.second))
            exact.first == 404 -> searchInstead(title, lead, seconds)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /** No exact match: take the closest-length result that has synced lyrics. */
    private fun searchInstead(title: String, artist: String?, seconds: Long): List<LyricLine>? {
        val reply = request("search", title, artist, 0) ?: return null
        if (reply.first != 200) return null
        val results = JSONArray(reply.second)
        var best: JSONObject? = null
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (item.isNull("syncedLyrics")) continue
            val gap = abs(item.optDouble("duration", -1.0) - seconds)
            if (seconds > 0 && gap > MAX_LENGTH_GAP_S) continue                        // a live version, a cover...
            if (best == null || gap < abs(best.optDouble("duration", -1.0) - seconds)) best = item
        }
        return best?.let(::synced) ?: emptyList()
    }

    private fun synced(item: JSONObject): List<LyricLine> =
        if (item.isNull("syncedLyrics")) emptyList() else LrcParser.parse(item.getString("syncedLyrics"))

    /** One request: its status code and body, or null if it can't be made. */
    private fun request(path: String, title: String, artist: String?, seconds: Long): Pair<Int, String>? {
        val query = buildString {
            append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            if (artist != null) append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            if (path == "get" && seconds > 0) append("&duration=").append(seconds)
        }
        val connection = URL("https://lrclib.net/api/$path?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "Lockgroove (https://github.com/plvzfq-rit/Lockgroove)")
            val code = connection.responseCode
            val body = if (code == 200) connection.inputStream.bufferedReader().use { it.readText() } else ""
            code to body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CACHE_SIZE = 40
        /** A result this many seconds longer or shorter than the song is a different recording. */
        const val MAX_LENGTH_GAP_S = 3.0
    }
}
