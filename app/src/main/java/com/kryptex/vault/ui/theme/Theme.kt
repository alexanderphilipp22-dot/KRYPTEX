package com.kryptex.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val KryptexShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * One look on every device: stealth monochrome on pitch black. It deliberately ignores the system
 * light/dark setting and wallpaper colours, so nothing about the app changes with the phone.
 */
@Composable
fun KryptexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KryptexColors,
        typography = KryptexTypography,
        shapes = KryptexShapes,
        content = content,
    )
}
