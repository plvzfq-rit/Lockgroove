package io.github.plvzfqrit.plvinyl.core.history

import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayLogTest {
    private val song = PlayedSong(SongKey.of("A", "Artist"), "A", "Artist", "com.spotify.music", 180_000)

    @Test fun `the same play seen twice is counted once and keeps its id`() {
        val log = PlayLog()
        val (first, new) = log.countPlay(song, 30_000, now = 1_000_000)
        assertTrue(new)
        val (again, newAgain) = log.countPlay(song, 30_000, now = 1_060_000)        // 1 min later
        assertFalse(newAgain)
        assertEquals(first.id, again.id)
        assertEquals(1, log.counts[song.key])
    }

    @Test fun `playing it again after a full listen is a new play`() {
        val log = PlayLog()
        log.countPlay(song, 30_000, now = 1_000_000)
        val (_, new) = log.countPlay(song, 30_000, now = 1_000_000 + 175_000)
        assertTrue(new)
        assertEquals(2, log.counts[song.key])
    }

    @Test fun `listening time only ever grows`() {
        val log = PlayLog()
        val (e, _) = log.countPlay(song, 30_000, now = 0)
        assertTrue(log.updateListened(e.id, 150_000))
        assertFalse(log.updateListened(e.id, 40_000))
        assertEquals(150_000, log.entries.single().listenedMs)
    }

    @Test fun `clearing the history leaves no wear behind`() {
        val log = PlayLog()
        repeat(20) { log.countPlay(song, 30_000, now = it * 1_000_000L) }
        assertTrue(log.wearFor(song.key) > 0f)
        log.clear()
        assertEquals(0f, log.wearFor(song.key))
        assertEquals(emptyList(), log.entries)
    }

    @Test fun `a couple of plays leave no wear, and wear tops out at 1`() {
        assertEquals(0f, Wear.of(2))
        assertTrue(Wear.of(10) > 0f)
        assertEquals(1f, Wear.of(500))
    }

    @Test fun `short songs count after most of them, long ones after 30 s`() {
        assertEquals(18_000, PlayLog.countAfter(20_000))
        assertEquals(30_000, PlayLog.countAfter(180_000))
        assertEquals(30_000, PlayLog.countAfter(0))
    }
}
