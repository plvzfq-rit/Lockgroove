package io.github.plvzfqrit.lockgroove.core.lighting

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The light at a place on the equator at 0° longitude, where local time is UTC. */
class RoomLightTest {
    private val place = Place(0.0, 0.0)
    private val day = LocalDate.parse("2026-03-20").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun at(minute: Float) = sunLight(day + (minute * 60_000).toLong(), minute, place)

    /** Sunrise and sunset that day, in minutes, found by stepping the sun. */
    private val riseSet: Pair<Float, Float> by lazy {
        var rise = 0f; var set = 0f
        var prev = SolarPosition.at(day, 0.0, 0.0).elevation
        for (m in 1..1440) {
            val now = SolarPosition.at(day + m * 60_000L, 0.0, 0.0).elevation
            if (prev < SolarPosition.HORIZON_DEG && now >= SolarPosition.HORIZON_DEG) rise = m.toFloat()
            if (prev >= SolarPosition.HORIZON_DEG && now < SolarPosition.HORIZON_DEG) set = m.toFloat()
            prev = now
        }
        rise to set
    }
    private val sunrise get() = riseSet.first
    private val sunset get() = riseSet.second

    @Test fun `night rises smoothly around sunset and never steps`() {
        assertEquals(0f, at(sunset - 60).night, 1e-3f)
        assertEquals(1f, at(sunset + 90).night, 1e-3f)
        var prev = at(sunset - 60).night
        var m = sunset - 60
        while (m <= sunset + 90) {
            val now = at(m).night
            assertTrue(now >= prev - 1e-4f, "night went down at minute $m")
            assertTrue(abs(now - prev) < 0.08f, "night jumped at minute $m")
            prev = now
            m += 1f
        }
    }

    @Test fun `golden hour peaks just before sunset and is gone at noon`() {
        assertTrue(at(sunset - 15).golden > 0.8f, "golden ${at(sunset - 15).golden}")
        assertEquals(0f, at(12 * 60f).golden)
    }

    @Test fun `dawn glows just after sunrise, softer than golden hour and only in the morning`() {
        val morning = (0..60).maxOf { at(sunrise + it).dawn }
        assertTrue(morning > 0.5f, "dawn $morning")
        assertEquals(0f, at(sunset - 15).dawn)
        assertEquals(0f, at(sunrise + 15).golden)
    }

    @Test fun `the light comes from the right at sunrise, the top at noon, the left at sunset`() {
        assertEquals(0f, at(sunrise).sunFrom, 6f)
        assertEquals(-90f, at((sunrise + sunset) / 2).sunFrom, 3f)
        assertEquals(-180f, at(sunset).sunFrom, 6f)
    }

    @Test fun `shadows are short at noon and long near sunset`() {
        assertTrue(at((sunrise + sunset) / 2).sunHeight > 0.9f)
        assertTrue(at(sunset - 20).sunHeight < 0.1f)
    }

    @Test fun `late night comes after midnight and ends at dawn`() {
        assertEquals(0f, at(30f).late, 1e-3f)                     // 00:30
        assertEquals(1f, at(120f).late, 1e-3f)                    // 02:00
        assertEquals(0f, at(sunrise + 30).late, 1e-3f)
    }

    @Test fun `the fixed modes ignore the sun and keep their looks`() {
        val noon = day + 12 * 3_600_000L
        assertEquals(RoomLight(night = 1f), LightingMode.Night.lightAt(noon, 720f, place))
        assertEquals(RoomLight(), LightingMode.Day.lightAt(day + 23 * 3_600_000L, 1380f, place))
        assertEquals(180f, LightingMode.Golden.lightAt(noon, 720f, place).sunFrom)
        val sunrise = LightingMode.Sunrise.lightAt(day + 22 * 3_600_000L, 1320f, place)
        assertEquals(1f, sunrise.dawn)
        assertEquals(0f, sunrise.sunFrom)                          // low sun from the right
        assertEquals(0f, sunrise.night)
        assertEquals(sunLight(noon, 720f, place), LightingMode.Clock.lightAt(noon, 720f, place))
    }
}
