package com.kryptex.vault.ui.embed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kryptex.vault.di.LocalAppContainer
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.stego.CapacityCalculator
import com.kryptex.vault.ui.appViewModel
import com.kryptex.vault.ui.components.Dissolvable
import com.kryptex.vault.ui.components.HelpBubble
import com.kryptex.vault.ui.components.KryptexLogo
import com.kryptex.vault.ui.components.VaultIcon
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline
import com.kryptex.vault.ui.components.LogoState
import com.kryptex.vault.ui.components.StatusCard
import com.kryptex.vault.ui.components.Tone
import com.kryptex.vault.ui.vault.typeIcon

private fun EmbedStep.label(strings: AppStrings): String = when (this) {
    EmbedStep.READING -> strings.embedStepReading
    EmbedStep.PREPARING -> strings.embedStepPreparing
    EmbedStep.HIDING -> strings.embedStepHiding
    EmbedStep.VERIFYING -> strings.embedStepVerifying
    EmbedStep.SAVING -> strings.embedStepSaving
}

/**
 * Hide a note, audio file, picture or any file inside a picture. The vault door reports progress:
 * its wheel turns while working and swings open on success; it jams and glows crimson on failure.
 * While the inputs are encrypted, the "what to hide" card crumbles into dust that drifts up into
 * the picture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val strings = LocalStrings.current
    val appContext = context.applicationContext
    val viewModel = appViewModel { EmbedViewModel(it, appContext) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handing control to a system picker must not lock the vault on the way out and back.
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onCarrierPicked(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onFilePicked(it) }
    fun choosePicture() {
        container.autoLock.expectExternalLaunch()
        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun chooseFile() {
        container.autoLock.expectExternalLaunch()
        pickFile.launch(arrayOf(state.kind.mimePrefix?.let { "$it*" } ?: "*/*"))
    }

    val stage = state.stage
    val logoState = when (stage) {
        is EmbedStage.Working -> LogoState.WORKING
        is EmbedStage.Done -> LogoState.SUCCESS
        is EmbedStage.Failed -> LogoState.ERROR
        EmbedStage.Idle -> LogoState.IDLE
    }

    // The form is drawn from the inputs as they were when hiding started: on success the view model
    // clears them, and that must not cut the dissolve short. The result appears once it has played.
    var frozen by remember { mutableStateOf<EmbedState?>(null) }
    var dissolved by remember { mutableStateOf(false) }
    LaunchedEffect(stage) {
        when (stage) {
            is EmbedStage.Working -> if (frozen == null) frozen = state
            is EmbedStage.Done -> if (frozen == null) dissolved = true // nothing to animate, e.g. after a rotation
            else -> {
                frozen = null
                dissolved = false
            }
        }
    }
    val sealing = stage is EmbedStage.Working || stage is EmbedStage.Done
    val done = (stage as? EmbedStage.Done)?.result?.takeIf { dissolved }
    LaunchedEffect(done != null) { if (done != null) frozen = null }
    val form = frozen ?: state

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(strings.embedTitle) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back) } },
        )
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(strings, logoState, stage, ready = state.canHide)

            if (done != null) {
                DoneSection(
                    strings = strings,
                    result = done,
                    onOpen = { onOpenItem(done.itemId) },
                    onAnother = viewModel::reset,
                )
            } else {
                CarrierSection(strings, form, onChoose = ::choosePicture)
                Dissolvable(dissolving = sealing, onDissolved = { dissolved = true }) {
                    PayloadSection(strings, form, viewModel, onChooseFile = ::chooseFile)
                }
                TitleSection(strings, form, viewModel)

                state.inputError?.let {
                    StatusCard(Icons.Filled.Warning, strings.embedCouldNotUse, it, tone = Tone.BAD, actionLabel = strings.dismiss, onAction = viewModel::dismissError)
                }
                if (stage is EmbedStage.Failed) {
                    StatusCard(Icons.Filled.Warning, strings.embedCouldNotHide, stage.message, tone = Tone.BAD, actionLabel = strings.dismiss, onAction = viewModel::dismissError)
                }

                if (sealing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Button(onClick = viewModel::hide, enabled = state.canHide, modifier = Modifier.fillMaxWidth()) {
                        Icon(VaultIcon, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.embedHideButton)
                    }
                }

                StatusCard(
                    icon = Icons.Filled.Shield,
                    title = strings.staysOnPhoneTitle,
                    body = strings.embedStaysBody,
                )
            }
        }
    }
}

@Composable
private fun Header(strings: AppStrings, logo: LogoState, stage: EmbedStage, ready: Boolean) {
    val (headline, detail) = when (stage) {
        EmbedStage.Idle ->
            if (ready) strings.embedHeadReady to strings.embedHeadReadyDetail else strings.embedHeadChoose to strings.embedHeadChooseDetail
        is EmbedStage.Working -> stage.step.label(strings) to strings.embedWorkingDetail
        is EmbedStage.Done -> strings.embedDoneHead to strings.embedDoneDetail
        is EmbedStage.Failed -> strings.embedFailedHead to strings.embedFailedDetail
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KryptexLogo(size = 104.dp, state = logo)
        Text(headline, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(step: Int, title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$step · $title", style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CarrierSection(strings: AppStrings, state: EmbedState, onChoose: () -> Unit) {
    SectionCard(1, strings.embedPictureSection) {
        HelpBubble(strings.helpEmbedPicture)
        val carrier = state.carrier
        if (carrier == null) {
            Text(
                strings.embedPictureIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = carrier.thumbnail.asImageBitmap(),
                    contentDescription = strings.embedSelectedPicture,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(carrier.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text("${carrier.width} × ${carrier.height} px", style = MaterialTheme.typography.bodySmall)
                    Text(
                        strings.embedRoomFor(strings.bytes(carrier.maxPayloadBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (carrier.scaledDown) {
                        Text(
                            strings.embedScaledDown(CapacityCalculator.MAX_CARRIER_PIXELS / 1_000_000),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = onChoose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            if (state.loadingCarrier) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(strings.embedOpening)
            } else {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (carrier == null) strings.embedChoosePicture else strings.embedChooseAnotherPicture)
            }
        }
    }
}

@Composable
private fun PayloadSection(strings: AppStrings, state: EmbedState, viewModel: EmbedViewModel, onChooseFile: () -> Unit) {
    SectionCard(2, strings.embedPayloadSection) {
        HelpBubble(strings.helpEmbedPayload)
        // FlowRow: four chips with icons do not fit one line on a narrow phone, least of all in RU.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PayloadKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { viewModel.onKind(kind) },
                    enabled = !state.busy,
                    label = { Text(strings.payloadType(kind.type)) },
                    leadingIcon = { Icon(typeIcon(kind.type), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
        }

        if (state.kind == PayloadKind.NOTE) {
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                minLines = 5,
                maxLines = 12,
                label = { Text(strings.embedNoteLabel) },
                // Best effort only: the keyboard app may still remember what is typed here.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Sentences),
            )
        } else {
            val file = state.file
            if (file != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(state.kind.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(
                            if (file.sizeBytes >= 0) strings.bytes(file.sizeBytes) else strings.embedUnknownSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onChooseFile, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (file == null) strings.embedChooseFile else strings.embedChooseAnotherFile)
            }
        }

        val carrier = state.carrier
        if (carrier != null && state.payloadBytes > 0) CapacityMeter(strings, state.payloadBytes, carrier.maxPayloadBytes)
    }
}

@Composable
private fun CapacityMeter(strings: AppStrings, payloadBytes: Long, maxBytes: Long) {
    val fraction = if (maxBytes <= 0) 2f else payloadBytes.toFloat() / maxBytes
    val over = fraction > 1f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            if (over) {
                strings.embedMeterOver(strings.bytes(payloadBytes), strings.bytes(maxBytes))
            } else {
                strings.embedMeterOk(strings.bytes(payloadBytes), strings.bytes(maxBytes))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TitleSection(strings: AppStrings, state: EmbedState, viewModel: EmbedViewModel) {
    SectionCard(3, strings.embedNameSection) {
        HelpBubble(strings.helpEmbedFinish)
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitle,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            singleLine = true,
            label = { Text(strings.embedTitleLabel) },
            placeholder = { Text(state.defaultTitle(strings)) },
            supportingText = { Text(strings.embedTitleHint) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun DoneSection(strings: AppStrings, result: EmbedResult, onOpen: () -> Unit, onAnother: () -> Unit) {
    StatusCard(
        icon = Icons.Filled.CheckCircle,
        title = strings.embedDoneTitle(result.savedName),
        body = strings.embedDoneBody(result.folder, strings.percent(result.usage)),
        tone = Tone.GOOD,
    )
    StatusCard(
        icon = Icons.Filled.Info,
        title = strings.embedKeepTitle,
        body = strings.embedKeepBody,
        tone = Tone.WARNING,
    )
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(strings.embedOpenItem) }
    TextButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(strings.embedHideAnother) }
}
