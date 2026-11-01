package io.github.plvzfqrit.lockgroove.core.turntable

import io.github.plvzfqrit.lockgroove.core.FakePlayer
import io.github.plvzfqrit.lockgroove.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Flicking with a player that takes its time to answer, like Spotify on a real phone. */
class SlowPlayerTest {
    private val h = ControllerHarness()
    private val c = h.controller

    private fun key(title: String) = SongKey.of(title, "Artist")

    /** Song B playing [atMs] into it, with a player that answers after [latencyMs]. */
    private fun playingB(atMs: Long, latencyMs: Long): FakePlayer {
        val p = FakePlayer(h.clock, listOf("A", "B", "C"), index = 1, latencyMs = latencyMs, startAtMs = atMs)
        h.player = p
        c.onSnapshot(p.snapshot())
        c.onStart()
        h.run(500)
        assertEquals(key("B"), h.deck.needleOn)
        h.moves.clear()
        h.commands.clear()
        return p
    }

    /** The arm parks once, the record changes, and only then does the needle go down. */
    private fun assertCleanSwapTo(title: String) {
        val place = h.moves.indexOfFirst { it is Move.PlaceRecord && it.disc.key == key(title) }
        assertTrue(place >= 0, "never put $title on: ${h.moves}")
        val before = h.moves.take(place)
        assertTrue(before.none { it is Move.DropNeedle }, "needle dropped on the old record: ${h.moves}")
        assertEquals(1, before.count { it is Move.Swing }, "the arm went back and forth: ${h.moves}")
        assertEquals(key(title), h.deck.needleOn)
    }

    @Test fun `previous mid-song with a slow player swaps the record once, with no detour`() {
        val p = playingB(atMs = 60_000, latencyMs = 900)
        c.onIntent(UserIntent.Flick(-1))
        h.run(8_000)
        assertEquals("A", p.title)
        assertCleanSwapTo("A")
    }

    @Test fun `next with a slow player swaps the record once, with no detour`() {
        val p = playingB(atMs = 60_000, latencyMs = 900)
        c.onIntent(UserIntent.Flick(+1))
        h.run(8_000)
        assertEquals("C", p.title)
        assertCleanSwapTo("C")
    }

    @Test fun `previous at the start of the playlist lands back on the same song`() {
        val p = FakePlayer(h.clock, listOf("A", "B"), index = 0, latencyMs = 400, startAtMs = 60_000)
        h.player = p
        c.onSnapshot(p.snapshot())
        c.onStart()
        h.run(500)
        c.onIntent(UserIntent.Flick(-1))
        h.run(8_000)
        assertEquals(key("A"), h.deck.needleOn)
        assertTrue(h.deck.disc?.key == key("A"))
    }
}
