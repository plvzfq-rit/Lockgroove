package io.github.plvzfqrit.lockgroove.ui.turntable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.plvzfqrit.lockgroove.AndroidClock
import io.github.plvzfqrit.lockgroove.core.settings.Settings
import io.github.plvzfqrit.lockgroove.core.turntable.Deck
import io.github.plvzfqrit.lockgroove.core.turntable.Move
import io.github.plvzfqrit.lockgroove.core.turntable.TurntableController
import io.github.plvzfqrit.lockgroove.core.turntable.UserIntent
import io.github.plvzfqrit.lockgroove.ui.drawing.Look
import io.github.plvzfqrit.lockgroove.data.PlayLogStore
import io.github.plvzfqrit.lockgroove.data.Plays
import io.github.plvzfqrit.lockgroove.data.SettingsStore
import io.github.plvzfqrit.lockgroove.media.MediaSessionWatcher
import io.github.plvzfqrit.lockgroove.lyrics.JapaneseReaders
import io.github.plvzfqrit.lockgroove.lyrics.LyricsSession
import io.github.plvzfqrit.lockgroove.lyrics.LyricsState
import io.github.plvzfqrit.lockgroove.lyrics.LyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the turntable controller (so it survives rotation), feeds it the player's
 * reports, ticks it while the app is on screen, and saves the settings.
 */
class TurntableViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = TurntableController(AndroidClock, MediaSessionWatcher::send, Plays.counter(app))
    private val store = SettingsStore(app)

    /** The play log (stats and record wear). */
    val history = PlayLogStore.get(app)

    val ui = controller.ui
    val held = controller.held
    val target = controller.target
    val settings = controller.settings
    val art = MediaSessionWatcher.art
    val look: StateFlow<Look> = store.look.stateIn(viewModelScope, SharingStarted.Eagerly, Look())

    // Lyrics (the "withLyrics" build only). They live here, not in the screen, so turning the phone
    // never asks again; and nothing is fetched until the listener taps "Get lyrics".
    private val lyrics = LyricsSession(
        viewModelScope,
        LyricsStore(app),
        controller.ui
            .map { if (it.player == null) null else LyricsSession.SongId(it.title, it.artist, it.durationMs) }
            .distinctUntilChanged(),
        controller.settings.map { it.simplifiedChinese }.distinctUntilChanged(),
    )
    val lyricsState: StateFlow<LyricsState> = lyrics.state
    /** How many songs' lyrics are saved on the phone. */
    val savedLyrics: StateFlow<Int> = lyrics.saved
    fun requestLyrics(fresh: Boolean = false) = lyrics.request(fresh)
    fun clearSavedLyrics() = lyrics.clearSaved()

    private var ticker: Job? = null

    init {
        viewModelScope.launch { MediaSessionWatcher.snapshot.collect(controller::onSnapshot) }
        viewModelScope.launch {
            val saved = store.settings.first()
            controller.updateSettings { saved }
            // From here on, whatever changes (the sheet, ↻1, a learned crossfade) is saved.
            controller.settings.drop(1).collect { store.save(it) }
        }
        // Lookups switched off: let go of the (big) Japanese dictionary.
        viewModelScope.launch {
            controller.settings.map { it.lyrics }.distinctUntilChanged().collect { allowed ->
                if (!allowed) withContext(Dispatchers.Default) { JapaneseReaders.reader()?.release() }
            }
        }
    }

    fun onStart() {
        Plays.appOnScreen = true
        controller.onStart()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                controller.tick()
            }
        }
    }

    fun onStop() {
        viewModelScope.launch(Dispatchers.Default) { JapaneseReaders.reader()?.release() }   // not needed off screen
        ticker?.cancel()
        ticker = null
        controller.onStop()
        Plays.appOnScreen = false
        // Hand over to the background counter right away, with the player's current state.
        MediaSessionWatcher.refresh(getApplication())
    }

    fun onIntent(intent: UserIntent) = controller.onIntent(intent)

    /** Where the song is now (ms): for the lyrics, which poll it themselves (a flow would redraw everything). */
    fun positionMs(): Long? = controller.positionMs()
    fun onMoveStarted(move: Move, deck: Deck) = controller.onMoveStarted(move, deck)
    fun onMoveFinished(move: Move, deck: Deck) = controller.onMoveFinished(move, deck)
    fun updateSettings(change: (Settings) -> Settings) = controller.updateSettings(change)
    fun forgetCrossfade(player: String) = controller.forgetCrossfade(player)
    fun previewDay() = controller.previewDay()
    fun stopPreviewDay() = controller.stopPreviewDay()
    fun setLook(look: Look) {
        viewModelScope.launch { store.save(look) }
    }

    private companion object {
        /** How often the controller looks at the time (grace periods, needle creep). */
        const val TICK_MS = 100L
    }
}
