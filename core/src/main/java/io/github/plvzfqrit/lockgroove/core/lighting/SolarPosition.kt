package io.github.plvzfqrit.lockgroove.core.lighting

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is, seen from a place on Earth. */
data class Sun(
    /** Height above the horizon, degrees (negative = below it). */
    val elevation: Double,
    /** Hour angle, degrees: negative before solar noon, positive after, 0 at noon. */
    val hourAngle: Double,
    /** The sun's declination that day, degrees. */
    val declination: Double,
)

/**
 * The sun's position from a time and a place, with the NOAA solar formulas (the ones
 * behind NOAA's solar calculator): accurate to well under a minute for sunrise and
 * sunset, far more than mood lighting needs. No network, no location: just math.
 */
object SolarPosition {
    /** The sun's elevation at sunrise and sunset: its disc's top edge, with refraction. */
    const val HORIZON_DEG = -0.833

    fun at(utcMs: Long, latDeg: Double, lonDeg: Double): Sun {
        val jd = utcMs / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0                                  // Julian century
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)   // mean longitude
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)               // mean anomaly
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)          // orbit eccentricity
        val mr = Math.toRadians(m)
        val center = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparentLong = l0 + center - 0.00569 - 0.00478 * sin(omega)
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(Math.toRadians(apparentLong)))

        // Equation of time (minutes): how far the sundial runs ahead of the clock today.
        val y = tan(obliquity / 2).pow(2)
        val l0r = Math.toRadians(l0)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
                    0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr)
        )
        val utcMinutes = (utcMs / 60_000.0).mod(1440.0)
        val trueSolarMinutes = (utcMinutes + eqTime + 4 * lonDeg).mod(1440.0)
        val hourAngle = trueSolarMinutes / 4 - 180

        val lat = Math.toRadians(latDeg)
        val cosZenith = (sin(lat) * sin(declination) +
                cos(lat) * cos(declination) * cos(Math.toRadians(hourAngle))).coerceIn(-1.0, 1.0)
        return Sun(
            elevation = 90 - Math.toDegrees(acos(cosZenith)),
            hourAngle = hourAngle,
            declination = Math.toDegrees(declination),
        )
    }

    /**
     * Half the day's length as an hour angle (degrees): the sun sets this far past solar
     * noon. 180 = it never sets (polar day), 0 = it never rises (polar night).
     */
    fun sunsetHourAngle(latDeg: Double, declinationDeg: Double): Double {
        val lat = Math.toRadians(latDeg)
        val d = Math.toRadians(declinationDeg)
        val x = cos(Math.toRadians(90 - HORIZON_DEG)) / (cos(lat) * cos(d)) - tan(lat) * tan(d)
        return when {
            x <= -1.0 -> 180.0
            x >= 1.0 -> 0.0
            else -> Math.toDegrees(acos(x))
        }
    }
}
