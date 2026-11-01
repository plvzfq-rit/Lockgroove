package io.github.plvzfqrit.lockgroove.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricScriptsTest {
    @Test fun `latin text, accents and symbols need no romanization`() {
        for (line in listOf("hello world", "Café déjà vu", "123 ♪", "", "Don't stop")) {
            assertFalse(LyricScripts.needsRomanization(line), line)
        }
    }

    @Test fun `chinese, korean, japanese, arabic and cyrillic do`() {
        for (line in listOf("你好吗", "안녕하세요", "こんにちは", "カタカナ", "مرحبا", "Привет")) {
            assertTrue(LyricScripts.needsRomanization(line), line)
        }
    }

    @Test fun `a latin word inside a foreign line still counts as foreign`() {
        assertTrue(LyricScripts.needsRomanization("I love 你"))
    }

    @Test fun `only han without kana or hangul counts as chinese`() {
        assertTrue(LyricScripts.isChinese("你好吗"))
        assertTrue(LyricScripts.isChinese("I love 你"))
        assertFalse(LyricScripts.isChinese("愛してる"))
        assertFalse(LyricScripts.isChinese("한국어 漢字"))
        assertFalse(LyricScripts.isChinese("hello"))
    }

    @Test fun `japanese with kanji and kana is mixed, but pure kana and pure hanzi are not`() {
        assertTrue(LyricScripts.isMixedJapanese("愛してる"))
        assertFalse(LyricScripts.isMixedJapanese("こんにちは"))
        assertFalse(LyricScripts.isMixedJapanese("你好吗"))
        assertFalse(LyricScripts.isMixedJapanese("안녕하세요"))
    }
}
