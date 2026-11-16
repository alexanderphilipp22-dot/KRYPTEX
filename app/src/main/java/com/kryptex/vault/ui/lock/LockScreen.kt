package com.kryptex.vault.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.crypto.GestureSlotInfo
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.ui.components.KryptexLogo
import com.kryptex.vault.ui.components.LogoState
import com.kryptex.vault.ui.components.SecurePasswordField
import com.kryptex.vault.ui.components.Wordmark
import com.kryptex.vault.ui.gesture.GestureAuthScreen
import com.kryptex.vault.ui.gesture.GestureMode

@Composable
fun LockScreen() {
    val viewModel = appViewModel { LockViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var useGesture by rememberSaveable { mutableStateOf(false) }

    // The gesture slot may have changed while we were away (attempt counter, re-enrolment).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) viewModel.refreshGesture() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(Modifier.fillMaxSize()) {
        if (useGesture && state.gesture is GestureSlotInfo.Ready) {
            GestureAuthScreen(
                mode = GestureMode.UNLOCK,
                onFinished = {}, // the root gate switches screens as soon as the vault opens
                onBack = {
                    useGesture = false
                    viewModel.refreshGesture()
                },
            )
        } else {
            PassphraseLock(state, viewModel, onUseGesture = { useGesture = true })
        }
    }
}

@Composable
private fun PassphraseLock(state: LockState, viewModel: LockViewModel, onUseGesture: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The handle turns while the key is derived, and jams with a crimson glow on a wrong passphrase.
        KryptexLogo(
            size = 132.dp,
            state = when {
                state.error != null -> LogoState.ERROR
                state.busy -> LogoState.WORKING
                else -> LogoState.IDLE
            },
        )
        Wordmark()
        Text(
            strings.lockLocked,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))

        SecurePasswordField(
            value = state.passphrase,
            onValueChange = viewModel::onPassphrase,
            label = strings.lockPassphrase,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            isError = state.error != null,
            supportingText = state.error,
            onImeAction = viewModel::unlock,
        )

        Button(
            onClick = viewModel::unlock,
            enabled = state.passphrase.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text(strings.lockUnlocking)
            } else {
                Text(strings.lockUnlock)
            }
        }

        state.startupProblem?.let {
            StatusCard(Icons.Filled.Warning, strings.startupSlowTitle, strings.startupSlowBody + "\n(" + it + ")", tone = Tone.WARNING)
        }

        when (val gesture = state.gesture) {
            is GestureSlotInfo.Ready -> OutlinedButton(onClick = onUseGesture, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Face, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(strings.lockUseGestures)
            }
            GestureSlotInfo.Locked -> Text(
                strings.lockGestureSwitchedOff,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GestureSlotInfo.NotEnrolled -> Unit
        }
    }
}
