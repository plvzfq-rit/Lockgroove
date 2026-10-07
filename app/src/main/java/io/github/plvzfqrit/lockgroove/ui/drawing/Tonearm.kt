package io.github.plvzfqrit.lockgroove.ui.drawing

import androidx.compose.foundation.Canvas
import io.github.plvzfqrit.lockgroove.core.model.ArmGeometry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Audio-Technica style tonearm unit, top-down view.
 *
 * Every coordinate below is in REFERENCE UNITS measured directly off the
 * product photo (1 unit = 1 px of the 1000-px-wide photo). The origin is the
 * rotation axis: the center of the round base. +x = right, +y = down.
 * At armAngleDeg = 0 the arm is parked in its rest clip. Positive angles swing
 * the arm clockwise, toward the record.
 *
 * Approximate play range on the matching platter: lead-in ≈ 10°, run-out ≈ 38°.
 */

@Immutable
data class TonearmColors(
    val plateLight: Color = Color(0xFFF3F3F3),
    val plateDark: Color = Color(0xFFE4E4E4),
    val plateOutline: Color = Color(0xFFAEAEAC),
    val innerRing: Color = Color(0xFFBDBDBB),

    val slotFill: Color = Color(0xFFF4F4F4),
    val slotOutline: Color = Color(0xFFC4C4C4),
    val lever: Color = Color(0xFF515156),
    val leverKnob: Color = Color(0xFF2E2F33),
    val leverKnobDot: Color = Color(0xFF60646A),
    val liftBar: Color = Color(0xFF1E1F22),

    val cwBlack: Color = Color(0xFF050507),
    val cwMid: Color = Color(0xFF1C2024),
    val cwGlare: Color = Color(0xFF48545A),
    val cwKnurl: Color = Color(0xB3000000),

    val housingBevel: Color = Color(0xFF676461),
    val housingFaceTop: Color = Color(0xFF444E57),
    val housingFaceBottom: Color = Color(0xFF363D45),
    val housingBand: Color = Color(0xFF3D454F),
    val housingGroove: Color = Color(0xFF1F232D),
    val cap: Color = Color(0xFF393E44),
    val capHighlight: Color = Color(0xFF4A5058),
    val sleeve: Color = Color(0xFF2B2F33),

    val tubeEdge: Color = Color(0xFF151716),
    val tubeDark: Color = Color(0xFF1E1F20),
    val tubeChrome: Color = Color(0xFFB8BABA),
    val tubeHighlight: Color = Color(0xFFE3E5E4),
    val tubeShade: Color = Color(0xFF9A9C9C),

    val collar: Color = Color(0xFF202326),
    val cartridgeBody: Color = Color(0xFF1A1A1B),
    val cartridgeSide: Color = Color(0xFF848681),
    val shellLeft: Color = Color(0xFF262C33),
    val shellMid: Color = Color(0xFF36404A),
    val shellRight: Color = Color(0xFF28303A),
    val fingerLift: Color = Color(0xFF323335),

    val restPost: Color = Color(0xFF1E1E1E),
    val restClip: Color = Color(0x8CD0D0D0),
    val restClipOutline: Color = Color(0xFF797979),
)

/** Geometry in reference units (see header). Tweak here, not in the draw code. */
object TonearmGeometry {
    // Content bounds over the whole swing (0°..40°), used to fit the canvas.
    val bounds = Rect(left = -175f, top = -52f, right = 66f, bottom = 284f)

    // Platform: "keyhole" = convex hull of two circles.
    val baseCenter = Offset(0f, 0f); const val baseRadius = 46f
    val tailCenter = Offset(15f, 74f); const val tailRadius = 34f
    val innerRingCenter = Offset(1f, 2f); const val innerRingRadius = 42.5f

    // Lift slot + lever + lift bar (fixed)
    val slot = Rect(3f, 36f, 17f, 76f)
    val leverKnob = Offset(11.5f, 43f)

    // Counterweight (rotates)
    val counterweight = Rect(-8f, -37f, 25f, -15f)
    const val knurlCount = 6

    // Pivot housing (rotates)
    val housingBevel = Rect(-10f, -16f, 33f, 23f)
    val housingFace = Rect(-7f, -16f, 33f, 20f)
    val housingBand = Rect(-7f, -16f, 33f, -9f)
    val housingGroove = Rect(-7f, -9f, 33f, -4f)
    val cap = Rect(31f, -17f, 49f, 18f)
    val capHighlight = Rect(32f, -16f, 48f, -12f)
    val sleeve = Rect(34.5f, 16f, 44.5f, 46f)

    // Arm tube (rotates)
    val tube = Rect(35f, 45f, 43f, 203f)

    // Headshell group (rotates)
    val collar = Rect(33f, 200f, 45f, 213f)
    val cartridgeBody = Rect(29f, 209f, 41f, 266f)
    val cartridgeSide = listOf(Offset(24f, 252f), Offset(29f, 252f), Offset(28f, 271f), Offset(20f, 271f))
    val fingerLiftStart = Offset(38f, 268f)
    val fingerLiftEnd = Offset(57f, 274f)

    // Arm rest (fixed, drawn on top of the arm)
    val restPost = Rect(28f, 177f, 34f, 189f)
    val restClip = Rect(26f, 188f, 53f, 198f)

    /** Stylus tip at 0°, handy for hit-testing / placing on a record. */
    val stylusTip = Offset(24f, 273f)
}

/**
 * How the tonearm sits relative to the platter on the real turntable. The numbers
 * live in the core ([ArmGeometry]); this adds the Compose types the drawing needs.
 *
 * The tonearm must NOT be sized/placed independently of the platter. Derive
 * both from the platter: scale = platterRadiusPx / PLATTER_RADIUS, and
 * pivot = spindle + PIVOT_FROM_SPINDLE * scale.
 */
object TurntableGeometry {
    const val PLATTER_RADIUS = ArmGeometry.PLATTER_RADIUS
    val PIVOT_FROM_SPINDLE = Offset(ArmGeometry.PIVOT_FROM_SPINDLE_X, ArmGeometry.PIVOT_FROM_SPINDLE_Y)
    const val REST_DEG = ArmGeometry.REST_DEG
}

/**
 * The tonearm is drawn in TWO layers, like the real turntable:
 *
 *   1. drawTonearmBase…  → the keyhole plate + lift lever. Draw this BEFORE
 *      the platter, so the platter overlaps the plate (as in the photo).
 *   2. drawTonearmArm…   → counterweight, housing, tube, headshell, rest clip.
 *      Draw this AFTER the platter/record, so the arm sits on top of it.
 *
 * @param pivot  where the rotation axis lands, in canvas px
 * @param pxPerUnit  canvas px per reference unit
 */
fun DrawScope.drawTonearmBase(pivot: Offset, pxPerUnit: Float, colors: TonearmColors = TonearmColors()) {
    withTransform({ translate(pivot.x, pivot.y); scale(pxPerUnit, pxPerUnit, pivot = Offset.Zero) }) {
        drawPlatform(colors)
    }
}

/** Union of the arm's main shapes, used for its shadow. Built once. */
private val ArmSilhouette: Path by lazy {
    with(TonearmGeometry) {
        fun rr(r: Rect, c: Float) = Path().apply { addRoundRect(RoundRect(r, CornerRadius(c))) }
        val parts = listOf(
            rr(counterweight, 3f),
            rr(housingBevel, 1.5f),
            rr(cap, 2.5f),
            rr(sleeve, 2f),
            rr(tube, 0f),
            rr(collar, 3f),
            rr(cartridgeBody, 2f),
            Path().apply {                                   // headshell plate
                moveTo(37f, 222f); lineTo(54f, 222f); lineTo(40f, 275f); lineTo(24f, 275f); close()
            },
            Path().apply {                                   // finger lift
                moveTo(fingerLiftStart.x, fingerLiftStart.y - 1.8f)
                lineTo(fingerLiftEnd.x, fingerLiftEnd.y - 1.8f)
                lineTo(fingerLiftEnd.x, fingerLiftEnd.y + 1.8f)
                lineTo(fingerLiftStart.x, fingerLiftStart.y + 1.8f)
                close()
            },
        )
        parts.drop(1).fold(parts.first()) { acc, p -> Path.combine(PathOperation.Union, acc, p) }
    }
}

/** The arm's shadow in the app's usual daylight (light from the top-left): down and to the right. */
val DaylightShadow = Offset(0.574f, 0.819f)

/** How much bigger the arm looks at full lift (it's closer to the viewer). */
private const val LIFT_SCALE = 0.035f

/**
 * @param lift  0 = resting (on the record or in the rest clip), 1 = fully raised.
 *              Shown the way a top-down view sees height: the arm grows slightly
 *              and its shadow drifts further away and gets fainter.
 */
fun DrawScope.drawTonearmArm(
    pivot: Offset,
    pxPerUnit: Float,
    armAngleDeg: Float,
    lift: Float = 0f,
    colors: TonearmColors = TonearmColors(),
    /** Which way the shadow falls, and how long (1 = usual daylight): see RoomLight.shadow(). */
    shadow: Offset = DaylightShadow,
) {
    val l = lift.coerceIn(0f, 1f)
    val grow = 1f + LIFT_SCALE * l
    withTransform({ translate(pivot.x, pivot.y); scale(pxPerUnit, pxPerUnit, pivot = Offset.Zero) }) {
        // Shadow: the arm's silhouette (one pre-built path, one draw call), cast away
        // from the light. Cheap enough to redraw every frame.
        val drop = (2f + 10f * l) * 1.64f
        withTransform({
            translate(shadow.x * drop, shadow.y * drop)
            rotate(armAngleDeg, pivot = Offset.Zero)
            scale(grow, grow, pivot = Offset.Zero)
        }) { drawPath(ArmSilhouette, Color.Black.copy(alpha = 0.32f - 0.14f * l)) }
        // The rest sits UNDER the arm: the tube rests on it, never slides beneath it.
        drawArmRest(colors)
        withTransform({
            rotate(armAngleDeg, pivot = Offset.Zero)
            scale(grow, grow, pivot = Offset.Zero)
        }) { drawArm(colors) }

    }
}

/** The desk lamp's warm light, as the arm's metal gives it back. */
private val LampShine = Color(0xFFFFE4BC)

/**
 * The desk lamp caught in the arm's metal at night: a thin line of light along the chrome
 * tube (brightest toward the headshell, nearer the lamp), and glints on the counterweight,
 * the bearing cap and the finger lift. Draw it ABOVE the room's darkness: in a dim room,
 * reflections are what you still see. Same placement and lift as [drawTonearmArmForPlatter].
 *
 * @param strength 0 = nothing (by day), 1 = full lamplight
 * @param lampFrom where the light comes from (degrees, screen: 0 = right, -90 = top)
 */
fun DrawScope.drawTonearmGlints(
    spindle: Offset,
    platterRadiusPx: Float,
    armAngleDeg: Float,
    lift: Float,
    strength: Float,
    lampFrom: Float,
) {
    if (strength <= 0f) return
    val (pivot, pxPerUnit) = tonearmPlacementFor(spindle, platterRadiusPx)
    val grow = 1f + LIFT_SCALE * lift.coerceIn(0f, 1f)
    // The lamp's side in the arm's own frame: a cylinder shows the light as a line along
    // the side that faces it.
    val side = cos(Math.toRadians((lampFrom - armAngleDeg).toDouble())).toFloat()
    fun shine(alpha: Float) = LampShine.copy(alpha = (alpha * strength).coerceIn(0f, 1f))
    withTransform({
        translate(pivot.x, pivot.y)
        scale(pxPerUnit, pxPerUnit, pivot = Offset.Zero)
        rotate(armAngleDeg, pivot = Offset.Zero)
        scale(grow, grow, pivot = Offset.Zero)
    }) {
        with(TonearmGeometry) {
            // Tube: a soft bloom, and the bright line in it.
            val x = tube.center.x + side * tube.width * 0.2f
            val from = Offset(x, tube.top + 2f)
            val to = Offset(x, tube.bottom - 2f)
            drawLine(
                Brush.verticalGradient(listOf(shine(0.04f), shine(0.16f)), startY = from.y, endY = to.y),
                from, to, strokeWidth = 4.5f, cap = StrokeCap.Round,
            )
            drawLine(
                Brush.verticalGradient(listOf(shine(0.30f), shine(0.90f)), startY = from.y, endY = to.y),
                from, to, strokeWidth = 1.3f, cap = StrokeCap.Round,
            )
            // Counterweight: the same along its drum, fainter (further from the lamp).
            val cx = counterweight.center.x + side * counterweight.width * 0.18f
            drawLine(
                shine(0.45f), Offset(cx, counterweight.top + 3f), Offset(cx, counterweight.bottom - 3f),
                strokeWidth = 1.4f, cap = StrokeCap.Round,
            )
            // The bearing cap, and the finger lift at the headshell.
            glint(Offset(cap.center.x + side * 4f, cap.top + 7f), 4f, shine(0.55f))
            drawLine(shine(0.85f), fingerLiftStart, fingerLiftEnd, strokeWidth = 1.1f, cap = StrokeCap.Round)
            glint(fingerLiftEnd, 5f, shine(0.45f))
        }
    }
}

private fun DrawScope.glint(at: Offset, radius: Float, color: Color) =
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = at, radius = radius), radius, at)

/** Both layers at once (only for standalone use, with no platter involved). */
fun DrawScope.drawTonearm(
    pivot: Offset, pxPerUnit: Float, armAngleDeg: Float, colors: TonearmColors = TonearmColors(),
) {
    drawTonearmBase(pivot, pxPerUnit, colors)
    drawTonearmArm(pivot, pxPerUnit, armAngleDeg, colors = colors)
}

/** Scale + pivot derived from a platter you draw yourself. */
fun tonearmPlacementFor(spindle: Offset, platterRadiusPx: Float): Pair<Offset, Float> {
    val pxPerUnit = platterRadiusPx / TurntableGeometry.PLATTER_RADIUS
    return (spindle + TurntableGeometry.PIVOT_FROM_SPINDLE * pxPerUnit) to pxPerUnit
}

fun DrawScope.drawTonearmBaseForPlatter(
    spindle: Offset, platterRadiusPx: Float, colors: TonearmColors = TonearmColors(),
) {
    val (pivot, pxPerUnit) = tonearmPlacementFor(spindle, platterRadiusPx)
    drawTonearmBase(pivot, pxPerUnit, colors)
}

fun DrawScope.drawTonearmArmForPlatter(
    spindle: Offset,
    platterRadiusPx: Float,
    armAngleDeg: Float,
    lift: Float = 0f,
    colors: TonearmColors = TonearmColors(),
    shadow: Offset = DaylightShadow,
) {
    val (pivot, pxPerUnit) = tonearmPlacementFor(spindle, platterRadiusPx)
    drawTonearmArm(pivot, pxPerUnit, armAngleDeg, lift, colors, shadow)
}

/** Standalone tonearm that fits itself into its own box (icons, previews). */
@Composable
fun Tonearm(
    armAngleDeg: Float,
    modifier: Modifier = Modifier,
    colors: TonearmColors = TonearmColors(),
) {
    Canvas(modifier) {
        val b = TonearmGeometry.bounds
        val scale = min(size.width / b.width, size.height / b.height)
        val pivot = Offset(
            (size.width - b.width * scale) / 2f - b.left * scale,
            (size.height - b.height * scale) / 2f - b.top * scale,
        )
        drawTonearm(pivot, scale, armAngleDeg, colors)
    }
}

// ---------------------------------------------------------------- fixed parts

private fun DrawScope.drawPlatform(c: TonearmColors) = with(TonearmGeometry) {
    val keyhole = keyholePath(baseCenter, baseRadius, tailCenter, tailRadius)
    drawPath(
        keyhole,
        Brush.radialGradient(
            listOf(c.plateLight, c.plateDark),
            center = Offset(-15f, -20f), radius = 120f,
        ),
    )
    drawPath(keyhole, c.plateOutline, style = Stroke(width = 1.6f))
    drawCircle(c.innerRing, innerRingRadius, innerRingCenter, style = Stroke(width = 1f))

    // Lift slot
    rr(slot, 5f, c.slotFill)
    rr(slot, 5f, c.slotOutline, Stroke(width = 1f))
    // Lever (stem with a notched tip)
    val lever = Path().apply {
        moveTo(8f, 44f)
        arcTo(Rect(8f, 40.5f, 15f, 47.5f), 180f, 180f, false)
        lineTo(15f, 66f); lineTo(13f, 69f); lineTo(13f, 72f)
        lineTo(10f, 72f); lineTo(10f, 69f); lineTo(8f, 66f); close()
    }
    drawPath(lever, c.lever)
    drawCircle(c.leverKnob, 4f, leverKnob)
    drawCircle(c.leverKnobDot, 1.6f, leverKnob)
    // Lift bar
    val bar = Path().apply { moveTo(-1f, 57f); quadraticTo(18f, 48f, 36f, 46f) }
    drawPath(bar, c.liftBar, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawArmRest(c: TonearmColors) = with(TonearmGeometry) {
    rr(restPost, 1f, c.restPost)
    rr(restClip, 1.5f, c.restClip)
    rr(restClip, 1.5f, c.restClipOutline, Stroke(width = 0.8f))
}

// ------------------------------------------------------------- rotating parts

private fun DrawScope.drawArm(c: TonearmColors) = with(TonearmGeometry) {
    // Counterweight: knurled black cylinder with a bluish glare stripe
    rr(
        counterweight, 3f,
        brush = Brush.horizontalGradient(
            0f to c.cwBlack, 0.45f to c.cwMid, 0.55f to c.cwGlare, 0.65f to c.cwMid, 1f to c.cwBlack,
            startX = counterweight.left, endX = counterweight.right,
        ),
    )
    repeat(knurlCount) { i ->
        val y = counterweight.top + 2f + i * 3.4f
        drawLine(c.cwKnurl, Offset(counterweight.left, y), Offset(counterweight.right, y), strokeWidth = 1.1f)
    }

    // Pivot housing
    rr(housingBevel, 1.5f, c.housingBevel)
    rr(
        housingFace, 1.5f,
        brush = Brush.verticalGradient(
            listOf(c.housingFaceTop, c.housingFaceBottom),
            startY = housingFace.top, endY = housingFace.bottom,
        ),
    )
    drawRect(c.housingBand, housingBand.topLeft, housingBand.size)
    drawRect(c.housingGroove, housingGroove.topLeft, housingGroove.size)
    drawCircle(c.housingGroove, 1f, Offset(-8.5f, -2f)) // side screw
    rr(cap, 2.5f, c.cap)
    rr(capHighlight, 1.5f, c.capHighlight)
    rr(sleeve, 2f, c.sleeve)

    // Chrome tube
    drawRect(
        Brush.horizontalGradient(
            0f to c.tubeEdge, 0.22f to c.tubeDark, 0.32f to c.tubeChrome,
            0.47f to c.tubeHighlight, 0.68f to c.tubeShade, 0.78f to c.tubeDark, 1f to c.tubeEdge,
            startX = tube.left, endX = tube.right,
        ),
        tube.topLeft, tube.size,
    )

    // Cartridge + collar
    rr(cartridgeBody, 2f, c.cartridgeBody)
    drawPath(Path().apply {
        moveTo(cartridgeSide[0].x, cartridgeSide[0].y)
        cartridgeSide.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }, c.cartridgeSide)
    rr(collar, 3f, c.collar)

    // Headshell front plate: tilted rounded parallelogram
    val shell = Path().apply {
        moveTo(40f, 222f); lineTo(51f, 222f)
        quadraticTo(54f, 222f, 53.4f, 225f)
        lineTo(39.4f, 272f)
        quadraticTo(38.6f, 275f, 35.6f, 275f)
        lineTo(26.6f, 275f)
        quadraticTo(23.6f, 275f, 24.4f, 272f)
        lineTo(37f, 225f)
        quadraticTo(37.8f, 222f, 40f, 222f)
        close()
    }
    drawPath(
        shell,
        Brush.horizontalGradient(
            0f to c.shellLeft, 0.6f to c.shellMid, 1f to c.shellRight,
            startX = 24f, endX = 54f,
        ),
    )
    drawLine(c.fingerLift, fingerLiftStart, fingerLiftEnd, strokeWidth = 3.6f, cap = StrokeCap.Round)
}

// -------------------------------------------------------------------- helpers

private fun DrawScope.rr(
    r: Rect, radius: Float, color: Color = Color.Unspecified,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
    brush: Brush? = null,
) {
    if (brush != null) drawRoundRect(brush, r.topLeft, r.size, CornerRadius(radius), style = style)
    else drawRoundRect(color, r.topLeft, r.size, CornerRadius(radius), style = style)
}

/** Convex hull of two circles (big one first) = the keyhole-shaped base. */
private fun keyholePath(c1: Offset, r1: Float, c2: Offset, r2: Float): Path {
    val dx = c2.x - c1.x; val dy = c2.y - c1.y
    val phi = atan2(dy, dx)
    val alpha = acos((r1 - r2) / hypot(dx, dy))
    fun pt(c: Offset, r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))
    fun deg(a: Float) = Math.toDegrees(a.toDouble()).toFloat()

    val a1 = pt(c1, r1, phi + alpha)
    val a2 = pt(c2, r2, phi + alpha)
    val b1 = pt(c1, r1, phi - alpha)
    val sweepSmall = deg(2 * alpha)
    return Path().apply {
        moveTo(a1.x, a1.y)
        lineTo(a2.x, a2.y)
        arcTo(Rect(c2, r2), deg(phi + alpha), -sweepSmall, false)      // around the tail
        lineTo(b1.x, b1.y)
        arcTo(Rect(c1, r1), deg(phi - alpha), -(360f - sweepSmall), false) // around the base
        close()
    }
}

@Preview(widthDp = 760, heightDp = 300)
@Composable
private fun TonearmPreview() {
    Row(
        Modifier.background(Color(0xFFE9E9E9)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(0f, 10f, 25f, 38f).forEach { angle ->
            Tonearm(angle, Modifier.size(width = 175.dp, height = 276.dp))
        }
    }
}