package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.FakeClock
import io.github.plvzfqrit.plvinyl.core.history.PlayCounter
import io.github.plvzfqrit.plvinyl.core.history.PlayLog
import io.github.plvzfqrit.plvinyl.core.history.PlaySink
import io.github.plvzfqrit.plvinyl.core.history.PlayedSong
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand

/**
 * A controller wired to an animator that performs every move instantly, and a
 * player that records what it was told.
 */
class ControllerHarness {
    val clock = FakeClock()
    val commands = ArrayList<PlayerCommand>()
    val log = PlayLog()
    private val plays = PlayCounter(clock, object : PlaySink {
        override fun countPlay(song: PlayedSong, listenedMs: Long) = log.countPlay(song, listenedMs, clock.wallMs()).first.id
        override fun updateListened(id: Long, listenedMs: Long) { log.updateListened(id, listenedMs) }
    })
    val controller = TurntableController(clock, { commands += it }, plays)
    var deck = Deck(disc = null)
    val moves = ArrayList<Move>()
    private var lastSeekId = 0
    private var lastSnapId = 0

    /** Lets the animator catch up with the target, one instant move at a time. */
    fun settleDeck() {
        repeat(50) {
            val t = controller.target.value
            val snap = t.snapId != lastSnapId
            lastSnapId = t.snapId
            val seeked = t.seekId != lastSeekId
            lastSeekId = t.seekId
            val m = MovePlanner.next(deck, t, seeked, snap) ?: return
            moves += m
            controller.onMoveStarted(m, deck)
            deck = apply(deck, m)
            controller.onMoveFinished(m, deck)
        }
        error("deck never settled: $moves")
    }

    /** Moves time on in small ticks, letting the deck follow. */
    fun run(ms: Long, step: Long = 100L) {
        var left = ms
        while (left > 0) {
            val d = minOf(step, left)
            clock.advance(d)
            left -= d
            controller.tick()
            settleDeck()
        }
    }

    private fun apply(d: Deck, m: Move): Deck = when (m) {
        is Move.Lift -> d.copy(armUp = true, needleOn = null)
        Move.Lower -> d.copy(armUp = false)
        is Move.DropNeedle -> d.copy(armUp = false, needleOn = m.on)
        is Move.Swing -> d.copy(armDeg = m.toDeg)
        is Move.Track -> d.copy(armDeg = m.toDeg)
        is Move.FollowHand -> d.copy(armDeg = m.toDeg)
        Move.RemoveRecord -> d.copy(disc = null)
        is Move.PlaceRecord -> d.copy(disc = m.disc)
        is Move.UpdateDisc -> d.copy(disc = m.disc)
        is Move.SnapTo -> m.deck
    }
}
