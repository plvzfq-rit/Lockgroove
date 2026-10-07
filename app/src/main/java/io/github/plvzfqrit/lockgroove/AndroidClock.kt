package io.github.plvzfqrit.lockgroove

import android.os.SystemClock
import io.github.plvzfqrit.lockgroove.core.model.Clock

/** The phone's clocks. Monotonic time matches the media sessions' position timestamps. */
object AndroidClock : Clock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
    override fun wallMs(): Long = System.currentTimeMillis()

    /** The phone's time zone, with old names (like "Asia/Calcutta") mapped to today's. */
    override fun zoneId(): String {
        val id = java.util.TimeZone.getDefault().id
        return android.icu.util.TimeZone.getCanonicalID(id) ?: id
    }
}
