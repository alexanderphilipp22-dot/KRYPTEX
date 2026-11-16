package com.kryptex.vault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Makes [content] crumble into the dark: it breaks into small cells that give way from the top
 * down along a ragged front, and every cell lets go a speck of silver dust or a hex digit that
 * drifts up and fades out.
 *
 * Nothing is copied. The content keeps drawing itself and is erased cell by cell inside its own
 * layer, so a revealed secret never ends up in an extra bitmap. Setting [dissolving] back to false
 * runs the effect backwards and the content re-forms, which is what a failed operation should look
 * like. While dissolving, the content takes no touches and is hidden from accessibility services.
 *
 * @param onDissolved called once the content has completely vanished
 */
@Composable
fun Dissolvable(
    dissolving: Boolean,
    onDissolved: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onDissolved)
    LaunchedEffect(dissolving) {
        if (dissolving) {
            val millis = ((1f - progress.value) * DISSOLVE_MILLIS).roundToInt()
            progress.animateTo(1f, tween(millis, easing = LinearEasing))
            finished()
        } else if (progress.value > 0f) {
            val millis = (progress.value * REFORM_MILLIS).roundToInt()
            progress.animateTo(0f, tween(millis, easing = FastOutSlowInEasing))
        }
    }

    val field = remember { DustField() }
    val light = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val glyphs = rememberHexGlyphs(light)

    val frozen = if (dissolving) {
        Modifier
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier
            .then(frozen)
            .drawWithContent {
                drawContent()
                val p = progress.value
                if (p > 0f) field.drawDust(this, p, light, dim, glyphs)
            },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    // Punching holes needs a layer of its own; idle content does not.
                    compositingStrategy = if (progress.value > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    val p = progress.value
                    if (p > 0f) field.erase(this, p)
                },
        ) {
            content()
        }
    }
}

@Composable
private fun rememberHexGlyphs(color: Color): Array<TextLayoutResult> {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    return remember(measurer, color) {
        val style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 9.sp)
        Array(HEX.length) { measurer.measure(HEX[it].toString(), style) }
    }
}

/**
 * The cells and dust particles for one content size, generated once. Timeline values run from 0
 * (intact) to 1 (gone): cell `i` starts to crumble at `start[i]` and its particle lives for
 * `life[i]` from then on.
 */
private class DustField {
    private var prepared = Size.Unspecified
    private var cell = 1f
    private var cols = 0
    private var rows = 0
    private var start = FloatArray(0)
    private var life = FloatArray(0)
    private var driftX = FloatArray(0)
    private var rise = FloatArray(0)
    private var speck = FloatArray(0)
    private var wobble = FloatArray(0)
    private var shade = FloatArray(0)
    private var glyph = IntArray(0)
    private var rowFirst = FloatArray(0)
    private var rowLast = FloatArray(0)

    private fun prepare(size: Size, density: Float) {
        if (size == prepared || size.width <= 0f || size.height <= 0f) return
        prepared = size
        // Whole pixels, so neighbouring cells meet exactly and no seams of half-erased pixels show.
        cell = sqrt(size.width * size.height / MAX_CELLS).coerceIn(MIN_CELL_DP * density, MAX_CELL_DP * density).roundToInt().toFloat()
        cols = ceil(size.width / cell).toInt().coerceAtLeast(1)
        rows = ceil(size.height / cell).toInt().coerceAtLeast(1)
        val n = cols * rows
        start = FloatArray(n)
        life = FloatArray(n)
        driftX = FloatArray(n)
        rise = FloatArray(n)
        speck = FloatArray(n)
        wobble = FloatArray(n)
        shade = FloatArray(n)
        glyph = IntArray(n)
        rowFirst = FloatArray(rows) { 1f }
        rowLast = FloatArray(rows)

        // The same pattern every time: this is decoration, no secure randomness needed.
        val random = Random(SEED)
        val maxRise = min(size.height * 0.9f, MAX_RISE_DP * density)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = r * cols + c
                val down = if (rows == 1) 0f else r / (rows - 1f)
                val across = if (cols == 1) 0f else c / (cols - 1f)
                // A ragged front that sweeps from the top down.
                val wave = 0.5f + 0.5f * sin(across * 2f * PI.toFloat() + down * 3f)
                val t = (0.78f * down + 0.1f * wave + 0.12f * random.nextFloat()).coerceIn(0f, 1f)
                start[i] = t * SPREAD
                rowFirst[r] = min(rowFirst[r], start[i])
                rowLast[r] = max(rowLast[r], start[i])
                life[i] = MIN_LIFE + (MAX_LIFE - MIN_LIFE) * random.nextFloat()
                driftX[i] = (random.nextFloat() * 2f - 1f) * cell * 2.5f
                rise[i] = maxRise * (0.45f + 0.55f * random.nextFloat())
                speck[i] = cell * (0.28f + 0.3f * random.nextFloat())
                wobble[i] = random.nextFloat() * 2f * PI.toFloat()
                shade[i] = random.nextFloat()
                glyph[i] = if (random.nextFloat() < GLYPH_SHARE) random.nextInt(HEX.length) else -1
            }
        }
    }

    /** Inside the content's layer: cuts away every cell that has started to crumble. */
    fun erase(scope: DrawScope, p: Float) = with(scope) {
        prepare(size, density)
        for (r in 0 until rows) {
            if (p <= rowFirst[r]) continue
            val y = r * cell
            if (p >= rowLast[r] + FADE) {
                drawRect(Color.Black, Offset(0f, y), Size(size.width, cell), blendMode = BlendMode.DstOut)
                continue
            }
            for (c in 0 until cols) {
                val q = ((p - start[r * cols + c]) / FADE).coerceIn(0f, 1f)
                if (q > 0f) drawRect(Color.Black, Offset(c * cell, y), Size(cell, cell), alpha = q, blendMode = BlendMode.DstOut)
            }
        }
    }

    /** Above the content: the breaking edge flashes, and the dust drifts up and away. */
    fun drawDust(scope: DrawScope, p: Float, light: Color, dim: Color, glyphs: Array<TextLayoutResult>) = with(scope) {
        prepare(size, density)
        for (i in start.indices) {
            val since = p - start[i]
            if (since <= 0f) continue
            val r = i / cols
            val c = i % cols
            if (since < FADE) {
                drawRect(light, Offset(c * cell, r * cell), Size(cell, cell), alpha = (1f - since / FADE) * 0.35f)
            }
            val age = since / life[i]
            if (age >= 1f) continue
            val x = (c + 0.5f) * cell + driftX[i] * age + sin(wobble[i] + age * 7f) * cell * 0.3f
            val y = (r + 0.5f) * cell - rise[i] * age * age // starts slowly, then lifts away
            val remaining = 1f - age
            val fade = remaining * sqrt(remaining)
            val g = glyph[i]
            if (g >= 0) {
                val text = glyphs[g]
                drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f), alpha = fade * 0.85f)
            } else {
                val s = speck[i] * (1f - 0.6f * age)
                drawRect(
                    lerp(dim, light, shade[i]),
                    Offset(x - s / 2f, y - s / 2f),
                    Size(s, s),
                    alpha = fade * (0.55f + 0.45f * shade[i]),
                )
            }
        }
    }
}

private const val DISSOLVE_MILLIS = 1900
private const val REFORM_MILLIS = 650

/** Cells start to crumble within the first [SPREAD] of the timeline; the rest lets the dust fade. */
private const val SPREAD = 0.6f

/** How long one cell takes to vanish, in timeline units. */
private const val FADE = 0.08f
private const val MIN_LIFE = 0.24f
private const val MAX_LIFE = 0.38f // SPREAD + MAX_LIFE stays within the timeline

private const val MAX_CELLS = 520f
private const val MIN_CELL_DP = 5f
private const val MAX_CELL_DP = 14f
private const val MAX_RISE_DP = 140f
private const val GLYPH_SHARE = 0.16f
private const val SEED = 0x4B52
private const val HEX = "0123456789ABCDEF"
