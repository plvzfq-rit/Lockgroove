package io.github.plvzfqrit.plvinyl.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MotionPolicyTest {
    private fun pace(motion: MotionQuality, phone: PhoneState = PhoneState()) = MotionPolicy.pace(motion, phone)

    @Test fun `Balanced and Smooth turn the record at every frame the screen shows`() {
        assertEquals(MotionPolicy.EVERY_FRAME, pace(MotionQuality.Balanced).framesPerSecond)
        assertEquals(MotionPolicy.EVERY_FRAME, pace(MotionQuality.Smooth).framesPerSecond)
    }

    @Test fun `Battery is 20 frames a second`() {
        assertEquals(20, pace(MotionQuality.Battery).framesPerSecond)
    }

    @Test fun `a long title goes round for good, or a few times when motion is light`() {
        assertEquals(Int.MAX_VALUE, pace(MotionQuality.Balanced).titlePasses)
        assertEquals(Int.MAX_VALUE, pace(MotionQuality.Smooth).titlePasses)
        assertEquals(3, pace(MotionQuality.Battery).titlePasses)
    }

    @Test fun `a warm phone or a dimmed screen get light motion, and say why`() {
        val warm = pace(MotionQuality.Smooth, phone = PhoneState(heat = Heat.Warm))
        assertEquals(Pace(20, 3, SlowDown.Warm), warm)
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
