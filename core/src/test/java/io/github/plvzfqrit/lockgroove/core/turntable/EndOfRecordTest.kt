package io.github.plvzfqrit.lockgroove.core.turntable

import io.github.plvzfqrit.lockgroove.core.model.ArmGeometry
import io.github.plvzfqrit.lockgroove.core.model.RecordSize
import io.github.plvzfqrit.lockgroove.core.playback.PlayerCommand
import io.github.plvzfqrit.lockgroove.core.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The end of a record, through the controller. */
class EndOfRecordTest {
    private val h = ControllerHarness()
    private val c = h.controller

    /** Song A playing, [left] ms before its end. */
    private fun nearTheEnd(left: Long = 10_000L) {
        c.onSnapshot(snapshot(title = "A", positionMs = 180_000 - left, at = h.clock.nowMs()))
        c.onStart()
        h.run(300)
        h.commands.clear()
        h.moves.clear()
    }

    private fun playerPaused() = c.onSnapshot(snapshot(title = "A", sounding = false, positionMs = 179_500, at = h.clock.nowMs()))

    @Test fun `the needle goes into the lead-out before the next song can start`() {
        nearTheEnd()
        h.run(8_500)                                                 // 1.5 s left: not yet
        assertFalse(c.ui.value.inLeadOut)
        h.run(800)                                                   // under the 1 s margin
        assertTrue(c.ui.value.inLeadOut)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Pause), h.commands)
        assertEquals(RecordSize.Single.lockedGrooveDeg, h.deck.armDeg, 0.01f)
        assertTrue(h.deck.needleOn != null, "the needle stays down in the lead-out")
    }

    @Test fun `a longer margin on the slider sends the needle into the lead-out earlier`() {
        c.updateSettings { it.copy(endMarginMs = 2_500L) }
        nearTheEnd()
        h.run(7_000)                                                 // 2.7 s left: not yet
        assertFalse(c.ui.value.inLeadOut)
        h.run(400)                                                   // 2.3 s left
        assertTrue(c.ui.value.inLeadOut)
    }

    @Test fun `after the lead-out the next record comes`() {
        nearTheEnd()
        h.run(9_300)
        playerPaused()
        h.run(3_500)
        assertTrue(PlayerCommand.Next in h.commands, "no next: ${h.commands}")
        assertTrue(h.deck.armParked, "the arm parks for the record swap")
    }

    @Test fun `with repeat one the arm goes back to the start without parking`() {
        c.updateSettings { it.copy(repeatOne = true) }
        nearTheEnd()
        h.run(9_300)
        playerPaused()
        h.run(3_500)
        // The rewind goes out while the needle is up: after the lift's pause, before the drop's play.
        val rewind = h.commands.indexOf(PlayerCommand.SeekTo(0L))
        assertTrue(rewind > 0 && h.commands[rewind - 1] == PlayerCommand.Pause, "rewind not while up: ${h.commands}")
        assertTrue(h.moves.none { it == Move.Swing(ArmGeometry.REST_DEG) }, "parked: ${h.moves}")
        assertEquals(RecordSize.Single.leadInDeg, h.deck.armDeg, 0.3f)
        assertEquals(PlayerCommand.Play, h.commands.last())
    }

    @Test fun `stop during the lead-out cancels it and sends the arm home`() {
        nearTheEnd()
        h.run(9_300)
        c.onIntent(UserIntent.StartStop)
        h.run(1_000)
        assertFalse(c.ui.value.inLeadOut)
        assertTrue(h.deck.armParked && h.deck.armDown)
    }

    @Test fun `a hand-off right at the end is paused at once, with no settling delay`() {
        nearTheEnd(left = 4_000L)
        c.onSnapshot(snapshot(title = "B", positionMs = 0, at = h.clock.nowMs()))
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Pause), h.commands)
    }

    @Test fun `a crossfade learned once makes the needle lift that much earlier`() {
        nearTheEnd(left = 3_000L)
        c.onSnapshot(snapshot(title = "B", positionMs = 0, at = h.clock.nowMs()))      // handed off 3 s early
        h.run(6_000)
        h.commands.clear()
        c.onSnapshot(snapshot(title = "C", positionMs = 180_000 - 5_000, at = h.clock.nowMs()))
        h.run(3_000)
        h.commands.clear()
        h.run(1_100)                                                 // ~4 s left: inside 1 s + 3 s
        assertTrue(c.ui.value.inLeadOut, "didn't lift early: ${h.commands}")
        val learned = c.settings.value.learnedCrossfades["com.spotify.music"] ?: 0L
        assertTrue(learned in 2_500L..3_000L, "learned $learned ms")              // saved with the settings
    }
}
