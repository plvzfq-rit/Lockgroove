package io.github.plvzfqrit.plvinyl.core.lighting

/**
 * A sped-up day playing ("Watch a day go by"): 24 hours in [durationMs], from the time
 * it started. Pure, so the screen can ask for the light at every frame and the motion
 * stays smooth.
 */
data class DayPreview(
    /** Monotonic time it started. */
    val startedAt: Long,
    /** Wall time and local minute of day it started from. */
    val fromWall: Long,
    val fromMinute: Float,
    val place: Place,
    val durationMs: Long = DURATION_MS,
) {
    /** 0..1 through the sped-up day. */
    fun progress(now: Long): Float = ((now - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)

    fun isOver(now: Long): Boolean = now - startedAt >= durationMs

    /** The sped-up day's local time, minutes since midnight. */
    fun minuteAt(now: Long): Float = (fromMinute + progress(now) * 1440f) % 1440f

    /** The light in the sped-up day. */
    fun lightAt(now: Long): RoomLight =
        sunLight(fromWall + (progress(now) * DAY_MS).toLong(), minuteAt(now), place)

    companion object {
        const val DURATION_MS = 48_000L
        private const val DAY_MS = 86_400_000L
    }
}
