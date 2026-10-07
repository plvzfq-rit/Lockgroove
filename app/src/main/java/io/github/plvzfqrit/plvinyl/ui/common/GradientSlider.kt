package io.github.plvzfqrit.plvinyl.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/**
 * A slider whose track shows what you'd get along it. Tap anywhere or drag the thumb.
 * @param value 0..1
 */
@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    stops: List<Color>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(onValueChange)
    Canvas(
        modifier
            .height(32.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> latest(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                val pad = 12.dp.toPx()
                fun at(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    latest(at(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        latest(at(change.position.x))
                    }
                }
            },
    ) {
        val pad = 12.dp.toPx()
        val trackH = 10.dp.toPx()
        val y = size.height / 2f
        drawRoundRect(
            Brush.horizontalGradient(stops, startX = pad, endX = size.width - pad),
            topLeft = Offset(pad, y - trackH / 2f),
            size = Size(size.width - 2 * pad, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val x = pad + value.coerceIn(0f, 1f) * (size.width - 2 * pad)
        drawCircle(Color.Black.copy(alpha = 0.35f), 10.dp.toPx(), Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White, 10.dp.toPx(), Offset(x, y))
        drawCircle(Color(0xFF1A1A19), 10.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
    }
}
