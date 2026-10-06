package io.github.plvzfqrit.plvinyl.core.playback

import io.github.plvzfqrit.plvinyl.core.FakeClock
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackArbiterTest {
    private val clock = FakeClock()
    private val arbiter = PlaybackArbiter(clock)
    private val song = SongKey.of("Song A", "Artist")

    private fun report(playing: Boolean, touching: Boolean, needleDown: Boolean = touching, natural: Boolean = false) =
        arbiter.onPlayer(playing, song, touching, needleDown, natural)

    @Test fun `music without the needle is paused, and the needle goes to it`() {
        val cmds = report(playing = true, touching = false)
        assertEquals(listOf(PlayerCommand.Pause), cmds)
        assertTrue(arbiter.wantPlaying)
    }

    @Test fun `pressing play doesn't flip back to pause while the player is slow to start`() {
        arbiter.wantPlaying = true
        assertEquals(listOf(PlayerCommand.Play), arbiter.onNeedleDown())
        assertEquals(emptyList(), report(playing = false, touching = true))
        clock.advance(1_000)
        assertEquals(emptyList(), arbiter.onTick())
        assertTrue(arbiter.wantPlaying)
    }

    @Test fun `a short self-pause does not lift the needle`() {
        arbiter.wantPlaying = true
        arbiter.onNeedleDown()
        report(playing = true, touching = true)                  // confirms our Play
        report(playing = false, touching = true)                 // a notification sound
        clock.advance(1_000); arbiter.onTick()
        report(playing = true, touching = true)
        clock.advance(1_000); arbiter.onTick()
        assertTrue(arbiter.wantPlaying)
    }

    @Test fun `a pause from outside that sticks lifts the needle`() {
        arbiter.wantPlaying = true
        arbiter.onNeedleDown()
        report(playing = true, touching = true)
        report(playing = false, touching = true)
        clock.advance(1_600)
        arbiter.onTick()
        assertFalse(arbiter.wantPlaying)
    }

    @Test fun `silence in the lead-out is left alone, not fixed by lifting or playing`() {
        arbiter.wantPlaying = true
        arbiter.onNeedleDown()
        report(playing = true, touching = true)
        assertEquals(listOf(PlayerCommand.Pause), arbiter.enterLeadOut())
        report(playing = false, touching = true)                 // confirms our Pause
        clock.advance(3_000)
        assertEquals(emptyList(), arbiter.onTick())
        assertTrue(arbiter.wantPlaying)
    }

    @Test fun `a song change gets a moment to settle before it counts`() {
        arbiter.wantPlaying = true
        arbiter.onNeedleDown()
        report(playing = true, touching = true)
        assertEquals(emptyList(), arbiter.onPlayer(true, SongKey.of("B", "Artist"), touching = false, needleDown = true))
        clock.advance(500)
        assertEquals(emptyList(), arbiter.onTick())
        clock.advance(300)
        assertEquals(listOf(PlayerCommand.Pause), arbiter.onTick())
    }

    @Test fun `a natural hand-off at the end of the record is paused at once`() {
        arbiter.wantPlaying = true
        arbiter.onNeedleDown()
        report(playing = true, touching = true)
        val cmds = arbiter.onPlayer(true, SongKey.of("B", "Artist"), touching = false, needleDown = true, naturalHandOff = true)
        assertEquals(listOf(PlayerCommand.Pause), cmds)
    }

    @Test fun `the safety net pauses again a player that ignored the first pause`() {
        assertEquals(listOf(PlayerCommand.Pause), report(playing = true, touching = false))
        clock.advance(2_100)                                     // our pause went unconfirmed
        assertEquals(listOf(PlayerCommand.Pause), arbiter.onTick())
    }

    @Test fun `just back in the app, the player is ignored while we snap into place`() {
        arbiter.startResumeGrace()
        assertEquals(emptyList(), report(playing = true, touching = false))
        clock.advance(1_500)
        assertEquals(listOf(PlayerCommand.Pause), arbiter.onTick())
    }

    @Test fun `adopting the player's state sends nothing`() {
        arbiter.adopt(playing = true)
        assertTrue(arbiter.wantPlaying)
        assertEquals(emptyList(), report(playing = true, touching = true))
    }
}
