package io.github.plvzfqrit.plvinyl.ui.turntable

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.settings.MotionQuality
import io.github.plvzfqrit.plvinyl.core.settings.Settings
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.UserIntent
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.RECORD_TO_PLATTER
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableBackdrop
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableGeometry
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.drawPlatterFelt
import io.github.plvzfqrit.plvinyl.ui.drawing.extractVinylPalette
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmArmForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmBaseForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmColors
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmPlacementFor
import io.github.plvzfqrit.plvinyl.ui.lighting.LampLight
import io.github.plvzfqrit.plvinyl.ui.lighting.RoomShade
import io.github.plvzfqrit.plvinyl.ui.lighting.litTextColor
import io.github.plvzfqrit.plvinyl.ui.lighting.rememberRoomLight
import io.github.plvzfqrit.plvinyl.ui.settings.SettingsSheet
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The turntable. The controller (in the view model) decides; the animator performs
 * each move; this draws them and passes your touches on as intents.
 *
 * Layers, bottom to top: backdrop → tonearm base → platter + record → tonearm arm
 * → title.
 */
@Composable
fun TurntableScreen(
    modifier: Modifier = Modifier,
    vm: TurntableViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val arts by vm.art.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    val backdrop = look.backdrop
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

    PhoneSettingsEffects(settings, lightRoom = backdrop.isLight && ui.roomLight.night < 0.5f, audible = ui.audible)
    // Room lighting by the clock (or always day / night, from settings).
    val roomLight = rememberRoomLight(ui.roomLight)
    SideEffect {
        // Singles turn at 45, i.e. 1.35× faster.
        animator.spinDegPerSec = settings.spin.degPerSec * (disc?.size?.spinFactor ?: 1f)
        // How often the record is redrawn (the biggest battery cost).
        animator.frameIntervalMs = settings.motion.fps.let { if (it == 0) 0L else 1000L / it }
    }

    Box(modifier.fillMaxSize()) {
        TurntableBackdrop(style = backdrop, art = art, artKey = disc?.key?.value)
        // The room's light on the background.
        RoomShade(roomLight, Modifier.fillMaxSize())

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A platter wider than the screen, bleeding off the left edge, high up.
            val platterSize = maxWidth * 1.213f
            val platterRadius = platterSize / 2
            val platterTop = (maxHeight - platterSize) / 2 * (1 - 0.65f)
            val platterLeft = (maxWidth - platterSize) / 2 * (1 + 2.13f)
            val spindleX = platterLeft + platterRadius
            val spindleY = platterTop + platterRadius

            val armColors = remember(backdrop) { backdrop.tonearmColors() }

            // Tonearm base: below the platter, so the platter overlaps the plate.
            Canvas(Modifier.fillMaxSize()) {
                drawTonearmBaseForPlatter(
                    spindle = Offset(spindleX.toPx(), spindleY.toPx()),
                    platterRadiusPx = platterRadius.toPx(),
                    colors = armColors,
                )
            }

            // Platter + record.
            Box(
                Modifier
                    .offset(x = platterLeft, y = platterTop)
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(platterSize)
                    .shadow(elevation = 16.dp, shape = CircleShape, clip = false),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPlatterFelt(center = center, outerRadius = size.minDimension / 2f)
                }
                if (disc != null) {
                    VinylRecord(
                        art = art,
                        finish = look.finish,
                        grooveGlints = settings.grooveGlints,
                        seedKey = disc.key.value,
                        rotation = { animator.spinAngle },
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
                    armAngleDeg = animator.armAngle.value,
                    lift = animator.armLift.value,
                    colors = armColors,
                )
            }

            // While you hold the arm: where in the song the needle would land.
            val held = ui.grabAngle
            if (held != null && ui.durationMs > 0L) {
                val unitDp = platterRadius / TurntableGeometry.PLATTER_RADIUS
                val heldSize = disc?.size ?: RecordSize.LP
                val label = if (held >= heldSize.leadInDeg - 1.5f) {
                    "${formatTime((heldSize.progressAt(held) * ui.durationMs).toLong())} / ${formatTime(ui.durationMs)}"
                } else "Back to rest"
                // Just above the pivot, so your finger doesn't cover it.
                Text(
                    text = label,
                    color = if (backdrop.isLight) Color(0xFF2B2A28) else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .offset(
                            x = spindleX + unitDp * (TurntableGeometry.PIVOT_FROM_SPINDLE.x - 150f),
                            y = spindleY + unitDp * (TurntableGeometry.PIVOT_FROM_SPINDLE.y - 20f),
                        )
                        .background(
                            (if (backdrop.isLight) Color(0xFFF5F2ED) else Color(0xFF303030)).copy(alpha = 0.92f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Lamp / golden-hour light on the deck: over the platter, record and arm,
            // under the title and keys (those stay readable at any hour).
            val density = LocalDensity.current
            val lampCenter = with(density) { Offset(spindleX.toPx(), spindleY.toPx()) }
            val lampRadius = with(density) { platterRadius.toPx() } * (disc?.size?.radiusOfPlatter ?: RECORD_TO_PLATTER)
            LampLight(roomLight, lampCenter, lampRadius, Modifier.fillMaxSize())

            // Settings: a knob under the tonearm, on the platter's baseline.
            val unit = platterRadius / TurntableGeometry.PLATTER_RADIUS          // dp per tonearm unit
            val pivotX = spindleX + unit * TurntableGeometry.PIVOT_FROM_SPINDLE.x
            val settingsCenterX = minOf(pivotX + unit * 40f, maxWidth - 12.dp - 27.dp)
            SettingsKnob(
                onClick = { showSettings = true },
                // 54 dp square (42 dp knob + 6 dp margin): its bottom sits on the platter's baseline.
                modifier = Modifier
                    .offset(x = settingsCenterX - 27.dp, y = platterTop + platterSize - 48.dp)
                    .graphicsLayer { alpha = 1f - 0.35f * roomLight().night },
            )

            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val panelHeight = FRONT_PANEL_HEIGHT + navInset

            // Title and artist sit on the top plate, between the record and the front edge.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = platterTop + platterSize + 16.dp, bottom = panelHeight)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Title/artist fade to the new song. Long ones scroll (marquee).
                Crossfade(targetState = ui.title to ui.artist, animationSpec = tween(400), label = "title") { (t, a) ->
                    Column(Modifier.fillMaxWidth()) {
                        val text = litTextColor(backdrop.contentColor, ui.roomLight)
                        MarqueeText(t, text, MaterialTheme.typography.headlineMedium)
                        if (a != null) {
                            MarqueeText(a, text.copy(alpha = 0.6f), MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // The controls live on the FRONT of the plinth:
            // |◀◀ previous · ▶■ start/stop (stop sends the arm home) · ▲▼ cue (pause in
            // place, needle hovers) · ↻1 repeat one · ▶▶| next.
            val panelPalette = remember(art) { art?.let(::extractVinylPalette) }
            FrontPanel(
                style = backdrop,
                albumPalette = panelPalette,
                playing = ui.playing,
                repeatOne = ui.repeatOne,
                night = { roomLight().night },
                onStartStop = { vm.onIntent(UserIntent.StartStop) },
                onCue = { vm.onIntent(UserIntent.Cue) },
                onRepeat = { vm.onIntent(UserIntent.ToggleRepeat) },
                onPrevious = { vm.onIntent(UserIntent.Flick(-1)) },
                onNext = { vm.onIntent(UserIntent.Flick(+1)) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            if (showSettings) {
                SettingsSheet(
                    current = backdrop,
                    finish = look.finish,
                    art = art,
                    seedKey = disc?.key?.value,
                    settings = settings,
                    learnedCrossfadeMs = ui.learnedCrossfadeMs,
                    onSelect = { vm.setLook(look.copy(backdrop = it)) },
                    onSelectFinish = { vm.setLook(look.copy(finish = it)) },
                    onSettings = { new -> vm.updateSettings { new } },
                    onResetCrossfade = { ui.player?.let(vm::forgetCrossfade) },
                    onDismiss = { showSettings = false },
                )
            }
        }
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

/** Swipe the record at least this far to change songs. */
private val SWIPE_SKIP_DISTANCE = 72.dp

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
