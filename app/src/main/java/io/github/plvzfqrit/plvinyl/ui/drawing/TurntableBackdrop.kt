package io.github.plvzfqrit.plvinyl.ui.drawing

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.plvzfqrit.plvinyl.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.plvzfqrit.plvinyl.core.look.Paint
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Screen backgrounds in the style of the references:
 *  - Paper:     warm off-white with a fine paper grain
 *  - Charcoal:  flat dark gray, a touch darker toward the bottom
 *  - Amber:     saturated orange that deepens toward the bottom
 *  - AlbumBlur: the current album art, heavily blurred and dimmed
 *  - Walnut, Oak: real wood, oiled (CC0 veneer photos from Poly Haven; see tools/make_wood.py)
 *  - Custom:    your own paint (drawn live, so painting recolors it instantly)
 *
 * Colors are sampled from the reference screenshots.
 */
enum class BackdropStyle(
    /** The background's main color (ARGB): where painting the deck starts from. */
    val baseColor: Int,
    /** Its front panel's main color (ARGB): where painting the panel starts from. */
    val panelColor: Int,
    /** A light deck: dark text and icons on it. */
    val isLight: Boolean = false,
    /** The wood photo, for the wooden decks. */
    @param:DrawableRes val wood: Int? = null,
) {
    Paper(0xFFF0EDE8.toInt(), 0xFFC3C6C8.toInt(), isLight = true),      // ribbed aluminium
    Charcoal(0xFF383838.toInt(), 0xFF212225.toInt()),                   // ribbed gunmetal
    Amber(0xFFEE991A.toInt(), 0xFF432914.toInt()),                      // ribbed bronze-brown
    AlbumBlur(0xFF383838.toInt(), 0xFF2C5F63.toInt()),                  // tinted by the album
    Walnut(0xFF4E3525.toInt(), 0xFF2A211B.toInt(), wood = R.drawable.wood_walnut),             // ribbed dark bronze
    Oak(0xFFCCAC80.toInt(), 0xFFC3C6C8.toInt(), isLight = true, wood = R.drawable.wood_oak),   // ribbed aluminium
    Custom(0xFF383838.toInt(), 0xFF212225.toInt()),
}

/** What's on screen: a baked background, or the painted deck drawn live. */
private sealed interface Layer {
    data class Baked(val image: ImageBitmap) : Layer
    data object Painted : Layer
}

@Composable
fun TurntableBackdrop(
    style: BackdropStyle,
    art: Bitmap?,
    /** Identifies the record the art belongs to (e.g. "title|artist"). */
    artKey: String?,
    modifier: Modifier = Modifier,
    /** The paint, for the Custom background. */
    deckPaint: Color? = null,
) {
    val density = LocalDensity.current
    val resources = LocalContext.current.resources

    // Blurred art, made off the main thread, once per record. If the art is briefly
    // missing we keep the last one instead of flashing to gray.
    var wash by remember { mutableStateOf<Pair<String?, ImageBitmap>?>(null) }
    LaunchedEffect(artKey, art) {
        if (art != null && wash?.first != artKey) {
            val blurred = withContext(Dispatchers.Default) { blurredWash(art) }
            wash = artKey to blurred
        }
    }
    val washImage = wash?.second

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.coerceAtLeast(1)
        val h = constraints.maxHeight.coerceAtLeast(1)

        // The new background fades in ON TOP of the old one, which stays fully opaque
        // underneath. (A regular crossfade makes both half-transparent at the midpoint,
        // so whatever is behind shows through: that was the flash.)
        var shown by remember { mutableStateOf<Layer?>(null) }
        var previous by remember { mutableStateOf<Layer?>(null) }
        val fade = remember { Animatable(1f) }
        // The painted deck's grain doesn't depend on the color: baked once per size.
        var grain by remember { mutableStateOf<Grain?>(null) }
        // Only the album blur changes with the record; the others are baked once.
        val albumWash = if (style == BackdropStyle.AlbumBlur) washImage else null
        LaunchedEffect(style, albumWash, w, h) {
            // Baked off the main thread; shown the moment it's ready.
            val next = if (style == BackdropStyle.Custom) {
                if (grain?.matches(w, h) != true) grain = withContext(Dispatchers.Default) { bakeGrain(w, h, density) }
                Layer.Painted
            } else {
                Layer.Baked(withContext(Dispatchers.Default) {
                    val wood = style.wood?.let { decodeWood(resources, it) }
                    bakeBackdrop(style, albumWash, wood, w, h, density)
                })
            }
            // If a fade was still running, keep whichever layer is mostly visible below.
            if (shown != null && (previous == null || fade.value >= 0.5f)) previous = shown
            shown = next
            fade.snapTo(0f)
            fade.animateTo(1f, tween(500))
            previous = null
        }

        Canvas(Modifier.fillMaxSize()) {
            fun draw(layer: Layer, alpha: Float) = when (layer) {
                is Layer.Baked -> drawImage(layer.image, alpha = alpha)
                Layer.Painted -> drawPaintedDeck(deckPaint ?: Color(BackdropStyle.Custom.baseColor), grain, alpha)
            }
            previous?.let { draw(it, 1f) }
            shown?.let { draw(it, if (previous != null) fade.value else 1f) }
        }
    }
}

/** Light and dark specks for the painted deck, baked once per screen size (they don't depend on the color). */
private class Grain(val light: ImageBitmap, val dark: ImageBitmap) {
    fun matches(w: Int, h: Int) = light.width == w && light.height == h
}

private fun bakeGrain(w: Int, h: Int, density: Density): Grain {
    fun bake(block: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(w.toFloat(), h.toFloat()), block)
        return bmp
    }
    return Grain(
        light = bake { drawGrain(lightAlpha = 1f, darkAlpha = 0f) },
        dark = bake { drawGrain(lightAlpha = 0f, darkAlpha = 1f) },
    )
}

/**
 * The painted deck: a gradient from the paint (a touch lighter at the top, deeper at the
 * bottom, like the presets) and the grain, stronger on light paints (paper fibre) and
 * fainter on dark ones. Drawn every frame it's needed, so a new paint shows at once.
 */
private fun DrawScope.drawPaintedDeck(paint: Color, grain: Grain?, alpha: Float) {
    val argb = paint.toArgb()
    val l = Paint.toHsl(argb).l
    drawRect(
        Brush.verticalGradient(
            0f to Color(Paint.shade(argb, +0.02f)),
            0.6f to paint,
            1f to Color(Paint.shade(argb, -0.07f)),
        ),
        alpha = alpha,
    )
    if (grain == null) return
    fun ramp(from: Float, to: Float) = ((l - from) / (to - from)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    drawImage(grain.light, alpha = alpha * (0.03f + 0.32f * ramp(0.6f, 0.95f)))
    drawImage(grain.dark, alpha = alpha * (0.06f - 0.025f * ramp(0.5f, 0.95f)))
}

/** Tonearm colors that belong with each background. */
internal fun BackdropStyle.tonearmColors(): TonearmColors = when (this) {
    // White turntable: cream plate, white arm parts, silver tube.
    BackdropStyle.Paper -> TonearmColors(
        plateLight = Color(0xFFFBF9F5), plateDark = Color(0xFFEFECE6),
        plateOutline = Color(0xFFCFC9BF), innerRing = Color(0xFFDDD8CF),
        slotFill = Color(0xFFFFFFFF), slotOutline = Color(0xFFD8D2C8),
        lever = Color(0xFFBDB7AD), leverKnob = Color(0xFFA8A295), leverKnobDot = Color(0xFFD8D2C8),
        liftBar = Color(0xFFB8B2A8),
        cwBlack = Color(0xFFE6E2DB), cwMid = Color(0xFFF7F5F1), cwGlare = Color(0xFFFFFFFF),
        cwKnurl = Color(0x22000000),
        housingBevel = Color(0xFFD2CCC2), housingFaceTop = Color(0xFFFBF9F6),
        housingFaceBottom = Color(0xFFEFEBE5), housingBand = Color(0xFFF4F1EC),
        housingGroove = Color(0xFFDCD6CC), cap = Color(0xFFF6F3EE), capHighlight = Color(0xFFFFFFFF),
        sleeve = Color(0xFFECE8E1),
        tubeEdge = Color(0xFF8C8E8E), tubeDark = Color(0xFFA5A7A7), tubeChrome = Color(0xFFC9CBCB),
        tubeHighlight = Color(0xFFF2F3F3), tubeShade = Color(0xFFB0B2B2),
        collar = Color(0xFFECE8E1), cartridgeBody = Color(0xFFE4E0D9), cartridgeSide = Color(0xFFA9ABA6),
        shellLeft = Color(0xFFEEEAE4), shellMid = Color(0xFFFFFFFF), shellRight = Color(0xFFF2EFEA),
        fingerLift = Color(0xFFD8D3CA),
        restPost = Color(0xFFCFC9BF), restClip = Color(0x8CFFFFFF), restClipOutline = Color(0xFFBDB7AD),
    )

    // Black arm on an amber deck.
    BackdropStyle.Amber -> TonearmColors(
        plateLight = Color(0xFFF4A42A), plateDark = Color(0xFFE3921C),
        plateOutline = Color(0xFFB06D0E), innerRing = Color(0xFFC47D12),
        slotFill = Color(0xFFEE9C24), slotOutline = Color(0xFFB87612),
        lever = Color(0xFF3A2A12), leverKnob = Color(0xFF1E1A14), leverKnobDot = Color(0xFF6A5A40),
        liftBar = Color(0xFF1A1410),
        cwBlack = Color(0xFF050505), cwMid = Color(0xFF1A1A1A), cwGlare = Color(0xFF3A3A3A),
        housingBevel = Color(0xFF2A2A2A), housingFaceTop = Color(0xFF1E1E1E),
        housingFaceBottom = Color(0xFF161616), housingBand = Color(0xFF202020),
        housingGroove = Color(0xFF0C0C0C), cap = Color(0xFF1A1A1A), capHighlight = Color(0xFF2E2E2E),
        sleeve = Color(0xFF141414),
        collar = Color(0xFF111111), cartridgeBody = Color(0xFF0E0E0E),
        shellLeft = Color(0xFF141414), shellMid = Color(0xFF222222), shellRight = Color(0xFF161616),
        fingerLift = Color(0xFF1A1A1A),
        restPost = Color(0xFF1A1A1A), restClip = Color(0x66FFE2B0), restClipOutline = Color(0xFF9C650C),
    )

    // Wood: the plate in the wood's own shades. A black arm on walnut, a white one on oak.
    BackdropStyle.Walnut -> BackdropStyle.Amber.tonearmColors().withPlate(baseColor)
    BackdropStyle.Oak -> BackdropStyle.Paper.tonearmColors().withPlate(baseColor)

    // Dark deck: the photo's slate-gray arm on a dark plate. (A painted deck starts from
    // this or the Paper set and shades the plate from its paint: see DeckColors.)
    BackdropStyle.Charcoal, BackdropStyle.AlbumBlur, BackdropStyle.Custom -> TonearmColors(
        plateLight = Color(0xFF3A3A3A), plateDark = Color(0xFF2C2C2C),
        plateOutline = Color(0xFF555555), innerRing = Color(0xFF444444),
        slotFill = Color(0xFF333333), slotOutline = Color(0xFF4A4A4A),
        lever = Color(0xFF6A6A6E), leverKnob = Color(0xFF1E1E20), leverKnobDot = Color(0xFF7A7E84),
        liftBar = Color(0xFF111111),
        restPost = Color(0xFF111111), restClip = Color(0x66BDBDBD), restClipOutline = Color(0xFF5A5A5A),
    )
}

/** A wood photo. It's already screen-sized, so it's decoded as it is. */
private fun decodeWood(resources: Resources, @DrawableRes id: Int): ImageBitmap? =
    BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })?.asImageBitmap()

private fun bakeBackdrop(
    style: BackdropStyle, wash: ImageBitmap?, wood: ImageBitmap?, w: Int, h: Int, density: Density,
): ImageBitmap {
    val bmp = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
        when (style) {
            BackdropStyle.Paper -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFFF2EFEA),
                        0.7f to Color(0xFFF0EDE8),
                        1f to Color(0xFFECE9E4),
                    )
                )
                drawGrain(lightAlpha = 0.35f, darkAlpha = 0.035f)
            }

            BackdropStyle.Charcoal -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF3B3B3B),
                        0.6f to Color(0xFF383838),
                        1f to Color(0xFF2E2E2E),
                    )
                )
                drawGrain(lightAlpha = 0.03f, darkAlpha = 0.06f)
            }

            BackdropStyle.Amber -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFFF29E1C),
                        0.45f to Color(0xFFEE991A),
                        0.62f to Color(0xFFD98A17),
                        0.80f to Color(0xFFB87612),
                        1f to Color(0xFF9C650C),
                    )
                )
                drawGrain(lightAlpha = 0.04f, darkAlpha = 0.05f)
            }

            BackdropStyle.Walnut, BackdropStyle.Oak -> {
                if (wood != null) drawCover(wood, FilterQuality.High) else drawRect(Color(style.baseColor))
                // Like the others, a little lighter at the top and deeper toward the front edge.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.04f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.16f),
                    )
                )
            }

            // Drawn live instead (drawPaintedDeck); a plain fill if it's ever baked.
            BackdropStyle.Custom -> drawRect(Color(BackdropStyle.Custom.baseColor))

            BackdropStyle.AlbumBlur -> {
                if (wash == null) {
                    drawRect(Color(0xFF383838))                   // no art yet: charcoal
                } else {
                    // Stretch the tiny blurred art to cover the screen (bilinear = smooth).
                    drawCover(wash, FilterQuality.Low)
                }
                // Dim it, more toward the bottom, so white text stays readable.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.5f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
                drawGrain(lightAlpha = 0.025f, darkAlpha = 0.05f)
            }
        }
    }
    return bmp
}

/** [image] scaled to cover the whole area, centered (the overflow is cropped). */
private fun DrawScope.drawCover(image: ImageBitmap, quality: FilterQuality) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val dw = (image.width * scale).roundToInt()
    val dh = (image.height * scale).roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset(((size.width - dw) / 2).roundToInt(), ((size.height - dh) / 2).roundToInt()),
        dstSize = IntSize(dw, dh),
        filterQuality = quality,
    )
}

/** Fine, static film/paper grain. Fixed seed, so it never flickers. */
private fun DrawScope.drawGrain(lightAlpha: Float, darkAlpha: Float) {
    val count = (size.width * size.height / 90f).toInt().coerceIn(4000, 40000)
    val rnd = Random(11)
    val light = ArrayList<Offset>(count / 2)
    val dark = ArrayList<Offset>(count / 2)
    repeat(count) { i ->
        val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        if (i % 2 == 0) light += p else dark += p
    }
    drawPoints(light, PointMode.Points, Color.White.copy(alpha = lightAlpha), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = darkAlpha), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

/** Tiny, heavily blurred copy of the art (48 px wide, 3 box-blur passes). */
private fun blurredWash(src: Bitmap): ImageBitmap {
    val w = 48
    val h = (w * src.height / src.width.toFloat()).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    repeat(3) {
        boxBlur(px, w, h, radius = 3, horizontal = true)
        boxBlur(px, w, h, radius = 3, horizontal = false)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val out = IntArray(px.size)
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until len) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, len - 1)
                val c = if (horizontal) px[line * w + j] else px[j * w + line]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
            val idx = if (horizontal) line * w + i else i * w + line
            out[idx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
    out.copyInto(px)
}