package io.github.plvzfqrit.lockgroove.ui.drawing

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Platter + felt mat, matched to the product photo.
 * Colors are sampled from the photo; ring widths are fractions of the outer
 * radius, measured from the center outward:
 *
 *   0.000–0.970  felt: cool blue-charcoal, very slightly lighter at the edge
 *   0.970–0.976  soft gray transition
 *   0.976–0.985  bright polished rim
 *   0.985–0.990  mid-gray
 *   0.990–1.000  dark outer lip
 */

private val FeltCenter = Color(0xFF161E20)
private val FeltEdge = Color(0xFF1C2427)
private val RimTransition = Color(0xFF747B7D)
private val RimBright = Color(0xFFE6E9EC)
private val RimMid = Color(0xFFB5B5B7)
private val RimLip = Color(0xFF353534)

fun DrawScope.drawPlatterFelt(
    center: Offset = this.center,
    outerRadius: Float = size.minDimension / 2f,
) {
    val r = outerRadius

    // Rim, outermost first
    drawCircle(RimLip, r, center)
    drawCircle(RimMid, r * 0.990f, center)
    drawCircle(RimBright, r * 0.985f, center)
    drawCircle(RimTransition, r * 0.9755f, center)

    // Felt base
    val feltR = r * 0.970f
    drawCircle(
        brush = Brush.radialGradient(
            0f to FeltCenter, 0.8f to FeltCenter, 1f to FeltEdge,
            center = center, radius = feltR,
        ),
        radius = feltR,
        center = center,
    )

    // Fine fibre texture: tiny, very low-contrast specks (fixed seed = no flicker)
    val rnd = Random(7)
    val light = ArrayList<Offset>(4500)
    val dark = ArrayList<Offset>(4500)
    repeat(9000) { i ->
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val d = feltR * sqrt(rnd.nextFloat()) // uniform over the disc
        val p = Offset(center.x + d * cos(a), center.y + d * sin(a))
        if (i % 2 == 0) light += p else dark += p
    }
    drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.035f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.10f), strokeWidth = 1.5f, cap = StrokeCap.Round)

    // Spindle: small polished post with a dark collar
    val sp = r * 0.02f
    drawCircle(Color(0xFF2B2E30), sp * 1.35f, center)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFF1F2F3), Color(0xFF9A9EA2)),
            center = center - Offset(sp * 0.3f, sp * 0.3f),
            radius = sp * 1.2f,
        ),
        radius = sp,
        center = center,
    )
}
/**
 * The platter and felt, drawn once into a bitmap per size (thousands of fibre specks
 * would otherwise be re-drawn on every frame the record spins above them).
 */
fun Modifier.platterFelt(): Modifier = drawWithCache {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val baked = ImageBitmap(w, h)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(baked), size) { drawPlatterFelt() }
    onDrawBehind { drawImage(baked) }
}
