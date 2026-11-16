package com.kryptex.vault.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.components.VaultIcon
import com.kryptex.vault.ui.dashboard.DashboardScreen
import com.kryptex.vault.ui.embed.EmbedScreen
import com.kryptex.vault.ui.extract.ExtractScreen
import com.kryptex.vault.ui.gesture.GestureAuthScreen
import com.kryptex.vault.ui.gesture.GestureMode
import com.kryptex.vault.ui.settings.SettingsScreen
import com.kryptex.vault.ui.vault.VaultItemDetailScreen
import com.kryptex.vault.ui.vault.VaultScreen

private data class TopLevelTab(val route: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(Routes.DASHBOARD, Icons.Filled.Dashboard),
    TopLevelTab(Routes.VAULT, VaultIcon),
    TopLevelTab(Routes.SETTINGS, Icons.Filled.Settings),
)

/**
 * Everything behind the lock. It leaves the composition the instant the vault locks, which drops
 * the back stack and every screen-scoped ViewModel (and with them any decrypted content).
 */
@Composable
fun MainNav() {
    val scope: UnlockedScope = viewModel()
    CompositionLocalProvider(LocalViewModelStoreOwner provides scope) {
        MainNavContent()
    }
}

@Composable
private fun MainNavContent() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val strings = LocalStrings.current

    Scaffold(
        // Screens bring their own top bars, which handle the status-bar inset themselves.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateTopLevel(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    when (tab.route) {
                                        Routes.DASHBOARD -> strings.navDashboard
                                        Routes.VAULT -> strings.navVault
                                        else -> strings.navSettings
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.DASHBOARD, modifier = Modifier.fillMaxSize()) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onHide = { navController.navigate(Routes.EMBED) },
                    onReveal = { navController.navigate(Routes.EXTRACT) },
                    onOpenItem = { navController.navigate(Routes.item(it)) },
                    onSetUpGesture = { navController.navigate(Routes.GESTURE_ENROLL) },
                    contentPadding = padding,
                )
            }
            composable(Routes.VAULT) {
                VaultScreen(
                    onOpenItem = { navController.navigate(Routes.item(it)) },
                    onHide = { navController.navigate(Routes.EMBED) },
                    onReveal = { navController.navigate(Routes.EXTRACT) },
                    contentPadding = padding,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onSetUpGesture = { navController.navigate(Routes.GESTURE_ENROLL) }, contentPadding = padding)
            }
            composable(
                Routes.ITEM,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID)
                if (id != null) {
                    Detail { VaultItemDetailScreen(itemId = id, onBack = { navController.popBackStack() }) }
                }
            }
            composable(Routes.EMBED) {
                Detail {
                    EmbedScreen(
                        onBack = { navController.popBackStack() },
                        // Replace the form with the new item so Back returns to where the user started.
                        onOpenItem = { id ->
                            navController.navigate(Routes.item(id)) { popUpTo(Routes.EMBED) { inclusive = true } }
                        },
                    )
                }
            }
            composable(Routes.EXTRACT) {
                Detail { ExtractScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Routes.GESTURE_ENROLL) {
                Detail {
                    GestureAuthScreen(
                        mode = GestureMode.ENROLL,
                        onFinished = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** Full-screen destinations have no bottom bar, so keep their content clear of the system navigation bar. */
@Composable
private fun Detail(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
