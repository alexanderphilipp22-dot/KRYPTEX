package com.kryptex.vault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.vision.Gesture

/**
 * A simple drawing of a gesture, as the user sees themselves in the mirror-style camera preview:
 * the user's left eye is on the right-hand side of the picture.
 */
@Composable
fun GestureGlyph(
    gesture: Gesture,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    showLabel: Boolean = false,
) {
    val name = LocalStrings.current.gestureName(gesture)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = modifier.size(size).semantics { contentDescription = name }) { drawGesture(gesture, tint) }
        // Capped width + centred: long DE/RU names wrap to two lines and the picker grid stays even.
        if (showLabel) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 88.dp),
            )
        }
    }
}

private fun DrawScope.drawGesture(gesture: Gesture, tint: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.06f, cap = StrokeCap.Round)

    // Eye centres: user's right eye on the left of the picture, user's left eye on the right.
    val rightEye = Offset(w * 0.28f, h * 0.40f)
    val leftEye = Offset(w * 0.72f, h * 0.40f)
    val eyeW = w * 0.30f
    val eyeH = h * 0.18f

    fun openEye(centre: Offset, pupilDx: Float = 0f, pupilDy: Float = 0f) {
        drawOval(tint, topLeft = Offset(centre.x - eyeW / 2, centre.y - eyeH / 2), size = Size(eyeW, eyeH), style = stroke)
        drawCircle(tint, radius = eyeH * 0.32f, center = Offset(centre.x + pupilDx * eyeW * 0.25f, centre.y + pupilDy * eyeH * 0.22f))
    }

    fun closedEye(centre: Offset) {
        val path = Path().apply {
            moveTo(centre.x - eyeW / 2, centre.y)
            quadraticTo(centre.x, centre.y + eyeH * 0.9f, centre.x + eyeW / 2, centre.y)
        }
        drawPath(path, tint, style = stroke)
    }

    fun brows(raise: Float) {
        for (c in listOf(rightEye, leftEye)) {
            val y = c.y - eyeH * (1.1f + raise)
            drawLine(tint, Offset(c.x - eyeW / 2, y), Offset(c.x + eyeW / 2, y - h * 0.02f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }

    fun mouth(open: Boolean, smile: Boolean) {
        val centre = Offset(w * 0.5f, h * 0.76f)
        when {
            open -> drawOval(tint, Offset(centre.x - w * 0.12f, centre.y - h * 0.08f), Size(w * 0.24f, h * 0.18f), style = stroke)
            smile -> {
                val path = Path().apply {
                    moveTo(centre.x - w * 0.2f, centre.y - h * 0.02f)
                    quadraticTo(centre.x, centre.y + h * 0.16f, centre.x + w * 0.2f, centre.y - h * 0.02f)
                }
                drawPath(path, tint, style = stroke)
            }
            else -> drawLine(tint, Offset(centre.x - w * 0.12f, centre.y), Offset(centre.x + w * 0.12f, centre.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }

    when (gesture) {
        Gesture.LONG_BLINK -> { closedEye(rightEye); closedEye(leftEye); mouth(false, false) }
        // User's left eye = right-hand side of the picture.
        Gesture.WINK_LEFT -> { openEye(rightEye); closedEye(leftEye); mouth(false, false) }
        Gesture.WINK_RIGHT -> { closedEye(rightEye); openEye(leftEye); mouth(false, false) }
        // Looking to the user's left is toward the right-hand side of the mirror-style picture.
        Gesture.GAZE_LEFT -> { openEye(rightEye, 1f); openEye(leftEye, 1f); mouth(false, false) }
        Gesture.GAZE_RIGHT -> { openEye(rightEye, -1f); openEye(leftEye, -1f); mouth(false, false) }
        Gesture.GAZE_UP -> { openEye(rightEye, 0f, -1f); openEye(leftEye, 0f, -1f); mouth(false, false) }
        Gesture.GAZE_DOWN -> { openEye(rightEye, 0f, 1f); openEye(leftEye, 0f, 1f); mouth(false, false) }
        Gesture.BROW_RAISE -> { openEye(rightEye); openEye(leftEye); brows(1.0f); mouth(false, false) }
        Gesture.MOUTH_OPEN -> { openEye(rightEye); openEye(leftEye); mouth(true, false) }
        Gesture.SMILE -> { openEye(rightEye); openEye(leftEye); mouth(false, true) }
    }
}
