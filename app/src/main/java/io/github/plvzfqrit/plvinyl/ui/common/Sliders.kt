package io.github.plvzfqrit.plvinyl.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** The track starts and ends this far in from each side (room for the thumb). */
private val TrackInset = 12.dp

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
    val latest = rememberUpdatedState(onValueChange)
    Canvas(modifier.height(32.dp).slider(value, label, valueText = null, latest)) {
        val pad = TrackInset.toPx()
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

/**
 * A plain slider in [color], with a notch at each named stop along it.
 * @param value 0..1
 * @param notches where the stops are, 0..1
 * @param valueText what the value means, read out by TalkBack ("Slow", "1.5 s")
 */
@Composable
fun NotchedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    notches: List<Float>,
    color: Color,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
) {
    val latest = rememberUpdatedState(onValueChange)
    Canvas(modifier.height(32.dp).slider(value, label, valueText, latest)) {
        val pad = TrackInset.toPx()
        val y = size.height / 2f
        val w = size.width - 2 * pad
        drawLine(color.copy(alpha = 0.22f), Offset(pad, y), Offset(pad + w, y), 4.dp.toPx(), StrokeCap.Round)
        notches.forEach { drawCircle(color.copy(alpha = 0.55f), 3.dp.toPx(), Offset(pad + it * w, y)) }
        drawCircle(color, 9.dp.toPx(), Offset(pad + value.coerceIn(0f, 1f) * w, y))
    }
}

/**
 * Tap anywhere on the track, or drag sideways along it; TalkBack can set it too. Calls
 * [onValue] with 0..1. A drag up or down is left alone, so a list with sliders in it
 * still scrolls.
 */
private fun Modifier.slider(value: Float, label: String, valueText: String?, onValue: State<(Float) -> Unit>) =
    semantics {
        contentDescription = label
        valueText?.let { stateDescription = it }
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        setProgress { v -> onValue.value(v.coerceIn(0f, 1f)); true }
    }.pointerInput(Unit) {
        detectTapGestures { onValue.value(at(it.x)) }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { onValue.value(at(it.x)) }) { change, _ ->
            change.consume()
            onValue.value(at(change.position.x))
        }
    }

/** Where along the track [x] is, 0..1. */
private fun PointerInputScope.at(x: Float): Float {
    val pad = TrackInset.toPx()
    return ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
}
