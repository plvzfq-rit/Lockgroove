package io.github.plvzfqrit.plvinyl.core.session

/** A media session's playback state, as Android reports it. */
enum class PlayerState {
    None, Stopped, Error, Playing, Paused, Buffering, Connecting,
    FastForwarding, Rewinding, SkippingToNext, SkippingToPrevious, SkippingToQueueItem, Other;

    /** The player has really gone away. */
    val isDead get() = this == None || this == Stopped || this == Error

    /** Music is (or is about to be) coming out of the speaker. */
    val isSounding get() = this == Playing || this == Buffering || this == Connecting

    /** On its way somewhere: buffering, seeking, skipping. */
    val isTransitional get() = this in TRANSITIONAL

    private companion object {
        val TRANSITIONAL = setOf(
            Buffering, Connecting, FastForwarding, Rewinding,
            SkippingToNext, SkippingToPrevious, SkippingToQueueItem,
        )
    }
}

/**
 * What we know about one media session. [hasTitle] is a function because reading a
 * player's song info costs something: it's only asked when it matters.
 */
class SessionInfo<T>(
    val token: T,
    /** Null = the player hasn't reported a state yet. */
    val state: PlayerState?,
    /** Monotonic time of the player's last position report. */
    val lastUpdate: Long,
    val hasTitle: () -> Boolean,
)

sealed interface Choice<out T> {
    /** Show this session (it may be the one we already follow). */
    data class Follow<T>(val token: T) : Choice<T>
    /** The followed player is mid-update: keep showing what we have. */
    data object Keep : Choice<Nothing>
    /** Nothing is playing. */
    data object NothingPlaying : Choice<Nothing>
}

/**
 * Picks the player to follow. Sticky: stay with the player we already follow, and
 * only switch when ANOTHER app is actually playing while ours isn't. Without that, a
 * split-second blip (ours pauses for a notification sound, a paused Chrome tab looks
 * "newer") flipped the app to another song, and the turntable lifted the needle,
 * which paused the music.
 */
object SessionChooser {
    fun <T> choose(sessions: List<SessionInfo<T>>, followed: T?): Choice<T> {
        val alive = sessions.filter { it.state != null && !it.state.isDead }
        val current = alive.firstOrNull { it.token == followed && it.hasTitle() }
        val currentSounding = current?.state?.isSounding == true
        val otherPlaying = if (currentSounding) null else alive.firstOrNull {
            it.token != followed && it.state == PlayerState.Playing && it.hasTitle()
        }
        // Our player is still around but mid-update (no title / no state yet).
        val followedAlive = followed != null && sessions.any {
            it.token == followed && it.state != null && !it.state.isDead
        }
        return when {
            current != null && currentSounding -> Choice.Follow(current.token)
            otherPlaying != null -> Choice.Follow(otherPlaying.token)
            current != null -> Choice.Follow(current.token)      // ours paused: keep it
            followedAlive -> Choice.Keep
            else -> {                                            // not following anyone yet
                val byPriority = alive.filter { it.state == PlayerState.Playing }
                    .ifEmpty { alive.filter { it.state!!.isTransitional } }
                    .ifEmpty { alive.filter { it.state == PlayerState.Paused } }
                byPriority
                    .sortedByDescending { it.lastUpdate }
                    .firstOrNull { it.hasTitle() }
                    ?.let { Choice.Follow(it.token) }
                    ?: Choice.NothingPlaying
            }
        }
    }
}
