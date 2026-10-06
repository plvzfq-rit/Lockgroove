package io.github.plvzfqrit.plvinyl.ui.drawing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * A slowly turning angle (30°/s) updated at a modest rate instead of every screen
 * refresh: a turning record looks the same, and it costs a fraction of the battery.
 * For the small decorative records (welcome screen, stats).
 */
@Composable
fun rememberPacedSpin(degPerSec: Float = 30f, fps: Int = 25): () -> Float {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(degPerSec, fps) {
        val step = 1000L / fps
        while (true) {
            delay(step)
            angle.floatValue = (angle.floatValue + degPerSec * step / 1000f) % 360f
        }
    }
    return { angle.floatValue }
}
