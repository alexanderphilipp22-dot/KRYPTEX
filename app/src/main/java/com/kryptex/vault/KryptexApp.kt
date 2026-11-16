package com.kryptex.vault

import android.app.Application
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.ui.RenderGuard

/**
 * Process-level entry point. Holds the dependency graph and nothing secret: keys live in
 * `SessionManager` only while the vault is unlocked. Nothing heavy happens here: native libraries
 * and the key manager are prepared on a background thread (see `AppContainer.startup`).
 */
class KryptexApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        RenderGuard.prefetch(this)
    }
}
