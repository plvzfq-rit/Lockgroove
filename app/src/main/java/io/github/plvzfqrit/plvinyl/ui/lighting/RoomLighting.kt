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
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
fun rememberRoomLight(
    target: RoomLight,
    /** Follow [target] exactly, no fades (a sped-up day already moves smoothly). */
    instant: Boolean = false,
): () -> RoomLight {
    val golden = remember { Animatable(target.golden) }
    val night = remember { Animatable(target.night) }
    val late = remember { Animatable(target.late) }
    val dawn = remember { Animatable(target.dawn) }
    val height = remember { Animatable(target.sunHeight) }
    // The light's direction is kept unwrapped, so it always turns the short way round.
    val from = remember { Animatable(target.sunFrom) }
    LaunchedEffect(target) {
        val toFrom = from.value + turn(from.value, target.sunFrom)
        val fade = abs(target.night - night.value) > 0.05f ||
                abs(target.golden - golden.value) > 0.05f || abs(target.late - late.value) > 0.05f ||
                abs(target.dawn - dawn.value) > 0.05f || abs(target.sunHeight - height.value) > 0.1f ||
                abs(toFrom - from.value) > 10f
        if (fade && !instant) {
            launch { golden.animateTo(target.golden, tween(1500)) }
            launch { late.animateTo(target.late, tween(1500)) }
            launch { dawn.animateTo(target.dawn, tween(1500)) }
            launch { height.animateTo(target.sunHeight, tween(1500)) }
            launch { from.animateTo(toFrom, tween(1500)) }
            night.animateTo(target.night, tween(1500))
        } else {
            golden.snapTo(target.golden); night.snapTo(target.night); late.snapTo(target.late)
            dawn.snapTo(target.dawn); height.snapTo(target.sunHeight); from.snapTo(toFrom)
        }
    }
    return remember { { RoomLight(golden.value, night.value, late.value, dawn.value, from.value, height.value) } }
}

/** The desk lamp hangs up-right of the record (where its glint is), and high. */
private const val LAMP_FROM = -60f
private const val LAMP_SHADOW = 0.5f

/** The shortest turn from [a] to [b], degrees (-180..180). */
private fun turn(a: Float, b: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** Where the light on the deck comes from (degrees, screen): the sun by day, the lamp at night. */
fun RoomLight.lightFrom(): Float = sunFrom + turn(sunFrom, LAMP_FROM) * night

/**
 * Which way shadows fall and how long they are, as one vector: away from the light, and
 * 1 = the app's usual daylight shadow. Short at noon, long as the sun gets low, short
 * again under the night lamp.
 */
fun RoomLight.shadow(): Offset {
    val a = Math.toRadians(lightFrom().toDouble())
    val day = 0.4f + 1.5f * (1f - sunHeight)
    val length = day + (LAMP_SHADOW - day) * night
    return Offset((-cos(a) * length).toFloat(), (-sin(a) * length).toFloat())
}

/** Darkens / warms the background (the room). Draw it right above the backdrop. */
@Composable
fun RoomShade(light: () -> RoomLight, modifier: Modifier) {
    Canvas(modifier) {
        val l = light()
        if (l.golden > 0f) drawRect(Color(0xFFFF9E40).copy(alpha = 0.14f * l.golden))
        if (l.dawn > 0f) drawRect(Color(0xFFFFC2A0).copy(alpha = 0.10f * l.dawn))
        val dark = 0.55f * l.night + 0.25f * l.late
        if (dark > 0f) drawRect(Color(0xFF0A0704).copy(alpha = dark))
    }
}

/**
 * The room's light over the deck: golden-hour warmth from the left, and at night a
 * desk lamp above the record (a warm pool on it, darkness around it, a glint on the
 * vinyl). It's fixed in the room: when the deck slides away for the stats, it slides
 * out from under the light, and the room stays lit the same.
 * @param lampCenter where the pool is centred (the record's center), in px
 * @param recordRadius record radius in px
 * @param glint how much of the lamp's reflection on the vinyl shows (0 once the record is gone)
 */
@Composable
fun LampLight(
    light: () -> RoomLight,
    lampCenter: Offset,
    recordRadius: Float,
    modifier: Modifier,
    glint: () -> Float = { 1f },
) {
    Canvas(modifier) { drawLampLight(light(), lampCenter, recordRadius, glint()) }
}

/**
 * Draws the lamp / golden-hour light into this scope, for things that apply the room's
 * light themselves (the front panel). [lampCenter] is in this scope's coordinates.
 */
fun DrawScope.drawLampLight(l: RoomLight, lampCenter: Offset, recordRadius: Float, glint: Float = 1f) {
    // Golden hour: warm on the sun's side, falling into a brown dusk on the far side.
    // (At sunset the sun is on the left: exactly the old left-to-right gradient.)
    if (l.golden > 0f) {
        drawSideLight(
            l.sunFrom,
            0f to Color(0xFFFFAA46).copy(alpha = 0.30f * l.golden),
            0.45f to Color(0xFFFF963C).copy(alpha = 0.12f * l.golden),
            1f to Color(0xFF5A280A).copy(alpha = 0.28f * l.golden),
        )
    }
    // Dawn: the same, softer and cooler: peach on the sun's side, a little blue dusk opposite.
    if (l.dawn > 0f) {
        drawSideLight(
            l.sunFrom,
            0f to Color(0xFFFFC9A0).copy(alpha = 0.20f * l.dawn),
            0.45f to Color(0xFFFFB98A).copy(alpha = 0.08f * l.dawn),
            1f to Color(0xFF2A3346).copy(alpha = 0.16f * l.dawn),
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
        if (glint > 0f) {
            val at = lampCenter + Offset(recordRadius * 0.32f, -recordRadius * 0.55f)
            val gr = recordRadius * 0.18f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD296).copy(alpha = 0.32f * n * (1f - 0.4f * l.late) * glint), Color.Transparent),
                    center = at, radius = gr,
                ),
                gr, at,
            )
        }
    }
}

/**
 * A gradient across the whole area, starting on the side the light comes [from] (degrees,
 * screen: 0 = right, -90 = top, ±180 = left) and ending on the opposite side.
 */
private fun DrawScope.drawSideLight(from: Float, vararg stops: Pair<Float, Color>) {
    val a = Math.toRadians(from.toDouble())
    val dx = cos(a).toFloat()
    val dy = sin(a).toFloat()
    // How far the area reaches along that direction from its center.
    val reach = (abs(dx) * size.width + abs(dy) * size.height) / 2f
    val toLight = Offset(dx * reach, dy * reach)
    drawRect(Brush.linearGradient(*stops, start = center + toLight, end = center - toLight))
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