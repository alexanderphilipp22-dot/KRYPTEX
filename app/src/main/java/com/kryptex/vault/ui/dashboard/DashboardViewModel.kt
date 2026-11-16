package com.kryptex.vault.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.SecuritySummary
import com.kryptex.vault.data.db.dao.VaultItemWithCarrier
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.repo.VaultStats
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.ui.vaultFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val empty = VaultStats(0, 0, 0, 0)

    val stats: StateFlow<VaultStats> = container
        .vaultFlow(empty) { it.repository.observeStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), empty)

    val recent: StateFlow<List<VaultItemWithCarrier>> = container
        .vaultFlow(emptyList()) { it.repository.observeRecentItems(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableSecurity = MutableStateFlow(container.keyManager.securitySummary())
    val security: StateFlow<SecuritySummary?> = mutableSecurity.asStateFlow()

    private val mutablePreviousUnlock = MutableStateFlow<Long?>(null)
    val previousUnlock: StateFlow<Long?> = mutablePreviousUnlock.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the security card (the gesture slot may have changed on another screen). */
    fun refresh() {
        mutableSecurity.value = container.keyManager.securitySummary()
        viewModelScope.launch(Dispatchers.IO) {
            mutablePreviousUnlock.value = container.unlocked.value?.repository?.previousUnlockTimestamp()
        }
    }

    fun lockNow() {
        viewModelScope.launch(Dispatchers.Default) {
            container.unlocked.value?.repository?.recordEvent(AuthEventType.LOCK)
            container.sessionManager.lock()
        }
    }
}
