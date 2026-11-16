package com.kryptex.vault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kryptex.vault.ui.theme.Graphite600

enum class Tone { NEUTRAL, GOOD, WARNING, BAD }

/**
 * A message card. Monochrome by design: [Tone.GOOD] and [Tone.WARNING] stand out through a lighter
 * face and a brighter edge, never through a hue. Crimson belongs to [Tone.BAD] alone, so it always
 * means that something went wrong.
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.NEUTRAL,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        Tone.NEUTRAL -> scheme.surfaceContainer
        Tone.GOOD, Tone.WARNING -> scheme.surfaceContainerHigh
        Tone.BAD -> scheme.errorContainer
    }
    val edge = when (tone) {
        Tone.NEUTRAL -> scheme.outlineVariant
        Tone.GOOD -> Graphite600
        Tone.WARNING -> scheme.outline
        Tone.BAD -> scheme.error.copy(alpha = 0.45f)
    }
    val iconTint = when (tone) {
        Tone.NEUTRAL -> scheme.onSurfaceVariant
        Tone.GOOD, Tone.WARNING -> scheme.primary
        Tone.BAD -> scheme.error
    }
    val content = if (tone == Tone.BAD) scheme.onErrorContainer else scheme.onSurface
    val bodyColor = if (tone == Tone.BAD) scheme.onErrorContainer else scheme.onSurfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, edge),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconTint)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
                if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
