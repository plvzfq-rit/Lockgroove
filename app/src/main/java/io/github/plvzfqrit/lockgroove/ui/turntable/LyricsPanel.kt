package io.github.plvzfqrit.lockgroove.ui.turntable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import io.github.plvzfqrit.lockgroove.lyrics.LyricsState

/**
 * The lyrics area. Until the listener asks for this song's lyrics: a "Get lyrics" button. Then
 * the line being sung, with its Latin-letter reading under it when it is in another script.
 * @param index the line being sung now (-1 before the first)
 * @param compact the gap in portrait: wide but short, so fewer rows (it counts how many fit, and
 *        draws nothing when there is no room at all)
 */
@Composable
fun LyricsPanel(
    state: LyricsState,
    index: Int,
    color: Color,
    onGet: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (compact && maxHeight < MIN_COMPACT_HEIGHT) return@BoxWithConstraints   // no room to be useful
        val lyricRows = if (!compact) 3 else if (maxHeight >= ROOMY_COMPACT_HEIGHT) 2 else 1
        val readingRows = if (compact) 1 else 2
        val lyricSize = if (compact) 15 else 16
        if (state == LyricsState.Idle) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClickLabel = "Get the lyrics of this song", onClick = onGet)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Get lyrics", color = color.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxWidth()) {
            when (state) {
                LyricsState.Idle -> Unit
                LyricsState.Looking -> Hint("Looking for lyrics…", color)
                LyricsState.None -> Notice(Icons.Filled.MusicOff, "No lyrics for this song", color, onRetry, compact)
                LyricsState.NoFit -> Notice(Icons.Filled.CompareArrows, "Lyrics don't fit this recording", color, onRetry, compact)
                LyricsState.Unreachable -> Notice(Icons.Filled.CloudOff, "Couldn't reach the lyrics service", color, onRetry, compact)
                // One lyric at a time, with its Latin-letter reading under it when it is in another script.
                is LyricsState.Found -> Crossfade(targetState = index, animationSpec = tween(250), label = "lyric") { i ->
                    val line = state.lines.getOrNull(i)
                    val reading = state.readings.getOrNull(i)
                    Column {
                        Line(line, color, lyricSize, FontWeight.SemiBold, maxLines = lyricRows)
                        if (reading != null) Line(LyricLine(0, reading), color.copy(alpha = 0.6f), 12, FontWeight.Normal, maxLines = readingRows)
                    }
                }
            }
        }
    }
}

/**
 * An explanation of why there are no lyrics, with its own icon, and a Retry button. Compact:
 * the message and the button share one row (the stacked layout needs about 56 dp).
 */
@Composable
private fun Notice(icon: ImageVector, message: String, color: Color, onRetry: () -> Unit, compact: Boolean) {
    if (compact) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Text(
                message, color = color.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            RetryPill(color, onRetry)
        }
        return
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Text(message, color = color.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
    RetryPill(color, onRetry, Modifier.padding(top = 8.dp))
}

@Composable
private fun RetryPill(color: Color, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Look for the lyrics again", onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("Retry", color = color.copy(alpha = 0.75f), fontSize = 13.sp)
    }
}

@Composable
private fun Hint(text: String, color: Color) {
    Text(text, color = color.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Below this height the portrait gap draws nothing; from [ROOMY_COMPACT_HEIGHT] it can show two lyric rows. */
private val MIN_COMPACT_HEIGHT = 40.dp
private val ROOMY_COMPACT_HEIGHT = 78.dp

/** One line of lyrics. A pause in the singing (an empty line) shows as a note. */
@Composable
private fun Line(line: LyricLine?, color: Color, size: Int, weight: FontWeight, maxLines: Int) {
    Text(
        text = when {
            line == null -> " "                                 // keeps the three rows from jumping about
            line.text.isEmpty() -> "♪"
            else -> line.text
        },
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
