package com.kryptex.vault.ui.vault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.dao.VaultItemWithCarrier
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.stego.CarrierBitmap
import com.kryptex.vault.stego.ExtractOutcome
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.Dissolvable
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ItemDetailState(
    val entry: VaultItemWithCarrier? = null,
    val loaded: Boolean = false,
    val revealed: PayloadContainer? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val deleted: Boolean = false,
)

class VaultItemDetailViewModel(
    private val container: AppContainer,
    private val context: Context,
    private val itemId: Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = container.unlocked.value?.repository?.getItem(itemId)
            mutableState.update { it.copy(entry = entry, loaded = true) }
        }
        // Revealed content must not outlive the unlocked vault.
        viewModelScope.launch {
            container.sessionManager.session.collect { if (it == null) clearRevealed() }
        }
    }

    fun reveal() {
        val entry = mutableState.value.entry ?: return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val picture = CarrierBitmap.decodeForExtraction(context, Uri.parse(entry.carrier.mediaUri))
                when (val outcome = container.stego.extract(picture.pixels, picture.width, picture.height, entry.carrier.containerKey)) {
                    is ExtractOutcome.Found -> mutableState.update { it.copy(busy = false, revealed = outcome.container) }
                    ExtractOutcome.NothingFound -> mutableState.update {
                        it.copy(busy = false, error = container.strings.itemNothingReadable)
                    }
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(busy = false, error = container.strings.itemPictureUnavailable) }
            }
        }
    }

    fun hide() = clearRevealed()

    fun delete() {
        val entry = mutableState.value.entry ?: return
        viewModelScope.launch(Dispatchers.IO) {
            clearRevealed()
            container.carrierStore.delete(Uri.parse(entry.carrier.mediaUri))
            container.unlocked.value?.repository?.deleteItem(entry)
            mutableState.update { it.copy(deleted = true) }
        }
    }

    suspend fun export(target: Uri) {
        val payload = mutableState.value.revealed ?: return
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "w")?.use { it.write(payload.data) }
        }
    }

    private fun clearRevealed() {
        mutableState.value.revealed?.data?.wipe()
        mutableState.update { it.copy(revealed = null) }
    }

    override fun onCleared() {
        clearRevealed()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultItemDetailScreen(itemId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val strings = LocalStrings.current
    val appContext = context.applicationContext
    val viewModel = appViewModel(key = "item-$itemId") { VaultItemDetailViewModel(it, appContext, itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    // A confirmed delete first lets the item crumble away, then wipes it.
    var deleting by remember { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    // Leaving before the effect has played must not cancel what the user confirmed.
    val deletePending by rememberUpdatedState(deleting && !deleteRequested)
    DisposableEffect(Unit) { onDispose { if (deletePending) viewModel.delete() } }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            // Saving is deliberate and leaves the vault; confirm what happened.
            kotlinx.coroutines.MainScope().launch {
                viewModel.export(uri)
                Toast.makeText(context, strings.itemSavedCopy, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.entry?.item?.title ?: strings.itemFallbackTitle) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back) } },
            actions = {
                IconButton(onClick = { confirmDelete = true }, enabled = state.entry != null && !deleting) {
                    Icon(Icons.Filled.Delete, contentDescription = strings.itemDelete)
                }
            },
        )
        val entry = state.entry
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (entry == null) {
                if (state.loaded) Text(strings.itemGone) else CircularProgressIndicator()
                return@Column
            }

            Dissolvable(
                dissolving = deleting,
                onDissolved = {
                    deleteRequested = true
                    viewModel.delete()
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CarrierThumbnail(entry.carrier.mediaUri, entry.carrier.status, Modifier.size(96.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(strings.payloadType(entry.item.payloadType), style = MaterialTheme.typography.titleMedium)
                            Text(strings.bytes(entry.item.sizeBytes), style = MaterialTheme.typography.bodyMedium)
                            Text(strings.itemHiddenOn(strings.dateTime(entry.item.createdAt)), style = MaterialTheme.typography.bodySmall)
                            Text(
                                strings.itemInPicture(entry.carrier.displayName, entry.carrier.widthPx, entry.carrier.heightPx),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    StatusLine(entry.carrier.status)

                    val revealed = state.revealed
                    if (revealed == null) {
                        Button(onClick = viewModel::reveal, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(12.dp))
                                Text(strings.itemReading)
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(strings.itemReveal)
                            }
                        }
                    } else {
                        // "Hide again" lets the revealed content crumble away first, then wipes it.
                        var hiding by remember(revealed) { mutableStateOf(false) }
                        Dissolvable(dissolving = hiding, onDissolved = viewModel::hide) {
                            PayloadViewer(revealed, onExport = {
                                container.autoLock.expectExternalLaunch()
                                saveAs.launch(revealed.name.ifBlank { "hidden-item" })
                            })
                        }
                        OutlinedButton(onClick = { hiding = true }, enabled = !hiding, modifier = Modifier.fillMaxWidth()) {
                            Text(strings.itemHideAgain)
                        }
                    }

                    state.error?.let {
                        StatusCard(Icons.Filled.Warning, strings.itemCouldNotReveal, it, tone = Tone.BAD)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.itemDeleteTitle) },
            text = { Text(strings.itemDeleteBody) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; deleting = true }) {
                    Text(strings.itemDeleteConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(strings.cancel) } },
        )
    }
}
