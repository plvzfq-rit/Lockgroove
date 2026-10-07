package io.github.plvzfqrit.lockgroove.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordSizeTest {
    @Test fun `progress 0 and 1 map exactly to the lead-in and run-out angles for both sizes`() {
        for (size in RecordSize.entries) {
            assertEquals(size.leadInDeg, size.playingAngle(0f))
            assertEquals(size.runOutDeg, size.playingAngle(1f))
        }
    }

    @Test fun `the needle never leaves the groove band, even for out of range progress`() {
        for (size in RecordSize.entries) {
            assertEquals(size.leadInDeg, size.playingAngle(-0.5f))
            assertEquals(size.runOutDeg, size.playingAngle(1.5f))
        }
    }

    @Test fun `progressAt undoes playingAngle`() {
        for (size in RecordSize.entries) {
            for (p in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
                assertEquals(p, size.progressAt(size.playingAngle(p)), 1e-4f)
            }
        }
    }

    @Test fun `auto picks a single up to four minutes and an LP after`() {
        assertEquals(RecordSize.Single, RecordSizeChoice.Auto.sizeFor(3 * 60_000L))
        assertEquals(RecordSize.Single, RecordSizeChoice.Auto.sizeFor(RecordSize.SINGLE_MAX_MS))
        assertEquals(RecordSize.LP, RecordSizeChoice.Auto.sizeFor(RecordSize.SINGLE_MAX_MS + 1))
    }

    @Test fun `auto falls back to an LP when the length is unknown`() {
        assertEquals(RecordSize.LP, RecordSizeChoice.Auto.sizeFor(0L))
    }

    @Test fun `fixed choices ignore the song length`() {
        assertEquals(RecordSize.LP, RecordSizeChoice.AlwaysLP.sizeFor(60_000L))
        assertEquals(RecordSize.Single, RecordSizeChoice.AlwaysSingle.sizeFor(10 * 60_000L))
    }
}
