package io.github.plvzfqrit.plvinyl.core.history

import io.github.plvzfqrit.plvinyl.core.FakeClock
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayCounterTest {
    private val clock = FakeClock()
    private val log = PlayLog()
    private val listened = HashMap<Long, Long>()
    private val counter = PlayCounter(clock, object : PlaySink {
        override fun countPlay(song: PlayedSong, listenedMs: Long) = log.countPlay(song, listenedMs, clock.wallMs()).first.id
        override fun updateListened(id: Long, listenedMs: Long) { listened[id] = listenedMs; log.updateListened(id, listenedMs) }
    })
    private val a = PlayedSong(SongKey.of("A", "Artist"), "A", "Artist", "p", 180_000)
    private val b = PlayedSong(SongKey.of("B", "Artist"), "B", "Artist", "p", 180_000)

    @Test fun `a play counts after 30 s of listening, pauses don't count`() {
        counter.update(a, listening = true)
        clock.advance(20_000); counter.update(a, listening = false)
        clock.advance(60_000); counter.update(a, listening = true)
        clock.advance(9_000); counter.tick()
        assertEquals(0, log.entries.size)
        clock.advance(1_000); counter.tick()
        assertEquals(1, log.entries.size)
    }

    @Test fun `the listening time is saved when the song changes`() {
        counter.update(a, listening = true)
        clock.advance(100_000)
        counter.update(b, listening = true)
        assertEquals(100_000L, listened.values.single())
    }

    @Test fun `a repeat after a full listen counts as a second play`() {
        counter.update(a, listening = true)
        clock.advance(30_000); counter.tick()                   // counted 30 s in
        clock.advance(150_000); counter.tick()
        counter.restart()
        clock.advance(31_000); counter.tick()
        assertEquals(2, log.counts[a.key])
    }

    @Test fun `msUntilCount says when to look again`() {
        counter.update(a, listening = true)
        clock.advance(12_000)
        assertEquals(18_000L, counter.msUntilCount())
    }
}
