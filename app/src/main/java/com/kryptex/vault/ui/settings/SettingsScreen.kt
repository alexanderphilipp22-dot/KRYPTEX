package com.kryptex.vault.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.crypto.SecuritySummary
import com.kryptex.vault.i18n.AppLanguage
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.HelpBubble
import com.kryptex.vault.ui.components.SecurePasswordField
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.ui.components.VaultIcon
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline
import com.kryptex.vault.ui.onboarding.PassphrasePolicy

private enum class SettingsDialog { PASSPHRASE, REMOVE_GESTURE, ERASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSetUpGesture: () -> Unit, contentPadding: PaddingValues) {
    val strings = LocalStrings.current
    val viewModel = appViewModel { SettingsViewModel(it) }
    val autoLock by viewModel.autoLockSeconds.collectAsStateWithLifecycle()
    val folder by viewModel.saveFolder.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val showHelp by viewModel.showHelpBubbles.collectAsStateWithLifecycle()
    val haptic by viewModel.hapticFeedback.collectAsStateWithLifecycle()
    val faceCheck by viewModel.faceCheck.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ui.completed) { if (ui.completed > 0) dialog = null }

    Column(Modifier.padding(contentPadding)) {
        TopAppBar(title = { Text(strings.settingsTitle) })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ui.notice?.let {
                StatusCard(
                    icon = if (it.isError) Icons.Filled.Warning else Icons.Filled.Shield,
                    title = if (it.isError) strings.noticeNotDone else strings.noticeDone,
                    body = it.message,
                    tone = if (it.isError) Tone.BAD else Tone.GOOD,
                    actionLabel = strings.dismiss,
                    onAction = viewModel::dismissNotice,
                )
            }

            LanguageSection(strings, language, onSelect = viewModel::setLanguage)
            HelpSection(strings, showHelp, onChange = viewModel::setShowHelpBubbles)
            HapticSection(strings, haptic, onChange = viewModel::setHapticFeedback)
            AutoLockSection(strings, autoLock, onSelect = viewModel::setAutoLock)
            FolderSection(strings, folder, onSave = viewModel::setSaveFolder)
            UnlockSection(
                strings = strings,
                security = security,
                onChangePassphrase = { viewModel.clearDialogError(); dialog = SettingsDialog.PASSPHRASE },
                onSetUpGesture = onSetUpGesture,
                onRemoveGesture = { dialog = SettingsDialog.REMOVE_GESTURE },
            )
            FaceCheckSection(strings, faceCheck, onChange = viewModel::setFaceCheck)
            MemorySection(strings, onLock = viewModel::lockAndClearMemory)
            AlwaysOnSection(strings)
            DangerSection(strings, onErase = { viewModel.clearDialogError(); dialog = SettingsDialog.ERASE })
        }
    }

    when (dialog) {
        SettingsDialog.PASSPHRASE -> ChangePassphraseDialog(
            strings = strings,
            busy = ui.busy,
            error = ui.dialogError,
            onConfirm = viewModel::changePassphrase,
            onDismiss = { if (!ui.busy) dialog = null },
        )
        SettingsDialog.REMOVE_GESTURE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(strings.settingsRemoveGestureTitle) },
            text = { Text(strings.settingsRemoveGestureBody) },
            confirmButton = { TextButton(onClick = { dialog = null; viewModel.removeGesture() }) { Text(strings.settingsRemove) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(strings.cancel) } },
        )
        SettingsDialog.ERASE -> EraseDialog(
            strings = strings,
            busy = ui.busy,
            error = ui.dialogError,
            onConfirm = viewModel::eraseVault,
            onDismiss = { if (!ui.busy) dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun LanguageSection(strings: AppStrings, current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Section(strings.settingsLanguageTitle) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppLanguage.entries.forEach { option ->
                FilterChip(selected = option == current, onClick = { onSelect(option) }, label = { Text(option.nativeName) })
            }
        }
        Hint(strings.settingsLanguageHint)
    }
}

@Composable
private fun HelpSection(strings: AppStrings, enabled: Boolean, onChange: (Boolean) -> Unit) =
    ToggleSection(strings.settingsHelpTitle, strings.settingsHelpBody, enabled, onChange)

@Composable
private fun HapticSection(strings: AppStrings, enabled: Boolean, onChange: (Boolean) -> Unit) =
    ToggleSection(strings.settingsHapticTitle, strings.settingsHapticBody, enabled, onChange)

@Composable
private fun FaceCheckSection(strings: AppStrings, enabled: Boolean, onChange: (Boolean) -> Unit) =
    ToggleSection(strings.settingsFaceCheckTitle, strings.settingsFaceCheckBody, enabled, onChange)

/** A whole-row switch card: the title and hint wrap in the space the switch leaves. */
@Composable
private fun ToggleSection(title: String, body: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Hint(body)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun AutoLockSection(strings: AppStrings, current: Int, onSelect: (Int) -> Unit) {
    // A value set by an older version that is not in the list stays visible and selected.
    val options = if (current in AUTO_LOCK_SECONDS) AUTO_LOCK_SECONDS else AUTO_LOCK_SECONDS + current
    Section(strings.settingsAutoLockTitle) {
        HelpBubble(strings.helpSettingsAutoLock)
        Hint(strings.settingsAutoLockHint)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { seconds ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = seconds == current, role = Role.RadioButton, onClick = { onSelect(seconds) }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = seconds == current, onClick = null)
                    Spacer(Modifier.size(12.dp))
                    Text(strings.autoLockLabel(seconds), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun FolderSection(strings: AppStrings, saved: String, onSave: (String, String) -> Unit) {
    val (savedRoot, savedSub) = splitSaveFolder(saved)
    var root by remember(saved) { mutableStateOf(savedRoot) }
    var sub by remember(saved) { mutableStateOf(savedSub) }
    val built = buildSaveFolder(root, sub)

    Section(strings.settingsFolderTitle) {
        HelpBubble(strings.helpSettingsFolder)
        Hint(strings.settingsFolderHint)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SAVE_FOLDER_ROOTS.forEach { option ->
                FilterChip(selected = root == option, onClick = { root = option }, label = { Text(option) })
            }
        }
        OutlinedTextField(
            value = sub,
            onValueChange = { sub = it.take(128) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.settingsFolderSub) },
            isError = built == null,
            supportingText = { Text(if (built == null) strings.settingsFolderNotAllowed else strings.settingsFolderSavedAs(built)) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        )
        OutlinedButton(onClick = { onSave(root, sub) }, enabled = built != null && built != saved, modifier = Modifier.fillMaxWidth()) {
            Text(strings.settingsFolderSave)
        }
    }
}

@Composable
private fun UnlockSection(
    strings: AppStrings,
    security: SecuritySummary?,
    onChangePassphrase: () -> Unit,
    onSetUpGesture: () -> Unit,
    onRemoveGesture: () -> Unit,
) {
    Section(strings.settingsUnlockTitle) {
        HelpBubble(strings.helpSettingsUnlock)
        if (security == null) {
            Text(strings.settingsKeyUnreadable, color = MaterialTheme.colorScheme.error)
            return@Section
        }
        val p = security.passphraseParams
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Text(strings.settingsPassphrase, style = MaterialTheme.typography.titleSmall)
        }
        Hint(strings.settingsPassphraseInfo(p.memoryKiB / 1024, p.iterations, p.parallelism, strings.date(security.createdAtEpochMs)))
        OutlinedButton(onClick = onChangePassphrase, modifier = Modifier.fillMaxWidth()) { Text(strings.settingsChangePassphrase) }

        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Face, contentDescription = null)
            Text(strings.settingsGestureUnlock, style = MaterialTheme.typography.titleSmall)
        }
        when {
            !security.gestureEnrolled -> {
                Hint(strings.settingsGestureOff)
                OutlinedButton(onClick = onSetUpGesture, modifier = Modifier.fillMaxWidth()) { Text(strings.settingsSetUpGestures) }
            }
            else -> {
                Hint(
                    if (security.gestureLocked) {
                        strings.settingsGestureLocked
                    } else {
                        strings.settingsGestureOn(security.gestureSteps ?: 0, security.gestureAttemptsRemaining ?: 0)
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onSetUpGesture) { Text(strings.settingsChangeGestures) }
                    TextButton(onClick = onRemoveGesture) { Text(strings.settingsRemove) }
                }
            }
        }
    }
}

@Composable
private fun MemorySection(strings: AppStrings, onLock: () -> Unit) {
    Section(strings.settingsMemoryTitle) {
        Hint(strings.settingsMemoryHint)
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) {
            Icon(VaultIcon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(strings.settingsLockAndClear)
        }
    }
}

@Composable
private fun AlwaysOnSection(strings: AppStrings) {
    StatusCard(
        icon = Icons.Filled.Shield,
        title = strings.settingsAlwaysOnTitle,
        body = strings.settingsAlwaysOnBody,
        tone = Tone.GOOD,
    )
}

@Composable
private fun DangerSection(strings: AppStrings, onErase: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = graphiteCardColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
        border = hairline(MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.settingsEraseTitle, style = MaterialTheme.typography.titleMedium)
            HelpBubble(strings.helpSettingsErase)
            Text(strings.settingsEraseHint, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onErase,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(strings.settingsEraseButton)
            }
        }
    }
}

@Composable
private fun ChangePassphraseDialog(strings: AppStrings, busy: Boolean, error: String?, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val problem = if (new.isEmpty()) null else PassphrasePolicy.check(new)?.let { strings.passphraseProblem(it, PassphrasePolicy.MIN_LENGTH) }
    val mismatch = confirm.isNotEmpty() && confirm != new
    val canSubmit = !busy && current.isNotEmpty() && new.isNotEmpty() && problem == null && confirm == new

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsChangePassTitle) },
        text = {
            // Scrollable: three fields plus error text do not fit above the keyboard on a small phone.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurePasswordField(current, { current = it }, strings.settingsCurrentPassphrase, Modifier.fillMaxWidth(), enabled = !busy)
                SecurePasswordField(new, { new = it }, strings.settingsNewPassphrase, Modifier.fillMaxWidth(), enabled = !busy, isError = problem != null, supportingText = problem)
                SecurePasswordField(
                    confirm, { confirm = it }, strings.settingsRepeatNewPassphrase, Modifier.fillMaxWidth(),
                    enabled = !busy, isError = mismatch, supportingText = if (mismatch) strings.settingsPassphrasesDiffer else null,
                    onImeAction = { if (canSubmit) onConfirm(current, new) },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Hint(strings.settingsNoRecovery)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, new) }, enabled = canSubmit) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(strings.settingsChange)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) } },
    )
}

@Composable
private fun EraseDialog(strings: AppStrings, busy: Boolean, error: String?, onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var deletePictures by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsEraseDialogTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.settingsEraseDialogBody)
                Row(
                    Modifier.fillMaxWidth().toggleable(value = deletePictures, enabled = !busy, role = Role.Checkbox, onValueChange = { deletePictures = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deletePictures, onCheckedChange = null, enabled = !busy)
                    Spacer(Modifier.size(10.dp))
                    Text(strings.settingsErasePictures, style = MaterialTheme.typography.bodyMedium)
                }
                SecurePasswordField(
                    passphrase, { passphrase = it }, strings.lockPassphrase, Modifier.fillMaxWidth(),
                    enabled = !busy, isError = error != null, supportingText = error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase, deletePictures) }, enabled = !busy && passphrase.isNotEmpty()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(strings.settingsEraseConfirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) } },
    )
}
