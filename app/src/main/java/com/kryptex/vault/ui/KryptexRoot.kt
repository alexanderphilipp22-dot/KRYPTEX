package com.kryptex.vault.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.di.StartupState
import com.kryptex.vault.ui.lock.LockScreen
import com.kryptex.vault.ui.navigation.MainNav
import com.kryptex.vault.ui.navigation.UnlockedScope
import com.kryptex.vault.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The splash is never shorter than this, so the logo does not just flash. */
private const val MIN_SPLASH_MILLIS = 1_600L

/**
 * The longest the splash waits for the start-up work (key manager, native libraries, Keystore).
 * Some phones (MediaTek/HyperOS among them) can be slow or stuck in those; after this the app moves
 * on to the lock screen, which then says that it started in safe mode.
 */
private const val STARTUP_DEADLINE_MILLIS = 4_000L

/**
 * Root gate. Splash on cold start (ended by a timer, see below), then exactly one of:
 *  - onboarding: no vault exists yet, or it was just created and onboarding has not finished,
 *  - the app: the vault is unlocked,
 *  - the lock screen: a vault exists and is locked.
 * Locking swaps the screen immediately, without a transition, so nothing decrypted lingers.
 */
@Composable
fun KryptexRoot() {
    val container = LocalAppContainer.current
    val unlocked by container.unlocked.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var onboarding by rememberSaveable { mutableStateOf(false) }
    // A plain file check. Never touch the key manager here: building it loads native libraries and
    // the Keystore wrapper, and doing that inside the first composition can freeze the first frame.
    val vaultExists = remember(unlocked) { container.headerStore.exists() }

    // The splash ends on a timer plus a deadline for start-up, not on an animation: a stalled frame
    // clock or a slow component must not be able to keep the user on a black screen.
    LaunchedEffect(Unit) {
        if (!splashDone) {
            val began = SystemClock.elapsedRealtime()
            withTimeoutOrNull(STARTUP_DEADLINE_MILLIS) { container.startup.first { it is StartupState.Ready } }
            val remaining = MIN_SPLASH_MILLIS - (SystemClock.elapsedRealtime() - began)
            if (remaining > 0) delay(remaining)
            splashDone = true
        }
    }

    // Onboarding creates the vault and opens it before its last step (gesture offer) is done, so
    // "unlocked" alone must not end it. A lock, or a restored process, always ends it: the user
    // then lands on the lock screen and can never be shown "create vault" over an existing one.
    LaunchedEffect(vaultExists, unlocked) {
        if (!vaultExists) {
            onboarding = true
        } else if (unlocked == null) {
            onboarding = false
        }
    }

    // Every lock drops the ViewModels of the unlocked screens, so nothing typed or revealed lingers.
    val unlockedScope: UnlockedScope = viewModel()
    LaunchedEffect(unlocked == null) { if (unlocked == null) unlockedScope.clearAll() }

    Surface(Modifier.fillMaxSize()) {
        Crossfade(targetState = splashDone, animationSpec = tween(400), label = "splash") { done ->
            when {
                !done -> SplashScreen()
                !vaultExists || (onboarding && unlocked != null) -> OnboardingScreen(onFinished = { onboarding = false })
                unlocked != null -> MainNav()
                else -> LockScreen()
            }
        }
    }
}
