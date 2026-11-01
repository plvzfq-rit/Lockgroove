package io.github.plvzfqrit.lockgroove.lyrics

import android.util.Log
import io.github.plvzfqrit.lockgroove.core.lyrics.LrcParser
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricMatch
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
    private val cache = object : LinkedHashMap<String, LyricsResult>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>) = size > CACHE_SIZE
    }

    private sealed interface Outcome {
        data object Busy : Outcome
        data object Failed : Outcome
        data class Done(val result: LyricsResult) : Outcome
    }

    override suspend fun find(title: String, artist: String?, durationMs: Long, fresh: Boolean): LyricsResult {
        val key = "$title|$artist|${durationMs / 1000}"
        if (!fresh) synchronized(cache) { cache[key] }?.let { return it }
        return lock.withLock {
            if (!fresh) synchronized(cache) { cache[key] }?.let { return@withLock it }   // fetched while we waited
            var found: LyricsResult? = null
            for (attempt in 0..RETRIES) {
                val outcome = withContext(Dispatchers.IO) { fetch(title, artist, durationMs) }
                // Only a summary: never write the lyrics themselves into the system log.
                val summary = when (outcome) {
                    is Outcome.Done -> when (val r = outcome.result) {
                        is LyricsResult.Found -> "found ${r.lines.size} lines"
                        else -> r.toString()
                    }
                    Outcome.Busy -> "the service is busy"
                    Outcome.Failed -> "failed"
                }
                Log.d(TAG, "\"$title\" by $artist (${durationMs / 1000} s), attempt ${attempt + 1}: $summary")
                when (outcome) {
                    is Outcome.Done -> { found = outcome.result; break }
                    Outcome.Failed -> break
                    Outcome.Busy -> delay(RETRY_DELAY_MS * (attempt + 1))              // the service is busy: wait, then ask again
                }
            }
            if (found != null) synchronized(cache) { cache[key] = found }              // a failed lookup is not remembered
            found ?: LyricsResult.Unreachable
        }
    }

    private fun fetch(title: String, artist: String?, durationMs: Long): Outcome = try {
        // Players join several artists with commas; the first one is the one the service knows.
        val lead = artist?.substringBefore(", ")?.takeIf { it.isNotBlank() }
        val seconds = durationMs / 1000
        search(listOf("track_name" to title) + listOfNotNull(lead?.let { "artist_name" to it }), seconds)
            .let { first ->
                // Nothing usable under that exact title and artist: try the service's looser free-text search,
                // and keep whichever answer is better.
                if (first is Outcome.Done && first.result !is LyricsResult.Found && lead != null) {
                    val second = search(listOf("q" to "$lead $title"), seconds)
                    if (second is Outcome.Done && rank(second.result) > rank(first.result)) second else first
                } else first
            }
    } catch (e: Exception) {
        Log.d(TAG, "lookup failed: $e")
        Outcome.Failed
    }

    private fun rank(result: LyricsResult) = when (result) {
        is LyricsResult.Found -> 3
        LyricsResult.NoFit -> 2
        LyricsResult.NotFound -> 1
        LyricsResult.Unreachable -> 0
    }

    /** Ask the search endpoint, and judge what comes back. */
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
                    Outcome.Done(judge(results, seconds))
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

    /**
     * What the results amount to: nothing with synced lyrics at all (NotFound), some that
     * don't fit this recording (NoFit, see [LyricMatch]), or the lyrics that do (Found).
     */
    private fun judge(results: JSONArray, seconds: Long): LyricsResult {
        val candidates = (0 until results.length()).mapNotNull { i ->
            val item = results.getJSONObject(i)
            if (item.isNull("syncedLyrics")) null
            else LyricMatch.Candidate(item.optDouble("duration", -1.0), LrcParser.parse(item.getString("syncedLyrics")))
        }
        if (candidates.isEmpty()) return LyricsResult.NotFound
        return LyricMatch.pick(candidates, seconds)?.let { LyricsResult.Found(candidates[it].lines) } ?: LyricsResult.NoFit
    }

    private companion object {
        const val TAG = "LrcLib"
        const val CACHE_SIZE = 40
        const val RETRIES = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
