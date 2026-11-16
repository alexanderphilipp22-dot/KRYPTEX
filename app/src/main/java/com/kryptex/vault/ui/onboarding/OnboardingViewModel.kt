package com.kryptex.vault.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.repo.VaultRepository
import com.kryptex.vault.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, PASSPHRASE, GESTURE_OFFER }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val passphrase: String = "",
    val confirmation: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val problemKind: PassphraseProblem? get() = if (passphrase.isEmpty()) null else PassphrasePolicy.check(passphrase)
    val mismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != passphrase
    val strength: Strength get() = PassphrasePolicy.strength(passphrase)
    val canCreate: Boolean get() = !busy && passphrase.isNotEmpty() && problemKind == null && confirmation == passphrase
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    fun start() = mutableState.update { it.copy(step = OnboardingStep.PASSPHRASE) }

    fun onPassphrase(value: String) = mutableState.update { it.copy(passphrase = value, error = null) }

    fun onConfirmation(value: String) = mutableState.update { it.copy(confirmation = value, error = null) }

    /** Derives the keys (about a second of Argon2id) and creates the vault, then opens it. */
    fun create() {
        val current = mutableState.value
        if (!current.canCreate) return
        mutableState.update { it.copy(busy = true, error = null) }

        val chars = current.passphrase.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val params = container.kdfCalibrator.calibrate(container.kdfMemoryBudgetKiB())
                val dek = container.keyManager.createVault(chars, params)
                try {
                    val session = container.sessionManager.open(dek) // takes ownership of dek
                    VaultRepository(session.database).recordEvent(AuthEventType.VAULT_CREATED)
                } catch (e: Exception) {
                    container.keyManager.destroyKeys() // do not leave a header without a database
                    container.databaseFactory.delete()
                    throw e
                }
                mutableState.update {
                    it.copy(step = OnboardingStep.GESTURE_OFFER, busy = false, passphrase = "", confirmation = "")
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(busy = false, error = container.strings.onboardingCreateFailed(e.message ?: e::class.java.simpleName)) }
            } catch (e: LinkageError) {
                // A native library (Argon2, SQLCipher) that would not load on this device: say so, never crash.
                mutableState.update { it.copy(busy = false, error = container.strings.onboardingCreateFailed(e::class.java.simpleName)) }
            } finally {
                chars.wipe()
            }
        }
    }
}
