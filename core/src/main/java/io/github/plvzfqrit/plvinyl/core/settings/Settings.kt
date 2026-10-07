package io.github.plvzfqrit.plvinyl.core.settings

import io.github.plvzfqrit.plvinyl.core.lighting.LightingMode
import io.github.plvzfqrit.plvinyl.core.model.RecordSizeChoice
import kotlin.math.roundToInt

/** The settings you slide. Their stops are the fixed choices they used to have. */
object Scales {
    /** How fast the platter turns, degrees a second: from lazy to a real 33⅓ RPM. */
    val spin = Scale(
        min = 15f, max = 200f, step = 1f, logarithmic = true,
        stops = listOf(Stop("Slow", 30f), Stop("Medium", 60f), Stop("33⅓ RPM", 200f)),
        describe = { degPerSec ->
            val rpm = degPerSec / 6f
            if (rpm < 10f) "%.1f RPM".format(rpm) else "${rpm.roundToInt()} RPM"
        },
    )

    /** How long before the end of a song the needle lifts, ms. */
    val endMargin = Scale(
        min = 300f, max = 3000f, step = 100f,
        stops = listOf(Stop("0.5 s", 500f), Stop("1 s", 1000f), Stop("2 s", 2000f)),
        describe = { ms -> "%.1f s".format(ms / 1000f) },
    )

    /** Surface noise, from none to a well-loved record. */
    val surfaceNoise = Scale(
        min = 0f, max = 1f, step = 0.05f,
        stops = listOf(Stop("Off", 0f), Stop("Light", 0.55f), Stop("Well-loved", 1f)),
        describe = { level -> "${(level * 100).roundToInt()}%" },
    )
}

/**
 * How often the spinning record is redrawn (see [MotionPolicy]). Most of the app's
 * battery use is redrawing the screen. Battery and Balanced also ask the screen for 60 Hz.
 */
enum class MotionQuality(val label: String) {
    Battery("Battery"),             // 20 frames a second (lightest)
    Balanced("Balanced"),           // 30 for slow spins, 60 at 33⅓, screen held at 60 Hz
    Smooth("Smooth"),               // every frame at the screen's full rate (90/120 Hz)
}

/** Everything adjustable, plus what the app has learned. Plain data: saved by the app. */
data class Settings(
    /** How fast the platter turns, degrees a second ([Scales.spin]; 30 = a turn every 12 s). */
    val spin: Float = 30f,
    val grooveGlints: Boolean = true,
    /** How smoothly (and how often) the record is redrawn. */
    val motion: MotionQuality = MotionQuality.Balanced,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    /** The app's own repeat one (the ↻1 key): at the end, the same record plays again. */
    val repeatOne: Boolean = false,
    /** Room lighting: follows the clock, or always one time of day. */
    val lighting: LightingMode = LightingMode.Clock,
    /**
     * With the Sun lighting: hold the sun at this time of day (minutes after midnight),
     * today's sun where you are. Null = follow the clock.
     */
    val sunTime: Int? = null,
    /** How long before the end of a song the needle lifts, ms: a margin against the player moving on by itself. */
    val endMarginMs: Long = 1000L,
    /** 7" singles for short songs, 12" LPs for long ones (or always one size). */
    val recordSize: RecordSizeChoice = RecordSizeChoice.Auto,
    /** Close the dust cover after a while idle. */
    val dustCover: Boolean = true,
    /** Surface noise while the needle is down: 0 = none, 1 = a well-loved record (made on the phone). */
    val surfaceNoise: Float = 0f,
    /** Records you play a lot show it (scuffs, spindle marks, a duller shine). */
    val recordWear: Boolean = true,
    /** Also count plays (for stats) while the app is closed. */
    val countInBackground: Boolean = false,
    /** Early hand-off learned per player app (package name → ms). */
    val learnedCrossfades: Map<String, Long> = emptyMap(),
)
