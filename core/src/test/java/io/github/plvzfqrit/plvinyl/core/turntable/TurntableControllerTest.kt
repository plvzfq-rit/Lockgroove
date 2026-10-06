package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import io.github.plvzfqrit.plvinyl.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurntableControllerTest {
    private val h = ControllerHarness()
    private val c = h.controller

    private fun playing(title: String = "Song A", positionMs: Long = 60_000L) =
        snapshot(title = title, positionMs = positionMs, at = h.clock.nowMs())

    @Test fun `opening the app mid-song snaps the needle into the groove without touching the music`() {
        c.onSnapshot(playing())
        c.onStart()
        h.settleDeck()
        assertIs<Move.SnapTo>(h.moves.single())
        assertEquals(playing().key, h.deck.needleOn)
        h.run(3_000)
        assertEquals(emptyList(), h.commands)
    }

    @Test fun `opening the app with nothing playing leaves the arm at rest`() {
        c.onStart()
        h.run(2_000)
        assertNull(h.deck.disc)
        assertTrue(h.deck.armParked && h.deck.armDown)
        assertEquals(emptyList(), h.commands)
    }

    @Test fun `coming back after the song changed in the background snaps to the new record`() {
        c.onSnapshot(playing("Song A"))
        c.onStart()
        h.run(1_000)
        c.onStop()
        c.onSnapshot(playing("Song B", positionMs = 10_000))       // changed while away
        h.moves.clear()
        c.onStart()
        h.run(2_000)
        assertTrue(h.moves.none { it is Move.RemoveRecord || it is Move.PlaceRecord }, "replayed a swap: ${h.moves}")
        assertEquals(playing("Song B").key, h.deck.disc?.key)
        assertEquals(playing("Song B").key, h.deck.needleOn)
        assertEquals(emptyList(), h.commands)
    }

    @Test fun `music that starts while the arm rests is paused and the needle goes to it`() {
        c.onStart()
        h.run(2_000)
        c.onSnapshot(playing())
        h.run(100)
        assertEquals(PlayerCommand.Pause, h.commands.first())
        h.run(3_000)                                               // record settles, arm swings over
        assertEquals(playing().key, h.deck.needleOn)
        assertEquals(PlayerCommand.Play, h.commands.last())
    }

    @Test fun `dropping the arm on the record seeks there and plays`() {
        c.onSnapshot(playing())
        c.onStart()
        h.run(500)
        val half = RecordSize.Single.playingAngle(0.5f)          // a 3-minute song is a 7" single
        c.onIntent(UserIntent.HoldArm(half))
        h.settleDeck()
        assertNull(h.deck.needleOn)                                // lifted by hand
        c.onIntent(UserIntent.LetGoOfArm)
        h.settleDeck()
        val seek = h.commands.filterIsInstance<PlayerCommand.SeekTo>().single()
        assertEquals(90_000.0, seek.ms.toDouble(), 100.0)
        assertEquals(playing().key, h.deck.needleOn)
    }

    @Test fun `putting the arm back on its rest stops`() {
        c.onSnapshot(playing())
        c.onStart()
        h.run(500)
        c.onIntent(UserIntent.HoldArm(1f))
        h.settleDeck()
        c.onIntent(UserIntent.LetGoOfArm)
        h.run(500)
        assertTrue(h.deck.armParked && h.deck.armDown)
        assertEquals(false, c.ui.value.playing)
    }

    @Test fun `flicking lifts the needle once, skips silently, and plays the song you land on`() {
        c.onSnapshot(playing("Song A"))
        c.onStart()
        h.run(500)
        h.commands.clear()
        c.onIntent(UserIntent.Flick(+1))
        c.onIntent(UserIntent.Flick(+1))
        h.run(300)
        assertTrue(h.deck.armParked, "arm not parked while flicking")
        c.onSnapshot(playing("Song B", positionMs = 0))
        h.run(300)
        c.onSnapshot(playing("Song C", positionMs = 0))
        h.run(4_000)
        val nexts = h.commands.count { it == PlayerCommand.Next }
        assertEquals(2, nexts)
        assertEquals(playing("Song C").key, h.deck.disc?.key)
        assertEquals(playing("Song C").key, h.deck.needleOn)
        assertEquals(PlayerCommand.Play, h.commands.last())
    }
}
