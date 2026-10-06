package io.github.plvzfqrit.plvinyl.ui.turntable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.plvzfqrit.plvinyl.AndroidClock
import io.github.plvzfqrit.plvinyl.core.settings.Settings
import io.github.plvzfqrit.plvinyl.core.turntable.Deck
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.TurntableController
import io.github.plvzfqrit.plvinyl.core.turntable.UserIntent
import io.github.plvzfqrit.plvinyl.data.Look
import io.github.plvzfqrit.plvinyl.data.PlayLogStore
import io.github.plvzfqrit.plvinyl.data.Plays
import io.github.plvzfqrit.plvinyl.data.SettingsStore
import io.github.plvzfqrit.plvinyl.media.MediaSessionWatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val target = controller.target
    val settings = controller.settings
    val art = MediaSessionWatcher.art
    val look: StateFlow<Look> = store.look.stateIn(viewModelScope, SharingStarted.Eagerly, Look())

    private var ticker: Job? = null

    init {
        viewModelScope.launch { MediaSessionWatcher.snapshot.collect(controller::onSnapshot) }
        viewModelScope.launch {
            val saved = store.settings.first()
            controller.updateSettings { saved }
            // From here on, whatever changes (the sheet, ↻1, a learned crossfade) is saved.
            controller.settings.drop(1).collect { store.save(it) }
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
        ticker?.cancel()
        ticker = null
        controller.onStop()
        Plays.appOnScreen = false
        // Hand over to the background counter right away, with the player's current state.
        MediaSessionWatcher.refresh(getApplication())
    }

    fun onIntent(intent: UserIntent) = controller.onIntent(intent)
    fun onMoveStarted(move: Move, deck: Deck) = controller.onMoveStarted(move, deck)
    fun onMoveFinished(move: Move, deck: Deck) = controller.onMoveFinished(move, deck)
    fun updateSettings(change: (Settings) -> Settings) = controller.updateSettings(change)
    fun forgetCrossfade(player: String) = controller.forgetCrossfade(player)
    fun setLook(look: Look) {
        viewModelScope.launch { store.save(look) }
    }

    private companion object {
        /** How often the controller looks at the time (grace periods, needle creep). */
        const val TICK_MS = 100L
    }
}
