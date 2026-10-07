package io.github.plvzfqrit.lockgroove.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * Vinyl surface noise, made in real time: no audio files.
 *
 *  - hiss:     soft filtered noise, the "air" of the groove
 *  - crackle:  tiny random clicks (dust), mostly faint, now and then a bigger pop
 *  - locked groove: once per turn of the platter, the soft "thump-tick" of the
 *    needle going round the last circle in the dead wax
 *
 * Pure math over a float buffer; the app plays it through an AudioTrack.
 */
class CrackleGenerator(private val sampleRate: Int = 44_100, random: Random = Random(System.nanoTime())) {
    private val rnd = random

    /** The current loudness, gliding toward the requested level (~30 ms fades). */
    var gain = 0f
        private set

    // hiss
    private var lp = 0f
    private var lpSlow = 0f
    // crackle (dust) and pops
    private var crack = 0f
    private var crackDecay = 0f
    private var pop = 0f
    private var popLp = 0f
    // locked groove thump
    private var thump = 0f
    private var thumpPhase = 0f
    private var untilThump = sampleRate / 3
    private val thumpDecay = exp(-1f / (0.03f * sampleRate))           // ~30 ms

    private fun noise() = rnd.nextFloat() * 2f - 1f

    /**
     * Fills [out] with the next samples.
     * @param level 0 = silent (fades out), up to 1 = well-loved record
     * @param lockedMs if > 0, the needle is circling the locked groove: one thump per turn
     */
    fun render(out: FloatArray, level: Float, lockedMs: Long) {
        for (i in out.indices) {
            gain += (level - gain) * 0.0008f

            // Hiss: noise, softened (low-pass) with the rumble taken out (high-pass).
            val w = noise()
            lp += (w - lp) * 0.25f
            lpSlow += (lp - lpSlow) * 0.01f
            var s = (lp - lpSlow) * 0.012f

            // Dust: ~9 clicks a second, most of them tiny.
            if (rnd.nextFloat() < 9f / sampleRate) {
                val r = rnd.nextFloat()
                crack = 0.015f + r * r * r * 0.11f
                crackDecay = exp(-1f / (4f + rnd.nextFloat() * 10f))
                // Now and then a proper pop.
                if (rnd.nextFloat() < 0.04f) pop = 0.09f + rnd.nextFloat() * 0.12f
            }
            s += crack * noise()
            crack *= crackDecay
            popLp += (pop * noise() - popLp) * 0.08f
            s += popLp
            pop *= 0.985f

            // Locked groove: one soft thump (and tick) per turn of the platter.
            if (lockedMs > 0L) {
                if (--untilThump <= 0) {
                    untilThump = (lockedMs * sampleRate / 1000L).toInt()
                    thump = 0.16f
                    crack = 0.05f
                    crackDecay = exp(-1f / 8f)
                }
            } else {
                untilThump = sampleRate / 3                          // first thump soon after arriving
            }
            if (thump > 1e-4f) {
                s += thump * sin(thumpPhase)
                thumpPhase += (2f * PI.toFloat() * 70f / sampleRate)
                if (thumpPhase > 2f * PI.toFloat()) thumpPhase -= 2f * PI.toFloat()
                thump *= thumpDecay
            }

            out[i] = (s * gain).coerceIn(-1f, 1f)
        }
    }
}
