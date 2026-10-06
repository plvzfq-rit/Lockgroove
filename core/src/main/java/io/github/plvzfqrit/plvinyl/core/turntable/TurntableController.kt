package io.github.plvzfqrit.plvinyl.core.turntable

import io.github.plvzfqrit.plvinyl.core.model.Clock
import io.github.plvzfqrit.plvinyl.core.model.Disc
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.model.RecordSizeChoice
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.model.sizeFor
import io.github.plvzfqrit.plvinyl.core.playback.PlaybackArbiter
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import io.github.plvzfqrit.plvinyl.core.playback.PlayerSnapshot
import io.github.plvzfqrit.plvinyl.core.playback.PositionTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The one place that decides. Player reports come in as snapshots, the listener's
 * taps as intents, and the animator reports each move it makes. The controller turns
 * them into player commands, a target for the turntable, and the UI state.
 *
 * Plain Kotlin and synchronous: the app calls tick() regularly and passes the time
 * in through the Clock, so tests can drive every scenario instantly.
 */
class TurntableController(
    private val clock: Clock,
    private val send: (PlayerCommand) -> Unit,
) {
    private val arbiter = PlaybackArbiter(clock)

    private val _target = MutableStateFlow(TurntableTarget())

    /** Where the turntable should be. Read by the animator, not by composition. */
    val target: StateFlow<TurntableTarget> = _target.asStateFlow()

    private val _ui = MutableStateFlow(TurntableUiState())
    val ui: StateFlow<TurntableUiState> = _ui.asStateFlow()

    /** 7" or 12", from the song length (a setting). */
    var recordSizeChoice: RecordSizeChoice = RecordSizeChoice.Auto
        set(value) { field = value; refresh() }

    // ---- What we know
    private var song: PlayerSnapshot? = null
    /** The physical deck, as the animator last reported it. */
    private var deck = Deck(disc = null)
    private val needleOn: SongKey? get() = deck.needleOn

    // ---- The record on its way: a new song must stick before the record changes
    private var settled: Disc? = null
    private var candidate: Disc? = null
    private var candidateSince = 0L

    // ---- Needle position
    private var seekId = 0
    /** Needle time on this song: the progress fallback for players that don't report a length. */
    private var playedMs = 0L
    private var lastTick = 0L
    /** Right after a needle drop by hand: hold it there until the player catches up. */
    private var dropAngle: Float? = null
    private var dropUntil = 0L
    private var grabAngle: Float? = null
    /** STOP sends the arm home; a pause from the cue lever (or the player) just hovers it. */
    private var parkWhenPaused = false

    // ---- Opening and coming back
    private sealed interface Phase {
        data object Stopped : Phase
        data class StartingUp(val deadline: Long) : Phase
        data class Resuming(val at: Long) : Phase
        data object Running : Phase
    }
    private var phase: Phase = Phase.Stopped
    private var everStarted = false
    private var snapId = 0

    // ------------------------------------------------------------------ inputs

    /** The followed player's latest report (null = nothing playing). */
    fun onSnapshot(s: PlayerSnapshot?) {
        val prev = song
        if (prev != null && s != null && PositionTracker.isSeek(prev, s)) seekId++
        if (s?.key != prev?.key) playedMs = 0L
        song = s
        refresh()
    }

    /** The app is on screen. */
    fun onStart() {
        val now = clock.nowMs()
        lastTick = now
        if (!everStarted) {
            // App just opened: wait briefly for the player's current song (and its art),
            // then START in that state. If it's playing, the record is already on and
            // the needle already in the groove: nothing pauses or replays.
            everStarted = true
            phase = Phase.StartingUp(deadline = now + STARTUP_WAIT_MS)
        } else {
            // Back from the background: things may have moved on (new song, paused…).
            // Snap straight to the current state instead of replaying it as animations.
            arbiter.startResumeGrace()
            phase = Phase.Resuming(at = now + RESUME_SNAP_DELAY_MS)
        }
        tick()
    }

    /** The app left the screen. */
    fun onStop() {
        phase = Phase.Stopped
    }

    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.HoldArm -> grabAngle = intent.angleDeg
            UserIntent.LetGoOfArm -> letGoOfArm()
        }
        refresh()
    }

    /** The animator is starting [move] from [deck]. */
    fun onMoveStarted(move: Move, deck: Deck) {
        // A snap's result is already known (see snapToPlayer); the old deck is history.
        if (move is Move.SnapTo) return
        this.deck = deck
        // Needle leaving the vinyl: silence first, then raise.
        if (move is Move.Lift && deck.needleOn != null) {
            this.deck = deck.copy(needleOn = null)
            sendAll(arbiter.onNeedleUp())
        }
        refresh()
    }

    /** The animator finished [move]; the deck is now [deck]. */
    fun onMoveFinished(move: Move, deck: Deck) {
        this.deck = deck
        // Needle touching the vinyl: music.
        if (move is Move.DropNeedle) sendAll(arbiter.onNeedleDown())
        refresh()
    }

    /** Call regularly (a few times a second) while on screen. */
    fun tick() {
        val now = clock.nowMs()
        if (audible()) playedMs += (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        when (val p = phase) {
            Phase.Stopped -> return
            is Phase.StartingUp -> if (song != null || now >= p.deadline) snapToPlayer()
            is Phase.Resuming -> if (now >= p.at) {
                grabAngle = null
                dropAngle = null
                parkWhenPaused = false
                snapToPlayer()
            }
            Phase.Running -> {}
        }
        sendAll(arbiter.onTick())
        refresh()
    }

    // ----------------------------------------------------------------- logic

    /** Be exactly where the player is: record on, needle down if it's playing. */
    private fun snapToPlayer() {
        settled = discFor(song)
        candidate = settled
        val playing = song?.sounding == true
        arbiter.adopt(playing)
        snapId++
        phase = Phase.Running
        // The animator will jump to exactly this; count on it now, so the player's
        // "playing" isn't mistaken for music without the needle in the meantime.
        val disc = settled
        deck = if (disc != null && playing) Deck(disc, playAngle(clock.nowMs()), armUp = false, needleOn = disc.key)
        else Deck(disc)
    }

    private fun letGoOfArm() {
        val a = grabAngle ?: return
        grabAngle = null
        val size = settled?.size ?: RecordSize.LP
        if (a < size.leadInDeg - 1.5f) {
            arbiter.wantPlaying = false                      // put back: stop
            return
        }
        // Dropped on the record: play from there.
        val p = size.progressAt(a)
        val s = song
        if (s != null && s.durationMs > 0L) send(PlayerCommand.SeekTo((p * s.durationMs).toLong()))
        playedMs = (p * NOMINAL_TRACK_MS).toLong()
        dropAngle = size.playingAngle(p)
        dropUntil = clock.nowMs() + DROP_HOLD_MS
        parkWhenPaused = false
        arbiter.wantPlaying = true
    }

    private fun discFor(s: PlayerSnapshot?): Disc? =
        s?.let { Disc(it.key, recordSizeChoice.sizeFor(it.durationMs), it.title, it.artist, it.hasArt) }

    private fun audible(): Boolean {
        val s = song ?: return false
        return s.sounding && needleOn == s.key
    }

    /** The record only changes once a new song (or "nothing playing") has stuck for a moment. */
    private fun settle(now: Long) {
        val next = discFor(song)
        if (next != candidate) {
            candidate = next
            candidateSince = now
        }
        if (next == settled) return
        var wait = 0L
        // Art usually arrives a beat after the title: wait a little for it, so a record
        // doesn't flash a plain label first.
        if (next != null && !next.hasArt && next.key != settled?.key) wait += ART_WAIT_MS
        // A player blip that flips the song away and back is ignored.
        if (settled != null && next?.key != settled?.key) wait += PlaybackArbiter.SONG_CHANGE_SETTLE_MS
        if (now - candidateSince >= wait) settled = next
    }

    private fun refresh() {
        val now = clock.nowMs()
        if (dropAngle != null && now >= dropUntil) dropAngle = null
        val s = song
        if (phase == Phase.Running) {
            settle(now)
            sendAll(arbiter.onPlayer(s?.sounding == true, s?.key, touching = s != null && needleOn == s.key,
                needleDown = needleOn != null))
            _target.value = TurntableTarget(
                disc = settled,
                playing = arbiter.wantPlaying,
                playAngle = playAngle(now),
                seekId = seekId,
                grabAngle = grabAngle,
                parkWhenPaused = parkWhenPaused,
                snapId = snapId,
            )
        }
        _ui.value = TurntableUiState(
            title = s?.title ?: "Nothing playing",
            artist = if (s == null) "Play something in any music app" else s.artist,
            durationMs = s?.durationMs ?: 0L,
            playing = arbiter.wantPlaying,
            audible = audible(),
            canGrab = settled != null,
            grabAngle = grabAngle,
        )
    }

    /** Where the needle belongs right now. */
    private fun playAngle(now: Long): Float {
        val progress = song?.let { PositionTracker.progress(it, now) }
            ?: (playedMs.toFloat() / NOMINAL_TRACK_MS).coerceIn(0f, 1f)
        return dropAngle ?: (settled?.size ?: RecordSize.LP).playingAngle(progress)
    }

    private fun sendAll(commands: List<PlayerCommand>) = commands.forEach(send)

    companion object {
        /** On app open: how long to wait for the player's current song before starting. */
        const val STARTUP_WAIT_MS = 1500L
        /** Back in the app: fresh player state arrives this soon after the start. */
        const val RESUME_SNAP_DELAY_MS = 250L
        /** How long a new song waits for its album art before getting a plain label. */
        const val ART_WAIT_MS = 1200L
        /** After dropping the needle by hand, hold it there this long while the player catches up. */
        const val DROP_HOLD_MS = 1500L
        /** Used to creep the needle inward when the player doesn't report a length. */
        const val NOMINAL_TRACK_MS = 4 * 60 * 1000L
    }
}
