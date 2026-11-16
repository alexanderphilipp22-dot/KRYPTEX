package com.kryptex.vault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kryptex.vault.ui.theme.Graphite700
import com.kryptex.vault.ui.theme.Graphite900
import com.kryptex.vault.ui.theme.Obsidian
import com.kryptex.vault.ui.theme.WordmarkStyle

/** The hairline edge that lifts a graphite card off the black background. */
@Composable
fun hairline(color: Color = MaterialTheme.colorScheme.outlineVariant): BorderStroke = BorderStroke(1.dp, color)

/** The standard card face: graphite, with secondary text still readable on it. */
@Composable
fun graphiteCardColors(
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
): CardColors = CardDefaults.cardColors(containerColor = container, contentColor = content)

/** Brushed-metal face for hero cards: light catches the top-left corner and fades into black. */
val MetalSheen: Brush = Brush.linearGradient(listOf(Graphite700, Graphite900, Obsidian))

/** A light-catching edge to go with [MetalSheen]. */
val MetalEdge: BorderStroke = BorderStroke(
    1.dp,
    Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF), Color(0x0AFFFFFF))),
)

/**
 * The KRYPTEX wordmark. Letter spacing is also added after the last letter, so the text is shifted
 * by the same amount to keep it optically centred.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = WordmarkStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val trailing = if (style.letterSpacing.isSp) with(LocalDensity.current) { style.letterSpacing.toDp() } else 0.dp
    Text("KRYPTEX", modifier.padding(start = trailing), style = style, color = color, maxLines = 1)
}
