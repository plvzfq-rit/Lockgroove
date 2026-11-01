package io.github.plvzfqrit.lockgroove.core.turntable

import io.github.plvzfqrit.lockgroove.core.FakePlayer
import io.github.plvzfqrit.lockgroove.core.model.SongKey
import io.github.plvzfqrit.lockgroove.core.playback.PlayerCommand
import io.github.plvzfqrit.lockgroove.core.settings.SkipStyle
import io.github.plvzfqrit.lockgroove.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapSkipTest {
    private val h = ControllerHarness()
    private val c = h.controller

    private fun song(title: String = "Song A", positionMs: Long = 60_000L, sounding: Boolean = true) =
        snapshot(title = title, positionMs = positionMs, at = h.clock.nowMs(), sounding = sounding)

    /** A song playing with the needle in the groove, and the Snap style on. */
    private fun playingInSnapStyle(positionMs: Long = 60_000L) {
        c.updateSettings { it.copy(skipStyle = SkipStyle.Snap) }
        c.onSnapshot(song(positionMs = positionMs))
        c.onStart()
        h.run(500)
        h.commands.clear()
    }

    @Test fun `next in the snap style sends next alone, and the deck snaps to the new song`() {
        playingInSnapStyle()
        c.onIntent(UserIntent.Flick(+1))
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Next), h.commands)
        c.onSnapshot(song("Song B", positionMs = 0))
        h.run(1_000)
        assertEquals(song("Song B").key, h.deck.disc?.key)
        assertEquals(song("Song B").key, h.deck.needleOn)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Next), h.commands)      // no pause, no play: the music never stopped
    }

    @Test fun `previous well into a song rewinds first, then goes back`() {
        playingInSnapStyle(positionMs = 60_000L)
        c.onIntent(UserIntent.Flick(-1))
        assertEquals(listOf<PlayerCommand>(PlayerCommand.SeekTo(0L)), h.commands)
        h.run(300)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.SeekTo(0L), PlayerCommand.Previous), h.commands)
    }

    @Test fun `previous at the start of a song goes straight back`() {
        playingInSnapStyle(positionMs = 500L)
        c.onIntent(UserIntent.Flick(-1))
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Previous), h.commands)
    }

    @Test fun `a stopped deck keeps the ritual even in the snap style`() {
        c.updateSettings { it.copy(skipStyle = SkipStyle.Snap) }
        c.onSnapshot(song(sounding = false))
        c.onStart()
        h.run(500)
        h.commands.clear()
        c.onIntent(UserIntent.Flick(+1))
        assertEquals(emptyList(), h.commands)                    // queued for the ritual, not sent at once
        assertTrue(c.ui.value.browsing)
    }

    @Test fun `the ritual style still queues the skip instead of sending it at once`() {
        c.updateSettings { it.copy(skipStyle = SkipStyle.Ritual) }
        c.onSnapshot(song())
        c.onStart()
        h.run(500)
        h.commands.clear()
        c.onIntent(UserIntent.Flick(+1))
        assertEquals(emptyList(), h.commands)
        assertTrue(c.ui.value.browsing)
    }

    @Test fun `a snap skip near the end of a song isn't learned as the player's crossfade`() {
        playingInSnapStyle(positionMs = 175_000L)
        c.onIntent(UserIntent.Flick(+1))
        c.onSnapshot(song("Song B", positionMs = 0))
        h.run(500)
        assertEquals(emptyMap(), c.settings.value.learnedCrossfades)
    }

    @Test fun `with a slow player the deck snaps once to the new song, and the music is never touched`() {
        val p = FakePlayer(h.clock, listOf("A", "B", "C"), index = 1, latencyMs = 900, startAtMs = 60_000)
        h.player = p
        c.onSnapshot(p.snapshot())
        c.onStart()
        h.run(500)
        h.moves.clear()
        h.commands.clear()
        c.onIntent(UserIntent.Flick(+1))
        h.run(4_000)
        assertEquals("C", p.title)
        assertEquals(SongKey.of("C", "Artist"), h.deck.disc?.key)
        assertEquals(SongKey.of("C", "Artist"), h.deck.needleOn)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Next), h.commands)
    }
}
