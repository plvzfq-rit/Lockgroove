package io.github.plvzfqrit.plvinyl.core.history

import io.github.plvzfqrit.plvinyl.core.model.Clock

/** Where counted plays go (the app's play log store). */
interface PlaySink {
    /** Count a play; returns its id (an existing one if it's the same play). */
    fun countPlay(song: PlayedSong, listenedMs: Long): Long
    fun updateListened(id: Long, listenedMs: Long)
}

/*
 * Counts plays and real listening time. One counter for the whole app: the turntable
 * feeds it while it's on screen (needle down + sounding), and the listener service
 * while the app is closed (if counting in the background is on). Being one counter,
 * there's no hand-off between the two to get wrong.
 */
class PlayCounter(private val clock: Clock, private val sink: PlaySink) {
    private var song: PlayedSong? = null
    private var heardMs = 0L
    /** Monotonic time listening started, or null while not listening. */
    private var listeningSince: Long? = null
    private var playId: Long? = null

    /**
     * The current song, and whether it's being listened to right now. A different song
     * ends the previous play (its listening time is saved).
     */
    fun update(song: PlayedSong?, listening: Boolean) {
        accrue()
        check()                                              // it may have counted since the last look
        if (song?.key != this.song?.key) {
            finish()
            heardMs = 0L
            playId = null
        }
        this.song = song
        listeningSince = if (listening && song != null) clock.nowMs() else null
        check()
    }

    /** Call now and then while listening (or when [msUntilCount] says). */
    fun tick() {
        accrue()
        check()
    }

    /** Going round again (repeat one): save this play's time; the next listen is a new play. */
    fun restart() {
        finish()
        heardMs = 0L
        playId = null
    }

    /** Save the listening time so far. */
    fun finish() {
        accrue()
        playId?.let { sink.updateListened(it, heardMs) }
    }

    /** How long until the current listening would count, or null if nothing is pending. */
    fun msUntilCount(): Long? {
        val s = song ?: return null
        if (playId != null || listeningSince == null) return null
        accrue()
        return (PlayLog.countAfter(s.durationMs) - heardMs).coerceAtLeast(0L)
    }

    private fun accrue() {
        val since = listeningSince ?: return
        val now = clock.nowMs()
        heardMs += (now - since).coerceAtLeast(0L)
        listeningSince = now
    }

    private fun check() {
        val s = song ?: return
        if (playId == null && heardMs >= PlayLog.countAfter(s.durationMs)) {
            playId = sink.countPlay(s, heardMs)
        }
    }
}
