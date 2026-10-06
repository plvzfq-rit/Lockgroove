package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.Disc
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovePlannerTest {
    private val a = Disc(SongKey.of("A", "Artist"), RecordSize.LP, "A", "Artist", hasArt = true)
    private val b = Disc(SongKey.of("B", "Artist"), RecordSize.LP, "B", "Artist", hasArt = true)
    private val lp = RecordSize.LP

    /** Applies a move the way the animator would. */
    private fun Deck.after(m: Move): Deck = when (m) {
        is Move.Lift -> copy(armUp = true, needleOn = null)
        Move.Lower -> copy(armUp = false)
        is Move.DropNeedle -> copy(armUp = false, needleOn = m.on)
        is Move.Swing -> copy(armDeg = m.toDeg)
        is Move.Track -> copy(armDeg = m.toDeg)
        is Move.FollowHand -> copy(armDeg = m.toDeg)
        Move.RemoveRecord -> copy(disc = null)
        is Move.PlaceRecord -> copy(disc = m.disc)
        is Move.UpdateDisc -> copy(disc = m.disc)
        is Move.SnapTo -> m.deck
    }

    /** Runs the planner until it's done; returns every move made. */
    private fun play(start: Deck, target: TurntableTarget, seeked: Boolean = false): Pair<List<Move>, Deck> {
        var deck = start
        val moves = ArrayList<Move>()
        var first = true
        while (true) {
            val m = MovePlanner.next(deck, target, seeked = seeked && first) ?: break
            first = false
            moves += m
            deck = deck.after(m)
            check(moves.size < 50) { "planner never settles: $moves" }
        }
        return moves to deck
    }

    private fun playingOn(disc: Disc, angle: Float) = Deck(disc, armDeg = angle, armUp = false, needleOn = disc.key)

    @Test fun `starting from rest lifts, swings, then drops, never sideways while down`() {
        val (moves, deck) = play(Deck(a), TurntableTarget(a, playing = true, playAngle = lp.leadInDeg))
        assertEquals(listOf(Move.Lift("leaving rest"), Move.Swing(lp.leadInDeg), Move.DropNeedle(a.key)), moves)
        assertEquals(a.key, deck.needleOn)
    }

    @Test fun `a seek lifts the needle first and never swings it across the record`() {
        val start = playingOn(a, lp.playingAngle(0.2f))
        val (moves, _) = play(start, TurntableTarget(a, playing = true, playAngle = lp.playingAngle(0.8f)), seeked = true)
        assertIs<Move.Lift>(moves.first())
        // Every sideways move happens with the arm up.
        var deck = start
        for (m in moves) {
            if (m is Move.Swing) assertTrue(deck.armUp, "swung while down: $moves")
            deck = deck.after(m)
        }
    }

    @Test fun `a small lag while playing is a glide in the groove, never a snap`() {
        val start = playingOn(a, lp.playingAngle(0.5f))
        val m = MovePlanner.next(start, TurntableTarget(a, playing = true, playAngle = start.armDeg + 0.2f))
        assertEquals(Move.Track(start.armDeg + 0.2f), m)
    }

    @Test fun `a tiny lag below the step leaves the arm alone`() {
        val start = playingOn(a, lp.playingAngle(0.5f))
        assertNull(MovePlanner.next(start, TurntableTarget(a, playing = true, playAngle = start.armDeg + 0.1f)))
    }

    @Test fun `the record is only swapped once the arm is parked`() {
        val start = playingOn(a, lp.playingAngle(0.5f))
        val (moves, deck) = play(start, TurntableTarget(b, playing = false))
        val swap = moves.indexOf(Move.RemoveRecord)
        var d = start
        moves.take(swap).forEach { d = d.after(it) }
        assertTrue(d.armParked && d.armDown, "swapped with the arm over the record: $moves")
        assertEquals(b, deck.disc)
    }

    @Test fun `flicking with a different record wanted parks and waits without swapping`() {
        val start = Deck(a)
        assertNull(MovePlanner.next(start, TurntableTarget(b, playing = true, holdArm = true)))
    }

    @Test fun `flicking lifts a playing needle and parks it`() {
        val (moves, deck) = play(playingOn(a, lp.playingAngle(0.5f)), TurntableTarget(a, playing = true, holdArm = true))
        assertIs<Move.Lift>(moves.first())
        assertTrue(deck.armParked && deck.armDown)
        assertNull(deck.needleOn)
    }

    @Test fun `repeat goes from the locked groove back to the lead-in without visiting the rest`() {
        val start = playingOn(a, lp.lockedGrooveDeg)
        val (moves, deck) = play(start, TurntableTarget(a, playing = true, playAngle = lp.leadInDeg), seeked = true)
        assertTrue(moves.none { it == Move.Swing(ArmGeometry.REST_DEG) }, "parked on repeat: $moves")
        assertEquals(lp.leadInDeg, deck.armDeg)
        assertEquals(a.key, deck.needleOn)
    }

    @Test fun `cue up hovers the needle where it was`() {
        val start = playingOn(a, lp.playingAngle(0.5f))
        val (moves, deck) = play(start, TurntableTarget(a, playing = false))
        assertEquals(listOf(Move.Lift("pause wanted")), moves)
        assertEquals(start.armDeg, deck.armDeg)
    }

    @Test fun `stop sends the arm home`() {
        val (_, deck) = play(playingOn(a, lp.playingAngle(0.5f)), TurntableTarget(a, playing = false, parkWhenPaused = true))
        assertTrue(deck.armParked && deck.armDown)
    }

    @Test fun `an arm let go over the edge goes back to rest`() {
        val start = Deck(a, armDeg = lp.leadInDeg - 3f, armUp = true)
        val (_, deck) = play(start, TurntableTarget(a, playing = false))
        assertTrue(deck.armParked && deck.armDown)
    }

    @Test fun `a finger on the arm lifts the needle, then the arm follows the finger`() {
        val start = playingOn(a, lp.playingAngle(0.5f))
        val target = TurntableTarget(a, playing = true, grabAngle = 20f)
        assertEquals(Move.Lift("picked up by hand"), MovePlanner.next(start, target))
        assertEquals(Move.FollowHand(20f), MovePlanner.next(start.copy(armUp = true, needleOn = null), target))
    }

    @Test fun `art arriving for the same record is swapped in silently`() {
        val plain = a.copy(hasArt = false)
        assertEquals(Move.UpdateDisc(a), MovePlanner.next(playingOn(plain, 15f), TurntableTarget(a, playing = true, playAngle = 15f)))
    }

    @Test fun `opening mid-song snaps straight to the needle in the groove`() {
        val m = MovePlanner.next(Deck(null), TurntableTarget(a, playing = true, playAngle = 15f), snap = true)
        assertEquals(Move.SnapTo(Deck(a, armDeg = 15f, armUp = false, needleOn = a.key)), m)
    }
}
