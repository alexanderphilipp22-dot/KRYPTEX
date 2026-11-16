package com.kryptex.vault.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns the ViewModels of every screen shown while the vault is unlocked.
 *
 * Navigation keeps each destination's ViewModels in the Activity's store until the Activity dies,
 * which would leave typed notes and revealed content in memory after the vault locks. This scope
 * is held by the Activity (so it survives rotation) but is [clear]ed on every lock, which calls
 * `onCleared()` on all of those ViewModels so they can wipe their secrets.
 */
class UnlockedScope : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** Clears every ViewModel in this scope. The scope itself stays usable for the next unlock. */
    fun clearAll() = viewModelStore.clear()

    override fun onCleared() = viewModelStore.clear()
}
