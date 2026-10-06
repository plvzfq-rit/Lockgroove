package io.github.plvzfqrit.plvinyl.ui.turntable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.Disc
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.turntable.Deck
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.MovePlanner
import io.github.plvzfqrit.plvinyl.core.turntable.TurntableTarget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Default platter speed (12 s per turn). */
const val SPIN_DEG_PER_SEC = 30f

/** Groove-tracking moves up to this size are the slow, steady glide. */
private const val TRACK_SNAP_DEG = 0.6f

/**
 * Animates the turntable. The core's MovePlanner decides each physical move; this
 * performs it, reports it, and asks for the next one. It also turns the platter.
 */
@Stable
class TurntableAnimator {
    // ---- Tonearm
    val armAngle = Animatable(ArmGeometry.REST_DEG)
    /** 0 = down (on the record or in the rest clip), 1 = raised. */
    val armLift = Animatable(0f)

    // ---- Record
    /** 0 = sitting on the platter, 1 = held up in the air. */
    val recordHeight = Animatable(0f)
    /** 0 = over the platter, +1 = carried off-screen (leaving), -1 = arriving from off-screen. */
    val recordTravel = Animatable(0f)
    val recordAlpha = Animatable(1f)
    /** How much of the platter's spin the record gets (0 while held by hand). */
    private val grip = Animatable(0f)

    /** The record currently on (or above) the platter. */
    var disc by mutableStateOf<Disc?>(null)
        private set

    /** The record the needle rests on (null = needle up or parked). */
    private var needleOn: SongKey? = null

    /** Platter speed in degrees per second. */
    var spinDegPerSec by mutableFloatStateOf(SPIN_DEG_PER_SEC)

    /** Record rotation in degrees. Read it only inside graphicsLayer / draw. */
    var spinAngle by mutableFloatStateOf(0f)
        private set

    /** Redraw the spinning record at most this often (ms). 0 = every screen refresh. */
    var frameIntervalMs by mutableStateOf(0L)

    /** Platter motor. Off = spins down and stops, and then nothing is redrawn. */
    var motorOn by mutableStateOf(true)

    /** False while the turntable isn't visible (e.g. the stats cover it): no redraws. */
    var visible by mutableStateOf(true)

    /** Motor speed, 0..1 (spins up / down like a real platter). */
    private val motor = Animatable(1f)

    val deck: Deck
        get() = Deck(disc, armAngle.value, armUp = armLift.value >= 0.01f, needleOn = needleOn)

    // ------------------------------------------------------------------ loops

    /** Keeps the platter turning; the record follows it according to grip. */
    suspend fun runPlatter() = coroutineScope {
        launch {
            snapshotFlow { motorOn }.collect { on ->
                if (on) motor.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
                else motor.animateTo(0f, tween(2500, easing = LinearOutSlowInEasing))
            }
        }
        var last = 0L
        while (true) {
            // Nothing to show: sleep until there is (no frames at all meanwhile).
            if (!visible || disc == null || (motor.value == 0f && !motorOn)) {
                snapshotFlow { visible && disc != null && (motorOn || motor.value > 0f) }.first { it }
                last = 0L
            }
            val interval = frameIntervalMs
            if (interval > 0L) delay(interval)
            withFrameNanos { now ->
                if (last != 0L) {
                    // Clamp: after a hiccup (or coming back) keep going smoothly instead of jumping.
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    val step = dt * spinDegPerSec * grip.value * motor.value
                    if (step != 0f) spinAngle = (spinAngle + step) % 360f
                }
                last = now
            }
        }
    }

    /** Runs forever, moving the turntable toward [target] one planned move at a time. */
    suspend fun run(
        target: StateFlow<TurntableTarget>,
        onStarted: (Move, Deck) -> Unit,
        onFinished: (Move, Deck) -> Unit,
    ) {
        var lastSeekId = target.value.seekId
        var lastSnapId = 0
        while (true) {
            val t = target.value
            val snap = t.snapId != lastSnapId
            lastSnapId = t.snapId
            val seeked = t.seekId != lastSeekId
            lastSeekId = t.seekId
            val move = MovePlanner.next(deck, t, seeked, snap)
            if (move == null) {
                target.first { it != t }                 // nothing to do: wait for a change
                continue
            }
            onStarted(move, deck)
            if (move is Move.Track) {
                // The groove glide is slow (up to 2 s): let a pause, a grab or a song
                // change cut it short instead of waiting for it to finish.
                coroutineScope {
                    val glide = launch { perform(move) }
                    val watch = launch {
                        target.first { t ->
                            MovePlanner.interrupts(move, deck, t, seeked = t.seekId != lastSeekId, snap = t.snapId != lastSnapId)
                        }
                        glide.cancel()
                    }
                    glide.join()
                    watch.cancel()
                }
            } else {
                perform(move)
            }
            onFinished(move, deck)
        }
    }

    // ----------------------------------------------------------------- moves

    private suspend fun perform(move: Move) {
        when (move) {
            is Move.Lift -> {
                needleOn = null
                armLift.animateTo(1f, tween(if (move.byHand) 150 else 350, easing = FastOutSlowInEasing))
            }
            // Cue lever descent: slow and damped, no bounce.
            Move.Lower -> armLift.animateTo(0f, tween(550, easing = LinearOutSlowInEasing))
            is Move.DropNeedle -> {
                armLift.animateTo(0f, tween(550, easing = LinearOutSlowInEasing))
                needleOn = move.on
            }
            is Move.Swing -> {
                val degrees = abs(armAngle.value - move.toDeg)
                val ms = max(450f, degrees / 40f * 1000f).roundToInt()      // ~40°/s, never snappier than 450 ms
                armAngle.animateTo(move.toDeg, tween(ms, easing = FastOutSlowInEasing))
            }
            // The groove pulling the needle: a slow, continuous glide. Small steps take
            // about as long as the groove needs to pull that far, so the arm creeps
            // inward steadily. Bigger moves (the glide into the lead-out) take 1.5 s.
            is Move.Track -> {
                val ms = if (abs(armAngle.value - move.toDeg) <= TRACK_SNAP_DEG) 2000 else 1500
                armAngle.animateTo(move.toDeg, tween(ms, easing = LinearEasing))
            }
            is Move.FollowHand -> armAngle.snapTo(move.toDeg)
            Move.RemoveRecord -> removeRecord()
            is Move.PlaceRecord -> placeRecord(move.disc)
            is Move.UpdateDisc -> disc = move.disc
            is Move.SnapTo -> snapTo(move.deck)
        }
    }

    private suspend fun snapTo(d: Deck) {
        disc = d.disc
        recordHeight.snapTo(0f)
        recordTravel.snapTo(0f)
        recordAlpha.snapTo(1f)
        grip.snapTo(if (d.disc != null) 1f else 0f)
        armLift.snapTo(if (d.armUp) 1f else 0f)
        armAngle.snapTo(d.armDeg)
        needleOn = d.needleOn
    }

    /** A hand stops the record, lifts it, and carries it away up-left. */
    private suspend fun removeRecord() {
        grip.animateTo(0f, tween(180))
        recordHeight.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        coroutineScope {
            launch { recordTravel.animateTo(1f, tween(450, easing = FastOutLinearInEasing)) }
            launch { delay(200); recordAlpha.animateTo(0f, tween(250)) }
        }
        disc = null
        recordTravel.snapTo(0f)
        recordHeight.snapTo(0f)
        recordAlpha.snapTo(1f)
    }

    /** The new record arrives from above, drops onto the platter at 0°, then the platter picks it up. */
    private suspend fun placeRecord(d: Disc) {
        grip.snapTo(0f)
        spinAngle = 0f
        recordHeight.snapTo(1f)
        recordTravel.snapTo(-1f)
        recordAlpha.snapTo(0f)
        disc = d
        coroutineScope {
            launch { recordTravel.animateTo(0f, tween(520, easing = LinearOutSlowInEasing)) }
            launch { recordAlpha.animateTo(1f, tween(260)) }
        }
        // Drop: settles with a tiny bounce as the vinyl lands on the felt.
        recordHeight.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f))
        grip.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
}
