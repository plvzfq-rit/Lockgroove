package io.github.plvzfqrit.plvinyl.ui.turntable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** How fast a long title scrolls. */
private val Velocity = 30.dp
/** The soft fade at each edge. */
private val Fade = 16.dp
/** The gap before the text comes round again, as a share of the width. */
private const val GAP = 1f / 3f
/** The pause at the start of each pass, so the beginning can be read. */
private const val PAUSE_MS = 1500L

/** The marquee keeps its own pace, whatever the phone's animation speed is set to. */
private object OwnPace : MotionDurationScale {
    override val scaleFactor = 1f
}

/**
 * Single-line text. If it fits, it just sits there. If it's too long, it scrolls like
 * a marquee, with soft fades at the edges: round and round while [scrolling], pausing at
 * the start of each pass. When [scrolling] stops, it finishes its pass and rests at the
 * start (so a resting screen isn't redrawn).
 *
 * @param onScreen false while something covers it: it stops at once, back at the start
 * @param passes at most this many passes for this text; then it rests anyway
 */
@Composable
fun MarqueeText(
    text: String,
    color: Color,
    style: TextStyle,
    scrolling: Boolean,
    modifier: Modifier = Modifier,
    onScreen: Boolean = true,
    passes: Int = Int.MAX_VALUE,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val textWidth = remember(text, style) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        }
        if (textWidth <= constraints.maxWidth) {
            Text(text = text, color = color, style = style, maxLines = 1, softWrap = false)
            return@BoxWithConstraints
        }

        // One pass moves the text along by its length plus the gap. By then the copy
        // drawn behind it has arrived where the text started, so the loop is seamless.
        val travel = textWidth + constraints.maxWidth * GAP
        val offset = remember(text) { Animatable(0f) }
        val keepGoing by rememberUpdatedState(scrolling)
        val pxPerSecond = with(LocalDensity.current) { Velocity.toPx() }
        LaunchedEffect(text, travel, passes, onScreen) {
            offset.snapTo(0f)
            if (!onScreen) return@LaunchedEffect
            withContext(OwnPace) {
                var done = 0
                while (done < passes) {
                    snapshotFlow { keepGoing }.first { it }      // rest until it may move
                    delay(PAUSE_MS)
                    if (!keepGoing) continue
                    // Linear, and finished even if [scrolling] stops on the way.
                    offset.animateTo(travel, tween((travel / pxPerSecond * 1000).roundToInt(), easing = LinearEasing))
                    offset.snapTo(0f)
                    done++
                }
            }
        }

        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                // The edge fades are a mask, which needs a layer of its own.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val f = (Fade.toPx() / size.width).coerceIn(0f, 0.5f)
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            f to Color.Black,
                            1f - f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .clipToBounds()
                // The text, and its copy coming round behind it. Read in the draw phase:
                // scrolling only redraws.
                .drawWithContent {
                    val x = offset.value
                    translate(left = -x) { this@drawWithContent.drawContent() }
                    translate(left = travel - x) { this@drawWithContent.drawContent() }
                }
                // Laid out at its full length, from the left edge.
                .wrapContentWidth(Alignment.Start, unbounded = true),
        )
    }
}
