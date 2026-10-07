package io.github.plvzfqrit.plvinyl.ui.turntable

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Round black button (the real deck's pairing button). Here it opens the settings.
 * At night its symbol is backlit: see [SettingsKnobLight].
 */
@Composable
fun SettingsKnob(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Which way its shadow falls (see RoomLight.shadow()). */
    shadow: () -> Offset = { Offset(0f, 1f) },
    /** Its presses (shared with its light, which flares on a press). */
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, tween(70), label = "knobPress")
    Box(
        modifier
            .semantics { contentDescription = "Settings" }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(6.dp)
            .size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.Black.copy(alpha = 0.3f), radius = size.minDimension / 2 * s, center = center + shadow() * 2.dp.toPx())
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF3A3A3A), Color(0xFF050505)),
                    center = center - Offset(size.width * 0.15f, size.height * 0.2f),
                    radius = size.minDimension * 0.6f,
                ),
                radius = size.minDimension / 2 * s,
            )
        }
        // Settings icon printed on the cap.
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(21.dp * s),
        )
    }
}

/**
 * The knob's backlight, like the front panel keys': at night its symbol glows a soft warm
 * white with a halo, and a press makes it flare. The knob sits on the deck, under the
 * room's darkness; this goes ABOVE the darkness, exactly over the knob (same position
 * modifier). It doesn't take touches: they reach the knob below.
 */
@Composable
fun SettingsKnobLight(
    interaction: InteractionSource,
    /** How dark the room is, 0..1. Read while drawing. */
    night: () -> Float,
    modifier: Modifier = Modifier,
) {
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(70), label = "knobFlare")
    val symbol = rememberVectorPainter(Icons.Default.Tune)
    Canvas(modifier.padding(6.dp).size(42.dp)) {
        val n = night()
        // The halo: faint behind the backlit symbol at night, a flare on a press.
        val halo = 0.14f * n + 0.18f * press
        if (halo > 0f) {
            val r = size.minDimension * 0.6f
            drawCircle(Brush.radialGradient(listOf(Backlight.copy(alpha = halo), Color.Transparent), center = center, radius = r), r, center)
        }
        // The backlit symbol (by day the printed one on the knob shows instead).
        if (n > 0f) {
            val px = 21.dp.toPx() * (1f - 0.05f * press)                  // pushed in with the knob
            translate(center.x - px / 2f, center.y - px / 2f) {
                with(symbol) {
                    draw(Size(px, px), alpha = n, colorFilter = ColorFilter.tint(lerp(Backlight, Color.White, 0.45f * press)))
                }
            }
        }
    }
}
