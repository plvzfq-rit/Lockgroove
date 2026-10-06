package io.github.plvzfqrit.plvinyl.ui.drawing

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The deck's look: background and vinyl. Saved with the settings. */
data class Look(
    val backdrop: BackdropStyle = BackdropStyle.Charcoal,
    val finish: VinylFinish = VinylFinish.Black,
)

/** Every color the deck needs, resolved from the look in one place. */
@Immutable
data class DeckColors(
    val style: BackdropStyle,
    /** Text and icons on the deck. */
    val contentColor: Color,
    /** A light deck: dark text, dark status-bar icons. */
    val isLight: Boolean,
    val tonearm: TonearmColors,
    /** The front panel's paint, or null for the background's own material. */
    val panelPaint: Color?,
)

fun Look.deckColors(): DeckColors {
    val light = backdrop == BackdropStyle.Paper
    return DeckColors(
        style = backdrop,
        contentColor = if (light) Color(0xFF2B2A28) else Color.White,
        isLight = light,
        tonearm = backdrop.tonearmColors(),
        panelPaint = null,
    )
}
