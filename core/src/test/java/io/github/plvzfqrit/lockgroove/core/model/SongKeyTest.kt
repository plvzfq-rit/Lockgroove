package io.github.plvzfqrit.lockgroove.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SongKeyTest {
    @Test fun `the same title and artist always make the same key`() {
        assertEquals(SongKey.of("Undressed", "sombr"), SongKey.of("Undressed", "sombr"))
    }

    @Test fun `a different artist makes a different key`() {
        assertNotEquals(SongKey.of("Undressed", "sombr"), SongKey.of("Undressed", "someone else"))
    }

    @Test fun `a stored key reads back as the same key`() {
        val key = SongKey.of("Title", "Artist")
        assertEquals(key, SongKey.parse(key.value))
    }
}
