package io.github.plvzfqrit.lockgroove.core.settings

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** A named point on a slider: one of the fixed choices the setting used to have. */
data class Stop(val label: String, val value: Float)

/**
 * A setting you slide, from [min] to [max] in steps of [step], with named [stops] along
 * it that the thumb clicks into when it comes near.
 *
 * @param logarithmic equal moves along the track multiply the value instead of adding
 *        to it (for speeds, so the slow end isn't squeezed into a corner)
 * @param describe how a value between the stops reads ("20 RPM", "70%")
 */
class Scale(
    val min: Float,
    val max: Float,
    val step: Float,
    val stops: List<Stop>,
    private val logarithmic: Boolean = false,
    private val describe: (Float) -> String,
) {
    /** Where [value] sits along the track, 0..1. */
    fun fraction(value: Float): Float {
        val v = value.coerceIn(min, max)
        return if (logarithmic) ln(v / min) / ln(max / min) else (v - min) / (max - min)
    }

    /** The value at [fraction] along the track: a stop if it's within reach, else rounded to a step. */
    fun valueAt(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        val near = stops.minByOrNull { abs(fraction(it.value) - f) }
        if (near != null && abs(fraction(near.value) - f) <= SNAP) return near.value
        val raw = if (logarithmic) min * exp(f * ln(max / min)) else min + f * (max - min)
        return ((raw / step).roundToInt() * step).coerceIn(min, max)
    }

    /** The stop [value] is on, if any. */
    fun stopAt(value: Float): Stop? = stops.firstOrNull { abs(it.value - value) < step / 2f }

    /** How [value] reads: its stop's name, or the value itself. */
    fun label(value: Float): String = stopAt(value)?.label ?: describe(value)

    companion object {
        /** How close (along the track, 0..1) the thumb has to come for a stop to catch it. */
        const val SNAP = 0.025f
    }
}
