package io.github.plvzfqrit.plvinyl.core.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Time, passed in. Nothing in the core reads the system clock directly, so tests can
 * use a fake clock and jump ahead instantly.
 */
interface Clock {
    /** Monotonic milliseconds (SystemClock.elapsedRealtime on the phone). Never jumps back. */
    fun nowMs(): Long

    /** Wall-clock milliseconds (System.currentTimeMillis): for dates in the play log. */
    fun wallMs(): Long

    /** Minutes since local midnight, with fractions (room lighting). */
    fun minuteOfDay(): Float {
        val c = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = wallMs() }
        return c.get(Calendar.HOUR_OF_DAY) * 60f + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60f
    }
}
