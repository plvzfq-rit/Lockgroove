package io.github.plvzfqrit.lockgroove.ui.common

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.plvzfqrit.lockgroove.core.settings.MotionPolicy
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The beat that everything moving on the turntable keeps: every [every]th frame of the
 * screen. The frames are picked by their own timestamps, so whoever waits for the beat
 * gets the SAME frames: the record and a scrolling title repaint together, evenly, instead
 * of taking turns on alternate frames (which would repaint the screen at its full rate).
 *
 * @param every 1 = every frame
 * @param frameNanos how long one frame of the screen lasts
 */
@Immutable
data class Beat(val every: Int, val frameNanos: Long) {

    /** Waits for the next frame on the beat and runs [onFrame] in it (with its time, ns). */
    suspend fun <R> withFrame(onFrame: (frameTimeNanos: Long) -> R): R {
        while (true) {
            val hit = withFrameNanos { t -> if (isOn(t)) Hit(onFrame(t)) else null }
            if (hit != null) return hit.value
        }
    }

    // Which frame this is (rounded, so timestamp jitter can't move it), then every [every]th.
    private fun isOn(frameTimeNanos: Long): Boolean =
        every <= 1 || ((frameTimeNanos + frameNanos / 2) / frameNanos) % every == 0L

    private class Hit<R>(val value: R)

    companion object {
        /** Every frame of a 60 Hz screen. */
        val EveryFrame = Beat(every = 1, frameNanos = 16_666_667L)

        /** [framesPerSecond] (or [MotionPolicy.EVERY_FRAME]) on a screen refreshing [screenHz] times a second. */
        fun of(framesPerSecond: Int, screenHz: Float): Beat {
            val hz = if (screenHz > 1f) screenHz else 60f
            val every = if (framesPerSecond <= 0) 1 else max(1, (hz / framesPerSecond).roundToInt())
            return Beat(every, (1_000_000_000.0 / hz).toLong())
        }
    }
}

/** The beat for [framesPerSecond] on this screen, following its refresh rate as it changes. */
@Composable
fun rememberBeat(framesPerSecond: Int): Beat {
    val hz = rememberRefreshRate()
    return remember(framesPerSecond, hz) { Beat.of(framesPerSecond, hz) }
}

/** The screen's refresh rate (Hz), updated when it switches (e.g. after asking for 60 Hz). */
@Composable
private fun rememberRefreshRate(): Float {
    val view = LocalView.current
    val context = LocalContext.current
    var hz by remember { mutableFloatStateOf(view.display?.refreshRate ?: 60f) }
    DisposableEffect(view, context) {
        val displays = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) hz = display.refreshRate
            }
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
        }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.display?.let { hz = it.refreshRate }
        onDispose { displays.unregisterDisplayListener(listener) }
    }
    return hz
}
