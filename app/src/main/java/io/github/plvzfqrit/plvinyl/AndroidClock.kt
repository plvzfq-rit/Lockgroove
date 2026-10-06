package io.github.plvzfqrit.plvinyl

import android.os.SystemClock
import io.github.plvzfqrit.plvinyl.core.model.Clock

/** The phone's clocks. Monotonic time matches the media sessions' position timestamps. */
object AndroidClock : Clock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
    override fun wallMs(): Long = System.currentTimeMillis()
}
