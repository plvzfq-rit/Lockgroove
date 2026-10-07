package io.github.plvzfqrit.lockgroove.ui.lighting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plvzfqrit.lockgroove.AndroidClock
import io.github.plvzfqrit.lockgroove.core.lighting.ZonePlaces
import io.github.plvzfqrit.lockgroove.core.lighting.sunLight
import io.github.plvzfqrit.lockgroove.ui.common.GradientSlider
import kotlin.math.roundToInt

private val CardBg = Color(0xF2242424)
private val Ink = Color(0xFFF3EFE7)
private val Muted = Color(0xFF9D9B92)

/**
 * The time-of-day card, shown at the top of the screen while you choose, so the room
 * below is the preview. The slider's track is today's sky where you are: dark until
 * sunrise, peach at dawn, daylight, gold before sunset, night blue.
 *
 * @param held the time the sun is held at (minutes after midnight), or null = Now
 */
@Composable
fun TimeCard(
    held: Int?,
    onHold: (Int?) -> Unit,
    onDone: () -> Unit,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = AndroidClock.minuteOfDay()
    val sky = remember {
        val place = ZonePlaces.forZone(AndroidClock.zoneId(), AndroidClock.utcOffsetMs())
        val midnight = AndroidClock.wallMs() - (now * 60_000).toLong()
        List(49) { i ->
            val minute = i * 30f
            sunLight(midnight + (minute * 60_000).toLong(), minute, place).skyColor()
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Time of day", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Pill("Now", selected = held == null) { tick(); onHold(null) }
            Spacer(Modifier.width(8.dp))
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
        Spacer(Modifier.height(10.dp))
        Text(
            text = timeOfDayLabel(held?.toFloat() ?: now),
            color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light,
        )
        Text(
            text = if (held == null) "Following the clock" else "Held: today's sun at this hour",
            color = Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        GradientSlider(
            value = (held?.toFloat() ?: now) / 1440f,
            // Five-minute steps, like a clock you'd set.
            onValueChange = { v -> onHold(((v * 1440f).roundToInt() / 5 * 5).coerceIn(0, 1435)) },
            stops = sky,
            label = "Time of day",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
