package com.kryptex.vault.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.GestureSlotInfo
import com.kryptex.vault.crypto.UnlockResult
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.VaultOpenException
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.repo.VaultRepository
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.di.StartupState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockState(
    val passphrase: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val gesture: GestureSlotInfo = GestureSlotInfo.NotEnrolled,
    /** Why start-up was cut short, or null if it went normally. */
    val startupProblem: String? = null,
)

class LockViewModel(private val container: AppContainer) : ViewModel() {
    // Nothing that can block runs while this is constructed: the first composition of the lock
    // screen must not wait for the key manager. The gesture slot is read on an IO thread below.
    private val mutableState = MutableStateFlow(
        LockState(startupProblem = (container.startup.value as? StartupState.Ready)?.degradedReason),
    )
    val state: StateFlow<LockState> = mutableState.asStateFlow()

    init {
        refreshGesture()
    }

    fun onPassphrase(value: String) = mutableState.update { it.copy(passphrase = value, error = null) }

    /** Re-reads the gesture slot, e.g. after a failed gesture attempt used one up. */
    fun refreshGesture() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = readGestureSlot()
            mutableState.update { it.copy(gesture = info) }
        }
    }

    fun unlock() {
        val current = mutableState.value
        if (current.busy || current.passphrase.isEmpty()) return
        mutableState.update { it.copy(busy = true, error = null) }

        val chars = current.passphrase.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            val strings = container.strings
            try {
                when (val result = container.keyManager.unlockWithPassphrase(chars)) {
                    is UnlockResult.Success -> {
                        try {
                            val session = container.sessionManager.open(result.dek)
                            VaultRepository(session.database).recordEvent(AuthEventType.UNLOCK_PASSPHRASE)
                            mutableState.update { it.copy(passphrase = "", busy = false, gesture = readGestureSlot()) }
                        } catch (e: VaultOpenException) {
                            fail(strings.lockDatabaseDamaged)
                        }
                    }
                    is UnlockResult.WrongCredential -> fail(strings.lockWrongPassphrase)
                    UnlockResult.Corrupt -> fail(strings.lockKeyFileDamaged)
                    UnlockResult.NoVault -> fail(strings.lockNoVault)
                    else -> fail(strings.lockFailed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(strings.lockUnexpected(e.message ?: e::class.java.simpleName))
            } catch (e: LinkageError) {
                // A native library (Argon2, SQLCipher) that did not load: say so instead of crashing.
                fail(strings.lockUnexpected(e::class.java.simpleName))
            } finally {
                chars.wipe()
            }
        }
    }

    private fun readGestureSlot(): GestureSlotInfo =
        try {
            container.keyManager.gestureSlotInfo()
        } catch (e: Exception) {
            GestureSlotInfo.NotEnrolled
        } catch (e: LinkageError) {
            GestureSlotInfo.NotEnrolled
        }

    private fun fail(message: String) =
        mutableState.update { it.copy(busy = false, error = message, gesture = readGestureSlot()) }
}
