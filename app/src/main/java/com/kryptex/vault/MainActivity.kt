package com.kryptex.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.components.LocalShowHelp
import com.kryptex.vault.ui.KryptexRoot
import com.kryptex.vault.ui.RenderGuard
import com.kryptex.vault.ui.theme.KryptexTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as KryptexApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No screenshots, screen recording or recents thumbnails of vault content.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        // Software-rendering fallback after failed launches; no vendor force-dark repainting.
        RenderGuard.install(this)
        // The UI is always black, so the system bars always get light icons, whatever the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        container.startup // starts the key-manager warm-up on an IO thread, before the first frame needs it
        container.autoLock // creating it starts the once-a-second inactivity check

        setContent {
            val language by container.language.collectAsStateWithLifecycle()
            val showHelp by container.showHelpBubbles.collectAsStateWithLifecycle()
            KryptexTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalStrings provides language.strings,
                    LocalShowHelp provides showHelp,
                ) {
                    KryptexRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.autoLock.onActivityStarted()
    }

    override fun onStop() {
        // Rotation and other configuration changes recreate the activity; that is not leaving the app.
        if (!isChangingConfigurations) container.autoLock.onActivityStopped()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.autoLock.onUserInteraction()
    }
}
