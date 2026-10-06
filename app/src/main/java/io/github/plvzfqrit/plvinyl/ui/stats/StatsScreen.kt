package io.github.plvzfqrit.plvinyl.ui.stats

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.stats.StatsCalculator
import io.github.plvzfqrit.plvinyl.core.stats.StatsPeriod
import io.github.plvzfqrit.plvinyl.core.stats.TopRecord
import io.github.plvzfqrit.plvinyl.data.PlayLogStore
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.DeckColors
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.rememberPacedSpin
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * "Liner notes": your listening, from the play log on this phone.
 * Opened by swiping left on the turntable (away from the record); swipe right
 * or press Back to return.
 */

/**
 * Colors for the stats, matched to the turntable's background (the stats sit on
 * the SAME backdrop). Chart colors were checked for contrast and color-blind
 * separation against each background.
 */
@Immutable
data class StatsColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val card: Color,
    val line: Color,
    val track: Color,          // empty part of a bar
    val accent: Color,         // single-series color (bars)
    val series1: Color,        // player split: 1st app
    val series2: Color,        // player split: 2nd app
    val other: Color,          // player split: everything else
    val chipOnText: Color,     // text on the selected chip
)

fun DeckColors.statsColors(): StatsColors {
    val fg = contentColor
    return when (style) {
        BackdropStyle.Paper -> StatsColors(
            textPrimary = fg, textSecondary = fg.copy(alpha = 0.72f), textMuted = fg.copy(alpha = 0.5f),
            card = Color.Black.copy(alpha = 0.05f), line = Color.Black.copy(alpha = 0.12f),
            track = Color.Black.copy(alpha = 0.08f),
            accent = Color(0xFF9A6400), series1 = Color(0xFF2A78D6), series2 = Color(0xFFEB6834),
            other = Color(0xFF8D8B82), chipOnText = Color(0xFFF3EFE7),
        )
        BackdropStyle.Amber -> StatsColors(
            textPrimary = fg, textSecondary = fg.copy(alpha = 0.82f), textMuted = fg.copy(alpha = 0.62f),
            card = Color.Black.copy(alpha = 0.12f), line = Color.White.copy(alpha = 0.3f),
            track = Color.Black.copy(alpha = 0.15f),
            // On orange, color bars would vanish: white and deep brown instead (labels name them).
            accent = Color.White, series1 = Color.White, series2 = Color(0xFF4A2300),
            other = Color.White.copy(alpha = 0.45f), chipOnText = Color(0xFFB86A0C),
        )
        else -> darkStats(albumBlur = style == BackdropStyle.AlbumBlur)
    }
}

/** The stats in the dark room, whatever the background. */
val NightStatsColors: StatsColors = darkStats(albumBlur = false)

/** Charcoal and the album blur (cards are darker so charts read on any art). */
private fun darkStats(albumBlur: Boolean) = StatsColors(
    textPrimary = Color(0xFFF3EFE7), textSecondary = Color(0xFFC3C2B7), textMuted = Color(0xFF9D9B92),
    card = Color.Black.copy(alpha = if (albumBlur) 0.32f else 0.22f),
    line = Color.White.copy(alpha = 0.16f), track = Color.White.copy(alpha = 0.1f),
    accent = Color(0xFFC98500), series1 = Color(0xFF3987E5), series2 = Color(0xFFD95926),
    other = Color(0xFF8D8B82), chipOnText = Color(0xFF1A1A19),
)

private val LocalStatsColors = staticCompositionLocalOf { NightStatsColors }

private val TextPrimary: Color @Composable get() = LocalStatsColors.current.textPrimary
private val TextSecondary: Color @Composable get() = LocalStatsColors.current.textSecondary
private val TextMuted: Color @Composable get() = LocalStatsColors.current.textMuted
private val Surface2: Color @Composable get() = LocalStatsColors.current.card
private val Line: Color @Composable get() = LocalStatsColors.current.line
private val Accent: Color @Composable get() = LocalStatsColors.current.accent
private val Series1: Color @Composable get() = LocalStatsColors.current.series1
private val Series2: Color @Composable get() = LocalStatsColors.current.series2
private val OtherGray: Color @Composable get() = LocalStatsColors.current.other

/**
 * Each section fades up in turn as [reveal] goes 0 → 1 (read inside the layer, so the
 * animation never rebuilds the screen). Going back plays it in reverse.
 */
private fun Modifier.reveal(step: Int, reveal: () -> Float): Modifier = graphicsLayer {
    val t = ((reveal() - step * REVEAL_STAGGER) / (1f - REVEAL_STEPS * REVEAL_STAGGER)).coerceIn(0f, 1f)
    // Pop: grows from 85% with a little overshoot, fading in quickly.
    val pop = PopEasing.transform(t)
    alpha = (t * 2.5f).coerceAtMost(1f)
    scaleX = 0.85f + 0.15f * pop
    scaleY = 0.85f + 0.15f * pop
    transformOrigin = TransformOrigin(0.5f, 0f)
}

private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)   // back-out

private const val REVEAL_STAGGER = 0.09f
private const val REVEAL_STEPS = 6

/**
 * @param reveal 0 = nothing shown yet, 1 = everything in place.
 * No background of its own: it sits on the same backdrop as the turntable.
 */
@Composable
fun StatsScreen(
    history: PlayLogStore,
    colors: StatsColors,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.Year) }
    val entries by history.entries.collectAsStateWithLifecycle()
    val stats = remember(entries, period) { StatsCalculator.compute(entries, period, System.currentTimeMillis()) }
    var confirmClear by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalStatsColors provides colors) {
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(Modifier.reveal(0, reveal)) {
                    Eyebrow("Liner notes")
                    Text("Your crate", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatsPeriod.entries.forEach { p -> PeriodChip(p.label, p == period) { period = p } }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (stats.plays == 0) {
                    Box(Modifier.reveal(1, reveal)) { EmptyStats(period) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.reveal(1, reveal)) {
                        Kpi("%,d".format(stats.plays), if (stats.plays == 1) "play" else "plays", Modifier.weight(1f))
                        Kpi(formatHours(stats.listenedMs), "listening", Modifier.weight(1f))
                        Kpi("%,d".format(stats.records), if (stats.records == 1) "record" else "records", Modifier.weight(1f))
                    }

                    Column(Modifier.reveal(2, reveal)) {
                        Spacer(Modifier.height(20.dp))
                        Eyebrow("Most played")
                        val maxPlays = stats.top.firstOrNull()?.plays ?: 1
                        stats.top.forEachIndexed { i, r -> TopRow(i + 1, r, maxPlays, history) }
                    }

                    stats.topArtist?.let { (name, n) ->
                        Column(Modifier.reveal(3, reveal)) {
                            Spacer(Modifier.height(18.dp))
                            Eyebrow("Top artist")
                            Text(name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp))
                            Text("$n plays across ${stats.topArtistRecords} ${if (stats.topArtistRecords == 1) "record" else "records"}",
                                color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    // ---------------- When you listen ----------------
                    Column(Modifier.reveal(4, reveal)) {
                        Spacer(Modifier.height(30.dp))
                        Eyebrow("When you listen")
                        Spacer(Modifier.height(10.dp))
                        Text("By hour of day", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        stats.peakHour?.let {
                            Text("Peak at ${hourLabel(it, long = true)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        HourChart(stats.byHour, stats.peakHour)

                        Spacer(Modifier.height(22.dp))
                        Text("Where it came from", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        PlayerSplit(stats.players, stats.plays)
                    }

                    Column(Modifier.reveal(5, reveal)) {
                        Spacer(Modifier.height(22.dp))
                        Text("Habits", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val facts = buildList {
                            stats.mostRepeats?.let { (r, n) -> add("$n×" to "Most times in a row\n${r.title ?: "Untitled"}") }
                            if (stats.longestStreakDays > 1) add("${stats.longestStreakDays} days" to "Longest streak with the needle down")
                            val s = (stats.singlesShare * 100).toInt()
                            add("$s% / ${100 - s}%" to "7\" singles / 12\" LPs")
                            stats.busiestDay?.let { add(dayName(it) to "Your busiest day") }
                        }
                        facts.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                pair.forEach { (v, l) -> Fact(v, l, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    // ---------------- Record of the period ----------------
                    stats.top.firstOrNull()?.let { r ->
                        Column(Modifier.reveal(REVEAL_STEPS, reveal)) {
                            Spacer(Modifier.height(20.dp))
                            RecordOfThePeriod(r, period, history)
                        }
                    }
                }

                Column(Modifier.reveal(REVEAL_STEPS, reveal)) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Counted on this phone only · never uploaded",
                        color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Swipe right to go back to the turntable",
                        color = TextMuted.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    if (entries.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Clear listening history",
                            color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Line, RoundedCornerShape(50))
                                .clickable(role = Role.Button) { confirmClear = true }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear listening history?") },
                text = {
                    Text(
                        "This deletes every play counted on this phone: your stats start from zero " +
                                "and worn records look new again. It can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { history.clear(); confirmClear = false }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Keep") }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Eyebrow(text: String) =
    Text(text.uppercase(), color = TextMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (selected) LocalStatsColors.current.chipOnText else TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(TextPrimary) else Modifier.border(1.dp, Line, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Surface2).padding(12.dp)) {
        Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Surface2).padding(12.dp)) {
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
    }
}

/** The saved cover for a record (loaded off the main thread), or null. */
@Composable
private fun rememberSleeve(history: PlayLogStore, key: SongKey): Bitmap? {
    val bmp by produceState<Bitmap?>(null, key) { value = withContext(Dispatchers.IO) { history.loadSleeve(key) } }
    return bmp
}

/** A record sleeve with the record peeking out. No saved cover: a plain colored sleeve. */
@Composable
private fun Sleeve(history: PlayLogStore, key: SongKey, dim: Int) {
    val art = rememberSleeve(history, key)
    Box(Modifier.size((dim + dim * 0.35f).dp, dim.dp)) {
        // Record peeking out to the right
        Box(
            Modifier
                .padding(start = (dim * 0.35f).dp)
                .size(dim.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C0C0C))
                .border(1.dp, Color(0xFF2A2A2A), CircleShape)
        )
        val sleeveMod = Modifier.size(dim.dp).clip(RoundedCornerShape(4.dp))
        if (art != null) {
            Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = sleeveMod)
        } else {
            val hue = Math.floorMod(key.value.hashCode(), 360).toFloat()
            Box(sleeveMod.background(Color.hsv(hue, 0.45f, 0.55f)))
        }
    }
}

@Composable
private fun TopRow(rank: Int, r: TopRecord, maxPlays: Int, history: PlayLogStore) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("$rank", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(16.dp))
        Sleeve(history, r.key, 42)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title ?: "Untitled", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.artist ?: "", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val track = LocalStatsColors.current.track
            val accent = Accent
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val cr = CornerRadius(2.dp.toPx())
                drawRoundRect(track, cornerRadius = cr)
                drawRoundRect(accent, size = Size(size.width * r.plays / maxPlays, size.height), cornerRadius = cr)
            }
        }
        Text("${r.plays}", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun HourChart(byHour: List<Int>, peak: Int?) {
    val max = (byHour.maxOrNull() ?: 0).coerceAtLeast(1)
    val summary = "Plays by hour of day." + (peak?.let { " Most at ${hourLabel(it, long = true)}." } ?: "")
    val accent = Accent
    val line = Line
    Column(Modifier.semantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * 23) / 24f
            val r = minOf(4.dp.toPx(), w / 2f)
            for (h in 0 until 24) {
                val v = byHour[h]
                if (v == 0) continue
                val bh = maxOf(2.dp.toPx(), size.height * v / max)
                val x = h * (w + gap)
                // Rounded top, square foot on the baseline.
                drawRoundRect(accent, Offset(x, size.height - bh), Size(w, bh), CornerRadius(r))
                drawRect(accent, Offset(x, size.height - minOf(bh, r)), Size(w, minOf(bh, r)))
            }
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach { Text(hourLabel(it), color = TextMuted, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun PlayerSplit(players: List<Pair<String, Int>>, total: Int) {
    val context = LocalContext.current
    // Two named apps get colors; anything beyond folds into "Other".
    val shown = players.take(2)
    val other = players.drop(2).sumOf { it.second }
    val parts = shown.mapIndexed { i, (pkg, n) -> Triple(appName(context, pkg), n, if (i == 0) Series1 else Series2) } +
            (if (other > 0) listOf(Triple("Other", other, OtherGray)) else emptyList())
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (parts.size - 1)
        var x = 0f
        parts.forEachIndexed { i, (_, n, c) ->
            val w = usable * n / total
            drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
            x += w + gap
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEach { (name, n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text("$name ${(n * 100f / total).toInt()}%", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RecordOfThePeriod(r: TopRecord, period: StatsPeriod, history: PlayLogStore) {
    val art = rememberSleeve(history, r.key)
    val angle = rememberPacedSpin()
    val title = when (period) {
        StatsPeriod.Month -> "Your record of the month"
        StatsPeriod.Year -> "Your record of the year"
        StatsPeriod.All -> "Your record of all time"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface2)
            .padding(horizontal = 20.dp, vertical = 26.dp),
    ) {
        Text(title.uppercase(), color = TextMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(18.dp))
        // Drawn exactly as it looks on the turntable, wear and all.
        VinylRecord(
            art = art,
            finish = VinylFinish.Black,
            seedKey = r.key.value,
            rotation = angle,
            title = r.title,
            artist = r.artist,
            wear = history.wearFor(r.key),
            modifier = Modifier.size(210.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(r.title ?: "Untitled", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        r.artist?.let { Text(it, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            "You dropped the needle on it ${r.plays} ${if (r.plays == 1) "time" else "times"}.",
            color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        val since = Calendar.getInstance().apply { timeInMillis = r.firstAt }
        val month = since.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault())
        Text(
            "In your crate since $month ${since.get(Calendar.DAY_OF_MONTH)}",
            color = TextSecondary, fontSize = 12.sp,
            modifier = Modifier
                .border(1.dp, Line, RoundedCornerShape(50))
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyStats(period: StatsPeriod) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
    ) {
        Text("Nothing in the crate yet", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            (if (period == StatsPeriod.All) "" else "Nothing for ${period.label.lowercase()} yet. ") +
                    "Drop the needle on something: a play counts after 30 seconds.",
            color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ text helpers

private fun formatHours(ms: Long): String {
    val min = ms / 60_000L
    return if (min < 60) "$min min" else "%,d h".format(min / 60)
}

private fun hourLabel(h: Int, long: Boolean = false): String {
    val suffix = if (h < 12) (if (long) " am" else "a") else (if (long) " pm" else "p")
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12$suffix"
}

private fun dayName(calendarDay: Int): String {
    val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, calendarDay) }
    return c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault()) + "s"
}

private val KnownPlayers = mapOf(
    "com.spotify.music" to "Spotify",
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "com.google.android.youtube" to "YouTube",
    "com.apple.android.music" to "Apple Music",
    "deezer.android.app" to "Deezer",
    "com.soundcloud.android" to "SoundCloud",
    "com.amazon.mp3" to "Amazon Music",
    "com.aspiro.tidal" to "TIDAL",
)

private fun appName(context: Context, pkg: String): String =
    KnownPlayers[pkg] ?: runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    }.getOrNull() ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
