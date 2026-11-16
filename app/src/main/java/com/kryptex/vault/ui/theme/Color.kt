package com.kryptex.vault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Stealth monochrome: pitch black, graphite and silver. The one hue left is a muted crimson,
// reserved for errors and failed attempts so that it never loses its meaning.

val PitchBlack = Color(0xFF000000)
val Obsidian = Color(0xFF080808)
val Graphite900 = Color(0xFF141414)
val Graphite800 = Color(0xFF1E1E1E)
val Graphite700 = Color(0xFF2C2C2C)
val Graphite600 = Color(0xFF3A3A3C)
val Steel = Color(0xFF636366)
val Silver = Color(0xFF8E8E93)
val Platinum = Color(0xFFC7C7CC)
val Pearl = Color(0xFFE5E5EA)

val Crimson = Color(0xFFE5484D)
private val CrimsonDeep = Color(0xFF2A0F12)
private val CrimsonMist = Color(0xFFF2B8B8)

/** Brushed-metal shades for the vault door. */
internal val MetalHighlight = Color(0xFFF2F2F7)
internal val MetalLight = Color(0xFFB4B4BA)
internal val MetalMid = Color(0xFF6E6E73)
internal val MetalDark = Color(0xFF2A2A2D)
internal val MetalDeep = Color(0xFF0B0B0C)

/**
 * Contrast on black: Pearl 16:1, Silver 6.4:1 (secondary text), Crimson 5.4:1, Steel 3.5:1 (input
 * and button outlines). Secondary text stays on surfaces no lighter than [Graphite800].
 */
internal val KryptexColors = darkColorScheme(
    primary = Pearl,
    onPrimary = PitchBlack,
    primaryContainer = Graphite800,
    onPrimaryContainer = Pearl,
    inversePrimary = Graphite700,
    secondary = Silver,
    onSecondary = PitchBlack,
    secondaryContainer = Graphite700,
    onSecondaryContainer = Pearl,
    tertiary = Platinum,
    onTertiary = PitchBlack,
    tertiaryContainer = Graphite800,
    onTertiaryContainer = Pearl,
    background = PitchBlack,
    onBackground = Pearl,
    surface = PitchBlack,
    onSurface = Pearl,
    surfaceVariant = Graphite800,
    onSurfaceVariant = Silver,
    surfaceTint = Graphite700,
    inverseSurface = Pearl,
    inverseOnSurface = PitchBlack,
    error = Crimson,
    onError = PitchBlack,
    errorContainer = CrimsonDeep,
    onErrorContainer = CrimsonMist,
    outline = Steel,
    outlineVariant = Graphite700,
    scrim = PitchBlack,
    surfaceBright = Graphite700,
    surfaceDim = PitchBlack,
    surfaceContainerLowest = PitchBlack,
    surfaceContainerLow = Obsidian,
    surfaceContainer = Graphite900,
    surfaceContainerHigh = Graphite800,
    surfaceContainerHighest = Graphite700,
)
