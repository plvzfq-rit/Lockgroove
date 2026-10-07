package io.github.plvzfqrit.plvinyl.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScaleTest {
    @Test fun `spin speeds are spread out along the track, not crammed at the slow end`() {
        val s = Scales.spin
        assertEquals(0.27f, s.fraction(30f), 0.01f)                // Slow
        assertEquals(0.54f, s.fraction(60f), 0.01f)                // Medium
        assertEquals(1f, s.fraction(200f), 0.001f)                 // 33⅓ RPM, at the end
    }

    @Test fun `the thumb clicks into a stop when it comes near`() {
        val s = Scales.surfaceNoise
        assertEquals(0.55f, s.valueAt(0.57f))
        assertEquals(0.55f, s.valueAt(0.53f))
        assertEquals(0.65f, s.valueAt(0.65f), 0.001f)              // out of reach: its own value
    }

    @Test fun `values between the stops round to the scale's step`() {
        val s = Scales.endMargin
        assertEquals(1_300f, s.valueAt(s.fraction(1_337f)))
        assertEquals(45f, Scales.spin.valueAt(Scales.spin.fraction(45.3f)))
    }

    @Test fun `both ends can be reached`() {
        for (s in listOf(Scales.spin, Scales.endMargin, Scales.surfaceNoise)) {
            assertEquals(s.min, s.valueAt(0f), 0.001f)
            assertEquals(s.max, s.valueAt(1f), 0.001f)
        }
    }

    @Test fun `a value off the scale sits at its end`() {
        assertEquals(0f, Scales.endMargin.fraction(0f))
        assertEquals(1f, Scales.spin.fraction(1_000f))
    }

    @Test fun `a value reads as its stop's name, or as the value`() {
        assertEquals("Slow", Scales.spin.label(30f))
        assertEquals("33⅓ RPM", Scales.spin.label(200f))
        assertEquals("20 RPM", Scales.spin.label(120f))
        assertEquals("Off", Scales.surfaceNoise.label(0f))
        assertEquals("70%", Scales.surfaceNoise.label(0.7f))
        assertEquals("2 s", Scales.endMargin.label(2_000f))
        assertNull(Scales.endMargin.stopAt(1_300f))
    }

    @Test fun `the defaults sit on the old default choices`() {
        val d = Settings()
        assertEquals("Slow", Scales.spin.stopAt(d.spin)?.label)
        assertEquals("1 s", Scales.endMargin.stopAt(d.endMarginMs.toFloat())?.label)
        assertEquals("Off", Scales.surfaceNoise.stopAt(d.surfaceNoise)?.label)
    }
}
