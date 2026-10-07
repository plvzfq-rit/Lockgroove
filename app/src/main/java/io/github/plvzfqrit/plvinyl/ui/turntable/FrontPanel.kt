package io.github.plvzfqrit.plvinyl.ui.turntable

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import io.github.plvzfqrit.plvinyl.core.look.Paint
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylPalette
import io.github.plvzfqrit.plvinyl.ui.lighting.drawLampLight
import kotlinx.coroutines.delay
import kotlin.math.max

/*
 * The deck's front edge: a thin lip under the top plate, then the front face with
 * five keys. Symbols sit on the keys; a key that's "on" glows amber.
 *
 *   |◀◀   ▶■  ▲▼  ↻1   ▶▶|
 *
 * Each background gets its own front material, so the face reads as the edge of
 * the plinth rather than more of the top:
 *   Paper → ribbed aluminium · Charcoal → ribbed gunmetal ·
 *   Amber → ribbed bronze-brown · Album Blur → ribbed, album-tinted
 */

/** Height of the panel above the navigation bar (lip + face). */
val FRONT_PANEL_HEIGHT = 12.dp + 84.dp

@Composable
fun FrontPanel(
    style: BackdropStyle,
    /** Colors of the album on the platter (for the Album Blur front). */
    albumPalette: VinylPalette?,
    /** The panel's paint, or null for the background's own material. */
    paint: Color?,
    playing: Boolean,
    repeatOne: Boolean,
    /** The room's light. The face gets the same light as the deck around it; lit keys glow. */
    light: () -> RoomLight,
    /** Where the lamp's pool is centred, in this panel's coordinates (px). */
    lampCenter: Offset,
    /** The record's radius (px): the lamp's pool is sized from it. */
    lampRadius: Float,
    onStartStop: () -> Unit,
    onCue: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Album Blur: the front's two album colors glide to the new record's colors
    // instead of switching at once (in step with the background's own fade).
    val (targetA, targetB) = albumPair(albumPalette)
    val albumA by animateColorAsState(targetA, tween(900, easing = FastOutSlowInEasing), label = "panelA")
    val albumB by animateColorAsState(targetB, tween(900, easing = FastOutSlowInEasing), label = "panelB")
    val album = albumA to albumB
    val night = { light().night }

    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .drawWithCache {
                        val lip = paint?.let(::paintedLip) ?: lipBrush(style, album)
                        onDrawBehind {
                            drawRect(lip)
                            drawLine(Color.Black.copy(alpha = 0.25f), Offset(0f, size.height - 0.5f),
                                Offset(size.width, size.height - 0.5f), 1f)
                            drawLampLight(light(), lampCenter, lampRadius, glint = 0f)
                        }
                    }
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val texture = faceTexture(style, album, paint, size, 4.dp.toPx())
                        val faceLamp = lampCenter - Offset(0f, 12.dp.toPx())        // the face sits under the lip
                        onDrawBehind {
                            texture()
                            drawLampLight(light(), faceLamp, lampRadius, glint = 0f)
                        }
                    }
                    .navigationBarsPadding()
                    .height(84.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp + 19.dp),   // keys centred on the face
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hold |◀◀ / ▶▶| to run through songs, faster and faster.
            Key(KeyIcon.Previous, "Previous song", lit = false, night, onPrevious, onHoldRepeat = onPrevious)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Key(KeyIcon.StartStop, if (playing) "Stop" else "Start", lit = playing, night, onStartStop)
                Key(KeyIcon.Cue, "Cue: lift or lower the needle", lit = false, night, onCue)
                Key(KeyIcon.RepeatOne, "Repeat one", lit = repeatOne, night, onRepeat,
                    state = if (repeatOne) "On" else "Off")
            }
            Spacer(Modifier.weight(1f))
            Key(KeyIcon.Next, "Next song", lit = false, night, onNext, onHoldRepeat = onNext)
        }
    }
}

// ------------------------------------------------------------------ materials

private fun lipBrush(style: BackdropStyle, album: Pair<Color, Color>): Brush = when (style) {
    BackdropStyle.Paper -> Brush.verticalGradient(listOf(Color(0xFFF7F4EE), Color(0xFFE3DED5)))
    BackdropStyle.Charcoal -> Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF2C2C2C)))
    BackdropStyle.Amber -> Brush.verticalGradient(listOf(Color(0xFF5A3A1C), Color(0xFF432A14)))
    BackdropStyle.AlbumBlur -> {
        val (a, b) = album
        Brush.horizontalGradient(listOf(lerp(a, Color.White, 0.12f), lerp(b, Color.White, 0.12f)))
    }
    // A painted deck always comes with a panel paint; this is only a fallback.
    BackdropStyle.Custom -> Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF2C2C2C)))
}

/** The lip of a painted panel: the paint, catching a little light along its top. */
private fun paintedLip(paint: Color): Brush {
    val argb = paint.toArgb()
    return Brush.verticalGradient(listOf(Color(Paint.shade(argb, +0.08f)), Color(Paint.shade(argb, -0.04f))))
}

private fun albumPair(p: VinylPalette?): Pair<Color, Color> =
    if (p == null) Color(0xFF2C5F63) to Color(0xFF5A3050)
    else lerp(p.marbleA, Color.Black, 0.35f) to lerp(p.marbleB, Color.Black, 0.35f)

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t, 1f,
)

/**
 * The face's material, built once per size/style (drawWithCache): a base color
 * plus regular horizontal RIBS, like the corrugated metal front of hi-fi gear.
 * Each rib is [ribPx] tall (4 dp): a lit top edge, a rounded face, a dark groove
 * below. One repeating gradient draws them all, so it costs a single draw call.
 */
private fun faceTexture(
    style: BackdropStyle, album: Pair<Color, Color>, paint: Color?, size: Size, ribPx: Float,
): DrawScope.() -> Unit {
    // (base, rib highlight strength, rib shadow strength)
    val base: Brush
    val hi: Float
    val sh: Float
    if (paint != null) {
        // Painted metal: light paints catch the light like aluminium, dark ones sink
        // into their grooves like gunmetal.
        val argb = paint.toArgb()
        val l = Paint.toHsl(argb).l
        base = Brush.verticalGradient(
            0f to Color(Paint.shade(argb, +0.06f)), 0.55f to paint, 1f to Color(Paint.shade(argb, -0.18f)),
        )
        hi = 0.10f + 0.45f * l
        sh = 0.55f - 0.33f * l
    } else when (style) {
        BackdropStyle.Paper -> {            // ribbed aluminium
            base = Brush.verticalGradient(0f to Color(0xFFDCDEDF), 0.55f to Color(0xFFC3C6C8), 1f to Color(0xFFAEB1B3))
            hi = 0.55f; sh = 0.22f
        }
        BackdropStyle.Charcoal -> {         // ribbed gunmetal
            base = Brush.verticalGradient(0f to Color(0xFF34363A), 0.6f to Color(0xFF212225), 1f to Color(0xFF151618))
            hi = 0.10f; sh = 0.55f
        }
        BackdropStyle.Amber -> {            // ribbed bronze-brown
            base = Brush.verticalGradient(0f to Color(0xFF5A3A1E), 0.55f to Color(0xFF432914), 1f to Color(0xFF2E1C0D))
            hi = 0.10f; sh = 0.50f
        }
        BackdropStyle.AlbumBlur -> {        // ribbed, tinted by the album
            val (a, b) = album
            base = Brush.horizontalGradient(listOf(a, b, lerp(a, b, 0.5f)))
            hi = 0.12f; sh = 0.45f
        }
        BackdropStyle.Custom -> {           // fallback: a painted deck always brings its panel paint
            base = Brush.verticalGradient(0f to Color(0xFF34363A), 0.6f to Color(0xFF212225), 1f to Color(0xFF151618))
            hi = 0.10f; sh = 0.55f
        }
    }
    val ribs = Brush.verticalGradient(
        0.00f to Color.White.copy(alpha = hi),
        0.15f to Color.White.copy(alpha = hi * 0.45f),
        0.45f to Color.Transparent,
        0.60f to Color.Transparent,
        0.80f to Color.Black.copy(alpha = sh * 0.5f),
        0.92f to Color.Black.copy(alpha = sh),
        1.00f to Color.Black.copy(alpha = sh),
        startY = 0f, endY = ribPx, tileMode = TileMode.Repeated,
    )
    val frost = paint == null && style == BackdropStyle.AlbumBlur
    return {
        drawRect(base)
        if (frost) drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.50f))))
        drawRect(ribs)
    }
}

// ------------------------------------------------------------------ keys

enum class KeyIcon { Previous, Next, StartStop, Cue, RepeatOne }

private val Ink = Color(0xFFE9E3D8)
private val Glow = Color(0xFFFFB54A)
/** The warm white of a backlit symbol at night. */
private val Backlight = Color(0xFFFFF0D8)

/** A black key on the front face: sinks in when pressed; its symbol glows when on. */
@Composable
private fun Key(
    icon: KeyIcon,
    description: String,
    lit: Boolean,
    night: () -> Float,
    onClick: () -> Unit,
    /** If set, holding the key repeats this, speeding up. */
    onHoldRepeat: (() -> Unit)? = null,
    state: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(70), label = "keyPress")
    val glow by animateFloatAsState(if (lit) 1f else 0f, tween(300), label = "keyGlow")
    val view = LocalView.current
    var wasPressed by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }
    val latestRepeat by rememberUpdatedState(onHoldRepeat)
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        else if (wasPressed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        wasPressed = pressed
        // Hold-to-repeat: first repeat after 450 ms, then faster (down to 150 ms).
        val repeat = latestRepeat
        if (pressed && repeat != null) {
            repeated = false
            delay(450)
            var interval = 380L
            while (true) {
                repeated = true
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                repeat()
                delay(interval)
                interval = max(150L, (interval * 0.8f).toLong())
            }
        }
    }
    Box(
        Modifier
            .size(width = 54.dp, height = 46.dp)                   // generous touch area
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            }
            .clickable(
                interaction, indication = null, role = Role.Button,
                // A hold that already repeated shouldn't fire one extra on release.
                onClick = { if (repeated) repeated = false else onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 33.dp)) {
            val n = night()
            val travel = 3.dp.toPx() * press
            val h = size.height - 3.dp.toPx()
            val corner = CornerRadius(6.dp.toPx())
            // On: a ring of amber light around the key, faint by day, glowing at night.
            // (Drawn first: the cap covers its inner half, so it reads as light around the edge.)
            if (glow > 0f) {
                val ring = glow * (0.35f + 0.65f * n)
                val grow = 2.dp.toPx()
                drawRoundRect(
                    Glow.copy(alpha = 0.22f * ring),
                    Offset(-grow, travel - grow), Size(size.width + 2 * grow, h + 2 * grow),
                    CornerRadius(corner.x + grow), style = Stroke(5.dp.toPx()),
                )
                drawRoundRect(
                    Glow.copy(alpha = 0.75f * ring),
                    Offset(-grow / 2, travel - grow / 2), Size(size.width + grow, h + grow),
                    CornerRadius(corner.x + grow / 2), style = Stroke(1.5.dp.toPx()),
                )
            }
            // Depth under the key (shrinks as it's pushed in)
            drawRoundRect(Color.Black, Offset(0f, travel + 3.dp.toPx() * (1f - press)), Size(size.width, h), corner)
            drawRoundRect(
                Brush.verticalGradient(
                    0f to Color(0xFF3A3A3A), 0.55f to Color(0xFF141414), 1f to Color(0xFF060606),
                    startY = travel, endY = travel + h,
                ),
                Offset(0f, travel), Size(size.width, h), corner,
            )
            drawLine(
                Color.White.copy(alpha = 0.14f * (1f - press) * (1f - 0.5f * n)),
                Offset(corner.x, travel + 1.dp.toPx()), Offset(size.width - corner.x, travel + 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            val c = Offset(size.width / 2f, travel + h / 2f)
            // Backlight: at night every symbol glows a soft warm white, and a press makes
            // it flare for a moment.
            val backlit = (0.14f * n + 0.18f * press) * (1f - glow)
            if (backlit > 0f) {
                val r = 12.dp.toPx()
                drawCircle(
                    Brush.radialGradient(listOf(Backlight.copy(alpha = backlit), Color.Transparent), center = c, radius = r),
                    r, c,
                )
            }
            // Glow: a soft amber halo behind a lit symbol (stronger in the dark).
            if (glow > 0f) {
                val r = 13.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        listOf(Glow.copy(alpha = (0.30f + 0.25f * n) * glow), Color.Transparent),
                        center = c, radius = r,
                    ),
                    r, c,
                )
            }
            // The symbol: printed cream by day, backlit warm white at night, amber when on;
            // a press flares it toward white.
            val symbol = lerp(lerp(lerp(Ink, Backlight, n), Glow, glow), Color.White, 0.45f * press)
            drawKeyIcon(icon, symbol, c, 21.dp.toPx())
        }
    }
}

/** Draws a key symbol from a 24×24 design, centered at [center], [px] wide. */
private fun DrawScope.drawKeyIcon(icon: KeyIcon, color: Color, center: Offset, px: Float) {
    val s = px / 24f
    translate(center.x - px / 2f, center.y - px / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            when (icon) {
                KeyIcon.Previous -> {
                    drawRoundRect(color, Offset(4f, 5f), Size(2.2f, 14f), CornerRadius(1f))
                    drawPath(tri(20f, 5.5f, 20f, 18.5f, 8.5f, 12f), color)
                }
                KeyIcon.Next -> {
                    drawRoundRect(color, Offset(17.8f, 5f), Size(2.2f, 14f), CornerRadius(1f))
                    drawPath(tri(4f, 5.5f, 4f, 18.5f, 15.5f, 12f), color)
                }
                KeyIcon.StartStop -> {
                    drawPath(tri(3f, 6f, 3f, 18f, 11f, 12f), color)
                    drawRoundRect(color, Offset(13.5f, 7f), Size(8f, 10f), CornerRadius(1f))
                }
                KeyIcon.Cue -> {
                    drawPath(tri(12f, 3.5f, 18f, 10f, 6f, 10f), color)
                    drawPath(tri(12f, 20.5f, 18f, 14f, 6f, 14f), color)
                }
                KeyIcon.RepeatOne -> {
                    val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    // Rectangular loop, open at the top where the arrow is
                    val loop = Path().apply {
                        moveTo(14f, 7f); lineTo(18f, 7f)
                        quadraticTo(20.5f, 7f, 20.5f, 9.5f); lineTo(20.5f, 14.5f)
                        quadraticTo(20.5f, 17f, 18f, 17f); lineTo(6f, 17f)
                        quadraticTo(3.5f, 17f, 3.5f, 14.5f); lineTo(3.5f, 9.5f)
                        quadraticTo(3.5f, 7f, 6f, 7f); lineTo(8f, 7f)
                    }
                    drawPath(loop, color, style = stroke)
                    val arrow = Path().apply { moveTo(11.5f, 4.5f); lineTo(14f, 7f); lineTo(11.5f, 9.5f) }
                    drawPath(arrow, color, style = stroke)
                    // The "1"
                    val one = Path().apply { moveTo(11f, 11f); lineTo(12.4f, 10.2f); lineTo(12.4f, 15f) }
                    drawPath(one, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

private fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Path().apply {
    moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
}