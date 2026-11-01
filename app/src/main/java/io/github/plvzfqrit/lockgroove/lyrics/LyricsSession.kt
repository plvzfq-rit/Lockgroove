package io.github.plvzfqrit.lockgroove.lyrics

import io.github.plvzfqrit.lockgroove.core.lyrics.LyricScripts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The lyrics of the song being played. It lives in the view model, so it outlives the screen:
 * turning the phone or leaving and coming back never asks again.
 *
 * IT NEVER ASKS THE INTERNET BY ITSELF. A lookup starts only from [request], which the listener
 * triggers (the "Get lyrics" button, or Retry). When a song starts it only looks at what was
 * already saved on the phone, so lyrics fetched once simply come back.
 */
class LyricsSession(
    private val scope: CoroutineScope,
    private val store: LyricsStore,
    songs: Flow<SongId?>,
    simplifiedChinese: Flow<Boolean>,
) {
    /** The song being played. [key] tells recordings apart by length (to the second). */
    data class SongId(val title: String, val artist: String?, val durationMs: Long) {
        val key = "$title|$artist|${durationMs / 1000}"
    }

    private val current = MutableStateFlow<SongId?>(null)
    private val results = MutableStateFlow<Map<String, LyricsResult>>(emptyMap())
    private val looking = MutableStateFlow<Set<String>>(emptySet())
    private val _saved = MutableStateFlow(0)

    /** How many songs' lyrics are saved on the phone. */
    val saved: StateFlow<Int> = _saved

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LyricsState> =
        combine(current, results, looking, simplifiedChinese) { song, found, busy, simplified ->
            Inputs(song, found, busy, simplified)
        }.mapLatest { (song, found, busy, simplified) ->
            when {
                song == null -> LyricsState.Idle
                song.key in busy -> LyricsState.Looking
                else -> when (val result = found[song.key]) {
                    null -> LyricsState.Idle
                    LyricsResult.NotFound -> LyricsState.None
                    LyricsResult.NoFit -> LyricsState.NoFit
                    LyricsResult.Unreachable -> LyricsState.Unreachable
                    // The readings need a dictionary: work them out in the background, once.
                    is LyricsResult.Found -> withContext(Dispatchers.Default) {
                        val shown = if (simplified) {
                            result.lines.map { if (LyricScripts.isChinese(it.text)) it.copy(text = Romanizer.simplify(it.text)) else it }
                        } else result.lines
                        LyricsState.Found(shown, shown.map { Romanizer.romanize(it.text) })
                    }
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, LyricsState.Idle)

    private data class Inputs(
        val song: SongId?, val found: Map<String, LyricsResult>, val busy: Set<String>, val simplified: Boolean,
    )

    init {
        if (LyricsFeature.available) {
            scope.launch(Dispatchers.IO) { _saved.value = store.count() }
            scope.launch {
                songs.collect { song ->
                    current.value = song
                    // A new song: only look at what is saved on the phone. No network.
                    if (song != null && song.key !in results.value) {
                        val saved = withContext(Dispatchers.IO) { store.read(song.key) }
                        if (saved != null) results.update { it + (song.key to LyricsResult.Found(saved)) }
                    }
                }
            }
        }
    }

    /** The listener asked for this song's lyrics (or pressed Retry, with [fresh] set). */
    fun request(fresh: Boolean = false) {
        val song = current.value ?: return
        val source = LyricsFeature.source() ?: return
        if (song.key in looking.value) return
        looking.update { it + song.key }
        scope.launch {                                                          // finishes even if the screen turns
            val result = source.find(song.title, song.artist, song.durationMs, fresh)
            if (result is LyricsResult.Found) {
                withContext(Dispatchers.IO) {
                    store.write(song.key, result.lines)
                    _saved.value = store.count()
                }
            }
            results.update { it + (song.key to result) }
            looking.update { it - song.key }
        }
    }

    /** Delete every saved lyric. */
    fun clearSaved() {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.clear()
                _saved.value = store.count()
            }
            results.update { all -> all.filterValues { it !is LyricsResult.Found } }
        }
    }
}
