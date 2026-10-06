package io.github.plvzfqrit.plvinyl.ui.lighting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Drawing the room's light (what the light IS comes from the core's roomLightAt):
 *  - RoomShade: darkens the BACKGROUND (the room). Stays put when the deck slides
 *    away for the stats.
 *  - LampLight: drawn over the deck (platter, record, arm), below the title and
 *    keys, with the lamp's pool of light on the record.
 * Night also saves battery on OLED screens (dark pixels use less power).
 */

/**
 * [target], with big changes (switching the mode in settings) faded over a second and
 * a half. The clock's own ramps are half an hour long, so those steps just apply.
 * Read the result inside draw lambdas, so a change redraws without recomposing.
 */
@Composable
fun rememberRoomLight(target: RoomLight): () -> RoomLight {
    val golden = remember { Animatable(target.golden) }
    val night = remember { Animatable(target.night) }
    val late = remember { Animatable(target.late) }
    LaunchedEffect(target) {
        val fade = abs(target.night - night.value) > 0.05f ||
                abs(target.golden - golden.value) > 0.05f || abs(target.late - late.value) > 0.05f
        if (fade) {
            launch { golden.animateTo(target.golden, tween(1500)) }
            launch { late.animateTo(target.late, tween(1500)) }
            night.animateTo(target.night, tween(1500))
        } else {
            golden.snapTo(target.golden); night.snapTo(target.night); late.snapTo(target.late)
        }
    }
    return remember { { RoomLight(golden.value, night.value, late.value) } }
}

/** Darkens / warms the background (the room). Draw it right above the backdrop. */
@Composable
fun RoomShade(light: () -> RoomLight, modifier: Modifier) {
    Canvas(modifier) {
        val l = light()
        if (l.golden > 0f) drawRect(Color(0xFFFF9E40).copy(alpha = 0.14f * l.golden))
        val dark = 0.55f * l.night + 0.25f * l.late
        if (dark > 0f) drawRect(Color(0xFF0A0704).copy(alpha = dark))
    }
}

/**
 * Light on the deck: golden-hour warmth from the left, and at night a desk lamp
 * above the record (a warm pool on it, darkness around it, a glint on the vinyl).
 * @param lampCenter where the pool is centred (the record's center), in px
 * @param recordRadius record radius in px
 */
@Composable
fun LampLight(light: () -> RoomLight, lampCenter: Offset, recordRadius: Float, modifier: Modifier) {
    Canvas(modifier) {
        val l = light()
        if (l.golden > 0f) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color(0xFFFFAA46).copy(alpha = 0.30f * l.golden),
                    0.45f to Color(0xFFFF963C).copy(alpha = 0.12f * l.golden),
                    1f to Color(0xFF5A280A).copy(alpha = 0.28f * l.golden),
                )
            )
        }
        if (l.night > 0f) {
            val n = l.night
            val pool = recordRadius * (1.25f - 0.45f * l.late)
            drawRect(
                Brush.radialGradient(
                    0f to Color(0xFFFFBE6E).copy(alpha = 0.10f * n),
                    0.55f to Color(0xFF1E1206).copy(alpha = 0.30f * n + 0.20f * l.late),
                    1f to Color(0xFF0A0704).copy(alpha = 0.75f * n + 0.15f * l.late),
                    center = lampCenter, radius = pool,
                )
            )
            // The lamp's reflection on the vinyl
            val glint = lampCenter + Offset(recordRadius * 0.32f, -recordRadius * 0.55f)
            val gr = recordRadius * 0.18f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD296).copy(alpha = 0.32f * n * (1f - 0.4f * l.late)), Color.Transparent),
                    center = glint, radius = gr,
                ),
                gr, glint,
            )
        }
    }
}

/** Title color: the background's text color by day, warm cream under the lamp. */
fun litTextColor(day: Color, light: RoomLight): Color {
    val night = Color(0xFFF0E2CC)
    val t = light.night
    return Color(
        day.red + (night.red - day.red) * t, day.green + (night.green - day.green) * t,
        day.blue + (night.blue - day.blue) * t, day.alpha,
    )
}