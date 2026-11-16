package com.kryptex.vault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptex.vault.ui.theme.Graphite600
import com.kryptex.vault.ui.theme.MetalDark
import com.kryptex.vault.ui.theme.MetalDeep
import com.kryptex.vault.ui.theme.MetalHighlight
import com.kryptex.vault.ui.theme.MetalLight
import com.kryptex.vault.ui.theme.MetalMid
import com.kryptex.vault.ui.theme.Pearl
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the vault door shows. Silver for everything except [ERROR], which turns crimson. */
enum class LogoState {
    /** At rest: a slow, soft glow. */
    IDLE,

    /** Work in progress: the handle wheel turns steadily and the glow breathes faster. */
    WORKING,

    /** Done: the handle swings a third of a turn, the bolts draw back and a ring pulses out. */
    SUCCESS,

    /** Failed or rejected: the handle jams with a short shake and the glow turns crimson. */
    ERROR,
}

/**
 * The Kryptex mark: a round bank-vault door in brushed metal, with locking bolts on its opening
 * side, hinges on the other, a dial track on the dark face and a three-spoke handle wheel. A soft
 * glow sits behind it. Light always falls from the top left, however the wheel turns.
 *
 * Every animated value is read while drawing, so the logo redraws but never recomposes per frame.
 * A tap brightens the door and nudges the handle.
 */
@Composable
fun KryptexLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    state: LogoState = LogoState.IDLE,
    onTap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(state)
    val press = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val pulse = remember { Animatable(0f) }
    val wheel = remember { Animatable(0f) }
    val retract = remember { Animatable(0f) }
    val alarm = animateFloatAsState(if (state == LogoState.ERROR) 1f else 0f, tween(300), label = "vault-alarm")
    val boost = animateFloatAsState(
        targetValue = when (state) {
            LogoState.IDLE -> 0f
            LogoState.WORKING -> 0.1f
            LogoState.SUCCESS -> 0.3f
            LogoState.ERROR -> 0.18f
        },
        animationSpec = tween(400),
        label = "vault-boost",
    )
    val breathing = rememberInfiniteTransition(label = "vault-breathe")
    val slow = breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vault-breathe-slow",
    )
    val fast = breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vault-breathe-fast",
    )
    val breathe = if (state == LogoState.WORKING) fast else slow
    val crimson = MaterialTheme.colorScheme.error

    LaunchedEffect(state) {
        when (state) {
            LogoState.WORKING -> {
                launch { retract.animateTo(0f, tween(200)) }
                while (true) {
                    wheel.snapTo(wheel.value % 360f) // the wheel looks the same every 120 degrees
                    wheel.animateTo(wheel.value + 360f, tween(WHEEL_TURN_MILLIS, easing = LinearEasing))
                }
            }
            LogoState.SUCCESS -> {
                pulse.snapTo(0f)
                launch { pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
                launch { retract.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
                launch { wheel.animateTo(nearestStop(wheel.value) + SPOKE_ANGLE, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)) }
                squash.animateTo(1.06f, tween(120))
                squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            LogoState.ERROR -> {
                launch { retract.animateTo(0f, tween(150)) }
                val rest = nearestStop(wheel.value)
                for (offset in JAM_DEGREES) wheel.animateTo(rest + offset, tween(60))
            }
            LogoState.IDLE -> {
                launch { retract.animateTo(0f, tween(300)) }
                wheel.animateTo(nearestStop(wheel.value), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }

    Spacer(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = squash.value
                scaleY = squash.value
            }
            .semantics { contentDescription = "Kryptex logo" }
            .pointerInput(onTap) {
                detectTapGestures(
                    onPress = {
                        scope.launch { squash.animateTo(0.95f, tween(80)) }
                        scope.launch { press.animateTo(1f, tween(80)) }
                        tryAwaitRelease()
                        scope.launch { squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        // Even a very short tap shows the full highlight before it fades.
                        scope.launch {
                            press.animateTo(1f, tween(40))
                            delay(120)
                            press.animateTo(0f, tween(700))
                        }
                        // The handle gives a little and springs back; never while it is turning on its own.
                        if (current != LogoState.WORKING) {
                            scope.launch {
                                val rest = nearestStop(wheel.value)
                                wheel.animateTo(rest + 24f, tween(140))
                                wheel.animateTo(rest, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            }
                        }
                    },
                    onTap = { onTap?.invoke() },
                )
            }
            .drawWithCache {
                val door = VaultDoor(this.size)
                onDrawBehind {
                    val alarmLevel = alarm.value
                    door.draw(
                        scope = this,
                        wheelDegrees = wheel.value,
                        boltsOut = 1f - retract.value,
                        glow = lerp(Pearl, crimson, alarmLevel),
                        glowAlpha = (0.10f + 0.10f * breathe.value + boost.value + 0.22f * press.value + 0.3f * alarmLevel).coerceAtMost(0.75f),
                        pulse = pulse.value,
                        alarm = alarmLevel,
                        crimson = crimson,
                        press = press.value,
                    )
                }
            },
    )
}

/**
 * The door at one canvas size. Geometry is in "units" matching the launcher icon, where the door
 * has a radius of 31; everything that does not move is computed once here.
 */
private class VaultDoor(canvas: Size) {
    private val c = Offset(canvas.width / 2f, canvas.height / 2f)
    private val glowRadius = canvas.minDimension / 2f
    private val u = glowRadius * DOOR_SHARE / 31f
    private val shadow = Offset(0.5f * u, 0.8f * u)

    private val glowBrush = Brush.radialGradient(
        0f to Color.White,
        0.5f to Color.White.copy(alpha = 0.35f),
        1f to Color.Transparent,
        center = c,
        radius = glowRadius,
    )
    private val bezelBrush = Brush.sweepGradient(
        listOf(MetalLight, MetalMid, MetalHighlight, MetalMid, MetalDark, MetalMid, MetalLight),
        center = c,
    )
    private val faceBrush = Brush.radialGradient(
        listOf(Color(0xFF2C2C2E), Color(0xFF161618), MetalDeep),
        center = c + Offset(-6f * u, -8f * u),
        radius = 32f * u,
    )
    private val hubBrush = Brush.radialGradient(
        listOf(MetalHighlight, MetalLight, MetalMid, MetalDark),
        center = c + Offset(-1.6f * u, -1.8f * u),
        radius = 6.4f * u,
    )
    private val hingeSize = Size(4.5f * u, 6f * u)
    private val hinges = listOf(c + Offset(-34.5f * u, -13f * u), c + Offset(-34.5f * u, 7f * u))
    private val hingeBrushes = hinges.map { Brush.verticalGradient(listOf(MetalLight, MetalMid, MetalDark), it.y, it.y + hingeSize.height) }

    /** Dial-track ticks; only drawn where they stay crisp. */
    private val ticks: FloatArray? = if (u < TICK_MIN_UNIT_PX) {
        null
    } else {
        FloatArray(TICKS * 4).also { t ->
            for (i in 0 until TICKS) {
                val a = 2.0 * PI * i / TICKS
                val inner = if (i % 4 == 0) 20.2f else 21.4f
                t[i * 4] = c.x + cos(a).toFloat() * inner * u
                t[i * 4 + 1] = c.y + sin(a).toFloat() * inner * u
                t[i * 4 + 2] = c.x + cos(a).toFloat() * 23f * u
                t[i * 4 + 3] = c.y + sin(a).toFloat() * 23f * u
            }
        }
    }

    fun draw(
        scope: DrawScope,
        wheelDegrees: Float,
        boltsOut: Float,
        glow: Color,
        glowAlpha: Float,
        pulse: Float,
        alarm: Float,
        crimson: Color,
        press: Float,
    ) = with(scope) {
        // Glow and success pulse.
        drawCircle(glowBrush, radius = glowRadius, center = c, alpha = glowAlpha, colorFilter = ColorFilter.tint(glow))
        if (pulse > 0f && pulse < 1f) {
            drawCircle(
                color = Pearl,
                radius = (31f * u) + (glowRadius - 31f * u) * pulse,
                center = c,
                alpha = (1f - pulse) * 0.7f,
                style = Stroke(width = 1.4f * u),
            )
        }

        // Locking bolts on the opening side, sliding out from under the bezel.
        val boltLength = 2.4f * u + 3f * u * boltsOut
        for (degrees in BOLT_DEGREES) {
            val a = Math.toRadians(degrees.toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(MetalDark, c + dir * (28f * u) + shadow, c + dir * (28f * u + boltLength) + shadow, strokeWidth = 2.8f * u, cap = StrokeCap.Round, alpha = 0.6f)
            drawLine(MetalLight, c + dir * (28f * u), c + dir * (28f * u + boltLength), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
        }

        // Hinges, tucked under the bezel.
        hinges.forEachIndexed { i, topLeft ->
            drawRoundRect(hingeBrushes[i], topLeft = topLeft, size = hingeSize, cornerRadius = CornerRadius(1f * u))
        }

        // Bezel: polished ring with a bright outer and a shadowed inner edge.
        drawCircle(bezelBrush, radius = 28f * u, center = c, style = Stroke(width = 6f * u))
        drawCircle(Color.White, radius = 31f * u, center = c, alpha = 0.22f, style = Stroke(width = 0.6f * u))
        drawCircle(Color.Black, radius = 25f * u, center = c, alpha = 0.7f, style = Stroke(width = 0.9f * u))
        if (alarm > 0f) {
            drawCircle(crimson, radius = 28f * u, center = c, alpha = 0.3f * alarm, style = Stroke(width = 6f * u))
            drawCircle(crimson, radius = 25.2f * u, center = c, alpha = 0.9f * alarm, style = Stroke(width = 0.9f * u))
        }

        // Door face and dial track.
        drawCircle(faceBrush, radius = 24.6f * u, center = c)
        ticks?.let { t ->
            for (i in 0 until TICKS) {
                drawLine(
                    Graphite600,
                    Offset(t[i * 4], t[i * 4 + 1]),
                    Offset(t[i * 4 + 2], t[i * 4 + 3]),
                    strokeWidth = if (i % 4 == 0) 0.8f * u else 0.45f * u,
                    alpha = 0.9f,
                )
            }
        }

        // Handle wheel: three spokes with knobs. Positions turn; the light stays top-left.
        for (k in 0 until 3) {
            val a = Math.toRadians((wheelDegrees - 90f + SPOKE_ANGLE * k).toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val from = c + dir * (4.6f * u)
            val to = c + dir * (15f * u)
            val knob = c + dir * (16.4f * u)
            drawLine(Color.Black, from + shadow, to + shadow, strokeWidth = 3f * u, cap = StrokeCap.Round, alpha = 0.55f)
            drawLine(MetalLight, from, to, strokeWidth = 2.5f * u, cap = StrokeCap.Round)
            drawLine(MetalHighlight, from + Offset(-0.45f * u, -0.45f * u), to + Offset(-0.45f * u, -0.45f * u), strokeWidth = 0.7f * u, cap = StrokeCap.Round, alpha = 0.55f)
            drawCircle(Color.Black, radius = 2.9f * u, center = knob + shadow, alpha = 0.55f)
            drawCircle(MetalMid, radius = 2.7f * u, center = knob)
            drawCircle(MetalLight, radius = 1.9f * u, center = knob + Offset(-0.5f * u, -0.6f * u))
            drawCircle(Color.White, radius = 0.7f * u, center = knob + Offset(-1f * u, -1.1f * u), alpha = 0.7f)
        }

        // Hub.
        drawCircle(Color.Black, radius = 6.1f * u, center = c + shadow, alpha = 0.55f)
        drawCircle(hubBrush, radius = 5.6f * u, center = c)
        drawCircle(Color.Black, radius = 5.6f * u, center = c, alpha = 0.45f, style = Stroke(width = 0.5f * u))
        if (alarm > 0f) drawCircle(crimson, radius = 5.6f * u, center = c, alpha = 0.25f * alarm)
        drawCircle(MetalDeep, radius = 2f * u, center = c)
        drawCircle(Color.White, radius = 0.6f * u, center = c + Offset(-0.6f * u, -0.6f * u), alpha = 0.5f)

        // Specular sweep on the bezel, brighter while pressed.
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = c - Offset(29.6f * u, 29.6f * u),
            size = Size(59.2f * u, 59.2f * u),
            alpha = 0.16f + 0.3f * press,
            style = Stroke(width = 1.1f * u, cap = StrokeCap.Round),
        )
        if (press > 0f) drawCircle(Color.White, radius = 31f * u, center = c, alpha = 0.05f * press)
    }
}

private fun nearestStop(degrees: Float): Float = (degrees / SPOKE_ANGLE).roundToInt() * SPOKE_ANGLE

/** Door radius as a share of half the canvas; the glow fills the rest. */
private const val DOOR_SHARE = 0.64f
private const val SPOKE_ANGLE = 120f
private const val WHEEL_TURN_MILLIS = 2400
private const val TICKS = 48
private const val TICK_MIN_UNIT_PX = 1.6f
private val JAM_DEGREES = floatArrayOf(9f, -7f, 5f, -3f, 1.5f, 0f)

/** Bolts on the opening side only (0 degrees points right; the hinges are on the left). */
private val BOLT_DEGREES = floatArrayOf(-135f, -90f, -45f, 0f, 45f, 90f, 135f)
