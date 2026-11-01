package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricReadingTest {
    @Test fun `words are separated by one space`() {
        assertEquals("kimi ga suki da", LyricReading.join(listOf("kimi", "ga", "suki", "da")))
    }

    @Test fun `punctuation sticks to the word before it`() {
        assertEquals("kimi, suki.", LyricReading.join(listOf("kimi", "、", "suki", "。")))
        assertEquals("aishiteru!", LyricReading.join(listOf("aishiteru", "！")))
    }

    @Test fun `nothing goes after an opening bracket or before a closing one`() {
        assertEquals("kimi (ai) suki", LyricReading.join(listOf("kimi", "（", "ai", "）", "suki")))
    }

    @Test fun `quote brackets are dropped and blanks skipped`() {
        assertEquals("ai", LyricReading.join(listOf("「", "ai", "」", "", "  ")))
    }

    @Test fun `a middle dot becomes a space`() {
        assertEquals("a b", LyricReading.join(listOf("a", "・", "b")))
    }

    @Test fun `nothing in gives nothing out`() {
        assertEquals("", LyricReading.join(emptyList()))
    }
}
