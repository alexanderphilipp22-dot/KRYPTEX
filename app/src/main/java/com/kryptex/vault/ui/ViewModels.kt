package com.kryptex.vault.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.di.UnlockedVault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Creates a ViewModel that is given the app's [AppContainer]; no DI framework involved. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/**
 * A flow that follows the vault through lock and unlock. While locked it yields [whenLocked]; on
 * every unlock it switches to a fresh flow built from the new database. Errors from a database
 * that is being closed at that very moment are absorbed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> AppContainer.vaultFlow(whenLocked: T, block: (UnlockedVault) -> Flow<T>): Flow<T> =
    unlocked.flatMapLatest { vault ->
        if (vault == null) flowOf(whenLocked) else block(vault).catch { emit(whenLocked) }
    }
