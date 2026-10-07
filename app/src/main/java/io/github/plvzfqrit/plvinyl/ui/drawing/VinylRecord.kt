package io.github.plvzfqrit.plvinyl.ui.drawing

import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import android.graphics.Bitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Color as AColor

/* ============================================================================
 * Vinyl record, single-song pressing.
 *
 * Layout (fractions of the RECORD radius, center → edge):
 *   0.000–0.028  spindle hole (the platter's spindle shows through)
 *   0.028–0.450  paper label: album art, printed look, pressed ring
 *   0.450–0.468  raised vinyl lip around the label
 *   0.468–0.600  dead wax, with the lead-out spiral + locked groove
 *   0.600–0.955  ONE continuous groove band (no track gaps, it's one song),
 *                with subtle loud/quiet variation like real music
 *   0.955–0.975  lead-in grooves (wider spaced)
 *   0.975–1.000  smooth edge + rounded rim
 *
 * Everything that belongs to the disc rotates (one cached layer, so spinning
 * costs nothing). The light reflection is a separate layer that does NOT
 * rotate: that's what makes a real record look like it's turning.
 * ========================================================================== */

enum class VinylFinish(val displayName: String) {
    Black("Black"),
    Smoke("Clear smoke"),
    AlbumColor("Album color"),
    Marble("Marble");
}

/** Record radius as a fraction of the platter radius (sits just inside the rim). */
const val RECORD_TO_PLATTER = 0.985f


@Composable
fun VinylRecord(
    /** Album art for the label. Null = a plain printed label (title and artist). */
    art: Bitmap?,
    finish: VinylFinish,
    seedKey: String,
    rotation: () -> Float,              // read only inside graphicsLayer → no redraws while spinning
    modifier: Modifier = Modifier,
    /**
     * How far to turn the reflection (degrees) from its usual top-left light, so it faces
     * wherever the light comes from. Read only inside graphicsLayer.
     */
    sheenAngle: () -> Float = { 0f },
    /** Faint glints on the grooves that make the spin easier to see. */
    grooveGlints: Boolean = true,
    /** 12" LP or 7" single. */
    size: RecordSize = RecordSize.LP,
    /** Printed on the label when there's no art. */
    title: String? = null,
    artist: String? = null,
    /** 0 = fresh, 1 = played to death: scuffs, spindle marks, a duller shine. */
    wear: Float = 0f,
) {
    val artImage = remember(art) { art?.asImageBitmap() }
    val seed = remember(seedKey) { seedKey.hashCode() }
    val palette = remember(art, seed) { art?.let(::extractVinylPalette) ?: paletteFromSeed(seed) }
    val textMeasurer = rememberTextMeasurer()
    val marble = rememberMarbleBitmap(
        enabled = finish == VinylFinish.Marble, seed = seed, palette = palette, size = 384,
    )
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val px = min(constraints.maxWidth, constraints.maxHeight).coerceAtLeast(1)

        // Bake the whole disc (material, ~200 grooves, label) into ONE bitmap, once per
        // song / finish / size. Spinning then just rotates a single image on the GPU,
        // instead of re-drawing hundreds of shapes every frame (that was the jank).
        val format = size
        val disc = remember(px, artImage, finish, marble, seed, palette, grooveGlints, format, title, artist, wear) {
            bake(px, density) {
                val r = this.size.minDimension / 2f * format.radiusOfPlatter
                // Everything on the disc is clipped to a ring: the center hole is really
                // a hole (the platter's spindle shows through).
                val disc = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(center, r))
                    addOval(Rect(center, r * format.hole))
                }
                clipPath(disc) {
                    drawMaterial(finish, palette, marble, r)
                    drawGrooves(r, seed, grooveGlints, format)
                    if (wear > 0f) drawGrooveWear(r, seed, wear, format)
                    if (artImage != null) drawLabel(artImage, r, format)
                    else drawPlainLabel(r, seed, format, title, artist, textMeasurer)
                    if (wear > 0f) drawSpindleMarks(r, seed, wear, format)
                }
            }
        }
        // The reflection is baked too (it never rotates, so it never needs redrawing).
        val sheen = remember(px, finish, format, wear) {
            bake(px, density) {
                val r = this.size.minDimension / 2f * format.radiusOfPlatter
                // Worn vinyl has lost some of its gloss.
                val gloss = (if (finish == VinylFinish.Marble) 0.8f else 1f) * (1f - 0.35f * wear)
                drawSheen(r, strength = gloss, format)
            }
        }

        // Rotating disc: one image, rotated in its own layer.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation() }
        ) {
            drawImage(disc, filterQuality = FilterQuality.Low)   // bilinear: smooth while rotating
        }
        // The reflection on top: it doesn't spin with the record, it faces the light.
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = sheenAngle() }) {
            drawImage(sheen)
        }
    }
}

/** Draws [block] into a fresh square bitmap of [px] × [px]. */
private fun bake(px: Int, density: Density, block: DrawScope.() -> Unit): ImageBitmap {
    val bmp = ImageBitmap(px, px)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(bmp),
        size = Size(px.toFloat(), px.toFloat()),
        block = block,
    )
    return bmp
}

// ------------------------------------------------------------------ material

private fun DrawScope.drawMaterial(
    finish: VinylFinish, palette: VinylPalette, marble: ImageBitmap?, r: Float,
) {
    when (finish) {
        VinylFinish.Black -> drawCircle(Color(0xFF0E0E0F), r, center)

        // Translucent smoky vinyl: the felt and spindle show through.
        VinylFinish.Smoke -> drawCircle(Color(0xFF3A3A3E).copy(alpha = 0.55f), r, center)

        // Translucent colored vinyl, a little deeper toward the edge.
        VinylFinish.AlbumColor -> drawCircle(
            Brush.radialGradient(
                0f to palette.tint.copy(alpha = 0.80f),
                0.6f to palette.tint.copy(alpha = 0.80f),
                1f to lerpColor(palette.tint, Color.Black, 0.25f).copy(alpha = 0.85f),
                center = center, radius = r,
            ),
            r, center,
        )

        VinylFinish.Marble -> {
            if (marble == null) {
                // Still generating (a few hundred ms): show the base color.
                drawCircle(palette.marbleA, r, center)
            } else {
                val disc = Path().apply { addOval(Rect(center, r)) }
                clipPath(disc) {
                    drawImage(
                        marble,
                        dstOffset = IntOffset((center.x - r).roundToInt(), (center.y - r).roundToInt()),
                        dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                        alpha = 0.93f,
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- grooves

private fun DrawScope.drawGrooves(r: Float, seed: Int, glints: Boolean, sz: RecordSize) {
    val rnd = kotlin.random.Random(seed)
    val p0 = rnd.nextFloat() * 6.28f
    val p1 = rnd.nextFloat() * 6.28f
    val p2 = rnd.nextFloat() * 6.28f
    // Loud/quiet passages: brightness of the grooves drifts across the band.
    fun loud(f: Float) = 1f + 0.35f * sin(f * 23f + p0) + 0.2f * sin(f * 57f + p1) + 0.12f * sin(f * 131f + p2)

    val pitchPx = 2.2f                          // one groove every ~2.2 px on screen
    val start = sz.grooveIn * r
    val end = sz.grooveOut * r
    var rad = start
    while (rad < end) {
        val f = rad / r
        val l = loud(f)
        drawCircle(Color.White.copy(alpha = (0.05f * l).coerceIn(0f, 1f)), rad + pitchPx * 0.25f, center, style = Stroke(1f))
        drawCircle(Color.Black.copy(alpha = (0.22f * l).coerceIn(0f, 1f)), rad + pitchPx * 0.75f, center, style = Stroke(1f))
        rad += pitchPx
    }

    // Lead-in: a few wider grooves at the outer edge
    for (f in floatArrayOf(0.960f, 0.966f, 0.972f)) {
        drawCircle(Color.Black.copy(alpha = 0.35f), f * r, center, style = Stroke(1.2f))
    }
    // Rounded rim
    drawCircle(Color.White.copy(alpha = 0.10f), r * 0.991f, center, style = Stroke(r * 0.018f))
    drawCircle(Color.Black.copy(alpha = 0.45f), r * 0.998f, center, style = Stroke(r * 0.004f))

    // Lead-out spiral in the dead wax, then the locked groove
    val gap = sz.grooveIn - sz.label * 1.05f
    for (k in floatArrayOf(0.12f, 0.30f, 0.48f)) {
        drawCircle(Color.Black.copy(alpha = 0.30f), (sz.grooveIn - gap * k) * r, center, style = Stroke(1.2f))
    }
    drawCircle(Color.Black.copy(alpha = 0.45f), (sz.grooveIn - gap * 0.7f) * r, center, style = Stroke(1.2f))

    // Raised vinyl lip around the label
    drawCircle(Color.White.copy(alpha = 0.06f), r * sz.label * 1.02f, center, style = Stroke(r * sz.label * 0.04f))

    if (glints) drawMotionCues(r, rnd, sz)
}

/**
 * Subtle details that belong to the disc and turn with it: light catching short
 * stretches of groove, and a couple of soft sheen bands. Perfect concentric grooves
 * look identical at every angle, so without these a spinning record can look like
 * it's standing still. (No dust or specks: those read as glitter.)
 */
private fun DrawScope.drawMotionCues(r: Float, rnd: kotlin.random.Random, sz: RecordSize) {
    fun arc(radius: Float, start: Float, sweep: Float, color: Color, width: Float) = drawArc(
        color = color, startAngle = start, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width, cap = StrokeCap.Round),
    )
    // Light catching stretches of groove: few, faint, varied lengths.
    repeat(22) {
        val radius = r * (sz.grooveIn + rnd.nextFloat() * (sz.grooveOut - sz.grooveIn))
        arc(
            radius, rnd.nextFloat() * 360f, 15f + rnd.nextFloat() * 45f,
            Color.White.copy(alpha = 0.04f + rnd.nextFloat() * 0.04f), 1.2f,
        )
    }
    // A couple of broad, very soft sheen bands (pressing variations).
    repeat(3) {
        val radius = r * (sz.grooveIn + 0.05f + rnd.nextFloat() * (sz.grooveOut - sz.grooveIn - 0.1f))
        arc(
            radius, rnd.nextFloat() * 360f, 50f + rnd.nextFloat() * 70f,
            Color.White.copy(alpha = 0.035f), r * 0.03f,
        )
    }
}

// --------------------------------------------------------------------- label

private val PrintedLook = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.88f) })

private fun DrawScope.drawLabel(art: ImageBitmap, r: Float, sz: RecordSize) {
    val labelR = r * sz.label
    val holeR = r * sz.hole
    // Label = ring with a hole, so the spindle underneath shows through.
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(center, labelR))
        addOval(Rect(center, holeR))
    }
    val side = min(art.width, art.height)
    clipPath(ring) {
        drawImage(
            art,
            srcOffset = IntOffset((art.width - side) / 2, (art.height - side) / 2),
            srcSize = IntSize(side, side),
            dstOffset = IntOffset((center.x - labelR).roundToInt(), (center.y - labelR).roundToInt()),
            dstSize = IntSize((2 * labelR).roundToInt(), (2 * labelR).roundToInt()),
            colorFilter = PrintedLook,
            filterQuality = FilterQuality.High,
        )
        // Warm paper tone + darkening toward the edge (the label is under the same light)
        drawCircle(Color(0xFFFAF4E8).copy(alpha = 0.06f), labelR, center)
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f),
                center = center, radius = labelR,
            ),
            labelR, center,
        )
    }
    // Pressed ring stamped into the label + shadow where vinyl meets paper
    drawCircle(Color.Black.copy(alpha = 0.18f), labelR * 0.93f, center, style = Stroke(1.2f))
    drawCircle(Color.Black.copy(alpha = 0.35f), labelR * 0.9925f, center, style = Stroke(labelR * 0.015f))
    // Hole edge
    drawCircle(Color.Black.copy(alpha = 0.5f), holeR * 0.93f, center, style = Stroke(holeR * 0.14f))
}

// --------------------------------------------------------- plain label

/** Classic plain label colors (paper, ink). Picked per song, so each record keeps its own. */
private val PlainLabels = listOf(
    Color(0xFFEFE6D2) to Color(0xFF2A2522),   // cream
    Color(0xFF7A1F1F) to Color(0xFFF3E6CF),   // oxblood
    Color(0xFF1F3354) to Color(0xFFEDE3CC),   // navy
    Color(0xFFD9A441) to Color(0xFF2B2116),   // mustard
    Color(0xFF2F4A38) to Color(0xFFEDE3CC),   // forest
    Color(0xFFD8663A) to Color(0xFF231A15),   // orange
    Color(0xFFE8E8E4) to Color(0xFF1D1D1F),   // white promo
)

/**
 * No album art: a plain printed label, like a promo or white-label pressing, with
 * the title and artist set around the hole. It turns with the record.
 */
private fun DrawScope.drawPlainLabel(
    r: Float, seed: Int, sz: RecordSize, title: String?, artist: String?, measurer: TextMeasurer,
) {
    val labelR = r * sz.label
    val holeR = r * sz.hole
    val (paper, ink) = PlainLabels[Math.floorMod(seed, PlainLabels.size)]
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(center, labelR))
        addOval(Rect(center, holeR))
    }
    clipPath(ring) {
        drawCircle(paper, labelR, center)
        // Same light as the art labels: darker toward the edge.
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f),
                center = center, radius = labelR,
            ),
            labelR, center,
        )
    }
    // Printed rules
    drawCircle(ink.copy(alpha = 0.55f), labelR * 0.86f, center, style = Stroke(labelR * 0.012f))
    drawCircle(ink.copy(alpha = 0.35f), labelR * 0.82f, center, style = Stroke(labelR * 0.006f))

    // Text sizes follow the label, not the screen.
    fun sp(px: Float) = (px / density / fontScale).sp
    val width = labelR * 1.25f
    fun print(text: String, px: Float, weight: FontWeight, centerY: Float, lines: Int, alpha: Float = 1f) {
        val style = TextStyle(
            color = ink.copy(alpha = alpha), fontSize = sp(px), fontWeight = weight,
            textAlign = TextAlign.Center, letterSpacing = sp(px * 0.04f),
        )
        val layout = measurer.measure(
            text, style, overflow = TextOverflow.Ellipsis, maxLines = lines,
            constraints = Constraints.fixedWidth(width.roundToInt()),
        )
        drawText(layout, topLeft = Offset(center.x - width / 2f, centerY - layout.size.height / 2f))
    }
    print(title?.uppercase() ?: "UNTITLED", labelR * 0.13f, FontWeight.Bold, center.y - labelR * 0.40f, lines = 2)
    if (artist != null) print(artist, labelR * 0.11f, FontWeight.Medium, center.y + labelR * 0.36f, lines = 1)
    print(if (sz == RecordSize.Single) "45 RPM" else "33⅓ RPM", labelR * 0.075f, FontWeight.Medium,
        center.y + labelR * 0.62f, lines = 1, alpha = 0.7f)

    drawCircle(Color.Black.copy(alpha = 0.35f), labelR * 0.9925f, center, style = Stroke(labelR * 0.015f))
    drawCircle(Color.Black.copy(alpha = 0.5f), holeR * 0.93f, center, style = Stroke(holeR * 0.14f))
}

// ------------------------------------------------------------------- wear

/**
 * A much-played record: fine hairline scuffs across the grooves (never specks:
 * those read as glitter) and a faint haze that dulls the black.
 */
private fun DrawScope.drawGrooveWear(r: Float, seed: Int, wear: Float, sz: RecordSize) {
    val rnd = kotlin.random.Random(seed * 31 + 7)
    // Haze over the playing surface
    val inner = sz.grooveIn * r
    val outer = sz.grooveOut * r
    drawCircle(
        Color(0xFF9A9A9A).copy(alpha = 0.05f * wear),
        (inner + outer) / 2f, center, style = Stroke(outer - inner),
    )
    // Hairline scuffs: short, straight, at random angles, mostly faint.
    val count = (4 + 26 * wear).toInt()
    repeat(count) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val rad = r * (sz.grooveIn + rnd.nextFloat() * (sz.grooveOut - sz.grooveIn))
        val mid = Offset(center.x + cos(a) * rad, center.y + sin(a) * rad)
        val dir = a + PI.toFloat() / 2f + (rnd.nextFloat() - 0.5f) * 1.6f  // mostly along the grooves
        val half = r * (0.015f + rnd.nextFloat() * 0.05f)
        val d = Offset(cos(dir) * half, sin(dir) * half)
        drawLine(
            Color.White.copy(alpha = (0.035f + rnd.nextFloat() * 0.07f) * (0.5f + wear / 2f)),
            mid - d, mid + d, strokeWidth = 0.8f, cap = StrokeCap.Round,
        )
    }
}

/** Spindle marks: the curly scuffs on the label left by years of putting it on in a hurry. */
private fun DrawScope.drawSpindleMarks(r: Float, seed: Int, wear: Float, sz: RecordSize) {
    val rnd = kotlin.random.Random(seed * 17 + 3)
    val holeR = r * sz.hole
    val labelR = r * sz.label
    val count = (1 + 9 * wear).toInt()
    repeat(count) {
        val a0 = rnd.nextFloat() * 2f * PI.toFloat()
        val len = labelR * (0.10f + rnd.nextFloat() * 0.28f * wear)
        val bend = (rnd.nextFloat() - 0.5f) * 1.4f
        val start = Offset(center.x + cos(a0) * holeR * 1.05f, center.y + sin(a0) * holeR * 1.05f)
        val a1 = a0 + bend * len / labelR
        val end = Offset(center.x + cos(a1) * (holeR + len), center.y + sin(a1) * (holeR + len))
        val ctrlR = holeR + len * 0.5f
        val ctrlA = a0 + bend * 0.9f * len / labelR
        val ctrl = Offset(center.x + cos(ctrlA) * ctrlR, center.y + sin(ctrlA) * ctrlR)
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
        }
        // A pale scrape with a faint dark edge, like the spindle scratching the paper.
        drawPath(path, Color.Black.copy(alpha = 0.18f * wear + 0.05f), style = Stroke(1.6f, cap = StrokeCap.Round))
        drawPath(path, Color.White.copy(alpha = 0.30f * wear + 0.10f), style = Stroke(0.9f, cap = StrokeCap.Round))
    }
}

/** A pleasant palette for records without art (so colored/marble vinyl still works). */
private fun paletteFromSeed(seed: Int): VinylPalette {
    val hue = Math.floorMod(seed, 360).toFloat()
    fun hsv(h: Float, sat: Float, v: Float) = Color(AColor.HSVToColor(floatArrayOf((h + 360f) % 360f, sat, v)))
    return VinylPalette(
        tint = hsv(hue, 0.5f, 0.6f),
        marbleA = hsv(hue, 0.4f, 0.75f),
        marbleB = hsv(hue + 35f, 0.55f, 0.5f),
        marbleVein = hsv(hue, 0.1f, 0.95f),
    )
}

// --------------------------------------------------------------------- sheen

/** (center of highlight as fraction of a turn, width, strength) */
private val SheenLobes = listOf(
    Triple(0.10f, 0.07f, 0.20f),
    Triple(0.60f, 0.06f, 0.22f),
    Triple(0.68f, 0.03f, 0.12f),
    Triple(0.15f, 0.10f, 0.06f),
    Triple(0.65f, 0.12f, 0.06f),
)

private val SheenStops: Array<Pair<Float, Color>> = Array(97) { i ->
    val t = i / 96f
    var a = 0f
    for ((c0, w, s) in SheenLobes) {
        val d0 = abs(t - c0)
        val d = min(d0, 1f - d0)
        a += s * exp(-(d / w).pow(2) * 3f)
    }
    t to Color.White.copy(alpha = a.coerceIn(0f, 1f))
}

private fun DrawScope.drawSheen(r: Float, strength: Float, sz: RecordSize) {
    drawCircle(Brush.sweepGradient(*SheenStops, center = center), r, center, alpha = strength)
    // Mask by zone: grooves shine most, dead wax less, paper label barely.
    drawCircle(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.30f),
            sz.label to Color.Black.copy(alpha = 0.30f),
            sz.label + 0.001f to Color.Black.copy(alpha = 0.75f),
            sz.grooveIn to Color.Black.copy(alpha = 0.75f),
            sz.grooveIn + 0.001f to Color.Black,
            0.975f to Color.Black,
            0.976f to Color.Black.copy(alpha = 0.6f),
            1f to Color.Black.copy(alpha = 0.6f),
            center = center, radius = r,
        ),
        r, center,
        blendMode = BlendMode.DstIn,
    )
}

// ------------------------------------------------------------------- palette

@Immutable
data class VinylPalette(
    val tint: Color,        // translucent "album color" vinyl
    val marbleA: Color,     // marble base
    val marbleB: Color,     // marble swirl
    val marbleVein: Color,  // marble veins / smoke wisps
)

/** Pulls two main colors out of the album art (no extra libraries needed). */
fun extractVinylPalette(art: Bitmap): VinylPalette {
    val small = Bitmap.createScaledBitmap(art, 24, 24, true)
    val px = IntArray(24 * 24)
    small.getPixels(px, 0, 24, 0, 0, 24, 24)

    val bins = 12
    val weight = FloatArray(bins)
    val rs = FloatArray(bins); val gs = FloatArray(bins); val bs = FloatArray(bins)
    var ar = 0f; var ag = 0f; var ab = 0f
    val hsv = FloatArray(3)
    for (c in px) {
        val r = AColor.red(c) / 255f; val g = AColor.green(c) / 255f; val b = AColor.blue(c) / 255f
        ar += r; ag += g; ab += b
        AColor.colorToHSV(c, hsv)
        if (hsv[2] < 0.15f) continue
        val w = hsv[1] * hsv[2]
        val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        weight[bin] += w; rs[bin] += r * w; gs[bin] += g * w; bs[bin] += b * w
    }
    val n = px.size.toFloat()
    val average = Color(ar / n, ag / n, ab / n)

    val order = weight.indices.sortedByDescending { weight[it] }
    val top = order[0]
    val total = weight.sum()
    fun binColor(i: Int) = Color(rs[i] / weight[i], gs[i] / weight[i], bs[i] / weight[i])

    // Mostly gray art → gray marble (like smoke marble), otherwise its main hues.
    val primary = if (total < n * 0.06f || weight[top] <= 0f) average else binColor(top)
    val secondary = order.getOrNull(1)?.takeIf { weight[it] >= weight[top] * 0.25f }?.let(::binColor)
        ?: adjust(primary, dv = 0.18f, ds = -0.12f)

    val tint = adjust(primary, minS = 0.45f, minV = 0.35f, maxV = 0.8f)
    val vein = if (luminance(primary) > 0.5f) lerpColor(primary, Color.Black, 0.7f)
    else lerpColor(primary, Color.White, 0.85f)
    return VinylPalette(tint = tint, marbleA = primary, marbleB = secondary, marbleVein = vein)
}

private fun luminance(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

private fun adjust(
    c: Color, dv: Float = 0f, ds: Float = 0f,
    minS: Float = 0f, minV: Float = 0f, maxV: Float = 1f,
): Color {
    val hsv = FloatArray(3)
    AColor.colorToHSV(c.toArgb(), hsv)
    val s0 = hsv[1]
    // Keep grays gray; only push saturation on colors that have some.
    hsv[1] = if (s0 < 0.05f) s0 else (s0 + ds).coerceIn(minS, 1f)
    hsv[2] = (hsv[2] + dv).coerceIn(minV, maxV)
    return Color(AColor.HSVToColor(hsv))
}

// ------------------------------------------------------------------- marble

/** Generates the marble texture off the main thread, once per song/palette. */
@Composable
fun rememberMarbleBitmap(enabled: Boolean, seed: Int, palette: VinylPalette, size: Int): ImageBitmap? {
    val key = MarbleKey(seed, palette, size)
    // Each result is tagged with the song/colors it was made for, and only returned
    // if it matches. (Before, the previous song's marble was reused while the new one
    // was still being generated, so a record could show up in the wrong colors.)
    val tagged by produceState<Pair<MarbleKey, ImageBitmap>?>(null, enabled, key) {
        if (!enabled) { value = null; return@produceState }
        // A small version first (a few ms): right colors almost instantly…
        val quick = withContext(Dispatchers.Default) {
            generateMarble(min(QUICK_MARBLE, size), seed, palette).asImageBitmap()
        }
        value = key to quick
        // …then the full-detail one.
        if (size > QUICK_MARBLE) {
            val full = withContext(Dispatchers.Default) {
                generateMarble(size, seed, palette).asImageBitmap()
            }
            value = key to full
        }
    }
    return tagged?.takeIf { it.first == key }?.second
}

private const val QUICK_MARBLE = 96

private data class MarbleKey(val seed: Int, val palette: VinylPalette, val size: Int)

fun generateMarble(n: Int, seed: Int, p: VinylPalette): Bitmap {
    val c1 = floatArrayOf(p.marbleA.red, p.marbleA.green, p.marbleA.blue)
    val c2 = floatArrayOf(p.marbleB.red, p.marbleB.green, p.marbleB.blue)
    val cv = floatArrayOf(p.marbleVein.red, p.marbleVein.green, p.marbleVein.blue)
    val out = IntArray(n * n)
    val veinK = 3.5f; val veinSharp = 5f; val veinA = 0.55f
    for (y in 0 until n) for (x in 0 until n) {
        val u = (x + 0.5f) / n * 2f - 1f
        val v = (y + 0.5f) / n * 2f - 1f
        val r = hypot(u, v)
        // Swirl: pressing drags the colors around the center
        val ang = 1.6f * r
        val ca = cos(ang); val sa = sin(ang)
        val px = (u * ca - v * sa) * 1.5f
        val py = (u * sa + v * ca) * 1.5f
        // Domain-warped noise (warp twice for the "ink in water" look)
        val qx = fbm(px, py, seed); val qy = fbm(px + 5.2f, py + 1.3f, seed + 1)
        val rx = fbm(px + 4 * qx + 1.7f, py + 4 * qy + 9.2f, seed + 2)
        val ry = fbm(px + 4 * qx + 8.3f, py + 4 * qy + 2.8f, seed + 3)
        val f = fbm(px + 4 * rx, py + 4 * ry, seed + 4)

        val t = smooth(0.3f, 0.7f, f)
        val ridge = (1f - abs(sin(f * PI.toFloat() * veinK))).pow(veinSharp)
        val ridge2 = (1f - abs(sin((rx + ry) * PI.toFloat() * veinK * 0.7f))).pow(veinSharp * 1.5f)
        val va = (ridge * veinA + ridge2 * veinA * 0.5f).coerceIn(0f, 0.9f)

        var cr = c1[0] + (c2[0] - c1[0]) * t
        var cg = c1[1] + (c2[1] - c1[1]) * t
        var cb = c1[2] + (c2[2] - c1[2]) * t
        cr += (cv[0] - cr) * va; cg += (cv[1] - cg) * va; cb += (cv[2] - cb) * va
        out[y * n + x] = AColor.rgb((cr * 255).roundToInt().coerceIn(0, 255),
            (cg * 255).roundToInt().coerceIn(0, 255), (cb * 255).roundToInt().coerceIn(0, 255))
    }
    return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun hash(ix: Int, iy: Int, seed: Int): Float {
    var h = ix * 374761393 + iy * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

private fun vnoise(x: Float, y: Float, seed: Int): Float {
    val fx0 = floor(x); val fy0 = floor(y)
    val ix = fx0.toInt(); val iy = fy0.toInt()
    val fx = x - fx0; val fy = y - fy0
    val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
    val a = hash(ix, iy, seed); val b = hash(ix + 1, iy, seed)
    val c = hash(ix, iy + 1, seed); val d = hash(ix + 1, iy + 1, seed)
    return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy
}

private fun fbm(x: Float, y: Float, seed: Int): Float {
    var s = 0f; var amp = 0.5f; var f = 1f
    for (o in 0 until 4) {
        s += amp * vnoise(x * f, y * f, seed + o * 31)
        f *= 2f; amp *= 0.5f
    }
    return s / 0.9375f
}

/** Small preview used by the settings sheet. */
@Composable
fun rememberFinishPreview(art: Bitmap?, seedKey: String?): Pair<VinylPalette?, ImageBitmap?> {
    // Worked out off the main thread, so opening settings doesn't stall.
    val palette by produceState<VinylPalette?>(null, art) {
        value = withContext(Dispatchers.Default) { art?.let(::extractVinylPalette) }
    }
    val seed = remember(seedKey) { (seedKey ?: "").hashCode() }
    val marble = rememberMarbleBitmap(
        enabled = palette != null, seed = seed,
        palette = palette ?: VinylPalette(Color.Gray, Color.Gray, Color.LightGray, Color.DarkGray),
        size = 96,
    )
    return palette to marble
}