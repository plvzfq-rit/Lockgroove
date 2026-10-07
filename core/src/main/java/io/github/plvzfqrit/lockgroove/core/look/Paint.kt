package io.github.plvzfqrit.lockgroove.core.look

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A color as hue (0..360°), saturation (0..1) and lightness (0..1). */
data class Hsl(val h: Float, val s: Float, val l: Float)

/**
 * Color math for painting the deck, on plain ARGB ints (no Android, so it's testable).
 * Readability uses the WCAG contrast ratio: text on any paint picks whichever of dark
 * ink or white stands out more.
 */
object Paint {
    /** The dark ink used for text on light decks (Paper's). */
    const val DARK_INK: Int = 0xFF2B2A28.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()

    fun red(c: Int) = ((c shr 16) and 0xFF) / 255f
    fun green(c: Int) = ((c shr 8) and 0xFF) / 255f
    fun blue(c: Int) = (c and 0xFF) / 255f

    /** An opaque color from 0..1 channels. */
    fun rgb(r: Float, g: Float, b: Float): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun toHsl(c: Int): Hsl {
        val r = red(c); val g = green(c); val b = blue(c)
        val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        if (hi == lo) return Hsl(0f, 0f, l)
        val d = hi - lo
        val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
        val h = when (hi) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return Hsl(h, s, l)
    }

    fun fromHsl(hsl: Hsl): Int {
        val (h, s, l) = hsl
        if (s <= 0f) return rgb(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val hk = ((h % 360f) + 360f) % 360f / 360f
        fun channel(tIn: Float): Float {
            var t = tIn
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return rgb(channel(hk + 1f / 3f), channel(hk), channel(hk - 1f / 3f))
    }

    /** WCAG relative luminance, 0 (black) .. 1 (white). */
    fun luminance(c: Int): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f * lin(red(c)) + 0.7152f * lin(green(c)) + 0.0722f * lin(blue(c))
    }

    /** WCAG contrast ratio between two colors, 1..21. */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** The text color that reads best on [c]: dark ink or white. */
    fun textOn(c: Int): Int = if (contrast(c, DARK_INK) >= contrast(c, WHITE)) DARK_INK else WHITE

    /** A light paint (dark text and icons go on it). */
    fun isLight(c: Int): Boolean = textOn(c) == DARK_INK

    /** [c] made lighter (positive [dl]) or darker (negative), keeping its hue. */
    fun shade(c: Int, dl: Float): Int {
        val hsl = toHsl(c)
        return fromHsl(hsl.copy(l = (hsl.l + dl).coerceIn(0f, 1f)))
    }

    /** Between [a] (t = 0) and [b] (t = 1). */
    fun mix(a: Int, b: Int, t: Float): Int = rgb(
        red(a) + (red(b) - red(a)) * t,
        green(a) + (green(b) - green(a)) * t,
        blue(a) + (blue(b) - blue(a)) * t,
    )

    /** Angle between two hues, 0..180°. */
    fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

/** A paint chip: a name for screen readers, and its color. */
data class PaintChip(val name: String, val argb: Int)

/** Hand-picked hi-fi paints: one tap to a color that looks right on a deck. */
object PaintChips {
    val all: List<PaintChip> = listOf(
        PaintChip("Cream", 0xFFEDE6D6.toInt()),
        PaintChip("Silver", 0xFFC9CCCE.toInt()),
        PaintChip("Sand", 0xFFC9AE8A.toInt()),
        PaintChip("Mustard", 0xFFD4A13A.toInt()),
        PaintChip("Burnt orange", 0xFFB8572A.toInt()),
        PaintChip("Oxblood", 0xFF6B2226.toInt()),
        PaintChip("Dusty rose", 0xFFC99A96.toInt()),
        PaintChip("Sage", 0xFF9AAA8E.toInt()),
        PaintChip("Racing green", 0xFF2E4A3B.toInt()),
        PaintChip("Teal", 0xFF2C6A6E.toInt()),
        PaintChip("Navy", 0xFF23324D.toInt()),
        PaintChip("Graphite", 0xFF38393B.toInt()),
    )
}
