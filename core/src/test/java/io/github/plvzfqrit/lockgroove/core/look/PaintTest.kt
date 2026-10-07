package io.github.plvzfqrit.lockgroove.core.look

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaintTest {
    @Test fun `every chip gives the title at least 4_5 to 1 contrast`() {
        for (chip in PaintChips.all) {
            val c = Paint.contrast(chip.argb, Paint.textOn(chip.argb))
            assertTrue(c >= 4.5f, "${chip.name}: $c")
        }
    }

    @Test fun `any paint keeps the big title readable (at least 3 to 1)`() {
        var h = 0f
        while (h < 360f) {
            for (s in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                var l = 0f
                while (l <= 1f) {
                    val c = Paint.fromHsl(Hsl(h, s, l))
                    val ratio = Paint.contrast(c, Paint.textOn(c))
                    assertTrue(ratio >= 3f, "h=$h s=$s l=$l: $ratio")
                    l += 0.05f
                }
            }
            h += 15f
        }
    }

    @Test fun `cream is light and navy is dark`() {
        assertTrue(Paint.isLight(0xFFEDE6D6.toInt()))
        assertFalse(Paint.isLight(0xFF23324D.toInt()))
        assertEquals(Paint.DARK_INK, Paint.textOn(0xFFEDE6D6.toInt()))
        assertEquals(Paint.WHITE, Paint.textOn(0xFF23324D.toInt()))
    }

    @Test fun `going through HSL and back keeps the color`() {
        for (chip in PaintChips.all) {
            val back = Paint.fromHsl(Paint.toHsl(chip.argb))
            for (shift in listOf(16, 8, 0)) {
                val a = (chip.argb shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue(abs(a - b) <= 1, "${chip.name}: ${Integer.toHexString(chip.argb)} -> ${Integer.toHexString(back)}")
            }
        }
    }

    @Test fun `shading a paint keeps its hue`() {
        for (chip in PaintChips.all.filter { Paint.toHsl(it.argb).s > 0.2f }) {
            val before = Paint.toHsl(chip.argb)
            val after = Paint.toHsl(Paint.shade(chip.argb, -0.12f))
            assertTrue(Paint.hueDistance(before.h, after.h) < 2f, "${chip.name}: ${before.h} -> ${after.h}")
            assertTrue(after.l < before.l)
        }
    }

    @Test fun `mixing halfway lands in the middle`() {
        assertEquals(Paint.rgb(0.5f, 0.5f, 0.5f), Paint.mix(0xFF000000.toInt(), Paint.WHITE, 0.5f))
    }
}
