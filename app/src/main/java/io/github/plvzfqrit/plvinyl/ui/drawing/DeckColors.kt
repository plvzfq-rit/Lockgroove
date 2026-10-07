package io.github.plvzfqrit.plvinyl.ui.drawing

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.plvzfqrit.plvinyl.core.look.Paint

/** The deck's look: background, vinyl and paint. Saved with the settings. */
data class Look(
    val backdrop: BackdropStyle = BackdropStyle.Charcoal,
    val finish: VinylFinish = VinylFinish.Black,
    /** The painted deck color (ARGB) for the Custom background; null = never painted. */
    val deckPaint: Int? = null,
    /** The painted front panel (ARGB), on any background; null = match the background. */
    val panelPaint: Int? = null,
) {
    /** The deck paint to use or start from: the saved one, else the current background's color. */
    val deckPaintOrSeed: Int get() = deckPaint ?: backdrop.baseColor

    /** What the front panel looks like when it matches the background (as one color). */
    val matchingPanel: Int
        get() = if (backdrop == BackdropStyle.Custom) Paint.shade(deckPaintOrSeed, -0.12f) else backdrop.panelColor
}

/** Every color the deck needs, resolved from the look in one place. */
@Immutable
data class DeckColors(
    val style: BackdropStyle,
    /** The painted deck color (the Custom background), else null. */
    val deck: Color?,
    /** Text and icons on the deck. */
    val contentColor: Color,
    /** A light deck: dark text, dark status-bar icons. */
    val isLight: Boolean,
    val tonearm: TonearmColors,
    /** The front panel's paint, or null for the background's own material. */
    val panelPaint: Color?,
)

fun Look.deckColors(): DeckColors {
    if (backdrop == BackdropStyle.Custom) {
        val paint = deckPaintOrSeed
        val light = Paint.isLight(paint)
        return DeckColors(
            style = backdrop,
            deck = Color(paint),
            contentColor = Color(Paint.textOn(paint)),
            isLight = light,
            tonearm = paintedTonearm(paint, light),
            // Matching: the plinth's front edge, in its own shade.
            panelPaint = Color(panelPaint ?: matchingPanel),
        )
    }
    val light = backdrop.isLight
    return DeckColors(
        style = backdrop,
        deck = null,
        contentColor = if (light) Color(0xFF2B2A28) else Color.White,
        isLight = light,
        tonearm = backdrop.tonearmColors(),
        panelPaint = panelPaint?.let(::Color),
    )
}

/** The arm on a painted deck: its plate in shades of the paint, the arm parts light or dark to suit. */
private fun paintedTonearm(paint: Int, light: Boolean): TonearmColors =
    (if (light) BackdropStyle.Paper else BackdropStyle.Charcoal).tonearmColors().withPlate(paint)

/** These arm colors with the plate (the disc the arm stands on) in shades of [color] (ARGB). */
internal fun TonearmColors.withPlate(color: Int): TonearmColors {
    fun shade(dl: Float) = Color(Paint.shade(color, dl))
    return copy(
        plateLight = shade(+0.04f), plateDark = shade(-0.02f),
        plateOutline = shade(-0.18f), innerRing = shade(-0.10f),
        slotFill = shade(+0.02f), slotOutline = shade(-0.14f),
        restClipOutline = shade(-0.25f),
    )
}
