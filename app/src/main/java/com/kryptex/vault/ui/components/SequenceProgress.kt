package com.kryptex.vault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.vision.Gesture

/**
 * One slot per step of the sequence.
 *
 * With [revealed] (enrolment) finished steps show the recognised gesture. Without it (unlocking)
 * they are anonymous dots, so nobody looking over the shoulder learns the sequence.
 */
@Composable
fun SequenceProgress(
    steps: Int,
    doneCount: Int,
    modifier: Modifier = Modifier,
    revealed: List<Gesture> = emptyList(),
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val description = LocalStrings.current.gestureProgressDescription(doneCount, steps)
    FlowRow(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(steps) { index ->
            val done = index < doneCount
            val current = index == doneCount
            val glyph = revealed.getOrNull(index)
            if (glyph != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    GestureGlyph(glyph, size = 40.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(6.dp))
                }
            } else {
                Box(
                    Modifier
                        .size(if (current) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .then(
                            if (done) Modifier.background(primary)
                            else Modifier.border(BorderStroke(if (current) 2.dp else 1.dp, if (current) primary else outline), CircleShape),
                        ),
                )
            }
        }
    }
}
