package io.github.plvzfqrit.plvinyl.core.settings

import io.github.plvzfqrit.plvinyl.core.lighting.LightingMode
import io.github.plvzfqrit.plvinyl.core.model.RecordSizeChoice

/** How fast the platter turns. */
enum class SpinSpeed(val label: String, val degPerSec: Float) {
    Slow("Slow", 30f),              // 12 s per turn (the original look)
    Medium("Medium", 60f),          // 6 s per turn
    Real("33⅓ RPM", 200f),          // a real record player
}

/** How long before the end of a song the needle lifts (margin against auto-advance). */
enum class EndMargin(val label: String, val ms: Long) {
    Short("0.5 s", 500L),
    Normal("1 s", 1000L),
    Long("2 s", 2000L),
}

/** Crackle and hiss while the needle is on the record (made on the phone, no audio files). */
enum class SurfaceNoiseLevel(val label: String, val gain: Float) {
    Off("Off", 0f),
    Light("Light", 0.55f),
    Heavy("Well-loved", 1f),
}

/**
 * How often the spinning record is redrawn. Most of the app's battery use is
 * redrawing the screen. Battery and Balanced also ask the screen for 60 Hz.
 */
enum class MotionQuality(val label: String, val fps: Int) {
    Battery("Battery", 20),         // steps (lightest)
    Balanced("Balanced", 0),        // every frame, screen held at 60 Hz
    Smooth("Smooth", 0),            // every frame at the screen's full rate (90/120 Hz)
}

/** Everything adjustable, plus what the app has learned. Plain data: saved by the app. */
data class Settings(
    val spin: SpinSpeed = SpinSpeed.Slow,
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
    val endMargin: EndMargin = EndMargin.Normal,
    /** 7" singles for short songs, 12" LPs for long ones (or always one size). */
    val recordSize: RecordSizeChoice = RecordSizeChoice.Auto,
    /** Close the dust cover after a while idle. */
    val dustCover: Boolean = true,
    /** Surface noise while the needle is down. */
    val surfaceNoise: SurfaceNoiseLevel = SurfaceNoiseLevel.Off,
    /** Records you play a lot show it (scuffs, spindle marks, a duller shine). */
    val recordWear: Boolean = true,
    /** Also count plays (for stats) while the app is closed. */
    val countInBackground: Boolean = false,
    /** Early hand-off learned per player app (package name → ms). */
    val learnedCrossfades: Map<String, Long> = emptyMap(),
)
