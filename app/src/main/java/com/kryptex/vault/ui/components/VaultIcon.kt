package com.kryptex.vault.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A minimalist vault door for 24 dp icon slots: rim, hinges and a three-spoke handle wheel. Single
 * colour, so [androidx.compose.material3.Icon] tints it like any Material icon.
 */
val VaultIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(name = "Vault", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = ink, pathFillType = PathFillType.EvenOdd) {
            circle(12f, 12f, 10f)
            circle(12f, 12f, 7.9f)
        }
        .path(fill = ink) {
            // Hinges on the left edge.
            moveTo(0.9f, 7.4f); lineTo(3f, 7.4f); lineTo(3f, 9.6f); lineTo(0.9f, 9.6f); close()
            moveTo(0.9f, 14.4f); lineTo(3f, 14.4f); lineTo(3f, 16.6f); lineTo(0.9f, 16.6f); close()
            // Hub and knobs.
            circle(12f, 12f, 2f)
            circle(12f, 5.75f, 1.15f)
            circle(17.41f, 15.12f, 1.15f)
            circle(6.59f, 15.12f, 1.15f)
        }
        .path(stroke = ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 10f); lineTo(12f, 6.6f)
            moveTo(13.73f, 13f); lineTo(16.68f, 14.7f)
            moveTo(10.27f, 13f); lineTo(7.32f, 14.7f)
        }
        .build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2f * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2f * r, dy1 = 0f)
    close()
}
