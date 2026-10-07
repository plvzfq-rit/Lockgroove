package io.github.plvzfqrit.plvinyl.ui.drawing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.plvzfqrit.plvinyl.ui.common.Beat

/**
 * A slowly turning angle (30°/s), updated on [beat] instead of every screen refresh: a
 * turning record looks the same, and it costs a fraction of the battery. For the small
 * decorative records (welcome screen, stats).
 */
@Composable
fun rememberPacedSpin(beat: Beat, degPerSec: Float = 30f): () -> Float {
    val angle = remember { mutableFloatStateOf(0f) }
    val latestBeat by rememberUpdatedState(beat)
    LaunchedEffect(degPerSec) {
        var last = 0L
        while (true) {
            latestBeat.withFrame { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    angle.floatValue = (angle.floatValue + degPerSec * dt) % 360f
                }
                last = now
            }
        }
    }
    return { angle.floatValue }
}
