package com.kryptex.vault.di

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.kryptex.vault.crypto.Argon2Kdf
import com.kryptex.vault.crypto.Argon2ktKdf
import com.kryptex.vault.crypto.AttemptLimiter
import com.kryptex.vault.crypto.DeviceKeyWrapper
import com.kryptex.vault.crypto.KdfCalibrator
import com.kryptex.vault.crypto.KeystoreWrapper
import com.kryptex.vault.crypto.SessionManager
import com.kryptex.vault.crypto.VaultHeaderStore
import com.kryptex.vault.crypto.VaultKeyManager
import com.kryptex.vault.crypto.VaultSession
import com.kryptex.vault.data.db.DatabaseFactory
import com.kryptex.vault.data.media.CarrierMediaStore
import com.kryptex.vault.data.repo.SettingsRepository
import com.kryptex.vault.data.repo.VaultRepository
import com.kryptex.vault.i18n.AppLanguage
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.stego.StegoEngine
import com.kryptex.vault.ui.AutoLock
import com.kryptex.vault.ui.FaceCheck
import com.kryptex.vault.ui.Haptics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything that exists only while the vault is unlocked. Dropped as soon as it locks. */
class UnlockedVault(val session: VaultSession) {
    val repository = VaultRepository(session.database)
    val settings = SettingsRepository(session.database.settingDao())
}

/** How far the work done on a background thread at launch has got. See [AppContainer.startup]. */
sealed interface StartupState {
    data object Loading : StartupState

    /**
     * Start-up is over, successfully or not. A non-null [degradedReason] means something failed or
     * was slow and the app carries on anyway; the lock screen then says so.
     */
    data class Ready(val vaultExists: Boolean, val degradedReason: String? = null) : StartupState
}

/**
 * Manual dependency wiring. No DI framework: fewer dependencies, nothing generated at build time
 * that could widen the attack surface, and the whole object graph is readable in one screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(private val application: Application) {
    /**
     * Background work must never take the process down: a failure in one collector (a database
     * closing under a query, a vendor quirk) is contained instead of killing the app. Nothing is
     * logged, by design; anything the user needs to know is reported through state instead.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    /**
     * Bumped when the vault is erased. Activity-scoped ViewModels of the first-run flow key on it,
     * so a new vault in the same process starts from a clean welcome screen, not the old state.
     */
    @Volatile
    var vaultGeneration: Int = 0
        private set

    @Synchronized
    fun nextVaultGeneration() {
        vaultGeneration++
    }

    /**
     * Builds the expensive parts (key manager, native Argon2 library, Keystore wrapper) on an IO
     * thread, never inside a composition. Anything that waits for it must use a timeout: a blocking
     * native or Keystore call cannot be cancelled, only waited for with a deadline.
     */
    val startup: StateFlow<StartupState> by lazy {
        MutableStateFlow<StartupState>(StartupState.Loading).also { state ->
            appScope.launch(Dispatchers.IO) { state.value = runStartup() }
        }
    }

    /**
     * Runs on an IO thread. Each step stands alone, so one failure (a native library that will not
     * load on this device, Keystore trouble) is reported on the lock screen while everything else
     * still gets ready. Cheap objects the UI thread also uses come first, so the UI thread never
     * waits on a lock held during a slow native load.
     */
    private fun runStartup(): StartupState.Ready {
        var problem: String? = null
        fun step(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                // UnsatisfiedLinkError, Keystore trouble, ...: carry on to the lock screen and say so.
                if (problem == null) problem = "${t::class.java.simpleName}: ${t.message ?: "no details"}"
            }
        }
        val exists = runCatching { headerStore.exists() }.getOrDefault(true)
        step {
            databaseFactory
            sessionManager
        }
        step { haptics } // reads its preference file here, not on the main thread
        step { faceCheck }
        step { DatabaseFactory.loadNativeLibrary() } // SQLCipher: loaded here so unlock never pays for it
        step { keyManager.vaultExists() } // builds the key manager: Argon2 (native) and the Keystore wrapper
        return StartupState.Ready(vaultExists = exists, degradedReason = problem)
    }

    /** Gesture vibration and its on/off switch. A plain preference: it is needed while the vault is locked. */
    val haptics: Haptics by lazy { Haptics(application) }

    /** The switch for the face check at gesture unlock; a plain preference for the same reason. */
    val faceCheck: FaceCheck by lazy { FaceCheck(application) }

    val headerStore: VaultHeaderStore by lazy { VaultHeaderStore(application.filesDir) }
    val argon2: Argon2Kdf by lazy { Argon2ktKdf() }
    val deviceKeyWrapper: DeviceKeyWrapper by lazy { KeystoreWrapper() }
    val attemptLimiter: AttemptLimiter by lazy { AttemptLimiter(headerStore) }

    val keyManager: VaultKeyManager by lazy {
        VaultKeyManager(headerStore, argon2, deviceKeyWrapper, attemptLimiter)
    }
    val kdfCalibrator: KdfCalibrator by lazy { KdfCalibrator(argon2) }

    val databaseFactory: DatabaseFactory by lazy { DatabaseFactory(application) }
    val sessionManager: SessionManager by lazy { SessionManager(databaseFactory) }

    val carrierStore: CarrierMediaStore by lazy { CarrierMediaStore(application) }
    val stego: StegoEngine by lazy { StegoEngine() }

    /** Non-null exactly while the vault is unlocked; collectors must restart on every change. */
    val unlocked: StateFlow<UnlockedVault?> by lazy {
        sessionManager.session
            .map { session -> session?.let(::UnlockedVault) }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    private val autoLockSeconds: StateFlow<Int> by lazy {
        unlocked
            .flatMapLatest { vault ->
                vault?.settings?.autoLockSeconds?.catch { emit(SettingsRepository.DEFAULT_AUTO_LOCK_SECONDS) }
                    ?: flowOf(SettingsRepository.DEFAULT_AUTO_LOCK_SECONDS)
            }
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_AUTO_LOCK_SECONDS)
    }

    /**
     * The UI language. While locked the saved choice cannot be read (it lives in the encrypted
     * database), so the last known language is kept, starting with the phone's own.
     */
    val language: StateFlow<AppLanguage> by lazy {
        val initial = AppLanguage.systemDefault()
        unlocked
            .flatMapLatest { vault ->
                vault?.settings?.language?.map { it ?: initial }?.catch { }
                    ?: emptyFlow()
            }
            .stateIn(appScope, SharingStarted.Eagerly, initial)
    }

    /** For code outside Compose; composables read `LocalStrings`. */
    val strings: AppStrings get() = language.value.strings

    val showHelpBubbles: StateFlow<Boolean> by lazy {
        unlocked
            .flatMapLatest { vault ->
                vault?.settings?.showHelpBubbles?.catch { } ?: flowOf(SettingsRepository.DEFAULT_SHOW_HELP_BUBBLES)
            }
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_SHOW_HELP_BUBBLES)
    }

    val autoLock: AutoLock by lazy {
        AutoLock(sessionManager, timeoutSeconds = { autoLockSeconds.value }).also { lock ->
            appScope.launch {
                while (true) {
                    delay(1_000)
                    // A failed tick must not stop the clock: the next one still locks on time.
                    try {
                        lock.tick()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Memory budget for Argon2id: a quarter of what the system reports as available right now.
     * Argon2 allocates natively, outside the Java heap, so the heap limit is irrelevant here.
     */
    fun kdfMemoryBudgetKiB(): Int {
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return (info.availMem / 1024 / 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
