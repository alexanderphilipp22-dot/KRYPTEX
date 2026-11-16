package com.kryptex.vault.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.KryptexLogo
import com.kryptex.vault.ui.components.SecurePasswordField
import com.kryptex.vault.ui.components.Wordmark
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.ui.gesture.GestureAuthScreen
import com.kryptex.vault.ui.gesture.GestureMode

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    // Keyed on the vault generation: after an erase the next first-run starts from the welcome screen.
    val viewModel = appViewModel(key = "onboarding-${LocalAppContainer.current.vaultGeneration}") { OnboardingViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var enrolling by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        when {
            enrolling -> GestureAuthScreen(
                mode = GestureMode.ENROLL,
                onFinished = onFinished,
                onBack = { enrolling = false },
            )
            state.step == OnboardingStep.WELCOME -> Welcome(onStart = viewModel::start)
            state.step == OnboardingStep.PASSPHRASE -> PassphraseStep(state, viewModel)
            else -> GestureOffer(onEnroll = { enrolling = true }, onSkip = onFinished)
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KryptexLogo(size = 128.dp)
        Wordmark()
        Text(strings.onboardingIntro, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        StatusCard(
            icon = Icons.Filled.Lock,
            title = strings.onboardingLocalTitle,
            body = strings.onboardingLocalBody,
        )
        StatusCard(
            icon = Icons.Filled.Shield,
            title = strings.onboardingKeyTitle,
            body = strings.onboardingKeyBody,
            tone = Tone.WARNING,
        )
        Button(onClick = onStart, Modifier.fillMaxWidth()) { Text(strings.onboardingCreateMyVault) }
    }
}

@Composable
private fun PassphraseStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val strings = LocalStrings.current
    val problem = state.problemKind?.let { strings.passphraseProblem(it, PassphrasePolicy.MIN_LENGTH) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(strings.onboardingChoosePassphrase, style = MaterialTheme.typography.headlineMedium)
        Text(strings.onboardingChooseBody, style = MaterialTheme.typography.bodyMedium)

        SecurePasswordField(
            value = state.passphrase,
            onValueChange = viewModel::onPassphrase,
            label = strings.lockPassphrase,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            isError = problem != null,
            supportingText = problem,
            imeAction = ImeAction.Next,
        )
        if (state.passphrase.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { state.strength.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (state.strength.fraction < 0.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(strings.onboardingStrength(strings.strength(state.strength)), style = MaterialTheme.typography.labelMedium)
        }

        SecurePasswordField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmation,
            label = strings.onboardingRepeat,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            isError = state.mismatch,
            supportingText = if (state.mismatch) strings.onboardingDiffer else null,
            onImeAction = { if (state.canCreate) viewModel.create() },
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = viewModel::create, enabled = state.canCreate, modifier = Modifier.fillMaxWidth()) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text(strings.onboardingSecuring)
            } else {
                Text(strings.onboardingCreateVault)
            }
        }
        if (state.busy) {
            Text(strings.onboardingTuning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GestureOffer(onEnroll: () -> Unit, onSkip: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(strings.onboardingGestureTitle, style = MaterialTheme.typography.headlineMedium)
        Text(strings.onboardingGestureBody, style = MaterialTheme.typography.bodyMedium)
        StatusCard(
            icon = Icons.Filled.Shield,
            title = strings.onboardingConvenienceTitle,
            body = strings.onboardingConvenienceBody,
            tone = Tone.WARNING,
        )
        Button(onClick = onEnroll, Modifier.fillMaxWidth()) { Text(strings.onboardingSetUpGestures) }
        OutlinedButton(onClick = onSkip, Modifier.fillMaxWidth()) { Text(strings.onboardingNotNow) }
    }
}
