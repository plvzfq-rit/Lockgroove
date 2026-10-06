package io.github.plvzfqrit.plvinyl.core.history

import io.github.plvzfqrit.plvinyl.core.model.SongKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayLogCodecTest {
    @Test fun `a play survives the round trip`() {
        val e = PlayEntry(5, SongKey.of("A", "B"), "A", "B", "com.spotify.music", 5, 180_000, 42_000)
        assertEquals(listOf(e), PlayLogCodec.parse(sequenceOf(PlayLogCodec.playLine(e))))
    }

    @Test fun `a listen line updates the play it belongs to`() {
        val e = PlayEntry(5, SongKey.of("A", "B"), "A", "B", "p", 5, 180_000, 30_000)
        val parsed = PlayLogCodec.parse(sequenceOf(PlayLogCodec.playLine(e), PlayLogCodec.listenLine(5, 170_000)))
        assertEquals(170_000, parsed.single().listenedMs)
    }

    @Test fun `old lines without listenedMs read as 0, and fall back to the song length`() {
        val v1 = """{"t":"play","key":"A|B","title":"A","artist":"B","player":"p","at":7,"durationMs":180000}"""
        val e = PlayLogCodec.parse(sequenceOf(v1)).single()
        assertEquals(0, e.listenedMs)
        assertEquals(7, e.id)                                  // no id: the time was the id
        assertEquals(180_000, e.timeMs)
    }

    @Test fun `a missing title stays missing, and broken lines are skipped`() {
        val noTitle = """{"t":"play","id":1,"key":"null|B","artist":"B","player":"p","at":1,"durationMs":1}"""
        val parsed = PlayLogCodec.parse(sequenceOf(noTitle, "{broken", ""))
        assertEquals(null, parsed.single().title)
    }
}
