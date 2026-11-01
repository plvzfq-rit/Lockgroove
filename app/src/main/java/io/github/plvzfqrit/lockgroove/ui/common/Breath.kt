package io.github.plvzfqrit.lockgroove.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * 0 → 1 → 0 on a slow loop while [active]; a constant 0, with no animation running at
 * all, otherwise. Read it inside graphicsLayer so the fade never recomposes anything.
 */
@Composable
fun rememberBreath(active: Boolean): State<Float> =
    if (active) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else remember { mutableStateOf(0f) }
