package io.github.plvzfqrit.plvinyl.ui.turntable

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plvzfqrit.plvinyl.core.model.ArmGeometry
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.core.turntable.Move
import io.github.plvzfqrit.plvinyl.core.turntable.UserIntent
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableBackdrop
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableGeometry
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.drawPlatterFelt
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmArmForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmBaseForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmColors
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmPlacementFor
import kotlinx.coroutines.launch

/**
 * The turntable. The controller (in the view model) decides; the animator performs
 * each move; this draws them and passes your touches on as intents.
 *
 * Layers, bottom to top: backdrop → tonearm base → platter + record → tonearm arm
 * → title.
 */
@Composable
fun TurntableScreen(
    backdrop: BackdropStyle,
    finish: VinylFinish,
    modifier: Modifier = Modifier,
    vm: TurntableViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val arts by vm.art.collectAsStateWithLifecycle()
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

    // The record physically on the platter (during a swap, still the old one).
    val disc = animator.disc
    val art = disc?.let { arts[it.key] }

    Box(modifier.fillMaxSize()) {
        TurntableBackdrop(style = backdrop, art = art, artKey = disc?.key?.value)

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
                        finish = finish,
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
            val latestUi by rememberUpdatedState(ui)
            Canvas(
                Modifier
                    .fillMaxSize()
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
                            if (!onArm) return@awaitEachGesture
                            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                ?: return@awaitEachGesture

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

            // Title and artist sit on the top plate, under the record.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = platterTop + platterSize + 16.dp)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Title/artist fade to the new song. Long ones scroll (marquee).
                Crossfade(targetState = ui.title to ui.artist, animationSpec = tween(400), label = "title") { (t, a) ->
                    Column(Modifier.fillMaxWidth()) {
                        MarqueeText(t, backdrop.contentColor, MaterialTheme.typography.headlineMedium)
                        if (a != null) {
                            MarqueeText(a, backdrop.contentColor.copy(alpha = 0.6f), MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
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
