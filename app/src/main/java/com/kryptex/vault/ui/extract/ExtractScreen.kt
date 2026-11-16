package com.kryptex.vault.ui.extract

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.Dissolvable
import com.kryptex.vault.ui.components.HelpBubble
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline
import com.kryptex.vault.ui.components.KryptexLogo
import com.kryptex.vault.ui.components.LogoState
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.ui.vault.PayloadViewer
import com.kryptex.vault.ui.vault.typeIcon
import kotlinx.coroutines.launch

/**
 * Read a hidden item out of a picture. The vault door reports the outcome: its wheel turns while
 * working and swings open on success; it jams and glows crimson when nothing could be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val strings = LocalStrings.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val viewModel = appViewModel { ExtractViewModel(it, appContext) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only PNG can carry a payload: any lossy format would have destroyed it.
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onPicked(it) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.export(uri)
                Toast.makeText(context, strings.itemSavedCopy, Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun choosePicture() {
        container.autoLock.expectExternalLaunch()
        pickPicture.launch(arrayOf("image/png"))
    }

    val stage = state.stage
    val logoState = when (stage) {
        is ExtractStage.Working -> LogoState.WORKING
        is ExtractStage.Done -> LogoState.SUCCESS
        is ExtractStage.Failed -> LogoState.ERROR
        ExtractStage.Idle -> LogoState.IDLE
    }
    val (headline, detail) = when (stage) {
        ExtractStage.Idle -> strings.extractHeadIdle to strings.extractHeadIdleDetail
        is ExtractStage.Working -> stage.step.label(strings) to (state.pictureName ?: strings.embedWorkingDetail)
        is ExtractStage.Done -> strings.extractHeadFound to strings.extractHeadFoundDetail
        is ExtractStage.Failed -> strings.extractHeadFailed to strings.extractHeadFailedDetail
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(strings.extractTitle) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back) } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KryptexLogo(size = 104.dp, state = logoState)
                Text(headline, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (stage) {
                is ExtractStage.Done -> {
                    // "Hide again" lets the decrypted item crumble away first, then wipes it.
                    var hiding by remember(stage) { mutableStateOf(false) }
                    Dissolvable(dissolving = hiding, onDissolved = viewModel::reset) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PayloadHeader(strings, stage.payload)
                            PayloadViewer(
                                payload = stage.payload,
                                onExport = {
                                    container.autoLock.expectExternalLaunch()
                                    saveAs.launch(stage.payload.name.ifBlank { "hidden-item" })
                                },
                            )
                        }
                    }
                    Button(onClick = { hiding = true }, enabled = !hiding, modifier = Modifier.fillMaxWidth()) { Text(strings.extractHideAgain) }
                }
                is ExtractStage.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    if (stage is ExtractStage.Failed) {
                        StatusCard(Icons.Filled.Warning, strings.extractCouldNot, stage.message, tone = Tone.BAD, actionLabel = strings.dismiss, onAction = viewModel::dismissError)
                    }
                    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HelpBubble(strings.helpExtract)
                            HelpBubble(strings.helpExtractFormat)
                            Text(
                                strings.extractIntro,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = ::choosePicture, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (stage is ExtractStage.Failed) strings.extractTryAnother else strings.extractChoose)
                            }
                        }
                    }
                    StatusCard(
                        icon = Icons.Filled.Shield,
                        title = strings.staysOnPhoneTitle,
                        body = strings.extractStaysBody,
                    )
                }
            }
        }
    }
}

@Composable
private fun PayloadHeader(strings: AppStrings, payload: PayloadContainer) {
    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(payload.name.ifBlank { strings.payloadType(payload.type) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                "${strings.payloadType(payload.type)} · ${strings.bytes(payload.data.size.toLong())} · ${strings.dateTime(payload.createdAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ExtractStep.label(strings: AppStrings): String = when (this) {
    ExtractStep.READING -> strings.extractStepReading
    ExtractStep.IDENTIFYING -> strings.extractStepIdentifying
    ExtractStep.DECRYPTING -> strings.extractStepDecrypting
}
