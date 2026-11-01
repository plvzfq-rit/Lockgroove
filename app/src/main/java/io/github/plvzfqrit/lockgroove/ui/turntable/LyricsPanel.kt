package io.github.plvzfqrit.lockgroove.ui.turntable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
 */
@Composable
fun LyricsPanel(
    state: LyricsState,
    index: Int,
    color: Color,
    onGet: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        val lyricRows = 3
        val readingRows = 2
        val lyricSize = 16
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
            return@Box
        }
        Column(Modifier.fillMaxWidth()) {
            when (state) {
                LyricsState.Idle -> Unit
                LyricsState.Looking -> Hint("Looking for lyrics…", color)
                LyricsState.None -> Notice(Icons.Filled.MusicOff, "No lyrics for this song", color, onRetry)
                LyricsState.NoFit -> Notice(Icons.Filled.CompareArrows, "Lyrics don't fit this recording", color, onRetry)
                LyricsState.Unreachable -> Notice(Icons.Filled.CloudOff, "Couldn't reach the lyrics service", color, onRetry)
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
 * An explanation of why there are no lyrics, with its own icon, and a Retry button.
 */
@Composable
private fun Notice(icon: ImageVector, message: String, color: Color, onRetry: () -> Unit) {
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
