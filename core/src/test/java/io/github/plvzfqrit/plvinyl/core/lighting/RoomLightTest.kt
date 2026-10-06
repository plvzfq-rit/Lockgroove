package io.github.plvzfqrit.plvinyl.core.lighting

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomLightTest {
    @Test fun `around 7 pm the night rises smoothly from 0 to 1 and never steps`() {
        assertEquals(0f, roomLightAt(1140f - 15f).night, 1e-4f)
        assertEquals(1f, roomLightAt(1140f + 15f).night, 1e-4f)
        var prev = roomLightAt(1125f).night
        var m = 1125f
        while (m <= 1155f) {
            val now = roomLightAt(m).night
            assertTrue(now >= prev, "night went down at minute $m")
            assertTrue(abs(now - prev) < 0.02f, "night jumped at minute $m")
            prev = now
            m += 0.25f
        }
    }

    @Test fun `late night starts after midnight, not before`() {
        assertEquals(0f, roomLightAt(30f).late, 1e-4f)          // 00:30
        assertEquals(1f, roomLightAt(120f).late, 1e-4f)         // 02:00
    }

    @Test fun `by 7 30 am it is plain daylight`() {
        assertEquals(RoomLight(), roomLightAt(7 * 60f + 30f))
    }

    @Test fun `golden hour peaks at 6 pm`() {
        val l = roomLightAt(18 * 60f)
        assertEquals(1f, l.golden, 1e-4f)
        assertEquals(0f, l.night, 1e-4f)
    }

    @Test fun `fixed modes ignore the clock`() {
        assertEquals(RoomLight(night = 1f), LightingMode.Night.lightAt(12 * 60f))
        assertEquals(RoomLight(), LightingMode.Day.lightAt(23 * 60f))
        assertEquals(roomLightAt(23 * 60f), LightingMode.Clock.lightAt(23 * 60f))
    }
}
