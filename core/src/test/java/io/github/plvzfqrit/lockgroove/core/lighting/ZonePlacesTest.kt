package io.github.plvzfqrit.lockgroove.core.lighting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZonePlacesTest {
    @Test fun `Manila is where Manila is`() {
        val p = ZonePlaces.forZone("Asia/Manila", 8 * 3_600_000)
        assertEquals(14.59, p.latitude, 0.05)
        assertEquals(120.97, p.longitude, 0.05)
    }

    @Test fun `west and south come out negative`() {
        val la = ZonePlaces.forZone("America/Los_Angeles", -8 * 3_600_000)
        assertEquals(34.05, la.latitude, 0.05)
        assertEquals(-118.24, la.longitude, 0.05)
        val sydney = ZonePlaces.forZone("Australia/Sydney", 10 * 3_600_000)
        assertTrue(sydney.latitude < -33.0)
    }

    @Test fun `every line of the table reads`() {
        assertTrue(ZonePlaces.places.size >= 400, "${ZonePlaces.places.size} zones")
        for ((zone, p) in ZonePlaces.places) {
            assertTrue(p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0, "$zone: $p")
        }
    }

    @Test fun `an unknown zone gets its longitude from the UTC offset`() {
        val p = ZonePlaces.forZone("Etc/GMT-3", 3 * 3_600_000)
        assertEquals(45.0, p.longitude, 1e-9)
        assertEquals(ZonePlaces.FALLBACK_LATITUDE, p.latitude)
    }

    @Test fun `coordinates with seconds read exactly`() {
        val p = ZonePlaces.parseCoordinates("+143512+1205804")
        assertEquals(14 + 35 / 60.0 + 12 / 3600.0, p.latitude, 1e-9)
        assertEquals(120 + 58 / 60.0 + 4 / 3600.0, p.longitude, 1e-9)
    }
}
