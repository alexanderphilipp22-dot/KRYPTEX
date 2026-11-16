package com.kryptex.vault.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.components.KryptexLogo
import com.kryptex.vault.ui.components.LogoState
import com.kryptex.vault.ui.components.Wordmark

private const val REVEAL_MILLIS = 700

/**
 * Cold-start splash. Purely visual: it never decides when it ends. The root gate (see
 * `KryptexRoot`) does that with a plain timer and a deadline for the start-up work, so a stalled
 * animation clock or a slow component can not trap the user here. The vault's handle turns while
 * the start-up work runs; the logo stays tappable.
 */
@Composable
fun SplashScreen() {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing)) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KryptexLogo(
                size = 176.dp,
                state = LogoState.WORKING,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    val s = 0.85f + 0.15f * reveal.value
                    scaleX = s
                    scaleY = s
                },
            )
            Wordmark(
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 12.dp.toPx()
                },
            )
            Text(
                LocalStrings.current.splashTagline,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = reveal.value },
            )
        }
    }
}
