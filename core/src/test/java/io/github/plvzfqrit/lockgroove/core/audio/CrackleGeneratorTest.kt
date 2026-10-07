package io.github.plvzfqrit.lockgroove.core.audio

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrackleGeneratorTest {
    private val rate = 8_000
    private val gen = CrackleGenerator(rate, Random(1))

    private fun render(seconds: Float, level: Float, lockedMs: Long = 0L): FloatArray {
        val out = FloatArray((rate * seconds).toInt())
        gen.render(out, level, lockedMs)
        return out
    }

    @Test fun `level 0 is silence`() {
        assertTrue(render(1f, level = 0f).all { it == 0f })
    }

    @Test fun `the noise never clips`() {
        assertTrue(render(5f, level = 1f).all { it in -1f..1f })
    }

    @Test fun `it fades in instead of starting with a click`() {
        val out = render(0.5f, level = 1f)
        val first = out.take(10).maxOf { abs(it) }
        val later = out.takeLast(rate / 10).maxOf { abs(it) }
        assertTrue(first < later)
    }

    @Test fun `the locked groove thumps once per turn`() {
        render(1f, level = 1f)                                     // settle the fade
        val out = render(4f, level = 1f, lockedMs = 1000L)
        // Thumps are much louder than the hiss: count the loud bursts, one per second.
        var bursts = 0
        var quietFor = rate
        for (v in out) {
            if (abs(v) > 0.08f && quietFor > rate / 2) bursts++
            quietFor = if (abs(v) > 0.08f) 0 else quietFor + 1
        }
        assertEquals(4, bursts)
    }
}
