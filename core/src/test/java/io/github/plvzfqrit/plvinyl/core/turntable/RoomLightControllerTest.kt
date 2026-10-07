package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.lighting.LightingMode
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomLightControllerTest {
    private val h = ControllerHarness()
    private val c = h.controller
    private val day = LocalDate.parse("2026-03-20").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `the room follows the sun, checked every 30 s`() {
        h.clock.zone = "Africa/Accra"                              // near 0°, 0°: local time is UTC
        h.clock.wall = day + 12 * 3_600_000L
        c.onStart()
        h.run(200)
        assertEquals(0f, c.ui.value.roomLight.night)
        h.clock.wall = day + 22 * 3_600_000L                       // the evening has come
        h.run(10_000)
        assertEquals(0f, c.ui.value.roomLight.night)               // not checked yet
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

    @Test fun `the light's direction moves with the time of day`() {
        h.clock.zone = "Africa/Accra"
        h.clock.wall = day + 9 * 3_600_000L                        // morning
        c.onStart()
        h.run(200)
        val morning = c.ui.value.roomLight.sunFrom
        h.clock.wall = day + 15 * 3_600_000L                       // afternoon
        h.run(31_000)
        val afternoon = c.ui.value.roomLight.sunFrom
        assertTrue(morning > -90f && afternoon < -90f, "morning $morning, afternoon $afternoon")
    }

    @Test fun `watching a day go by shows night and day in under a minute, then returns to now`() {
        h.clock.zone = "Africa/Accra"
        h.clock.wall = day + 12 * 3_600_000L                       // noon
        h.clock.minute = 12 * 60f
        c.onStart()
        h.run(200)
        val now = c.ui.value.roomLight
        c.updateSettings { it.copy(lighting = LightingMode.Day) }  // whatever the setting, it plays the sun
        h.run(200)
        c.previewDay()
        val nights = ArrayList<Float>()
        repeat(48) { h.run(1_000); nights += c.ui.value.roomLight.night }
        assertTrue(nights.max() > 0.99f && nights.min() < 0.01f, "nights: $nights")
        h.run(1_000)
        assertEquals(null, c.ui.value.previewMinute)
        assertEquals(RoomLight(), c.ui.value.roomLight)            // back to the Day setting
        assertTrue(now.night < 0.01f)
    }

    @Test fun `the preview shows its own clock while it plays`() {
        h.clock.minute = 6 * 60f
        c.onStart()
        h.run(200)
        c.previewDay()
        h.run(12_000)                                              // a quarter of the way: about noon
        val m = c.ui.value.previewMinute
        assertTrue(m != null && kotlin.math.abs(m - 12 * 60f) < 30f, "minute $m")
    }
}
