package com.kryptex.vault.ui.gesture

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.crypto.AttemptLimiter
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.CountdownRing
import com.kryptex.vault.ui.components.GestureGlyph
import com.kryptex.vault.ui.components.SequenceProgress
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.vision.Gesture
import com.kryptex.vault.vision.GestureSequence
import com.kryptex.vault.vision.GlanceState
import kotlin.math.roundToInt

/**
 * Gesture unlock and gesture enrolment. The camera starts only from the "Start camera" button and
 * is released the moment a sequence is captured, fails, times out, or this screen goes away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureAuthScreen(
    mode: GestureMode,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val appContext = context.applicationContext
    val viewModel = appViewModel(key = "gesture-$mode-${container.vaultGeneration}") { GestureAuthViewModel(it, appContext, mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceProvider by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startCamera(lifecycleOwner, surfaceProvider) else viewModel.onPermissionDenied()
    }

    // Only ever called from the button's click handler.
    fun onStartTapped() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startCamera(lifecycleOwner, surfaceProvider)
        } else {
            container.autoLock.expectExternalLaunch()
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    val strings = LocalStrings.current
    val currentStrings by rememberUpdatedState(strings)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GestureEvent.FallBackToPassphrase -> {
                    Toast.makeText(context, currentStrings.gestureFallback(event.reason), Toast.LENGTH_LONG).show()
                    onBack()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(
            title = {
                Text(
                    if (mode == GestureMode.UNLOCK) strings.gestureTitleUnlock else strings.gestureTitleSetup,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back) }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (state.stage) {
                GestureStage.CHOOSE_LENGTH -> ChooseLength(state, viewModel)
                GestureStage.SAVED -> Saved(onFinished)
                else -> Recorder(
                    state = state,
                    onSurfaceProvider = { surfaceProvider = it },
                    onStart = ::onStartTapped,
                    onCancel = viewModel::cancelCamera,
                )
            }
        }
    }
}

@Composable
private fun ChooseLength(state: GestureUiState, viewModel: GestureAuthViewModel) {
    val strings = LocalStrings.current
    Text(strings.gestureChooseLengthTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        strings.gestureChooseLengthBody(state.steps),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Gesture.entries.forEach { GestureGlyph(it, size = 52.dp, showLabel = true) }
    }

    Text(strings.gestureCount(state.steps), style = MaterialTheme.typography.headlineSmall)
    Slider(
        value = state.steps.toFloat(),
        onValueChange = { viewModel.setSteps(it.roundToInt()) },
        valueRange = GestureSequence.MIN_STEPS.toFloat()..GestureSequence.MAX_STEPS.toFloat(),
        steps = GestureSequence.MAX_STEPS - GestureSequence.MIN_STEPS - 1,
    )
    val bits = state.entropyBits.roundToInt()
    val short = bits < SHORT_SEQUENCE_BITS
    StatusCard(
        icon = Icons.Filled.Warning,
        title = strings.gestureEntropyTitle(bits, short),
        body = strings.gestureEntropyBody(AttemptLimiter.DEFAULT_MAX_ATTEMPTS),
        tone = if (short) Tone.WARNING else Tone.NEUTRAL,
    )
    Button(onClick = viewModel::beginRecording, Modifier.fillMaxWidth()) { Text(strings.gestureContinue) }
}

@Composable
private fun Saved(onFinished: () -> Unit) {
    val strings = LocalStrings.current
    Spacer(Modifier.size(24.dp))
    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Text(strings.gestureReadyTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(strings.gestureReadyBody, textAlign = TextAlign.Center)
    Button(onClick = onFinished, Modifier.fillMaxWidth()) { Text(strings.gestureDone) }
}

@Composable
private fun Recorder(
    state: GestureUiState,
    onSurfaceProvider: (Preview.SurfaceProvider) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    val glance = state.glance
    val capturing = glance as? GlanceState.Capturing
    val faceDetected = capturing?.faceDetected ?: (glance as? GlanceState.Calibrating)?.faceDetected ?: false
    val message = state.message?.let(strings::gestureMessage)

    Text(headline(state, strings), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(244.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            CameraPreview(Modifier.fillMaxSize(), onSurfaceProvider)
            if (!state.cameraRunning) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Face,
                        contentDescription = strings.gestureCameraOff,
                        modifier = Modifier.size(88.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FaceOverlay(
            faceDetected = state.cameraRunning && faceDetected,
            holdProgress = capturing?.holdProgress ?: 0f,
            modifier = Modifier.size(260.dp),
        )
        if (capturing != null) {
            CountdownRing(
                secondsLeft = capturing.secondsLeft,
                totalSeconds = capturing.totalSeconds,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }

    Text(
        instruction(state, message, strings),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = if (message != null && !state.cameraRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SequenceProgress(
        steps = state.steps,
        doneCount = capturing?.committedCount ?: 0,
        revealed = capturing?.committed.orEmpty(),
    )
    capturing?.candidate?.let { candidate ->
        // Only present while enrolling: during unlock the engine never reveals the gesture.
        GestureGlyph(candidate, size = 48.dp, showLabel = true)
    }

    when {
        state.stage == GestureStage.WORKING -> {
            CircularProgressIndicator()
            Text(strings.gestureChecking, style = MaterialTheme.typography.bodyMedium)
        }
        state.cameraRunning -> OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text(strings.cancel) }
        else -> Button(onClick = onStart, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Face, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(strings.gestureStartCamera)
        }
    }

    Text(
        strings.gesturePrivacyNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Below this many bits of guessing entropy the length picker warns that the sequence is short. */
private const val SHORT_SEQUENCE_BITS = 24

private fun headline(state: GestureUiState, strings: AppStrings): String = when (state.stage) {
    GestureStage.UNLOCK_READY, GestureStage.WORKING ->
        if (state.mode == GestureMode.UNLOCK) strings.gestureHeadlineUnlock else strings.gestureHeadlineSaving
    GestureStage.RECORD_FIRST -> strings.gestureHeadlineRecord
    GestureStage.RECORD_CONFIRM -> strings.gestureHeadlineConfirm
    else -> ""
}

/** [message] is the already-translated [GestureUiState.message], if any. */
private fun instruction(state: GestureUiState, message: String?, strings: AppStrings): String {
    if (message != null && !state.cameraRunning) return message
    return when (val glance = state.glance) {
        GlanceState.Idle -> when (state.stage) {
            GestureStage.UNLOCK_READY -> strings.gestureInstructionReady(state.steps)
            GestureStage.RECORD_FIRST -> strings.gestureInstructionRecord(state.steps)
            GestureStage.RECORD_CONFIRM -> strings.gestureInstructionConfirm(state.steps)
            else -> ""
        }
        GlanceState.Starting -> strings.gestureStarting
        is GlanceState.Calibrating -> if (glance.faceDetected) strings.gestureHoldStill else strings.gestureFindFace
        is GlanceState.Capturing -> when {
            !glance.faceDetected -> strings.gestureFaceLost
            glance.awaitingNeutral -> strings.gestureRelax
            else -> strings.gestureProgress(glance.committedCount + 1, glance.steps)
        }
        is GlanceState.Completed -> strings.gestureFinished
        is GlanceState.Failed -> message ?: strings.gestureFailedGeneric
    }
}
