package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcWriterTest {
    @Test fun `writes minutes, seconds and hundredths`() {
        assertEquals("[01:12.34]Hello", LrcWriter.format(listOf(LyricLine(72_340, "Hello"))))
        assertEquals("[00:00.05]Hi\n[75:00.00]Long", LrcWriter.format(listOf(LyricLine(50, "Hi"), LyricLine(4_500_000, "Long"))))
    }

    @Test fun `what is written is read back the same`() {
        val lines = listOf(
            LyricLine(7_600, "Расцветали яблони и груши"),
            LyricLine(55_460, ""),                                  // a pause
            LyricLine(83_190, "二人で歩こう"),
            LyricLine(251_000, "last"),
        )
        assertEquals(lines, LrcParser.parse(LrcWriter.format(lines)))
    }

    @Test fun `times finer than a hundredth are cut down to it`() {
        val back = LrcParser.parse(LrcWriter.format(listOf(LyricLine(1_239, "x"))))
        assertEquals(listOf(LyricLine(1_230, "x")), back)
    }

    @Test fun `a line break inside a lyric cannot split the line`() {
        val back = LrcParser.parse(LrcWriter.format(listOf(LyricLine(1_000, "one\ntwo"))))
        assertEquals(listOf(LyricLine(1_000, "one two")), back)
    }

    @Test fun `no lines gives no text`() {
        assertEquals("", LrcWriter.format(emptyList()))
    }
}
