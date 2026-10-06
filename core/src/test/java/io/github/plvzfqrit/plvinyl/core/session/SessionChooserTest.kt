package io.github.plvzfqrit.plvinyl.core.session

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionChooserTest {
    private fun s(token: String, state: PlayerState?, lastUpdate: Long = 0L, hasTitle: Boolean = true) =
        SessionInfo(token, state, lastUpdate) { hasTitle }

    @Test fun `with nothing followed yet, a playing app beats a paused one`() {
        val choice = SessionChooser.choose(
            listOf(s("chrome", PlayerState.Paused, lastUpdate = 9), s("spotify", PlayerState.Playing, lastUpdate = 1)),
            followed = null,
        )
        assertEquals(Choice.Follow("spotify"), choice)
    }

    @Test fun `a notification sound or a paused Chrome tab doesn't steal the session`() {
        val choice = SessionChooser.choose(
            listOf(s("spotify", PlayerState.Paused, lastUpdate = 1), s("chrome", PlayerState.Paused, lastUpdate = 9)),
            followed = "spotify",
        )
        assertEquals(Choice.Follow("spotify"), choice)
    }

    @Test fun `another app that really starts playing takes over from our paused one`() {
        val choice = SessionChooser.choose(
            listOf(s("spotify", PlayerState.Paused), s("ytmusic", PlayerState.Playing)),
            followed = "spotify",
        )
        assertEquals(Choice.Follow("ytmusic"), choice)
    }

    @Test fun `while ours is playing, another playing app doesn't take over`() {
        val choice = SessionChooser.choose(
            listOf(s("spotify", PlayerState.Playing), s("ytmusic", PlayerState.Playing, lastUpdate = 99)),
            followed = "spotify",
        )
        assertEquals(Choice.Follow("spotify"), choice)
    }

    @Test fun `buffering counts as playing, so a network hiccup keeps our player`() {
        val choice = SessionChooser.choose(
            listOf(s("spotify", PlayerState.Buffering), s("ytmusic", PlayerState.Playing)),
            followed = "spotify",
        )
        assertEquals(Choice.Follow("spotify"), choice)
    }

    @Test fun `the followed player mid-update with no title yet is kept, not dropped`() {
        val choice = SessionChooser.choose(
            listOf(s("spotify", PlayerState.Paused, hasTitle = false)),
            followed = "spotify",
        )
        assertEquals(Choice.Keep, choice)
    }

    @Test fun `a followed player with no state at all counts as gone`() {
        val choice = SessionChooser.choose(listOf(s("spotify", null)), followed = "spotify")
        assertEquals(Choice.NothingPlaying, choice)
    }

    @Test fun `a stopped player means nothing is playing`() {
        val choice = SessionChooser.choose(listOf(s("spotify", PlayerState.Stopped)), followed = "spotify")
        assertEquals(Choice.NothingPlaying, choice)
    }

    @Test fun `sessions without a title are never picked fresh`() {
        val choice = SessionChooser.choose(
            listOf(s("teams", PlayerState.Playing, lastUpdate = 9, hasTitle = false), s("spotify", PlayerState.Playing)),
            followed = null,
        )
        assertEquals(Choice.Follow("spotify"), choice)
    }

    @Test fun `among equals, the most recently updated player wins`() {
        val choice = SessionChooser.choose(
            listOf(s("a", PlayerState.Paused, lastUpdate = 5), s("b", PlayerState.Paused, lastUpdate = 7)),
            followed = null,
        )
        assertEquals(Choice.Follow("b"), choice)
    }
}
