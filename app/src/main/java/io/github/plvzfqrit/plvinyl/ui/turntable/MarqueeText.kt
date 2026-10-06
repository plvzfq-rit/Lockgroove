package io.github.plvzfqrit.plvinyl.ui.turntable

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Single-line text. If it fits, it just sits there. If it's too long, it
 * scrolls like a marquee (after a short pause) with soft fades at the edges.
 */
@Composable
fun MarqueeText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val textWidth = remember(text, style) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        }
        val overflows = textWidth > constraints.maxWidth
        val fade = 16.dp

        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = if (!overflows) Modifier else Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val f = (fade.toPx() / size.width).coerceIn(0f, 0.5f)
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
                .basicMarquee(
                    // A few passes, then it rests (an endless marquee kept the whole
                    // screen redrawing at full speed).
                    iterations = 3,
                    initialDelayMillis = 1500,
                    velocity = 30.dp,
                )
        )
    }
}
