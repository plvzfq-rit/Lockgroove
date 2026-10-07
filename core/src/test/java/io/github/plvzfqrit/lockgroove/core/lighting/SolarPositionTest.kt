package io.github.plvzfqrit.lockgroove.core.lighting

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarPositionTest {
    private val londonLat = 51.5074
    private val londonLon = -0.1278

    private fun midnightUtc(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Sunrise and sunset that day, in minutes after midnight, found by stepping the sun.
     * [utcOffsetHours]: step through that local day instead of the UTC one.
     */
    private fun riseAndSet(date: String, lat: Double, lon: Double, utcOffsetHours: Int = 0): Pair<Double, Double> {
        val start = midnightUtc(date) - utcOffsetHours * 3_600_000L
        var rise = Double.NaN
        var set = Double.NaN
        var prev = SolarPosition.at(start, lat, lon).elevation
        for (minute in 1..1440) {
            val now = SolarPosition.at(start + minute * 60_000L, lat, lon).elevation
            if (prev < SolarPosition.HORIZON_DEG && now >= SolarPosition.HORIZON_DEG) rise = minute.toDouble()
            if (prev >= SolarPosition.HORIZON_DEG && now < SolarPosition.HORIZON_DEG) set = minute.toDouble()
            prev = now
        }
        return rise to set
    }

    private fun assertMinute(expected: String, actual: Double) {
        val (h, m) = expected.split(":").map { it.toInt() }
        assertTrue(abs(h * 60 + m - actual) <= 4, "expected $expected, got ${actual.toInt() / 60}:${actual.toInt() % 60}")
    }

    @Test fun `London midsummer sunrise and sunset match the published times`() {
        val (rise, set) = riseAndSet("2026-06-21", londonLat, londonLon)
        assertMinute("03:43", rise)                                // 04:43 BST
        assertMinute("20:21", set)                                 // 21:21 BST
    }

    @Test fun `London midwinter sunrise and sunset match the published times`() {
        val (rise, set) = riseAndSet("2026-12-21", londonLat, londonLon)
        assertMinute("08:04", rise)
        assertMinute("15:53", set)
    }

    @Test fun `at the equinox the day is about 12 hours long anywhere`() {
        for (lat in listOf(0.0, 25.0, 45.0)) {
            val (rise, set) = riseAndSet("2026-03-20", lat, 0.0)
            val length = set - rise
            assertTrue(length in 720.0..735.0, "lat $lat: $length min")
        }
    }

    @Test fun `sunrise and sunset sit evenly around solar noon`() {
        val start = midnightUtc("2026-10-07") - 8 * 3_600_000L        // Manila's local day (UTC+8)
        val (rise, set) = riseAndSet("2026-10-07", 14.59, 120.98, utcOffsetHours = 8)
        val noon = (rise + set) / 2
        val atNoon = SolarPosition.at(start + (noon * 60_000).toLong(), 14.59, 120.98)
        assertTrue(abs(atNoon.hourAngle) < 1.0, "hour angle at noon: ${atNoon.hourAngle}")
    }

    @Test fun `the noon sun stands 90 degrees minus the gap between latitude and declination`() {
        val start = midnightUtc("2026-06-21")
        val noonSun = (0 until 1440).map { SolarPosition.at(start + it * 60_000L, 40.0, 0.0) }.maxBy { it.elevation }
        assertEquals(90 - abs(40.0 - noonSun.declination), noonSun.elevation, 0.3)
    }

    @Test fun `the day's half-length matches the stepped sunrise and sunset`() {
        val (rise, set) = riseAndSet("2026-06-21", londonLat, londonLon)
        val decl = SolarPosition.at(midnightUtc("2026-06-21") + 12 * 3_600_000L, londonLat, londonLon).declination
        val halfDay = SolarPosition.sunsetHourAngle(londonLat, decl) * 4               // degrees → minutes
        assertEquals((set - rise) / 2, halfDay, 3.0)
    }

    @Test fun `far north the sun never sets in June and never rises in December`() {
        val june = SolarPosition.at(midnightUtc("2026-06-21"), 70.0, 0.0).declination
        val december = SolarPosition.at(midnightUtc("2026-12-21"), 70.0, 0.0).declination
        assertEquals(180.0, SolarPosition.sunsetHourAngle(70.0, june))
        assertEquals(0.0, SolarPosition.sunsetHourAngle(70.0, december))
    }
}
