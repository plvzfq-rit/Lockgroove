package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import io.github.plvzfqrit.plvinyl.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ▶■, ▲▼ and ↻1 keys, through the controller. */
class FrontPanelKeysTest {
    private val h = ControllerHarness()
    private val c = h.controller

    private fun startPlaying() {
        c.onSnapshot(snapshot(positionMs = 60_000, at = h.clock.nowMs()))
        c.onStart()
        h.run(500)
        assertTrue(h.deck.needleOn != null)
        h.commands.clear()
        h.moves.clear()
    }

    @Test fun `stop while playing sends the arm home`() {
        startPlaying()
        c.onIntent(UserIntent.StartStop)
        h.run(500)
        assertTrue(h.deck.armParked && h.deck.armDown)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Pause), h.commands)
        assertFalse(c.ui.value.playing)
    }

    @Test fun `cue while playing lifts the arm and leaves it hovering in place`() {
        startPlaying()
        val angle = h.deck.armDeg
        c.onIntent(UserIntent.Cue)
        h.run(500)
        assertTrue(h.deck.armUp)
        assertNull(h.deck.needleOn)
        assertEquals(angle, h.deck.armDeg, 0.2f)
    }

    @Test fun `start while hovering plays from the same spot`() {
        startPlaying()
        val angle = h.deck.armDeg
        c.onIntent(UserIntent.Cue)
        h.run(500)
        c.onIntent(UserIntent.StartStop)
        h.run(500)
        assertEquals(angle, h.deck.armDeg, 0.2f)
        assertTrue(h.deck.needleOn != null)
        assertEquals(PlayerCommand.Play, h.commands.last())
    }

    @Test fun `cue with the arm at rest and the record stopped does nothing`() {
        c.onSnapshot(snapshot(sounding = false, at = h.clock.nowMs()))
        c.onStart()
        h.run(2_000)
        h.moves.clear()
        c.onIntent(UserIntent.Cue)
        h.run(1_000)
        assertEquals(emptyList<PlayerCommand>(), h.commands)
        assertEquals(emptyList<Move>(), h.moves)
    }

    @Test fun `cue while hovering lowers the needle and plays`() {
        startPlaying()
        c.onIntent(UserIntent.Cue)
        h.run(500)
        c.onIntent(UserIntent.Cue)
        h.run(500)
        assertTrue(h.deck.needleOn != null)
        assertEquals(PlayerCommand.Play, h.commands.last())
    }

    @Test fun `repeat one lights the key`() {
        c.onIntent(UserIntent.ToggleRepeat)
        assertTrue(c.ui.value.repeatOne)
        c.onIntent(UserIntent.ToggleRepeat)
        assertFalse(c.ui.value.repeatOne)
    }
}
