package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkipQueueTest {
    private val q = SkipQueue()
    private val a = SongKey.of("A", "Artist")
    private val b = SongKey.of("B", "Artist")
    private val c = SongKey.of("C", "Artist")
    private var now = 0L

    /** Ticks every 50 ms for [ms], collecting the commands sent. */
    private fun run(ms: Long, key: () -> SongKey?, position: () -> Long? = { 0L }, settled: () -> SongKey? = key,
                    needleUp: Boolean = true): List<SkipEffect> {
        val out = ArrayList<SkipEffect>()
        val end = now + ms
        while (now < end) {
            now += 50
            out += q.tick(now, needleUp, key(), position(), settled())
        }
        return out
    }

    private fun List<SkipEffect>.commands() = filterIsInstance<SkipEffect.Send>().map { it.command }

    @Test fun `nothing is sent while the needle is still on the record`() {
        q.flick(+1, now)
        assertEquals(emptyList(), run(1_000, key = { a }, needleUp = false))
        assertEquals(1, q.queued)
    }

    @Test fun `each skip is sent with a silencing pause behind it`() {
        q.flick(+1, now)
        val out = run(300, key = { a })
        assertEquals(listOf(SkipEffect.Send(PlayerCommand.Next), SkipEffect.Silence), out)
    }

    @Test fun `three quick flicks send three nexts, one at a time`() {
        repeat(3) { q.flick(+1, now) }
        var song = a
        val sent = run(3_000, key = { song }).commands()
        assertEquals(listOf(PlayerCommand.Next, PlayerCommand.Next, PlayerCommand.Next), sent)
        assertEquals(0, q.queued)
        song = b
    }

    @Test fun `previous 40 s into a song rewinds to the start first, then goes back`() {
        q.flick(-1, now)
        var pos = 40_000L
        val out = ArrayList<SkipEffect>()
        out += run(50, key = { a }, position = { pos })
        pos = 0L                                                   // the player rewound
        out += run(1_000, key = { a }, position = { pos })
        assertEquals(listOf(PlayerCommand.SeekTo(0L), PlayerCommand.Previous), out.commands())
    }

    @Test fun `previous right at the start goes straight back a song`() {
        q.flick(-1, now)
        assertEquals(listOf(PlayerCommand.Previous), run(300, key = { a }, position = { 500L }).commands())
    }

    @Test fun `the run-out sends no next if the player already moved on by itself`() {
        q.runOut(a, now)
        assertEquals(emptyList(), run(1_000, key = { b }).commands())
        assertEquals(0, q.queued)
    }

    @Test fun `the run-out sends next when the player is still on the ended song`() {
        q.runOut(a, now)
        assertEquals(listOf(PlayerCommand.Next), run(300, key = { a }).commands())
    }

    @Test fun `after fast-forwarding the arm keeps holding until the new record has settled`() {
        q.flick(+1, now)
        run(300, key = { b }, settled = { a })                     // player moved; record hasn't yet
        run(1_500, key = { b }, settled = { a })
        assertTrue(q.browsing)
        run(100, key = { b }, settled = { b })
        assertFalse(q.browsing)
    }

    @Test fun `landing doesn't wait forever for a record that never settles`() {
        q.flick(+1, now)
        run(4_000, key = { c }, settled = { a })
        assertFalse(q.browsing)
    }
}
