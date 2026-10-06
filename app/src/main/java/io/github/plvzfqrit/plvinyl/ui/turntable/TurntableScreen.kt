package io.github.plvzfqrit.plvinyl.ui.turntable

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.TurntableBackdrop
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.drawPlatterFelt
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmArmForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.drawTonearmBaseForPlatter
import io.github.plvzfqrit.plvinyl.ui.drawing.tonearmColors

/**
 * The turntable, drawn from plain values. Nothing here moves or decides anything:
 * later the controller's UI state will feed these parameters.
 *
 * Layers, bottom to top: backdrop → tonearm base → platter + record → tonearm arm.
 */
@Composable
fun TurntableScreen(
    backdrop: BackdropStyle,
    finish: VinylFinish,
    /** The record on the platter, or null for an empty platter. */
    record: RecordSize?,
    seedKey: String,
    title: String?,
    artist: String?,
    art: Bitmap?,
    armAngleDeg: Float,
    armLift: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TurntableBackdrop(style = backdrop, art = art, artKey = seedKey)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same placement as the old app: a platter wider than the screen,
            // bleeding off the left edge, high up on the screen.
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
                if (record != null) {
                    VinylRecord(
                        art = art,
                        finish = finish,
                        seedKey = seedKey,
                        rotation = { 0f },
                        size = record,
                        title = title,
                        artist = artist,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // The shadow follows the record, not the platter-sized layer.
                                shadowElevation = 1.dp.toPx()
                                shape = RecordShape(record.radiusOfPlatter)
                                clip = false
                            },
                    )
                }
            }

            // Tonearm arm: above the record.
            Canvas(Modifier.fillMaxSize()) {
                drawTonearmArmForPlatter(
                    spindle = Offset(spindleX.toPx(), spindleY.toPx()),
                    platterRadiusPx = platterRadius.toPx(),
                    armAngleDeg = armAngleDeg,
                    lift = armLift,
                    colors = armColors,
                )
            }
        }
    }
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

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun TurntableScreenPreview() {
    TurntableScreen(
        backdrop = BackdropStyle.Charcoal,
        finish = VinylFinish.Black,
        record = RecordSize.LP,
        seedKey = "PLVinyl|plvzfqrit",
        title = "PLVinyl",
        artist = "plvzfqrit",
        art = null,
        armAngleDeg = RecordSize.LP.playingAngle(0.3f),
        armLift = 0f,
    )
}
