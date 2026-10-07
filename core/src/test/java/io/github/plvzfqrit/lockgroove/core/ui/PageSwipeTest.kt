package io.github.plvzfqrit.lockgroove.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageSwipeTest {
    private val w = 1000f
    private val fling = 350f * 2.625f                                // px/s at 2.625 density

    @Test fun `grabbed mid-transition and dragged right, the deck comes back`() {
        val p = PageSwipe.dragged(startProgress = 0.3f, dx = 0.10f * w, widthPx = w)
        assertTrue(p < 0.3f)
        assertFalse(PageSwipe.settle(PageSwipe.startsOpen(0.3f), dx = 0.10f * w, velocityX = 0f, widthPx = w, flingPxPerS = fling))
    }

    @Test fun `on the stats, a slow 12 percent drag right closes them`() {
        assertFalse(PageSwipe.settle(startedOpen = true, dx = 0.12f * w, velocityX = 100f, widthPx = w, flingPxPerS = fling))
    }

    @Test fun `on the stats, a 5 percent drag right stays open`() {
        assertTrue(PageSwipe.settle(startedOpen = true, dx = 0.05f * w, velocityX = 100f, widthPx = w, flingPxPerS = fling))
    }

    @Test fun `a short fast flick left opens the stats`() {
        assertTrue(PageSwipe.settle(startedOpen = false, dx = -0.03f * w, velocityX = -400f * 2.625f, widthPx = w, flingPxPerS = fling))
    }

    @Test fun `the deck follows the finger but never past its exit`() {
        assertTrue(PageSwipe.dragged(0f, dx = -2f * w, widthPx = w) <= PageSwipe.FADE_OUT_END)
    }
}
