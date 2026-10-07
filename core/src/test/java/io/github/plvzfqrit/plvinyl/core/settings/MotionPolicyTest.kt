package io.github.plvzfqrit.plvinyl.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class MotionPolicyTest {
    private fun fps(motion: MotionQuality, spin: Float) = MotionPolicy.framesPerSecond(motion, spin)

    @Test fun `Balanced turns slow records at 30 frames a second and 33⅓ at 60`() {
        assertEquals(30, fps(MotionQuality.Balanced, 30f))           // Slow
        assertEquals(30, fps(MotionQuality.Balanced, 60f))           // Medium
        assertEquals(60, fps(MotionQuality.Balanced, 200f))          // 33⅓ RPM
    }

    @Test fun `Battery is 20 frames a second whatever the speed`() {
        assertEquals(20, fps(MotionQuality.Battery, 30f))
        assertEquals(20, fps(MotionQuality.Battery, 200f))
    }

    @Test fun `Smooth uses every frame the screen shows`() {
        assertEquals(MotionPolicy.EVERY_FRAME, fps(MotionQuality.Smooth, 30f))
    }

    @Test fun `a long title goes round for good, or a few times when motion is light`() {
        assertEquals(Int.MAX_VALUE, MotionPolicy.titlePasses(30))
        assertEquals(Int.MAX_VALUE, MotionPolicy.titlePasses(MotionPolicy.EVERY_FRAME))
        assertEquals(3, MotionPolicy.titlePasses(20))
    }
}
