package io.github.plvzfqrit.plvinyl.ui.turntable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.plvzfqrit.plvinyl.AndroidClock
import io.github.plvzfqrit.plvinyl.core.turntable.Deck
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.TurntableController
import io.github.plvzfqrit.plvinyl.core.turntable.UserIntent
import io.github.plvzfqrit.plvinyl.media.MediaSessionWatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the turntable controller (so it survives rotation), feeds it the player's
 * reports, and ticks it while the app is on screen.
 */
class TurntableViewModel : ViewModel() {
    private val controller = TurntableController(AndroidClock) { MediaSessionWatcher.send(it) }

    val ui = controller.ui
    val target = controller.target
    val art = MediaSessionWatcher.art

    private var ticker: Job? = null

    init {
        viewModelScope.launch { MediaSessionWatcher.snapshot.collect(controller::onSnapshot) }
    }

    fun onStart() {
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
    }

    fun onIntent(intent: UserIntent) = controller.onIntent(intent)
    fun onMoveStarted(move: Move, deck: Deck) = controller.onMoveStarted(move, deck)
    fun onMoveFinished(move: Move, deck: Deck) = controller.onMoveFinished(move, deck)

    private companion object {
        /** How often the controller looks at the time (grace periods, needle creep). */
        const val TICK_MS = 100L
    }
}
