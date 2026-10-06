package io.github.plvzfqrit.plvinyl.ui.turntable

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Round black button (the real deck's pairing button). Here it opens the settings.
 */
@Composable
fun SettingsKnob(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
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
            drawCircle(Color.Black.copy(alpha = 0.3f), radius = size.minDimension / 2 * s, center = center + Offset(0f, 2.dp.toPx()))
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
