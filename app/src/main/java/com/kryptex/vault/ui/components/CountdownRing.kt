package com.kryptex.vault.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Seconds left, drawn as a ring that empties. [totalSeconds] only scales the ring. */
@Composable
fun CountdownRing(
    secondsLeft: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { (secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            strokeWidth = 3.dp,
            color = if (secondsLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(secondsLeft.toString(), style = MaterialTheme.typography.labelLarge)
    }
}
