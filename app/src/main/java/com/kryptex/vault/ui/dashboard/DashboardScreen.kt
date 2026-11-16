package com.kryptex.vault.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.kryptex.vault.ui.components.MetalEdge
import com.kryptex.vault.ui.components.MetalSheen
import com.kryptex.vault.ui.components.VaultIcon
import com.kryptex.vault.ui.components.Wordmark
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline
import com.kryptex.vault.ui.theme.WordmarkStyle
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.crypto.SecuritySummary
import com.kryptex.vault.data.repo.VaultStats
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onHide: () -> Unit,
    onReveal: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onSetUpGesture: () -> Unit,
    contentPadding: PaddingValues,
) {
    val strings = LocalStrings.current
    val viewModel = appViewModel { DashboardViewModel(it) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val previousUnlock by viewModel.previousUnlock.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Wordmark(style = WordmarkStyle.copy(fontSize = 17.sp, letterSpacing = 6.sp)) },
            actions = {
                TextButton(onClick = viewModel::lockNow) {
                    Icon(VaultIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.dashboardLockNow)
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { VaultStatusCard(strings, stats, previousUnlock) }
            item { QuickActions(strings, onHide, onReveal) }
            item { SecurityCard(strings, security, onSetUpGesture) }

            item { Text(strings.dashboardRecent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            if (recent.isEmpty()) {
                item {
                    Text(
                        strings.dashboardEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(recent, key = { it.item.id }) { entry ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenItem(entry.item.id) },
                        colors = graphiteCardColors(),
                        border = hairline(),
                    ) {
                        ListItem(
                            headlineContent = { Text(entry.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text("${strings.payloadType(entry.item.payloadType)} · ${strings.bytes(entry.item.sizeBytes)} · ${strings.date(entry.item.createdAt)}")
                            },
                            // The card is the surface; a list row of its own would paint a black block on it.
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultStatusCard(strings: AppStrings, stats: VaultStats, previousUnlock: Long?) {
    // The hero card: brushed metal catching the light at its top-left edge.
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
        border = MetalEdge,
    ) {
        Column(Modifier.fillMaxWidth().background(MetalSheen).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(VaultIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.dashboardUnlocked, style = MaterialTheme.typography.titleLarge)
            }
            // Equal columns: long labels (DE/RU) wrap inside their own column instead of squeezing the others.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(strings.dashboardHiddenItems, stats.itemCount.toString(), Modifier.weight(1f))
                Stat(strings.dashboardCarrierPictures, stats.carrierCount.toString(), Modifier.weight(1f))
                Stat(strings.dashboardDataStored, strings.bytes(stats.storedBytes), Modifier.weight(1f))
            }
            previousUnlock?.let {
                Text(
                    strings.dashboardPreviousUnlock(strings.dateTime(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QuickActions(strings: AppStrings, onHide: () -> Unit, onReveal: () -> Unit) {
    // IntrinsicSize.Min keeps both buttons the same height when one label wraps (e.g. "Etwas verstecken").
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onHide, Modifier.weight(1f).fillMaxHeight()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(strings.dashboardHide, textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = onReveal, Modifier.weight(1f).fillMaxHeight()) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(strings.dashboardReveal, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SecurityCard(strings: AppStrings, security: SecuritySummary?, onSetUpGesture: () -> Unit) {
    if (security == null) {
        StatusCard(Icons.Filled.Shield, strings.dashboardSecurity, strings.dashboardKeyUnreadable, tone = Tone.BAD)
        return
    }
    val p = security.passphraseParams
    StatusCard(
        icon = Icons.Filled.Lock,
        title = strings.dashboardPassphraseProtection,
        body = strings.dashboardPassphraseBody(p.memoryKiB / 1024, p.iterations, p.parallelism),
        tone = Tone.NEUTRAL,
    )
    when {
        !security.gestureEnrolled -> StatusCard(
            icon = Icons.Filled.Face,
            title = strings.dashboardGestureOffTitle,
            body = strings.dashboardGestureOffBody,
            actionLabel = strings.dashboardSetUp,
            onAction = onSetUpGesture,
        )
        security.gestureLocked -> StatusCard(
            icon = Icons.Filled.Face,
            title = strings.dashboardGestureSwitchedOffTitle,
            body = strings.dashboardGestureSwitchedOffBody,
            tone = Tone.WARNING,
        )
        else -> StatusCard(
            icon = Icons.Filled.Face,
            title = strings.dashboardGestureOnTitle,
            body = strings.dashboardGestureOnBody(security.gestureSteps ?: 0, security.gestureAttemptsRemaining ?: 0),
            tone = Tone.GOOD,
        )
    }
    StatusCard(
        icon = Icons.Filled.Shield,
        title = strings.dashboardOfflineTitle,
        body = strings.dashboardOfflineBody,
        tone = Tone.GOOD,
    )
}
