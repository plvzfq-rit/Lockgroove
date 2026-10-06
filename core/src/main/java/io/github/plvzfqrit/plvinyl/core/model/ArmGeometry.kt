package io.github.plvzfqrit.plvinyl.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the tonearm sits and how far it turns, in the tonearm's REFERENCE UNITS
 * (1 unit = 1 px of the 1000-px-wide product photo; see Tonearm.kt). The arm is
 * always sized and placed from the platter: scale = platterRadiusPx / PLATTER_RADIUS.
 */
object ArmGeometry {
    /** Outer radius of the platter (silver rim). */
    const val PLATTER_RADIUS = 205f

    /** Tonearm pivot (center of the round base), measured from the spindle. */
    const val PIVOT_FROM_SPINDLE_X = 182f
    const val PIVOT_FROM_SPINDLE_Y = -169f

    /** Arm parked in its rest clip. */
    const val REST_DEG = 0f

    /** Angle (degrees) of the point (x, y) seen from the pivot. */
    fun pointerDeg(x: Float, y: Float, pivotX: Float, pivotY: Float): Float =
        Math.toDegrees(atan2((y - pivotY).toDouble(), (x - pivotX).toDouble())).toFloat()

    /**
     * Is (x, y) on the arm's tube or headshell? Works in the arm's own frame: undo the
     * rotation, then check a generous box around the tube and headshell.
     * @param unit px per reference unit
     * @param slopPx extra touch margin, in px
     */
    fun touchesArm(
        x: Float, y: Float, pivotX: Float, pivotY: Float, unit: Float, angleDeg: Float, slopPx: Float,
    ): Boolean {
        val vx = (x - pivotX) / unit
        val vy = (y - pivotY) / unit
        val rad = Math.toRadians(angleDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val ax = vx * c + vy * s                    // rotate by -angle
        val ay = -vx * s + vy * c
        val slop = slopPx / unit
        return ax in (15f - slop)..(65f + slop) && ay in (40f - slop)..(290f + slop)
    }
}
