package io.github.plvzfqrit.plvinyl.core

import io.github.plvzfqrit.plvinyl.core.model.Clock

/** A clock that only moves when the test says so. */
class FakeClock(var t: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    var minute: Float = 12 * 60f
    override fun nowMs() = t
    override fun wallMs() = wall
    override fun minuteOfDay() = minute
    fun advance(ms: Long) { t += ms; wall += ms }
}
