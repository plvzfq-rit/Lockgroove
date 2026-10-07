package io.github.plvzfqrit.plvinyl.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MotionPolicyTest {
    private fun pace(motion: MotionQuality, spin: Float = 30f, phone: PhoneState = PhoneState()) =
        MotionPolicy.pace(motion, spin, phone)

    @Test fun `Balanced turns slow records at 30 frames a second and 33⅓ at 60`() {
        assertEquals(30, pace(MotionQuality.Balanced, 30f).framesPerSecond)        // Slow
        assertEquals(30, pace(MotionQuality.Balanced, 60f).framesPerSecond)        // Medium
        assertEquals(60, pace(MotionQuality.Balanced, 200f).framesPerSecond)       // 33⅓ RPM
    }

    @Test fun `Battery is 20 frames a second whatever the speed`() {
        assertEquals(20, pace(MotionQuality.Battery, 30f).framesPerSecond)
        assertEquals(20, pace(MotionQuality.Battery, 200f).framesPerSecond)
    }

    @Test fun `Smooth uses every frame the screen shows`() {
        assertEquals(MotionPolicy.EVERY_FRAME, pace(MotionQuality.Smooth).framesPerSecond)
    }

    @Test fun `a long title goes round for good, or a few times when motion is light`() {
        assertEquals(Int.MAX_VALUE, pace(MotionQuality.Balanced).titlePasses)
        assertEquals(Int.MAX_VALUE, pace(MotionQuality.Smooth).titlePasses)
        assertEquals(3, pace(MotionQuality.Battery).titlePasses)
    }

    @Test fun `a warm phone, Battery Saver or a dimmed screen get light motion, and say why`() {
        val warm = pace(MotionQuality.Smooth, phone = PhoneState(heat = Heat.Warm))
        assertEquals(Pace(20, 3, SlowDown.Warm), warm)
        assertEquals(SlowDown.BatterySaver, pace(MotionQuality.Balanced, phone = PhoneState(batterySaver = true)).slowDown)
        assertEquals(20, pace(MotionQuality.Balanced, phone = PhoneState(dimmed = true)).framesPerSecond)
    }

    @Test fun `a hot phone turns the record in slow steps and rests the title`() {
        assertEquals(Pace(10, 0, SlowDown.Hot), pace(MotionQuality.Balanced, phone = PhoneState(heat = Heat.Hot)))
    }

    @Test fun `Battery motion on a warm phone is already light, so there's nothing to explain`() {
        val p = pace(MotionQuality.Battery, phone = PhoneState(heat = Heat.Warm))
        assertEquals(20, p.framesPerSecond)
        assertNull(p.slowDown)
    }
}
