package io.github.plvzfqrit.lockgroove.ui.turntable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricScripts
import io.github.plvzfqrit.lockgroove.lyrics.Romanizer

/** What the lyrics area knows about the song being played (once the listener has allowed lyrics). */
sealed interface LyricsState {
    /** Still asking. */
    data object Looking : LyricsState

    /** No synced lyrics, or the lookup failed (offline, say). */
    data object None : LyricsState

    data class Found(val lines: List<LyricLine>) : LyricsState
}

/**
 * The lyrics area of the landscape layout. Off: a "Show lyrics" button (tapping it asks the
 * listener first). On: the line being sung (with its Latin-letter reading under it when it is in
 * another script), and a ✕ that turns it off again.
 * @param index the line being sung now (-1 before the first)
 */
@Composable
fun LyricsPanel(
    on: Boolean,
    state: LyricsState,
    index: Int,
    color: Color,
    onShow: () -> Unit,
    onHide: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Show Chinese lines in simplified characters. */
    simplified: Boolean = false,
) {
    Box(modifier.fillMaxWidth()) {
        if (!on) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClickLabel = "Show lyrics", onClick = onShow)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Show lyrics", color = color.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            return@Box
        }
        Column(Modifier.fillMaxWidth().padding(end = 36.dp)) {
            when (state) {
                LyricsState.Looking -> Hint("Looking for lyrics…", color)
                LyricsState.None -> {
                    Hint("No lyrics for this song", color)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClickLabel = "Look for the lyrics again", onClick = onRetry)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Retry", color = color.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                }
                // One lyric at a time, with its Latin-letter reading under it when it is in another script.
                is LyricsState.Found -> Crossfade(targetState = index, animationSpec = tween(250), label = "lyric") { i ->
                    val line = remember(i, simplified) {
                        state.lines.getOrNull(i)?.let {
                            if (simplified && LyricScripts.isChinese(it.text)) it.copy(text = Romanizer.simplify(it.text)) else it
                        }
                    }
                    val reading = remember(line) { line?.text?.let(Romanizer::romanize) }
                    Column {
                        Line(line, color, 16, FontWeight.SemiBold, maxLines = 3)
                        if (reading != null) Line(LyricLine(0, reading), color.copy(alpha = 0.6f), 12, FontWeight.Normal, maxLines = 2)
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .clickable(role = Role.Button, onClickLabel = "Hide lyrics", onClick = onHide),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Hide lyrics", tint = color.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
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
