package io.github.plvzfqrit.plvinyl.ui.settings

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plvzfqrit.plvinyl.AndroidClock
import io.github.plvzfqrit.plvinyl.core.lighting.LightingMode
import io.github.plvzfqrit.plvinyl.core.lighting.ZonePlaces
import io.github.plvzfqrit.plvinyl.core.lighting.sunLight
import io.github.plvzfqrit.plvinyl.core.look.Paint
import io.github.plvzfqrit.plvinyl.core.model.RecordSizeChoice
import io.github.plvzfqrit.plvinyl.core.settings.EndMargin
import io.github.plvzfqrit.plvinyl.core.settings.MotionQuality
import io.github.plvzfqrit.plvinyl.core.settings.Settings
import io.github.plvzfqrit.plvinyl.core.settings.SpinSpeed
import io.github.plvzfqrit.plvinyl.core.settings.SurfaceNoiseLevel
import io.github.plvzfqrit.plvinyl.ui.common.GradientSlider
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.DeckColors
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylPalette
import io.github.plvzfqrit.plvinyl.ui.drawing.rememberFinishPreview
import io.github.plvzfqrit.plvinyl.ui.lighting.skyColor
import io.github.plvzfqrit.plvinyl.ui.lighting.timeOfDayLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ------------------------------------------------------------------------
 * The settings sheet (opened from the knob under the tonearm). A LAZY list, so it
 * opens at once.
 * ---------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    deck: DeckColors,
    /** The deck paint (saved, or where painting would start). */
    deckPaint: Color,
    finish: VinylFinish,
    art: Bitmap?,
    seedKey: String?,
    settings: Settings,
    learnedCrossfadeMs: Long,
    onSelect: (BackdropStyle) -> Unit,
    /** Paint the deck and front panel (the Custom background). */
    onPaint: () -> Unit,
    onSelectFinish: (VinylFinish) -> Unit,
    onSettings: (Settings) -> Unit,
    onResetCrossfade: () -> Unit,
    /** Play the sun through a whole day, in under a minute. */
    onPreviewDay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = deck.style
    val light = deck.isLight
    val fg = if (light) Color(0xFF2B2A28) else Color.White
    val bg = if (light) Color(0xFFF7F4EF) else Color(0xFF242424)
    val view = LocalView.current
    val tick = {
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bg,
        contentColor = fg,
    ) {
        // A LAZY list: only the rows on screen are built, so the sheet opens at once
        // instead of building ~30 rows (switches, previews) before it can slide up.
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item { Column(Modifier.fillMaxWidth()) {
                // ---------------- Look ----------------
                SectionTitle("Background")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BackdropStyle.entries.forEach { style ->
                        BackdropSwatch(
                            style = style,
                            art = art,
                            paint = deckPaint,
                            selected = style == current,
                            ringColor = fg,
                            onClick = {
                                tick()
                                if (style == BackdropStyle.Custom) onPaint() else onSelect(style)
                            },
                        )
                    }
                }

            } }
            item { Column(Modifier.fillMaxWidth()) {
                SectionTitle("Vinyl")
                val (palette, marble) = rememberFinishPreview(art, seedKey)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    VinylFinish.entries.forEach { f ->
                        VinylSwatch(
                            finish = f,
                            palette = palette,
                            marble = marble,
                            selected = f == finish,
                            ringColor = fg,
                            onClick = { tick(); onSelectFinish(f) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Record size", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = RecordSizeChoice.entries.map { it.label },
                    selected = settings.recordSize.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(recordSize = RecordSizeChoice.entries[it])) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Auto: songs up to 4 min come as 7\" singles at 45, longer ones as 12\" LPs.",
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Groove glints",
                    subtitle = "Faint light on the grooves, so you can see the record turn",
                    checked = settings.grooveGlints,
                    fg = fg, bg = bg,
                ) { tick(); onSettings(settings.copy(grooveGlints = it)) }

            } }
            item { Column(Modifier.fillMaxWidth()) {
                // ---------------- Turntable ----------------
                SectionTitle("Spin speed")
                ChoiceRow(
                    options = SpinSpeed.entries.map { it.label },
                    selected = settings.spin.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(spin = SpinSpeed.entries[it])) }

            } }
            item { Column(Modifier.fillMaxWidth()) {
                SectionTitle("Lighting")
                ChoiceRow(
                    options = LightingMode.entries.map { it.label },
                    selected = settings.lighting.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(lighting = LightingMode.entries[it])) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (settings.lighting) {
                        LightingMode.Clock -> "Follows the sun where your phone's time zone says you are (no location used): a soft glow at sunrise, golden hour before sunset, a desk lamp after dark. Or hold it at any time of day below."
                        LightingMode.Day -> "Always soft daylight."
                        LightingMode.Golden -> "Always golden hour: warm, low sun from the left."
                        LightingMode.Night -> "Always the desk lamp in a dark room."
                        LightingMode.Late -> "Always late night: the lamp dimmed to a small pool."
                    },
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.6f),
                )
                if (settings.lighting == LightingMode.Clock) {
                    Spacer(Modifier.height(12.dp))
                    SunTimeRow(settings.sunTime, fg, tick) { onSettings(settings.copy(sunTime = it)) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Watch a day go by",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .clickable(role = Role.Button) { tick(); onPreviewDay() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )

                Spacer(Modifier.height(14.dp))
                Text("Lift the needle early", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = EndMargin.entries.map { it.label },
                    selected = settings.endMargin.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(endMargin = EndMargin.entries[it])) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Gives up the last moment of each song so the next one never starts before the record is swapped.",
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.6f),
                )

                // Crossfade: what the app has learned from your player, with a reset.
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Crossfade", fontSize = 15.sp)
                        Text(
                            text = if (learnedCrossfadeMs > 0L)
                                "Learned ${"%.1f".format(learnedCrossfadeMs / 1000f)} s: the needle lifts that much early"
                            else "None detected for this player",
                            fontSize = 12.sp,
                            color = fg.copy(alpha = 0.6f),
                        )
                    }
                    if (learnedCrossfadeMs > 0L) {
                        Text(
                            text = "Reset",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button) { tick(); onResetCrossfade() }
                                .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }

            } }
            item { Column(Modifier.fillMaxWidth()) {
                SectionTitle("Surface noise")
                ChoiceRow(
                    options = SurfaceNoiseLevel.entries.map { it.label },
                    selected = settings.surfaceNoise.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(surfaceNoise = SurfaceNoiseLevel.entries[it])) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Soft crackle while the needle is down, made on your phone. Plays alongside your music; turn it off any time.",
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Record wear",
                    subtitle = "Records you play a lot slowly show it: scuffs, spindle marks, less shine",
                    checked = settings.recordWear,
                    fg = fg, bg = bg,
                ) { tick(); onSettings(settings.copy(recordWear = it)) }

                ToggleRow(
                    title = "Count plays when the app is closed",
                    subtitle = "For your stats. Off: only records played on the turntable count. Either way, it stays on this phone",
                    checked = settings.countInBackground,
                    fg = fg, bg = bg,
                ) { tick(); onSettings(settings.copy(countInBackground = it)) }

                ToggleRow(
                    title = "Dust cover",
                    subtitle = "Closes over the deck after 2 minutes idle; tap to lift it",
                    checked = settings.dustCover,
                    fg = fg, bg = bg,
                ) { tick(); onSettings(settings.copy(dustCover = it)) }

            } }
            item { Column(Modifier.fillMaxWidth()) {
                // ---------------- Phone ----------------
                SectionTitle("Phone")
                Text("Motion", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = MotionQuality.entries.map { it.label },
                    selected = settings.motion.ordinal,
                    fg = fg,
                ) { tick(); onSettings(settings.copy(motion = MotionQuality.entries[it])) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (settings.motion) {
                        MotionQuality.Battery -> "The record turns in small steps. Lightest on the battery."
                        MotionQuality.Balanced -> "Smooth turning at 60 Hz. Recommended."
                        MotionQuality.Smooth -> "Your screen's full rate (90/120 Hz). Uses noticeably more battery."
                    },
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Keep screen on while playing",
                    subtitle = "The screen won't dim or sleep while the needle is down",
                    checked = settings.keepScreenOn,
                    fg = fg, bg = bg,
                ) { tick(); onSettings(settings.copy(keepScreenOn = it)) }
                ToggleRow(
                    title = "Haptics",
                    subtitle = "Button clicks, needle drop, and arm ticks",
                    checked = settings.haptics,
                    fg = fg, bg = bg,
                ) {
                    // Give feedback when turning it ON (it's already off otherwise).
                    if (it) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSettings(settings.copy(haptics = it))
                }
            } }
        }
    }
}

/**
 * Hold the sun at a time of day, or follow the clock (Now). The track is today's sky
 * where you are: dark until sunrise, peach at dawn, daylight, gold before sunset.
 */
@Composable
private fun SunTimeRow(held: Int?, fg: Color, tick: () -> Unit, onHold: (Int?) -> Unit) {
    val now = AndroidClock.minuteOfDay()
    val sky = remember {
        val place = ZonePlaces.forZone(AndroidClock.zoneId(), AndroidClock.utcOffsetMs())
        val midnight = AndroidClock.wallMs() - (now * 60_000).toLong()
        List(49) { i ->
            val minute = i * 30f
            sunLight(midnight + (minute * 60_000).toLong(), minute, place).skyColor()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Time of day", fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            text = timeOfDayLabel(held?.toFloat() ?: now),
            fontSize = 13.sp,
            color = fg.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(10.dp))
        val on = held == null
        Text(
            text = "Now",
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) (if (fg.luminance() > 0.5f) Color(0xFF242424) else Color.White) else fg,
            modifier = Modifier
                .semantics { selected = on }
                .clip(RoundedCornerShape(50))
                .background(if (on) fg else Color.Transparent)
                .border(1.dp, fg.copy(alpha = if (on) 1f else 0.3f), RoundedCornerShape(50))
                .clickable(role = Role.RadioButton) { tick(); onHold(null) }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
    GradientSlider(
        value = (held?.toFloat() ?: now) / 1440f,
        // Five-minute steps, like a clock you'd set.
        onValueChange = { v -> onHold(((v * 1440f).roundToInt() / 5 * 5).coerceIn(0, 1435)) },
        stops = sky,
        label = "Time of day",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(14.dp))
}

/** A row of pill-shaped options; exactly one is selected. */
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, fg: Color, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val isOn = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isOn) fg else Color.Transparent)
                    .border(1.dp, fg.copy(alpha = if (isOn) 1f else 0.3f), RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onPick(i) }
                    .padding(vertical = 9.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isOn) (if (fg.luminance() > 0.5f) Color(0xFF242424) else Color.White) else fg,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Title + explanation on the left, a switch on the right. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    fg: Color,
    bg: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = fg.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = bg,
                checkedTrackColor = fg,
                checkedBorderColor = fg,
                uncheckedThumbColor = fg.copy(alpha = 0.6f),
                uncheckedTrackColor = fg.copy(alpha = 0.08f),
                uncheckedBorderColor = fg.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun BackdropSwatch(
    style: BackdropStyle,
    art: Bitmap?,
    paint: Color,
    selected: Boolean,
    ringColor: Color,
    onClick: () -> Unit,
) {
    val name = when (style) {
        BackdropStyle.Paper -> "Paper"
        BackdropStyle.Charcoal -> "Charcoal"
        BackdropStyle.Amber -> "Amber"
        BackdropStyle.AlbumBlur -> "Album blur"
        BackdropStyle.Custom -> "Custom"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = "$name background" }
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .border(2.dp, if (selected) ringColor else Color.Transparent, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
        ) {
            when (style) {
                BackdropStyle.Paper -> Box(Modifier.size(50.dp).background(Color(0xFFF2EFEA)))
                BackdropStyle.Charcoal -> Box(Modifier.size(50.dp).background(Color(0xFF383838)))
                BackdropStyle.Amber -> Box(
                    Modifier.size(50.dp).background(
                        Brush.verticalGradient(listOf(Color(0xFFF29E1C), Color(0xFFB87612)))
                    )
                )
                BackdropStyle.AlbumBlur -> {
                    // Shrunk off the main thread (the art can be 1000+ px).
                    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, art) {
                        value = withContext(Dispatchers.Default) {
                            art?.let { Bitmap.createScaledBitmap(it, 8, 8, true).asImageBitmap() }
                        }
                    }
                    val t = thumb                       // local copy, so Kotlin can smart-cast it
                    if (t != null) {
                        Image(
                            bitmap = t,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.size(50.dp),
                        )
                    } else {
                        Box(
                            Modifier.size(50.dp).background(
                                Brush.linearGradient(listOf(Color(0xFF6F929F), Color(0xFF434442)))
                            )
                        )
                    }
                }
                // Your paint, with a palette printed on it.
                BackdropStyle.Custom -> Box(
                    Modifier.size(50.dp).background(paint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Palette, contentDescription = null,
                        tint = Color(Paint.textOn(paint.toArgb())).copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 12.sp)
    }
}

@Composable
private fun VinylSwatch(
    finish: VinylFinish,
    palette: VinylPalette?,
    marble: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    ringColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = "${finish.displayName} vinyl" }
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .border(2.dp, if (selected) ringColor else Color.Transparent, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF161E20)),          // felt, seen through clear vinyl
            contentAlignment = Alignment.Center,
        ) {
            when (finish) {
                VinylFinish.Black -> Box(Modifier.size(50.dp).background(Color(0xFF0E0E0F)))
                VinylFinish.Smoke -> Box(Modifier.size(50.dp).background(Color(0xFF3A3A3E).copy(alpha = 0.55f)))
                VinylFinish.AlbumColor -> Box(
                    Modifier.size(50.dp).background((palette?.tint ?: Color(0xFFC45C28)).copy(alpha = 0.8f))
                )
                VinylFinish.Marble -> if (marble != null) {
                    Image(
                        bitmap = marble,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(50.dp),
                    )
                } else {
                    Box(Modifier.size(50.dp).background(palette?.marbleA ?: Color(0xFFD6D6D0)))
                }
            }
            // Grooves hint + tiny label so it reads as a record
            Canvas(Modifier.size(50.dp)) {
                val r = size.minDimension / 2f
                for (i in 0 until 6) drawCircle(Color.Black.copy(alpha = 0.18f), r * (0.62f + i * 0.06f), center, style = Stroke(1f))
                drawCircle(Color(0xFFEDE6D8), r * 0.34f, center)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(finish.displayName, fontSize = 12.sp)
    }
}
