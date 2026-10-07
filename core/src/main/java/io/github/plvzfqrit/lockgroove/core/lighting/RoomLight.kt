package io.github.plvzfqrit.lockgroove.core.lighting

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * Lighting that follows the sun where the phone's time zone is (no location, no internet):
 *
 *   Dawn         a soft, cool glow just after sunrise, from the right
 *   Day          plain daylight; the light turns from the right (morning) over the top
 *                (noon) to the left (evening), and shadows lengthen as the sun drops
 *   Golden hour  warm, low sun from the left as it sets
 *   Night        dark room, a desk lamp lights the record
 *   Late night   after 1 am, the lamp dims to a small pool
 *
 * Every change ramps with the sun's height, so you never see a switch.
 */

/** How much of each kind of light there is right now, and where it comes from. */
data class RoomLight(
    /** Evening golden hour, 0..1. */
    val golden: Float = 0f,
    val night: Float = 0f,
    val late: Float = 0f,
    /** The soft morning glow just after sunrise, 0..1. */
    val dawn: Float = 0f,
    /** Where the light comes from, as a screen direction in degrees: 0 = right, -90 = top, ±180 = left. */
    val sunFrom: Float = DAY_FROM,
    /** How high the sun is: 0 = on the horizon, 1 = overhead. Shadows lengthen as it drops. */
    val sunHeight: Float = DAY_HEIGHT,
) {
    companion object {
        /** The daylight look the app always had: light from the top-left, the sun about halfway up. */
        const val DAY_FROM = -125f
        const val DAY_HEIGHT = 0.6f
    }
}

/** Follow the sun, or keep one time of day. Labels are short to fit one row. */
enum class LightingMode(val label: String, val fixed: RoomLight?) {
    Clock("Sun", null),
    Sunrise("Sunrise", RoomLight(dawn = 1f, sunFrom = 0f, sunHeight = 0.06f)),
    Day("Day", RoomLight()),
    Golden("Golden", RoomLight(golden = 1f, sunFrom = 180f, sunHeight = 0.08f)),
    Night("Night", RoomLight(night = 1f)),
    Late("Late", RoomLight(night = 1f, late = 1f)),
}

private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

/** 0 at [from], 1 at [to], smooth in between (either direction). */
private fun ramp(x: Float, from: Float, to: Float) = smooth((x - from) / (to - from))

/**
 * The light right now at [place]. [minuteOfDay] is the local clock (late night is a time
 * of night, not a position of the sun).
 */
fun sunLight(wallMs: Long, minuteOfDay: Float, place: Place): RoomLight {
    val sun = SolarPosition.at(wallMs, place.latitude, place.longitude)
    val e = sun.elevation.toFloat()
    val evening = sun.hourAngle > 0
    // Night from just above the horizon to civil dusk (about 35–45 minutes), and back at dawn.
    val night = 1f - ramp(e, -7f, 1f)
    // Golden hour: the sun low in the west, lingering into the first minutes after sunset.
    val golden = if (evening) ramp(e, 12f, 5f) * ramp(e, -6f, -2f) else 0f
    // Dawn: a softer glow for about half an hour after sunrise.
    val dawn = if (!evening) ramp(e, -4f, 1f) * (1f - ramp(e, 6f, 12f)) else 0f
    // Late night: after 1 am, while it's still dark.
    val late = if (minuteOfDay < 12 * 60) ramp(minuteOfDay, 45f, 75f) * night else 0f
    // Where it comes from: sunrise on the right, noon from the top, sunset on the left.
    val halfDay = SolarPosition.sunsetHourAngle(place.latitude, sun.declination)
    val across = if (halfDay > 0.0) (sun.hourAngle / halfDay).toFloat().coerceIn(-1f, 1f) else 0f
    return RoomLight(
        golden = golden,
        night = night,
        late = late,
        dawn = dawn,
        sunFrom = -90f - 90f * across,
        sunHeight = sin(max(e, 0f) * PI.toFloat() / 180f),
    )
}

/** The light for [mode] at this moment and place. */
fun LightingMode.lightAt(wallMs: Long, minuteOfDay: Float, place: Place): RoomLight =
    fixed ?: sunLight(wallMs, minuteOfDay, place)
