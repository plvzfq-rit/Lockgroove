package io.github.plvzfqrit.plvinyl.ui.turntable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle

/* ------------------------------------------------------------------------
 * Dust cover: a smoked acrylic lid hinged at the back (top of the screen).
 * Seen from above, closing = the lid swinging down toward you, so it grows
 * from the top edge until it covers the whole top plate.
 * ---------------------------------------------------------------------- */

@Composable
fun DustCover(
    closed: Boolean,
    style: BackdropStyle,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 0 = daylight, 1 = night: the hinges and the lid's bright edges fall into shadow. */
    night: () -> Float = { 0f },
) {
    val amount by animateFloatAsState(
        targetValue = if (closed) 1f else 0f,
        animationSpec = tween(if (closed) 1400 else 700, easing = FastOutSlowInEasing),
        label = "lid",
    )
    val light = style.isLight
    val tint = if (light) Color(0xFF3A3632) else Color(0xFF0A0A0A)
    Box(
        modifier
            .fillMaxSize()
            .then(
                // While (mostly) closed, a tap anywhere on it lifts the lid.
                if (closed) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClickLabel = "Open the dust cover", onClick = onOpen,
                ) else Modifier
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = 1f - 0.7f * night()
            // Hinges at the back edge (always visible, like on the real deck)
            val hingeW = 34.dp.toPx(); val hingeH = 8.dp.toPx()
            for (fx in floatArrayOf(0.2f, 0.8f)) {
                val hinge = if (light) Color(0xFFB9B2A7) else Color(0xFF4A4A4A)
                drawRoundRect(
                    Color(hinge.red * dim, hinge.green * dim, hinge.blue * dim),
                    topLeft = Offset(size.width * fx - hingeW / 2, 0f), size = Size(hingeW, hingeH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            if (amount <= 0.001f) return@Canvas
            val h = size.height * amount
            // Shadow the lid's front edge casts on the deck
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.22f * amount), Color.Transparent),
                    startY = h, endY = h + 18.dp.toPx(),
                ),
                topLeft = Offset(0f, h), size = Size(size.width, 18.dp.toPx()),
            )
            // Smoked acrylic
            drawRect(tint.copy(alpha = (if (light) 0.18f else 0.30f) * (0.4f + 0.6f * amount)), size = Size(size.width, h))
            // Soft reflection streak across the acrylic
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.42f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.10f * amount * dim),
                    0.58f to Color.Transparent, 1f to Color.Transparent,
                    start = Offset(0f, 0f), end = Offset(size.width, h),
                ),
                size = Size(size.width, h),
            )
            // Front edge of the lid catching the light
            drawLine(
                Color.White.copy(alpha = 0.35f * amount * dim), Offset(0f, h - 1.dp.toPx()), Offset(size.width, h - 1.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}
