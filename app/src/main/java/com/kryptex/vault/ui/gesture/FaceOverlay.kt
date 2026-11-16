package com.kryptex.vault.ui.gesture

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A ring around the circular preview. While no face is in view it is a slowly turning dashed
 * scan line; once a face is found it settles into a quiet solid ring, and a bright arc sweeps
 * around it as the current gesture is held.
 */
@Composable
fun FaceOverlay(
    faceDetected: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier,
) {
    val bright = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val scan = rememberInfiniteTransition(label = "face-scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "face-scan-phase",
    )
    Canvas(modifier.fillMaxSize()) {
        val strokeWidth = 6.dp.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        if (faceDetected) {
            drawArc(
                color = bright.copy(alpha = 0.28f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth / 2),
            )
        } else {
            val dash = 7.dp.toPx()
            drawArc(
                color = dim.copy(alpha = 0.75f),
                startAngle = 360f * scan.value, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(strokeWidth / 2, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
        if (holdProgress > 0f) {
            drawArc(
                color = bright,
                startAngle = -90f, sweepAngle = 360f * holdProgress.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}
