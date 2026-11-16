package com.kryptex.vault.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.SecuritySummary
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.repo.SettingsRepository
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.i18n.AppLanguage
import com.kryptex.vault.ui.vaultFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seconds without a touch before the vault locks; 0 = no inactivity timer (it still locks when the app is left). */
val AUTO_LOCK_SECONDS: List<Int> = listOf(0, 15, 30, 60, 120, 300, 600)

data class Notice(val message: String, val isError: Boolean)

data class SettingsUi(
    val busy: Boolean = false,
    val notice: Notice? = null,
    /** Problem to show inside the open dialog (wrong passphrase, ...). */
    val dialogError: String? = null,
    /** Incremented whenever a dialog's action succeeded, so the screen can close it. */
    val completed: Int = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val autoLockSeconds: StateFlow<Int> = container
        .vaultFlow(SettingsRepository.DEFAULT_AUTO_LOCK_SECONDS) { it.settings.autoLockSeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_AUTO_LOCK_SECONDS)

    val saveFolder: StateFlow<String> = container
        .vaultFlow(SettingsRepository.DEFAULT_SAVE_FOLDER) { it.settings.saveFolder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_SAVE_FOLDER)

    /** The language in use right now (the saved one, once the vault is open). */
    val language: StateFlow<AppLanguage> = container.language

    val showHelpBubbles: StateFlow<Boolean> = container.showHelpBubbles

    private val mutableSecurity = MutableStateFlow(container.keyManager.securitySummary())
    val security: StateFlow<SecuritySummary?> = mutableSecurity.asStateFlow()

    private val mutableUi = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = mutableUi.asStateFlow()

    /** Re-reads the unlock-methods card (the gesture slot may have changed on another screen). */
    fun refresh() {
        mutableSecurity.value = container.keyManager.securitySummary()
    }

    fun dismissNotice() = mutableUi.update { it.copy(notice = null) }

    fun clearDialogError() = mutableUi.update { it.copy(dialogError = null) }

    // -- language and help -----------------------------------------------------------------------

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch(Dispatchers.IO) {
            val vault = container.unlocked.value ?: return@launch
            runCatching { vault.settings.setLanguage(language) }
                .onFailure { notice(container.strings.settingsSaveFailed, error = true) }
        }
    }

    fun setShowHelpBubbles(show: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val vault = container.unlocked.value ?: return@launch
            runCatching { vault.settings.setShowHelpBubbles(show) }
                .onFailure { notice(container.strings.settingsSaveFailed, error = true) }
        }
    }

    // -- auto-lock and folder --------------------------------------------------------------------

    fun setAutoLock(seconds: Int) {
        if (seconds < 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val vault = container.unlocked.value ?: return@launch
            runCatching { vault.settings.setAutoLockSeconds(seconds) }
                .onFailure { notice(container.strings.settingsSaveFailed, error = true) }
        }
    }

    fun setSaveFolder(root: String, sub: String) {
        val strings = container.strings
        val folder = buildSaveFolder(root, sub) ?: return notice(strings.settingsFolderRejected, error = true)
        viewModelScope.launch(Dispatchers.IO) {
            val vault = container.unlocked.value ?: return@launch
            runCatching { vault.settings.setSaveFolder(folder) }
                .onSuccess { notice(strings.settingsFolderSavedNotice(folder), error = false) }
                .onFailure { notice(strings.settingsFolderFailed, error = true) }
        }
    }

    // -- unlock methods --------------------------------------------------------------------------

    fun changePassphrase(current: String, new: String) {
        if (mutableUi.value.busy) return
        mutableUi.update { it.copy(busy = true, dialogError = null) }
        val currentChars = current.toCharArray()
        val newChars = new.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            val strings = container.strings
            try {
                val session = container.sessionManager.session.value
                if (session == null) {
                    dialogError(strings.vaultLocked)
                } else if (!container.keyManager.verifyPassphrase(currentChars)) {
                    dialogError(strings.settingsErrNotCurrent)
                } else {
                    // Re-tuned for this phone as it is today, like at setup.
                    val params = container.kdfCalibrator.calibrate(container.kdfMemoryBudgetKiB())
                    session.withDek { dek -> container.keyManager.changePassphrase(dek, newChars, params) }
                    container.unlocked.value?.repository?.recordEvent(AuthEventType.PASSPHRASE_CHANGED)
                    refresh()
                    succeeded(strings.settingsPassphraseChanged)
                }
            } catch (e: Exception) {
                dialogError(strings.settingsPassphraseChangeFailed(e.message ?: e::class.java.simpleName))
            } finally {
                currentChars.wipe()
                newChars.wipe()
                mutableUi.update { it.copy(busy = false) }
            }
        }
    }

    fun removeGesture() {
        viewModelScope.launch(Dispatchers.Default) {
            val strings = container.strings
            try {
                container.keyManager.removeGesture()
                container.unlocked.value?.repository?.recordEvent(AuthEventType.GESTURE_REMOVED)
                refresh()
                notice(strings.settingsGestureRemoved, error = false)
            } catch (e: Exception) {
                notice(strings.settingsGestureRemoveFailed(e.message ?: e::class.java.simpleName), error = true)
            }
        }
    }

    // -- memory and erasing ----------------------------------------------------------------------

    /** Locks right now: the keys are wiped, the database is closed and every screen drops what it decrypted. */
    fun lockAndClearMemory() {
        viewModelScope.launch(Dispatchers.Default) {
            container.unlocked.value?.repository?.recordEvent(AuthEventType.LOCK)
            container.sessionManager.lock()
            // Only a hint to the runtime, but it lets the freed buffers go sooner.
            Runtime.getRuntime().gc()
        }
    }

    /**
     * Destroys the vault after the passphrase is confirmed: carrier pictures (optionally), the key
     * file, the device key and the database. Without the key file nothing that remains can be
     * decrypted, so this is a cryptographic erase.
     */
    fun eraseVault(passphrase: String, deletePictures: Boolean) {
        if (mutableUi.value.busy) return
        mutableUi.update { it.copy(busy = true, dialogError = null) }
        val chars = passphrase.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            val strings = container.strings
            try {
                val vault = container.unlocked.value
                if (vault == null) {
                    dialogError(strings.vaultLocked)
                } else if (!container.keyManager.verifyPassphrase(chars)) {
                    dialogError(strings.settingsErrNotYours)
                } else {
                    // The Settings screen is torn down the moment the vault locks, which cancels this
                    // scope; once past the point of no return everything must run to the end.
                    withContext(NonCancellable) {
                        if (deletePictures) {
                            for (carrier in vault.repository.allCarriers()) {
                                runCatching { container.carrierStore.delete(Uri.parse(carrier.mediaUri)) }
                                carrier.containerKey.wipe()
                            }
                        }
                        // Keys first, while the session is still open, so the root gate sees "no vault" on lock.
                        container.keyManager.destroyKeys()
                        container.nextVaultGeneration()
                        try {
                            container.sessionManager.lock()
                        } finally {
                            container.databaseFactory.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                dialogError(strings.settingsEraseFailed(e.message ?: e::class.java.simpleName))
            } finally {
                chars.wipe()
                mutableUi.update { it.copy(busy = false) }
            }
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private fun notice(message: String, error: Boolean) = mutableUi.update { it.copy(notice = Notice(message, error)) }

    private fun dialogError(message: String) = mutableUi.update { it.copy(dialogError = message) }

    private fun succeeded(message: String) =
        mutableUi.update { it.copy(notice = Notice(message, isError = false), dialogError = null, completed = it.completed + 1) }
}
