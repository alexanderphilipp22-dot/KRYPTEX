package com.kryptex.vault.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.data.db.dao.VaultItemWithCarrier
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.ui.vaultFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<VaultItemWithCarrier>> = container
        .vaultFlow(emptyList()) { it.repository.observeItems() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshIntegrity()
    }

    /** Re-checks every carrier file in the photo library: still there, still unchanged? */
    fun refreshIntegrity() {
        viewModelScope.launch(Dispatchers.IO) {
            val vault = container.unlocked.value ?: return@launch
            try {
                for (carrier in vault.repository.allCarriers()) {
                    val status = container.carrierStore.check(carrier)
                    if (status != carrier.status) vault.repository.setCarrierStatus(carrier.id, status)
                }
            } catch (_: Exception) {
                // The vault locked while checking; nothing to update.
            }
        }
    }
}
