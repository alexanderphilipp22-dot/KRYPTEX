package com.kryptex.vault.ui.gesture

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.GestureSlotInfo
import com.kryptex.vault.crypto.UnlockResult
import com.kryptex.vault.crypto.VaultKeyManager
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.VaultOpenException
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.repo.VaultRepository
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import com.kryptex.vault.vision.GestureSequence
import com.kryptex.vault.vision.GlanceEngine
import com.kryptex.vault.vision.GlanceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GestureMode { UNLOCK, ENROLL }

enum class GestureStage {
    /** Unlock: waiting for the user to tap "Start camera". */
    UNLOCK_READY,

    /** Enrol: choosing how many gestures the sequence has. */
    CHOOSE_LENGTH,

    /** Enrol: first pass, recognised gestures are shown back to the user. */
    RECORD_FIRST,

    /** Enrol: second pass, anonymous; must match the first. */
    RECORD_CONFIRM,

    /** Argon2id is running (verifying an unlock or storing a new slot). */
    WORKING,

    /** Enrol: the sequence is saved. */
    SAVED,
}

/**
 * What the screen tells the user. The ViewModel only says *what happened*; the wording comes from
 * `AppStrings` at render time, so it follows the language and no text lives in this layer.
 */
sealed interface GestureMessage {
    data object PermissionDenied : GestureMessage

    /** Enrolment: the first pass was recorded, the user must now repeat it. */
    data object Recorded : GestureMessage

    data object Mismatch : GestureMessage

    data class SaveFailed(val detail: String) : GestureMessage

    /** A wrong unlock attempt. [attemptsLeft] is null when the limiter reports no count. */
    data class Wrong(val attemptsLeft: Int?) : GestureMessage

    data class Failure(val reason: FailureReason) : GestureMessage
}

/** Why gesture unlock hands the user back to the passphrase screen. */
enum class GestureFallback { DB_OPEN, TOO_MANY_ATTEMPTS, LOCKED, UNAVAILABLE, NOT_ENROLLED, UNREADABLE }

data class GestureUiState(
    val mode: GestureMode,
    val stage: GestureStage,
    val steps: Int,
    val glance: GlanceState,
    val message: GestureMessage?,
) {
    val cameraRunning: Boolean
        get() = glance is GlanceState.Starting || glance is GlanceState.Calibrating || glance is GlanceState.Capturing
    val entropyBits: Double get() = GestureSequence(List(steps) { Gesture.SMILE }).entropyBits
}

sealed interface GestureEvent {
    /** Gesture unlock cannot continue; send the user back to the passphrase. */
    data class FallBackToPassphrase(val reason: GestureFallback) : GestureEvent
}

class GestureAuthViewModel(
    private val container: AppContainer,
    private val context: Context,
    val mode: GestureMode,
) : ViewModel() {
    private val engine = GlanceEngine(context)

    private val stage = MutableStateFlow(if (mode == GestureMode.UNLOCK) GestureStage.UNLOCK_READY else GestureStage.CHOOSE_LENGTH)
    private val steps = MutableStateFlow(initialSteps())
    private val message = MutableStateFlow<GestureMessage?>(null)
    private var firstPass: GestureSequence? = null

    private val eventChannel = Channel<GestureEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<GestureUiState> =
        combine(stage, steps, message, engine.state) { stage, steps, message, glance ->
            GestureUiState(mode, stage, steps, glance, message)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            GestureUiState(mode, stage.value, steps.value, GlanceState.Idle, null),
        )

    init {
        viewModelScope.launch {
            engine.state.collect { glance ->
                when (glance) {
                    is GlanceState.Completed -> {
                        engine.reset()
                        onSequence(glance.sequence)
                    }
                    is GlanceState.Failed -> {
                        engine.reset()
                        message.value = GestureMessage.Failure(glance.reason)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Runs while the screen is built, so it must not throw: an unreadable key file falls back to the default. */
    private fun initialSteps(): Int =
        if (mode == GestureMode.UNLOCK) {
            val slot = runCatching { container.keyManager.gestureSlotInfo() }.getOrNull()
            (slot as? GestureSlotInfo.Ready)?.steps ?: GestureSequence.DEFAULT_STEPS
        } else {
            GestureSequence.DEFAULT_STEPS
        }

    fun setSteps(value: Int) {
        if (stage.value == GestureStage.CHOOSE_LENGTH) {
            steps.value = value.coerceIn(GestureSequence.MIN_STEPS, GestureSequence.MAX_STEPS)
        }
    }

    /** Enrolment: leave the length picker and get ready to record. */
    fun beginRecording() {
        firstPass = null
        message.value = null
        stage.value = GestureStage.RECORD_FIRST
    }

    /**
     * Opens the camera. Must only be called from the tap on the "Start camera" button, and only
     * once the camera permission is granted.
     */
    fun startCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        if (state.value.cameraRunning) return
        message.value = null
        val reveal = mode == GestureMode.ENROLL && stage.value == GestureStage.RECORD_FIRST
        engine.start(owner, steps.value, reveal, surfaceProvider)
    }

    fun cancelCamera() {
        engine.reset()
        message.value = null
    }

    fun onPermissionDenied() {
        message.value = GestureMessage.PermissionDenied
    }

    // ---- result handling --------------------------------------------------------------------

    private fun onSequence(sequence: GestureSequence) {
        when (mode) {
            GestureMode.UNLOCK -> verifyUnlock(sequence)
            GestureMode.ENROLL -> when (stage.value) {
                GestureStage.RECORD_FIRST -> {
                    firstPass = sequence
                    message.value = GestureMessage.Recorded
                    stage.value = GestureStage.RECORD_CONFIRM
                }
                GestureStage.RECORD_CONFIRM -> confirmEnrolment(sequence)
                else -> Unit
            }
        }
    }

    private fun verifyUnlock(sequence: GestureSequence) {
        stage.value = GestureStage.WORKING
        viewModelScope.launch(Dispatchers.Default) {
            val secret = sequence.toSecret()
            try {
                when (val result = container.keyManager.unlockWithGesture(secret)) {
                    is UnlockResult.Success -> {
                        try {
                            val session = container.sessionManager.open(result.dek)
                            VaultRepository(session.database).recordEvent(AuthEventType.UNLOCK_GESTURE)
                        } catch (e: VaultOpenException) {
                            fallBack(GestureFallback.DB_OPEN)
                        }
                        // On success the root gate swaps this screen out by itself.
                    }
                    is UnlockResult.WrongCredential -> {
                        val left = result.attemptsRemaining
                        message.value = GestureMessage.Wrong(left)
                        if (left == 0) fallBack(GestureFallback.TOO_MANY_ATTEMPTS) else stage.value = GestureStage.UNLOCK_READY
                    }
                    UnlockResult.GestureLocked -> fallBack(GestureFallback.LOCKED)
                    UnlockResult.GestureUnavailable -> fallBack(GestureFallback.UNAVAILABLE)
                    UnlockResult.GestureNotEnrolled -> fallBack(GestureFallback.NOT_ENROLLED)
                    UnlockResult.Corrupt, UnlockResult.NoVault -> fallBack(GestureFallback.UNREADABLE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keystore or storage trouble: hand over to the passphrase instead of crashing.
                fallBack(GestureFallback.UNREADABLE)
            } catch (_: LinkageError) {
                // A native library (Argon2, SQLCipher) that would not load on this device.
                fallBack(GestureFallback.UNREADABLE)
            } finally {
                secret.wipe()
            }
        }
    }

    private fun confirmEnrolment(second: GestureSequence) {
        val first = firstPass
        if (first == null || first != second) {
            firstPass = null
            message.value = GestureMessage.Mismatch
            stage.value = GestureStage.RECORD_FIRST
            return
        }
        stage.value = GestureStage.WORKING
        viewModelScope.launch(Dispatchers.Default) {
            val secret = first.toSecret()
            try {
                val session = checkNotNull(container.sessionManager.session.value) { "vault is locked" }
                val params = container.kdfCalibrator.calibrate(container.kdfMemoryBudgetKiB())
                session.withDek { dek -> container.keyManager.enrollGesture(dek, secret, first.size, params) }
                VaultRepository(session.database).recordEvent(AuthEventType.GESTURE_ENROLLED)
                firstPass = null
                message.value = null
                stage.value = GestureStage.SAVED
            } catch (e: Exception) {
                message.value = GestureMessage.SaveFailed(e.message ?: e::class.java.simpleName)
                stage.value = GestureStage.RECORD_FIRST
            } catch (e: LinkageError) {
                message.value = GestureMessage.SaveFailed(e::class.java.simpleName)
                stage.value = GestureStage.RECORD_FIRST
            } finally {
                secret.wipe()
            }
        }
    }

    private fun fallBack(reason: GestureFallback) {
        eventChannel.trySend(GestureEvent.FallBackToPassphrase(reason))
        stage.value = GestureStage.UNLOCK_READY
    }

    override fun onCleared() {
        engine.reset() // releases the camera if the screen is left mid-capture
        super.onCleared()
    }
}
