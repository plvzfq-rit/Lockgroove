package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {
    @Test fun `reads timed lines in order`() {
        val lines = LrcParser.parse("[00:12.34] First\n[00:20.00] Second")
        assertEquals(listOf(LyricLine(12_340, "First"), LyricLine(20_000, "Second")), lines)
    }

    @Test fun `a line with several tags is sung at each of them`() {
        val lines = LrcParser.parse("[00:15.00][01:30.50] Chorus\n[00:30.00] Verse")
        assertEquals(
            listOf(LyricLine(15_000, "Chorus"), LyricLine(30_000, "Verse"), LyricLine(90_500, "Chorus")),
            lines,
        )
    }

    @Test fun `two digits are hundredths and three are thousandths`() {
        val lines = LrcParser.parse("[00:01.5] a\n[00:02.50] b\n[00:03.505] c\n[00:04] d")
        assertEquals(listOf(1_500L, 2_500L, 3_505L, 4_000L), lines.map { it.timeMs })
    }

    @Test fun `minutes can pass 59, and a colon can stand in for the dot`() {
        val lines = LrcParser.parse("[75:00.00] long\n[00:05:25] odd")
        assertEquals(listOf(5_250L, 4_500_000L), lines.map { it.timeMs })
    }

    @Test fun `metadata and untimed lines are ignored`() {
        val lines = LrcParser.parse("[ar:Someone]\n[ti:Song]\nplain text\n\n[00:01.00] Hello")
        assertEquals(listOf(LyricLine(1_000, "Hello")), lines)
    }

    @Test fun `an empty line is kept as a pause`() {
        val lines = LrcParser.parse("[00:01.00] Hello\n[00:05.00]\n[00:09.00] Back")
        assertEquals(listOf("Hello", "", "Back"), lines.map { it.text })
    }

    @Test fun `a positive offset brings the lyrics earlier`() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00] Line")
        assertEquals(listOf(LyricLine(9_500, "Line")), lines)
    }

    @Test fun `word by word marks are dropped`() {
        val lines = LrcParser.parse("[00:01.00] <00:01.00>Hel<00:01.40>lo")
        assertEquals(listOf(LyricLine(1_000, "Hello")), lines)
    }

    @Test fun `lines given out of order are sorted by time`() {
        val lines = LrcParser.parse("[00:20.00] b\n[00:10.00] a")
        assertEquals(listOf("a", "b"), lines.map { it.text })
    }

    @Test fun `nothing to read gives nothing`() {
        assertEquals(emptyList(), LrcParser.parse(""))
    }
}
