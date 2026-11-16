package com.kryptex.vault.ui.vault

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.data.db.dao.VaultItemWithCarrier
import com.kryptex.vault.data.db.entity.CarrierStatus
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onOpenItem: (Long) -> Unit,
    onHide: () -> Unit,
    onReveal: () -> Unit,
    contentPadding: PaddingValues,
) {
    val strings = LocalStrings.current
    val viewModel = appViewModel { VaultViewModel(it) }
    val items by viewModel.items.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(strings.vaultTitle) },
                actions = {
                    IconButton(onClick = onReveal) { Icon(Icons.Filled.Search, contentDescription = strings.vaultRevealFromPicture) }
                },
            )
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        strings.vaultEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.item.id }) { entry -> ItemCard(entry) { onOpenItem(entry.item.id) } }
                }
            }
        }
        FloatingActionButton(
            onClick = onHide,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = strings.vaultHideFab) }
    }
}

@Composable
private fun ItemCard(entry: VaultItemWithCarrier, onClick: () -> Unit) {
    val strings = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = graphiteCardColors(),
        border = hairline(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CarrierThumbnail(entry.carrier.mediaUri, entry.carrier.status, Modifier.size(68.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(entry.item.payloadType), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        entry.item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    "${strings.payloadType(entry.item.payloadType)} · ${strings.bytes(entry.item.sizeBytes)} · ${strings.date(entry.item.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusLine(entry.carrier.status)
            }
        }
    }
}

@Composable
fun StatusLine(status: CarrierStatus) {
    when (status) {
        CarrierStatus.OK -> Unit
        CarrierStatus.MISSING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Text(LocalStrings.current.carrierMissing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        CarrierStatus.MODIFIED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Text(LocalStrings.current.carrierModified, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

fun typeIcon(type: PayloadType) = when (type) {
    PayloadType.TEXT -> Icons.AutoMirrored.Filled.TextSnippet
    PayloadType.AUDIO -> Icons.Filled.AudioFile
    PayloadType.IMAGE -> Icons.Filled.Image
    PayloadType.FILE -> Icons.Filled.Description
}

/** The carrier picture as a small square, loaded from the photo library off the main thread. */
@Composable
fun CarrierThumbnail(uri: String, status: CarrierStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, status) {
        value = if (status == CarrierStatus.MISSING) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(Uri.parse(uri), Size(256, 256), null) }.getOrNull()
            }
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(current.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
