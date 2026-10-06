package io.github.plvzfqrit.plvinyl.core.lighting

/*
 * Lighting that follows the phone's clock (no location, no internet).
 *
 *   Day          7 am – 5 pm   soft window light (the normal look)
 *   Golden hour  5 pm – 7 pm   warm, low sun from the left
 *   Night        7 pm – 1 am   dark room, a desk lamp lights the record
 *   Late night   1 am – 7 am   the lamp dims to a small pool
 *
 * Each change ramps over half an hour, so you never see a switch.
 */

/** How much of each kind of light there is right now (0..1 each). */
data class RoomLight(val golden: Float = 0f, val night: Float = 0f, val late: Float = 0f)

/** Follow the clock, or keep one time of day. Labels are short to fit one row. */
enum class LightingMode(val label: String, val fixed: RoomLight?) {
    Clock("Clock", null),
    Day("Day", RoomLight()),
    Golden("Golden", RoomLight(golden = 1f)),
    Night("Night", RoomLight(night = 1f)),
    Late("Late", RoomLight(night = 1f, late = 1f)),
}

private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

/** 0 before [start], 1 after [end] (minutes of the day), smooth in between. */
private fun rise(m: Float, start: Int, end: Int) = smooth((m - start) / (end - start))

/** The light for a time of day, in minutes since midnight. */
fun roomLightAt(minuteOfDay: Float): RoomLight {
    val m = minuteOfDay
    val pm = m >= 12 * 60
    val night = if (pm) rise(m, 18 * 60 + 45, 19 * 60 + 15) else 1f - rise(m, 6 * 60 + 45, 7 * 60 + 15)
    val late = if (pm) 0f else rise(m, 45, 75) * (1f - rise(m, 6 * 60 + 45, 7 * 60 + 15))
    val golden = if (pm) rise(m, 16 * 60 + 45, 17 * 60 + 15) * (1f - rise(m, 18 * 60 + 45, 19 * 60 + 15)) else 0f
    return RoomLight(golden, night, late)
}

/** The light for [mode] at [minuteOfDay]. */
fun LightingMode.lightAt(minuteOfDay: Float): RoomLight = fixed ?: roomLightAt(minuteOfDay)
