package io.github.plvzfqrit.plvinyl.core.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmGeometryTest {
    private val pivotX = 500f
    private val pivotY = 300f
    private val unit = 2f

    /** A point on the arm tube (reference units in the arm's own frame), turned by [deg]. */
    private fun onTube(deg: Float): Pair<Float, Float> {
        val ax = 39f; val ay = 120f
        val r = Math.toRadians(deg.toDouble())
        val x = ax * cos(r) - ay * sin(r)
        val y = ax * sin(r) + ay * cos(r)
        return (pivotX + x.toFloat() * unit) to (pivotY + y.toFloat() * unit)
    }

    @Test fun `a touch on the tube grabs the arm at rest`() {
        val (x, y) = onTube(0f)
        assertTrue(ArmGeometry.touchesArm(x, y, pivotX, pivotY, unit, 0f, slopPx = 0f))
    }

    @Test fun `a touch on the tube grabs the arm when it is swung over the record`() {
        val (x, y) = onTube(25f)
        assertTrue(ArmGeometry.touchesArm(x, y, pivotX, pivotY, unit, 25f, slopPx = 0f))
    }

    @Test fun `the spot the arm left behind no longer grabs it`() {
        val (x, y) = onTube(0f)
        assertFalse(ArmGeometry.touchesArm(x, y, pivotX, pivotY, unit, 30f, slopPx = 0f))
    }

    @Test fun `straight below the pivot is 90 degrees`() {
        assertEquals(90f, ArmGeometry.pointerDeg(pivotX, pivotY + 100f, pivotX, pivotY), 1e-3f)
    }
}
