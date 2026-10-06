package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.lighting.LightingMode
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomLightControllerTest {
    private val h = ControllerHarness()
    private val c = h.controller

    @Test fun `the room follows the clock, checked every 30 s`() {
        h.clock.minute = 12 * 60f
        c.onStart()
        h.run(200)
        assertEquals(RoomLight(), c.ui.value.roomLight)
        h.clock.minute = 22 * 60f                                  // the evening has come
        h.run(10_000)
        assertEquals(RoomLight(), c.ui.value.roomLight)            // not checked yet
        h.run(21_000)
        assertEquals(1f, c.ui.value.roomLight.night)
    }

    @Test fun `a fixed lighting mode applies at once`() {
        c.onStart()
        h.run(200)
        c.updateSettings { it.copy(lighting = LightingMode.Late) }
        h.run(200)
        assertEquals(RoomLight(night = 1f, late = 1f), c.ui.value.roomLight)
    }
}
