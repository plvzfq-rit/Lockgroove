package io.github.plvzfqrit.plvinyl.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.plvzfqrit.plvinyl.AndroidClock
import io.github.plvzfqrit.plvinyl.core.history.PlayCounter
import io.github.plvzfqrit.plvinyl.core.history.PlayedSong
import io.github.plvzfqrit.plvinyl.media.MediaSessionWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one play counter. While the turntable is on screen its controller feeds it
 * (needle down + sounding). While the app is closed, the listener service does, if
 * "Count plays when the app is closed" is on.
 */
object Plays {
    @Volatile private var counter: PlayCounter? = null

    /** True while the turntable is on screen (then it does the counting). */
    @Volatile var appOnScreen = false

    @Volatile private var countInBackground = false
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { counter?.tick(); schedule() }

    fun counter(context: Context): PlayCounter =
        counter ?: synchronized(this) {
            counter ?: PlayCounter(AndroidClock, PlayLogStore.get(context)).also { counter = it }
        }

    /** Start listening for background plays. Call once, when the process starts. */
    fun install(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            SettingsStore(app).settings.collect { countInBackground = it.countInBackground }
        }
        MediaSessionWatcher.onSnapshot = { ctx, s ->
            if (!appOnScreen) {
                val song = s?.let { PlayedSong(it.key, it.title, it.artist, it.player, it.durationMs) }
                counter(ctx).update(song, listening = countInBackground && s?.sounding == true)
                schedule()
            }
        }
    }

    /** Look again when the current listening would count (no ticking in the background). */
    private fun schedule() {
        handler.removeCallbacks(recheck)
        if (appOnScreen) return
        counter?.msUntilCount()?.let { handler.postDelayed(recheck, it + 100L) }
    }
}
