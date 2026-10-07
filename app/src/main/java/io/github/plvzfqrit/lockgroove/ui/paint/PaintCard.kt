package io.github.plvzfqrit.lockgroove.ui.paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plvzfqrit.lockgroove.core.look.Hsl
import io.github.plvzfqrit.lockgroove.core.look.Paint
import io.github.plvzfqrit.lockgroove.core.look.PaintChips
import io.github.plvzfqrit.lockgroove.ui.common.GradientSlider

/** What's being painted. */
enum class PaintTarget(val label: String) { Deck("Deck"), Panel("Front panel") }

private val CardBg = Color(0xF2242424)
private val Ink = Color(0xFFF3EFE7)
private val Muted = Color(0xFF9D9B92)

/** Lightness is kept off pure black and white: a deck is never a void or a glare. */
private const val MIN_L = 0.05f
private const val MAX_L = 0.95f

/**
 * The paint card, shown at the top of the screen while you paint: the real turntable
 * underneath is the preview. Chips for one-tap paints, sliders to reach any color.
 *
 * @param color the current paint of [target] (ARGB); null = the panel matches the background
 * @param matchColor what the panel looks like when it matches (where the sliders start from)
 * @param onColor a new paint for [target]; null = match the background (panel only)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaintCard(
    target: PaintTarget,
    onTarget: (PaintTarget) -> Unit,
    color: Int?,
    matchColor: Int,
    onColor: (Int?) -> Unit,
    onDone: () -> Unit,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The sliders work in HSL. Kept here while you drag (instead of re-reading it from the
    // color every time), so hue and saturation don't drift when the color goes gray.
    var hsl by remember(target) { mutableStateOf(Paint.toHsl(color ?: matchColor)) }
    // A chip (or Match) tapped elsewhere moves the sliders to it.
    val latest by rememberUpdatedState(color)
    fun pick(c: Int?) {
        tick()
        hsl = Paint.toHsl(c ?: matchColor)
        onColor(c)
    }
    fun slide(next: Hsl) {
        hsl = next
        onColor(Paint.fromHsl(next))
    }

    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // ---- What you're painting, and Done
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaintTarget.entries.forEach { t ->
                Tab(t.label, selected = t == target) { tick(); onTarget(t) }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                color = Color(0xFF1A1A19), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink)
                    .clickable(role = Role.Button) { tick(); onDone() }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---- Paint chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (target == PaintTarget.Panel) {
                MatchChip(matchColor, selected = latest == null) { pick(null) }
            }
            PaintChips.all.forEach { chip ->
                Chip(Color(chip.argb), chip.name, selected = latest == chip.argb) { pick(chip.argb) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Fine-tune: each track shows what moving along it does
        val (h, s, l) = hsl
        SliderRow("Hue", value = h / 360f, stops = List(13) { Color(Paint.fromHsl(Hsl(it * 30f, 0.7f, 0.5f))) }) {
            slide(hsl.copy(h = it * 360f))
        }
        SliderRow("Saturation", value = s, stops = listOf(0f, 0.5f, 1f).map { Color(Paint.fromHsl(Hsl(h, it, l))) }) {
            slide(hsl.copy(s = it))
        }
        SliderRow(
            "Lightness",
            value = ((l - MIN_L) / (MAX_L - MIN_L)).coerceIn(0f, 1f),
            stops = listOf(MIN_L, 0.5f, MAX_L).map { Color(Paint.fromHsl(Hsl(h, s, it))) },
        ) {
            slide(hsl.copy(l = MIN_L + it * (MAX_L - MIN_L)))
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (selected) Color(0xFF1A1A19) else Ink,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .semantics { this.selected = selected }
            .clip(shape)
            .then(if (selected) Modifier.background(Ink) else Modifier.border(1.dp, Ink.copy(alpha = 0.3f), shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** A round paint chip; a ring marks the one in use. */
@Composable
private fun Chip(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .semantics {
                contentDescription = "$name paint"
                this.selected = selected
            }
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val r = size.minDimension / 2f
            if (selected) drawCircle(Ink, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(color, r - 5.dp.toPx())
            drawCircle(Color.White.copy(alpha = 0.18f), r - 5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
    }
}

/** "Match": the panel goes back to the background's own material. */
@Composable
private fun MatchChip(match: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(36.dp)
            .semantics { this.selected = selected; stateDescription = if (selected) "On" else "Off" }
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, Ink, shape) else Modifier.border(1.dp, Ink.copy(alpha = 0.3f), shape))
            .clickable(role = Role.RadioButton, onClickLabel = "Match the background", onClick = onClick)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(match)))
        Spacer(Modifier.width(6.dp))
        Text("Match", color = Ink, fontSize = 13.sp)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, stops: List<Color>, onValue: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        GradientSlider(value, onValue, stops, label, Modifier.weight(1f))
    }
}
