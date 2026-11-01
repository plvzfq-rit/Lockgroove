package io.github.plvzfqrit.lockgroove.lyrics

import android.util.Log
import io.github.plvzfqrit.lockgroove.core.lyrics.LrcParser
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * (so a lock), is sometimes too busy to answer (so a few patient retries), and it is never asked
 * twice for the same song (a small cache).
 *
 * It uses the search endpoint, not the exact one: the exact one was often "busy" when tried, and
 * it misses titles that differ by a look-alike character (Spotify's "-" against a "‐").
 */
class LrcLibSource : LyricsSource {
    override val serviceName = "lrclib.net"

    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>) = size > CACHE_SIZE
    }

    private sealed interface Outcome {
        data object Busy : Outcome
        data object Failed : Outcome
        data class Done(val lines: List<LyricLine>) : Outcome
    }

    override suspend fun find(title: String, artist: String?, durationMs: Long, fresh: Boolean): List<LyricLine>? {
        val key = "$title|$artist|${durationMs / 1000}"
        if (!fresh) synchronized(cache) { cache[key] }?.let { return it }
        return lock.withLock {
            if (!fresh) synchronized(cache) { cache[key] }?.let { return@withLock it }   // fetched while we waited
            var found: List<LyricLine>? = null
            for (attempt in 0..RETRIES) {
                val outcome = withContext(Dispatchers.IO) { fetch(title, artist, durationMs) }
                Log.d(TAG, "\"$title\" by $artist (${durationMs / 1000} s), attempt ${attempt + 1}: $outcome")
                when (outcome) {
                    is Outcome.Done -> { found = outcome.lines; break }
                    Outcome.Failed -> break
                    Outcome.Busy -> delay(RETRY_DELAY_MS * (attempt + 1))              // the service is busy: wait, then ask again
                }
            }
            if (found != null) synchronized(cache) { cache[key] = found }              // a failed lookup is not remembered
            found
        }
    }

    private fun fetch(title: String, artist: String?, durationMs: Long): Outcome = try {
        // Players join several artists with commas; the first one is the one the service knows.
        val lead = artist?.substringBefore(", ")?.takeIf { it.isNotBlank() }
        val seconds = durationMs / 1000
        search(listOf("track_name" to title) + listOfNotNull(lead?.let { "artist_name" to it }), seconds)
            .let { first ->
                // Nothing under that exact title and artist: try the service's looser free-text search.
                if (first is Outcome.Done && first.lines.isEmpty() && lead != null) {
                    search(listOf("q" to "$lead $title"), seconds)
                } else first
            }
    } catch (e: Exception) {
        Log.d(TAG, "lookup failed: $e")
        Outcome.Failed
    }

    /** Ask the search endpoint, and take the result closest in length that has synced lyrics. */
    private fun search(params: List<Pair<String, String>>, seconds: Long): Outcome {
        val query = params.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("https://lrclib.net/api/search?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "Lockgroove (https://github.com/plvzfq-rit/Lockgroove)")
            return when (val code = connection.responseCode) {
                200 -> {
                    val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    Outcome.Done(best(results, seconds)?.let(::synced) ?: emptyList())
                }
                429, 503 -> Outcome.Busy
                else -> {
                    Log.d(TAG, "unexpected answer: HTTP $code")
                    Outcome.Failed
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** The synced result whose length is closest to the song's (a live version or a cover is rejected). */
    private fun best(results: JSONArray, seconds: Long): JSONObject? {
        var best: JSONObject? = null
        var bestGap = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (item.isNull("syncedLyrics")) continue
            val gap = if (seconds > 0) abs(item.optDouble("duration", -1.0) - seconds) else 0.0
            if (seconds > 0 && gap > MAX_LENGTH_GAP_S) continue
            if (gap < bestGap) {
                best = item
                bestGap = gap
            }
        }
        return best
    }

    private fun synced(item: JSONObject): List<LyricLine> = LrcParser.parse(item.getString("syncedLyrics"))

    private companion object {
        const val TAG = "LrcLib"
        const val CACHE_SIZE = 40
        const val RETRIES = 3
        const val RETRY_DELAY_MS = 1_500L
        /** A result this many seconds longer or shorter than the song is a different recording. */
        const val MAX_LENGTH_GAP_S = 3.0
    }
}
