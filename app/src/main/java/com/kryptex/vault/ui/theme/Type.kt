package com.kryptex.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Crisp and quiet: light display sizes, firm titles, slightly tightened headlines. */
internal val KryptexTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Light),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelMedium = Base.labelMedium.copy(letterSpacing = 0.4.sp),
)

/** The KRYPTEX wordmark: light weight, wide tracking. */
val WordmarkStyle = TextStyle(
    fontWeight = FontWeight.Light,
    fontSize = 28.sp,
    letterSpacing = 9.sp,
)

/** For fingerprints, hashes and sizes where digit alignment matters. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
