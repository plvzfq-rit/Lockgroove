package io.github.plvzfqrit.plvinyl.ui.turntable

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plvzfqrit.plvinyl.audio.SurfaceNoise
import io.github.plvzfqrit.plvinyl.core.history.Wear
import io.github.plvzfqrit.plvinyl.core.lighting.DayPreview
import io.github.plvzfqrit.plvinyl.core.lighting.RoomLight
import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.settings.MotionPolicy
import io.github.plvzfqrit.plvinyl.core.settings.MotionQuality
import io.github.plvzfqrit.plvinyl.core.settings.Settings
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.UserIntent
import io.github.plvzfqrit.plvinyl.core.ui.PageSwipe
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.Look
import io.github.plvzfqrit.plvinyl.ui.drawing.RECORD_TO_PLATTER
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableBackdrop
import io.github.plvzfqrit.plvinyl.ui.common.rememberBeat
import io.github.plvzfqrit.plvinyl.ui.common.rememberPhoneState
import io.github.plvzfqrit.plvinyl.ui.drawing.TonearmGeometry
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableGeometry
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.deckColors
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmArmForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmBaseForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.extractVinylPalette
import io.github.plvzfqrit.plvinyl.ui.drawing.platterFelt
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmPlacementFor
import io.github.plvzfqrit.plvinyl.ui.lighting.LampLight
import io.github.plvzfqrit.plvinyl.ui.lighting.RoomShade
import io.github.plvzfqrit.plvinyl.ui.lighting.TimeCard
import io.github.plvzfqrit.plvinyl.ui.lighting.lightFrom
import io.github.plvzfqrit.plvinyl.ui.lighting.litTextColor
import io.github.plvzfqrit.plvinyl.ui.lighting.rememberRoomLight
import io.github.plvzfqrit.plvinyl.ui.lighting.shadow
import io.github.plvzfqrit.plvinyl.ui.lighting.timeOfDayLabel
import io.github.plvzfqrit.plvinyl.ui.paint.PaintCard
import io.github.plvzfqrit.plvinyl.ui.paint.PaintTarget
import io.github.plvzfqrit.plvinyl.ui.settings.SettingsSheet
import io.github.plvzfqrit.plvinyl.ui.stats.NightStatsColors
import io.github.plvzfqrit.plvinyl.ui.stats.StatsScreen
import io.github.plvzfqrit.plvinyl.ui.stats.statsColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The turntable. The controller (in the view model) decides; the animator performs
 * each move; this draws them and passes your touches on as intents.
 *
 * Layers, bottom to top: backdrop and room shade → the deck (tonearm base, platter,
 * record, arm, knob) → the room's light, fixed in place → the deck's readable layer
 * (title, held label, front panel, dust cover) → the stats. Both deck layers slide
 * away together for the stats; the light stays, so the room looks the same.
 */
@Composable
fun TurntableScreen(
    modifier: Modifier = Modifier,
    vm: TurntableViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val arts by vm.art.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved by vm.look.collectAsStateWithLifecycle()
    // Painting: the card at the top, and the screen showing the paint being chosen (a
    // draft) instead of the saved look. Done saves it; back throws it away.
    var painting by remember { mutableStateOf<PaintTarget?>(null) }
    // Choosing a time of day for the sun: its card at the top, the room below as the preview.
    var choosingTime by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Look?>(null) }
    val look = draft ?: saved
    // After Done, keep showing the paint until the saved look has caught up (saving takes
    // a moment), so the old colors don't flash back for a frame.
    LaunchedEffect(painting, draft) {
        val d = draft ?: return@LaunchedEffect
        if (painting != null) return@LaunchedEffect
        withTimeoutOrNull(2_000) { vm.look.first { it == d } }
        draft = null
    }
    val backdrop = look.backdrop
    val deck = remember(look) { look.deckColors() }
    var showSettings by remember { mutableStateOf(false) }
    val animator = remember { TurntableAnimator() }
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    LifecycleStartEffect(vm) {
        vm.onStart()
        onStopOrDispose { vm.onStop() }
    }
    LaunchedEffect(animator) {
        launch { animator.runPlatter() }
        animator.run(
            target = vm.target,
            onStarted = vm::onMoveStarted,
            onFinished = { move, deck ->
                if (move is Move.DropNeedle) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.onMoveFinished(move, deck)
            },
        )
    }

    val scope = rememberCoroutineScope()
    // Swiping nudges the record on the felt.
    val swipeNudge = remember { Animatable(0f) }

    // The record physically on the platter (during a swap, still the old one).
    val disc = animator.disc
    val art = disc?.let { arts[it.key] }

    // Room lighting by the clock (or always day / night, from settings). While painting
    // it's daytime, so you see the true colors.
    val light = if (painting != null) RoomLight() else ui.roomLight
    // While you set the time of day, the light keeps up with the slider.
    val roomLight = rememberRoomLight(light, preview = ui.dayPreview, following = choosingTime)
    // What composition needs from the light (text and status bar colors), in coarse steps:
    // the drawing reads it at every frame, the screen doesn't rebuild for it.
    val nightNow by remember { derivedStateOf { (roomLight().night * 20f).roundToInt() / 20f } }
    PhoneSettingsEffects(settings, lightRoom = deck.isLight && nightNow < 0.5f, audible = ui.audible)
    // How often what moves is redrawn (the record, a long title): the biggest battery
    // cost. Lighter when the phone is warm or saving battery. Everything moving keeps the
    // same beat, so it all repaints together.
    val phone = rememberPhoneState(dimmed = false)
    val pace = MotionPolicy.pace(settings.motion, settings.spin, phone)
    val beat = rememberBeat(pace.framesPerSecond)
    SideEffect {
        // Singles turn at 45, i.e. 1.35× faster.
        animator.spinDegPerSec = settings.spin * (disc?.size?.spinFactor ?: 1f)
        animator.beat = beat
    }

    val counts by vm.history.counts.collectAsStateWithLifecycle()

    // ---------------- Surface noise ----------------
    // Crackle while the needle is on a sounding record, the locked-groove thump in the
    // lead-out, silence otherwise (and whenever the app isn't on screen).
    val noise = remember { SurfaceNoise() }
    DisposableEffect(noise) { onDispose { noise.release() } }
    var inForeground by remember { mutableStateOf(false) }
    LifecycleStartEffect(noise) {
        inForeground = true
        onStopOrDispose { inForeground = false }
    }
    val noiseSize = disc?.size ?: RecordSize.LP
    LaunchedEffect(settings.surfaceNoise, inForeground, ui.needleDown, ui.sounding, ui.inLeadOut, noiseSize) {
        val on = inForeground && ui.needleDown && (ui.sounding || ui.inLeadOut)
        noise.set(
            level = if (on) settings.surfaceNoise else 0f,
            lockedGrooveMs = if (ui.inLeadOut) noiseSize.revolutionMs else 0L,
        )
    }

    // ---------------- Dust cover ----------------
    // Closes after a while with nothing playing and nobody touching; opens on a tap or
    // as soon as music is wanted. The same idle timer switches the platter motor off:
    // it coasts to a stop and the app stops redrawing. Any touch or music wakes it.
    var coverClosed by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(settings.dustCover, ui.playing, lastTouch, ui.title, showSettings, painting, choosingTime) {
        animator.motorOn = true
        if (!settings.dustCover) coverClosed = false
        if (ui.playing || showSettings || painting != null || choosingTime) {
            coverClosed = false
            return@LaunchedEffect
        }
        delay(DUST_COVER_IDLE_MS)
        animator.motorOn = false
        if (settings.dustCover) coverClosed = true
    }

    // ---------------- Stats ("Liner notes") ----------------
    // One progress value, 0 = turntable, 1 = stats (see the core's PageSwipe).
    val statsProgress = remember { Animatable(0f) }
    val screenDensity = LocalDensity.current
    val statsShown by remember { derivedStateOf { statsProgress.value > 0f } }
    // The stats only take touches once the deck has gone (before that they're invisible).
    val statsOnScreen by remember { derivedStateOf { statsProgress.value > PageSwipe.FADE_OUT_END } }
    fun animateStats(open: Boolean) {
        val target = if (open) 1f else 0f
        val ms = PageSwipe.durationMs(statsProgress.value, target)
        scope.launch { statsProgress.animateTo(target, tween(ms, easing = LinearEasing)) }
    }
    BackHandler(enabled = statsShown) { animateStats(false) }
    // While the stats cover the deck, don't spend anything redrawing the record.
    LaunchedEffect(animator) {
        snapshotFlow { statsProgress.value < PageSwipe.FADE_OUT_END }.collect { animator.visible = it }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // ONE swipe for the whole page, both directions, from anywhere (except the
            // record and the arm, which the deck takes first): left opens the stats,
            // right goes back. Works mid-animation too: grab it and change your mind.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (painting != null || choosingTime) return@awaitEachGesture // no stats meanwhile
                    val startP = statsProgress.value
                    val startedOpen = PageSwipe.startsOpen(startP)
                    var dx = 0f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume(); dx += over
                    } ?: return@awaitEachGesture
                    val w = size.width.toFloat()
                    tracker.addPosition(start.uptimeMillis, start.position)
                    scope.launch { statsProgress.snapTo(PageSwipe.dragged(startP, dx, w)) }
                    horizontalDrag(start.id) { change ->
                        dx += change.positionChange().x
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        scope.launch { statsProgress.snapTo(PageSwipe.dragged(startP, dx, w)) }
                    }
                    val open = PageSwipe.settle(startedOpen, dx, tracker.calculateVelocity().x, w,
                        PageSwipe.FLING_DP_S * screenDensity.density)
                    if (open != startedOpen) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    animateStats(open)
                }
            }
            // Notice every touch (without taking it) to keep the dust cover open.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTouch > 1000L) lastTouch = now
                    }
                }
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        // A platter wider than the screen, bleeding off the left edge, high up.
        val platterSize = maxWidth * 1.213f
        val platterRadius = platterSize / 2
        val platterTop = (maxHeight - platterSize) / 2 * (1 - 0.65f)
        val platterLeft = (maxWidth - platterSize) / 2 * (1 + 2.13f)
        val spindleX = platterLeft + platterRadius
        val spindleY = platterTop + platterRadius
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHeight = FRONT_PANEL_HEIGHT + navInset
        val panelTop = with(screenDensity) { (screenHeight - panelHeight).toPx() }
        val armColors = deck.tonearm

        // The lamp hangs over the record's resting place.
        val lampCenter = with(screenDensity) { Offset(spindleX.toPx(), spindleY.toPx()) }
        val lampRadius = with(screenDensity) { platterRadius.toPx() } * (disc?.size?.radiusOfPlatter ?: RECORD_TO_PLATTER)
        // How far the deck has slid away for the stats, 0..1.
        val deckOut = { (statsProgress.value / PageSwipe.FADE_OUT_END).coerceIn(0f, 1f) }

        TurntableBackdrop(style = backdrop, art = art, artKey = disc?.key?.value, deckPaint = deck.deck)
        // The room's light on the background.
        RoomShade(roomLight, Modifier.fillMaxSize())

        // ---- The deck, lower layer: the turntable itself, lit by the room.
        Box(Modifier.fillMaxSize().deckSlide(deckOut)) {
            // Tonearm base: below the platter, so the platter overlaps the plate.
            Canvas(Modifier.fillMaxSize()) {
                drawTonearmBaseForPlatter(
                    spindle = Offset(spindleX.toPx(), spindleY.toPx()),
                    platterRadiusPx = platterRadius.toPx(),
                    colors = armColors,
                )
            }

            // The platter's shadow on the deck: cast away from the light, longer as the sun
            // gets low, short under the night lamp. (Android's own elevation shadows have
            // their light fixed at the top of the screen, so this one is drawn.)
            Canvas(Modifier.fillMaxSize()) {
                val s = roomLight().shadow()
                val r = platterRadius.toPx()
                val c = Offset(spindleX.toPx(), spindleY.toPx())
                fun softDisc(at: Offset, alpha: Float, spread: Float) = drawCircle(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = alpha),
                        (r - spread * 0.25f) / (r + spread) to Color.Black.copy(alpha = alpha),
                        1f to Color.Transparent,
                        center = at, radius = r + spread,
                    ),
                    r + spread, at,
                )
                softDisc(c, alpha = 0.10f, spread = 10.dp.toPx())                     // ambient, all round
                softDisc(c + s * 9.dp.toPx(), alpha = 0.24f, spread = 22.dp.toPx())   // cast by the light
            }

            // Platter + record.
            Box(
                Modifier
                    .offset(x = platterLeft, y = platterTop)
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(platterSize),
            ) {
                Spacer(Modifier.fillMaxSize().platterFelt())
                if (disc != null) {
                    VinylRecord(
                        art = art,
                        finish = look.finish,
                        grooveGlints = settings.grooveGlints,
                        wear = if (settings.recordWear) Wear.of(counts[disc.key] ?: 0) else 0f,
                        seedKey = disc.key.value,
                        rotation = { animator.spinAngle },
                        sheenAngle = { roomLight().lightFrom() - RoomLight.DAY_FROM },
                        size = disc.size,
                        title = disc.title,
                        artist = disc.artist,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Height: closer to the viewer = slightly bigger, bigger shadow.
                                val h = animator.recordHeight.value
                                val scale = 1f + 0.06f * h
                                scaleX = scale
                                scaleY = scale
                                // Travel: leaves up-left, arrives from above.
                                val tr = animator.recordTravel.value
                                if (tr >= 0f) {
                                    translationX = -0.30f * size.width * tr
                                    translationY = -1.15f * size.height * tr
                                } else {
                                    translationX = 0.08f * size.width * -tr
                                    translationY = -1.15f * size.height * -tr
                                }
                                translationX += swipeNudge.value
                                alpha = animator.recordAlpha.value
                                shadowElevation = (1f + 22f * h.coerceAtLeast(0f)).dp.toPx()
                                // The shadow follows the RECORD, not the platter-sized layer.
                                shape = RecordShape(disc.size.radiusOfPlatter)
                                clip = false
                            },
                    )
                }
            }

            // Tonearm arm, above the record. Grab the arm or headshell and drag: the
            // needle lifts (music pauses), and where you let go is where it drops and
            // plays from. Let go off the record and it goes back to rest.
            // Swipe the record itself to change songs: ← next, → previous.
            val latestUi by rememberUpdatedState(ui)
            // While a song plays, the needle creeps along the groove at well under a pixel a
            // second. Drawn at the nearest quarter pixel (at the needle), the arm repaints a
            // few times a second instead of every frame. Swings and drags still move it every
            // frame. (Touches use the exact angle.)
            val armStepDeg = with(screenDensity) {
                val stylusPx = TonearmGeometry.stylusTip.getDistance() * platterRadius.toPx() / TurntableGeometry.PLATTER_RADIUS
                Math.toDegrees(0.25 / stylusPx).toFloat()
            }
            val drawnArmDeg by remember(armStepDeg) {
                derivedStateOf { (animator.armAngle.value / armStepDeg).roundToInt() * armStepDeg }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Screen readers can't drag or swipe: offer song changes as actions.
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Next song") { vm.onIntent(UserIntent.Flick(+1)); true },
                            CustomAccessibilityAction("Previous song") { vm.onIntent(UserIntent.Flick(-1)); true },
                        )
                    }
                    .pointerInput(spindleX, spindleY, platterRadius) {
                        fun geometry() = tonearmPlacementFor(Offset(spindleX.toPx(), spindleY.toPx()), platterRadius.toPx())
                        fun sizeNow() = animator.disc?.size ?: RecordSize.LP

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val (pivot, unit) = geometry()
                            val onArm = latestUi.canGrab && ArmGeometry.touchesArm(
                                down.position.x, down.position.y, pivot.x, pivot.y, unit,
                                animator.armAngle.value, 14.dp.toPx(),
                            )
                            val spindle = Offset(spindleX.toPx(), spindleY.toPx())
                            val recordR = platterRadius.toPx() * sizeNow().radiusOfPlatter
                            val onRecord = !onArm && animator.disc != null && (down.position - spindle).getDistance() <= recordR
                            if (!onArm && !onRecord) return@awaitEachGesture
                            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                ?: return@awaitEachGesture

                            if (onRecord) {
                                var swipeDx = start.position.x - down.position.x
                                val max = 48.dp.toPx()
                                drag(start.id) { change ->
                                    swipeDx += change.positionChange().x
                                    scope.launch { swipeNudge.snapTo((swipeDx * 0.35f).coerceIn(-max, max)) }
                                    change.consume()
                                }
                                if (abs(swipeDx) > SWIPE_SKIP_DISTANCE.toPx()) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    vm.onIntent(UserIntent.Flick(if (swipeDx < 0f) +1 else -1))
                                }
                                scope.launch { swipeNudge.animateTo(0f, spring(dampingRatio = 0.6f)) }
                                return@awaitEachGesture
                            }

                            val startPointer = ArmGeometry.pointerDeg(down.position.x, down.position.y, pivot.x, pivot.y)
                            val startArm = animator.armAngle.value
                            var lastBucket = -1
                            vm.onIntent(UserIntent.HoldArm(startArm))
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                            fun onMove(change: PointerInputChange) {
                                val (pv, _) = geometry()
                                var delta = ArmGeometry.pointerDeg(change.position.x, change.position.y, pv.x, pv.y) - startPointer
                                if (delta > 180f) delta -= 360f
                                if (delta < -180f) delta += 360f
                                val size = sizeNow()
                                val a = (startArm + delta).coerceIn(ArmGeometry.REST_DEG, size.runOutDeg)
                                vm.onIntent(UserIntent.HoldArm(a))
                                // A light tick every 15 s of song you pass over.
                                val duration = latestUi.durationMs
                                if (duration > 0L && a >= size.leadInDeg) {
                                    val bucket = (size.progressAt(a) * duration / 15_000f).toInt()
                                    if (lastBucket != -1 && bucket != lastBucket) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    lastBucket = bucket
                                }
                                change.consume()
                            }
                            onMove(start)
                            drag(start.id) { onMove(it) }
                            vm.onIntent(UserIntent.LetGoOfArm)
                        }
                    }
            ) {
                drawTonearmArmForPlatter(
                    spindle = Offset(spindleX.toPx(), spindleY.toPx()),
                    platterRadiusPx = platterRadius.toPx(),
                    armAngleDeg = drawnArmDeg,
                    lift = animator.armLift.value,
                    colors = armColors,
                    shadow = roomLight().shadow(),
                )
            }

            // Settings: a knob under the tonearm, on the platter's baseline.
            val unit = platterRadius / TurntableGeometry.PLATTER_RADIUS          // dp per tonearm unit
            val pivotX = spindleX + unit * TurntableGeometry.PIVOT_FROM_SPINDLE.x
            val settingsCenterX = minOf(pivotX + unit * 40f, screenWidth - 12.dp - 27.dp)
            SettingsKnob(
                onClick = { showSettings = true },
                shadow = { roomLight().shadow() },
                // 54 dp square (42 dp knob + 6 dp margin): its bottom sits on the platter's baseline.
                modifier = Modifier.offset(x = settingsCenterX - 27.dp, y = platterTop + platterSize - 48.dp),
            )
        }

        // ---- The room's light: golden hour, or the desk lamp at night. It stays put
        // when the deck slides away, so the stats sit in the same light.
        LampLight(roomLight, lampCenter, lampRadius, Modifier.fillMaxSize(), glint = { 1f - deckOut() })

        // ---- The deck, upper layer: what stays readable at any hour.
        Box(Modifier.fillMaxSize().deckSlide(deckOut)) {
            // While you hold the arm: where in the song the needle would land.
            HeldLabel(
                held = vm.held,
                durationMs = ui.durationMs,
                size = disc?.size ?: RecordSize.LP,
                light = deck.isLight,
                modifier = Modifier.offset(
                    x = spindleX + (platterRadius / TurntableGeometry.PLATTER_RADIUS) * (TurntableGeometry.PIVOT_FROM_SPINDLE.x - 150f),
                    y = spindleY + (platterRadius / TurntableGeometry.PLATTER_RADIUS) * (TurntableGeometry.PIVOT_FROM_SPINDLE.y - 20f),
                ),
            )

            // Title and artist sit on the top plate, between the record and the front edge.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = platterTop + platterSize + 16.dp, bottom = panelHeight)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Title/artist fade to the new song. Long ones scroll (marquee) while the
                // record plays (the screen is redrawing for the spin then anyway), and rest
                // when it stops or the stats cover it. Light motion: a few passes per song.
                val passes = pace.titlePasses
                Crossfade(targetState = ui.title to ui.artist, animationSpec = tween(400), label = "title") { (t, a) ->
                    Column(Modifier.fillMaxWidth()) {
                        val text = litTextColor(deck.contentColor, RoomLight(night = nightNow))
                        MarqueeText(
                            t, text, MaterialTheme.typography.headlineMedium,
                            scrolling = ui.playing, beat = beat, onScreen = animator.visible, passes = passes,
                        )
                        if (a != null) {
                            MarqueeText(
                                a, text.copy(alpha = 0.6f), MaterialTheme.typography.bodyMedium,
                                scrolling = ui.playing, beat = beat, onScreen = animator.visible, passes = passes,
                            )
                        }
                    }
                }
            }

            // The controls live on the FRONT of the plinth:
            // |◀◀ previous · ▶■ start/stop (stop sends the arm home) · ▲▼ cue (pause in
            // place, needle hovers) · ↻1 repeat one · ▶▶| next.
            // It takes the room's light itself (so its keys stay readable and lit keys glow).
            val panelPalette = remember(art) { art?.let(::extractVinylPalette) }
            FrontPanel(
                style = backdrop,
                albumPalette = panelPalette,
                paint = deck.panelPaint,
                playing = ui.playing,
                repeatOne = ui.repeatOne,
                light = roomLight,
                lampCenter = lampCenter - Offset(0f, panelTop),
                lampRadius = lampRadius,
                onStartStop = { coverClosed = false; vm.onIntent(UserIntent.StartStop) },
                onCue = { coverClosed = false; vm.onIntent(UserIntent.Cue) },
                onRepeat = { vm.onIntent(UserIntent.ToggleRepeat) },
                onPrevious = { coverClosed = false; vm.onIntent(UserIntent.Flick(-1)) },
                onNext = { coverClosed = false; vm.onIntent(UserIntent.Flick(+1)) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Dust cover over the top plate (not the front panel: that stays reachable).
            DustCover(
                closed = coverClosed,
                light = deck.isLight,
                onOpen = { coverClosed = false },
                night = { roomLight().night },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight - panelHeight),
            )

            if (showSettings) {
                SettingsSheet(
                    deck = deck,
                    deckPaint = Color(look.deckPaintOrSeed),
                    finish = look.finish,
                    art = art,
                    seedKey = disc?.key?.value,
                    settings = settings,
                    learnedCrossfadeMs = ui.learnedCrossfadeMs,
                    // Going Custom keeps the paint (or starts from the current background's color).
                    // A preset is the whole look: its own front panel comes back with it.
                    onSelect = { vm.setLook(look.copy(backdrop = it, panelPaint = null)) },
                    onPaint = {
                        showSettings = false
                        // Going Custom starts from the paint so far (or the current background's color).
                        draft = saved.copy(backdrop = BackdropStyle.Custom, deckPaint = saved.deckPaintOrSeed)
                        painting = PaintTarget.Deck
                    },
                    onSelectFinish = { vm.setLook(look.copy(finish = it)) },
                    onSettings = { new -> vm.updateSettings { new } },
                    onResetCrossfade = { ui.player?.let(vm::forgetCrossfade) },
                    onPreviewDay = {
                        showSettings = false                                // so you can watch
                        vm.previewDay()
                    },
                    slowDown = pace.slowDown,
                    onSetTime = {
                        showSettings = false                                // so you can see the room
                        vm.stopPreviewDay()
                        choosingTime = true
                    },
                    onDismiss = { showSettings = false },
                )
            }
        }

        // While a sped-up day plays: what time it is in it, and a way to stop it early
        // (the ✕, or back).
        ui.dayPreview?.let { preview ->
            BackHandler { vm.stopPreviewDay() }
            DayPreviewPill(
                preview,
                onStop = vm::stopPreviewDay,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
            )
        }

        // ---- Choosing a time of day: the card on top, the room below as the preview.
        if (choosingTime) {
            // Nothing to throw away here (the time applies as you slide): a tap outside is Done.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { choosingTime = false } })
            BackHandler { choosingTime = false }
            TimeCard(
                held = settings.sunTime,
                onHold = { t -> vm.updateSettings { it.copy(sunTime = t) } },
                onDone = { choosingTime = false },
                tick = { if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ---- Painting: the card on top, the real turntable as the preview.
        painting?.let { target ->
            // While the card is open the turntable is only a preview: touches on it do
            // nothing, so a key or the arm can't be bumped by accident.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            )
            BackHandler { draft = null; painting = null }
            PaintCard(
                target = target,
                onTarget = { painting = it },
                color = when (target) {
                    PaintTarget.Deck -> look.deckPaint.takeIf { look.backdrop == BackdropStyle.Custom }
                    PaintTarget.Panel -> look.panelPaint
                },
                matchColor = when (target) {
                    PaintTarget.Deck -> look.deckPaintOrSeed
                    PaintTarget.Panel -> look.matchingPanel
                },
                onColor = { c ->
                    draft = when (target) {
                        PaintTarget.Deck -> look.copy(backdrop = BackdropStyle.Custom, deckPaint = c ?: look.deckPaintOrSeed)
                        PaintTarget.Panel -> look.copy(panelPaint = c)
                    }
                },
                onDone = {
                    draft?.let(vm::setLook)
                    painting = null
                },
                tick = { if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (statsOnScreen) {
            // Pops in on the same background, in the same light, once the deck has gone.
            // In the dark room the stats use the dark colors, whatever the background.
            StatsScreen(
                history = vm.history,
                colors = if (nightNow > 0.5f) NightStatsColors else deck.statsColors(),
                reveal = { PageSwipe.reveal(statsProgress.value) },
                beat = beat,
            )
        }
    }
}

/**
 * The deck sliding off to the left like it's going down a slope: it tilts away and
 * shrinks a little, and the closer it gets to the edge, the more it blurs (slowly at
 * first, then quickly). Both deck layers use it, so they move as one.
 * @param out how far it has gone, 0..1 (read only here, in the layer)
 */
private fun Modifier.deckSlide(out: () -> Float): Modifier = graphicsLayer {
    val o = out()
    val slope = o * o
    translationX = -o * size.width
    rotationY = 22f * slope                                 // left side tips away from you
    cameraDistance = 12f * density
    transformOrigin = TransformOrigin(0f, 0.5f)
    scaleX = 1f - 0.12f * slope
    scaleY = 1f - 0.12f * slope
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = if (o > 0.001f && o < 1f) {
            val r = 2.dp.toPx() + slope * 30.dp.toPx()
            BlurEffect(r, r * 0.6f, TileMode.Decal)
        } else null
        alpha = if (o >= 1f) 0f else 1f
    } else {
        alpha = 1f - slope                                  // no blur before Android 12: fade
    }
}

/**
 * The sped-up day's time, and a ✕ to stop it. Its own little composable: it updates as
 * the minutes fly by, without rebuilding the rest of the screen.
 */
@Composable
private fun DayPreviewPill(preview: DayPreview, onStop: () -> Unit, modifier: Modifier) {
    val minute by produceState(preview.minuteAt(SystemClock.elapsedRealtime()).toInt(), preview) {
        while (true) withFrameMillis { value = preview.minuteAt(SystemClock.elapsedRealtime()).toInt() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClickLabel = "Stop the sped-up day", onClick = onStop)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(timeOfDayLabel(minute.toFloat()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
    }
}

/** The settings that act on the phone itself rather than the turntable. */
@Composable
private fun PhoneSettingsEffects(settings: Settings, lightRoom: Boolean, audible: Boolean) {
    val view = LocalView.current
    // Keep the screen awake while the record is playing (like a video app does).
    DisposableEffect(view, audible, settings.keepScreenOn) {
        view.keepScreenOn = audible && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // Haptics on/off for the whole screen (every click and tick goes through this view).
    SideEffect { view.isHapticFeedbackEnabled = settings.haptics }
    if (view.isInEditMode) return
    val window = (view.context as Activity).window
    // Dark status-bar icons on the light Paper background by day, light icons otherwise.
    SideEffect { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightRoom }
    // Battery / Balanced motion: ask the display for a 60 Hz mode (same resolution).
    // Smooth = no preference, so 90/120 Hz phones run at their full rate.
    LaunchedEffect(settings.motion) {
        val display = view.display ?: return@LaunchedEffect
        val current = display.mode
        val modeId = if (settings.motion != MotionQuality.Smooth) {
            display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - 60f) }
                ?.modeId ?: 0
        } else 0
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }
}

/** The dust cover closes after this long with nothing playing and no touches. */
private const val DUST_COVER_IDLE_MS = 2 * 60 * 1000L

/** Swipe the record at least this far to change songs. */
private val SWIPE_SKIP_DISTANCE = 72.dp

/**
 * "1:23 / 3:45" while a finger holds the arm (or "Back to rest"), just above the
 * pivot so the finger doesn't cover it. It collects the held angle itself, so a drag
 * only redraws this label, not the whole screen.
 */
@Composable
private fun HeldLabel(
    held: StateFlow<Float?>,
    durationMs: Long,
    size: RecordSize,
    light: Boolean,
    modifier: Modifier,
) {
    val angle by held.collectAsStateWithLifecycle()
    val a = angle ?: return
    if (durationMs <= 0L) return
    val label = if (a >= size.leadInDeg - 1.5f) {
        "${formatTime((size.progressAt(a) * durationMs).toLong())} / ${formatTime(durationMs)}"
    } else "Back to rest"
    Text(
        text = label,
        color = if (light) Color(0xFF2B2A28) else Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background((if (light) Color(0xFFF5F2ED) else Color(0xFF303030)).copy(alpha = 0.92f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** m:ss */
private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** A circle of [fraction] × the layer's radius, centered (the record's outline). */
private class RecordShape(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = minOf(size.width, size.height) / 2f * fraction
        val c = Offset(size.width / 2f, size.height / 2f)
        return Outline.Rounded(RoundRect(Rect(c, r), CornerRadius(r, r)))
    }
    override fun equals(other: Any?) = other is RecordShape && other.fraction == fraction
    override fun hashCode() = fraction.hashCode()
}
