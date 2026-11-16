package com.kryptex.vault.ui.embed

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.AesGcm
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.VaultItemEntity
import com.kryptex.vault.data.media.CarrierMediaStore
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.i18n.AppStrings
import com.kryptex.vault.stego.CapacityCalculator
import com.kryptex.vault.stego.CarrierBitmap
import com.kryptex.vault.stego.EmbedOutcome
import com.kryptex.vault.stego.ExtractOutcome
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.stego.UnsupportedCarrierException
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the user wants to hide. [mimePrefix] restricts the file picker and is checked on the result. */
enum class PayloadKind(val type: PayloadType, val mimePrefix: String?) {
    NOTE(PayloadType.TEXT, null),
    AUDIO(PayloadType.AUDIO, "audio/"),
    IMAGE(PayloadType.IMAGE, "image/"),
    FILE(PayloadType.FILE, null),
}

/** The picture the secret goes into. Only a thumbnail is held; the pixels are decoded when hiding starts. */
class CarrierSelection(
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val scaledDown: Boolean,
    val thumbnail: Bitmap,
    /** Largest payload that is guaranteed to fit, before compression. */
    val maxPayloadBytes: Long,
)

class FileSelection(val uri: Uri, val name: String, val mimeType: String, val sizeBytes: Long)

/** The stages of hiding, in order. The wording is in the language files. */
enum class EmbedStep { READING, PREPARING, HIDING, VERIFYING, SAVING }

class EmbedResult(
    val itemId: Long,
    val savedName: String,
    val folder: String,
    val usedBytes: Long,
    val capacityBytes: Long,
) {
    val usage: Double get() = if (capacityBytes == 0L) 1.0 else usedBytes.toDouble() / capacityBytes
}

sealed interface EmbedStage {
    data object Idle : EmbedStage
    data class Working(val step: EmbedStep) : EmbedStage
    class Done(val result: EmbedResult) : EmbedStage
    data class Failed(val message: String) : EmbedStage
}

data class EmbedState(
    val carrier: CarrierSelection? = null,
    val loadingCarrier: Boolean = false,
    val kind: PayloadKind = PayloadKind.NOTE,
    val note: String = "",
    val noteBytes: Long = 0,
    val file: FileSelection? = null,
    val title: String = "",
    /** A problem with a picked picture or file, shown next to the picker. */
    val inputError: String? = null,
    val stage: EmbedStage = EmbedStage.Idle,
) {
    val payloadBytes: Long get() = if (kind == PayloadKind.NOTE) noteBytes else (file?.sizeBytes ?: 0).coerceAtLeast(0)
    val hasPayload: Boolean get() = if (kind == PayloadKind.NOTE) note.isNotBlank() else file != null
    val busy: Boolean get() = loadingCarrier || stage is EmbedStage.Working
    val canHide: Boolean get() = carrier != null && hasPayload && !busy

    /** What the item is called when the user does not type a title. */
    fun defaultTitle(strings: AppStrings): String =
        if (kind == PayloadKind.NOTE) {
            strings.embedDefaultNote
        } else {
            file?.name?.substringBeforeLast('.')?.ifBlank { null } ?: file?.name ?: strings.embedDefaultItem
        }
}

/**
 * Hides one payload in one picture. Everything happens on this device:
 *
 *  1. the payload is read and wrapped in a [PayloadContainer];
 *  2. a fresh random 256-bit container key is drawn;
 *  3. [com.kryptex.vault.stego.StegoEngine] seals the container with AES-256-GCM (keys derived by HKDF
 *     from that container key) and writes it into the picture's pixels;
 *  4. the resulting PNG is decoded again and the payload read back, so a picture that cannot be read is never saved;
 *  5. the PNG goes to the photo library, and its row plus the container key go into the SQLCipher
 *     database (which only opens with the key that Argon2id derives from the user's secret).
 *
 * Secrets typed or picked here must not outlive the unlocked vault: they are cleared on lock.
 */
class EmbedViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EmbedState())
    val state: StateFlow<EmbedState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            container.sessionManager.session.collect { if (it == null) wipeEverything() }
        }
    }

    // -- inputs ------------------------------------------------------------------------------

    fun onCarrierPicked(uri: Uri?) {
        if (uri == null || mutableState.value.busy) return
        mutableState.update { it.copy(loadingCarrier = true, inputError = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val preview = CarrierBitmap.previewForEmbedding(context, uri)
                val selection = CarrierSelection(
                    uri = uri,
                    name = queryMeta(uri).first ?: container.strings.embedDefaultPicture,
                    width = preview.width,
                    height = preview.height,
                    scaledDown = preview.scaledDown,
                    thumbnail = preview.thumbnail,
                    maxPayloadBytes = CapacityCalculator.maxPayloadBytes(preview.width, preview.height),
                )
                mutableState.update { it.copy(loadingCarrier = false, carrier = selection) }
            } catch (e: UnsupportedCarrierException) {
                mutableState.update { it.copy(loadingCarrier = false, inputError = container.strings.embedErrNotPicture) }
            } catch (e: OutOfMemoryError) {
                mutableState.update { it.copy(loadingCarrier = false, inputError = container.strings.embedErrPictureTooLarge) }
            } catch (e: Exception) {
                mutableState.update { it.copy(loadingCarrier = false, inputError = container.strings.embedErrPictureOpen) }
            }
        }
    }

    fun onKind(kind: PayloadKind) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(kind = kind, file = if (kind == it.kind) it.file else null, inputError = null) }
    }

    fun onNote(text: String) {
        val limited = if (text.length > MAX_NOTE_CHARS) text.take(MAX_NOTE_CHARS) else text
        mutableState.update { it.copy(note = limited, noteBytes = utf8Size(limited), inputError = null) }
    }

    fun onTitle(text: String) = mutableState.update { it.copy(title = text.take(MAX_TITLE_CHARS)) }

    fun onFilePicked(uri: Uri?) {
        if (uri == null || mutableState.value.busy) return
        val kind = mutableState.value.kind
        viewModelScope.launch(Dispatchers.IO) {
            val (name, size) = queryMeta(uri)
            val mime = context.contentResolver.getType(uri)
            val strings = container.strings
            val problem = when {
                kind.mimePrefix != null && mime?.startsWith(kind.mimePrefix) != true ->
                    if (kind == PayloadKind.AUDIO) strings.embedErrNotAudio else strings.embedErrNotImage
                size == 0L -> strings.embedErrEmpty
                size != null && size > PayloadContainer.MAX_RAW_BYTES -> strings.embedErrFileTooBig(MAX_PAYLOAD_MB)
                else -> null
            }
            mutableState.update {
                if (problem != null) {
                    it.copy(inputError = problem)
                } else {
                    it.copy(
                        file = FileSelection(
                            uri = uri,
                            name = fitUtf8(name ?: container.strings.embedDefaultItem, PayloadContainer.MAX_NAME_BYTES),
                            mimeType = mime?.takeIf { m -> utf8Size(m) <= PayloadContainer.MAX_MIME_BYTES } ?: "application/octet-stream",
                            sizeBytes = size ?: -1,
                        ),
                        inputError = null,
                    )
                }
            }
        }
    }

    fun dismissError() = mutableState.update {
        if (it.stage is EmbedStage.Failed) it.copy(stage = EmbedStage.Idle) else it.copy(inputError = null)
    }

    /** Back to an empty form after a success. */
    fun reset() {
        if (mutableState.value.stage is EmbedStage.Working) return
        wipeEverything()
    }

    // -- the work ----------------------------------------------------------------------------

    fun hide() {
        val snapshot = mutableState.value
        val carrier = snapshot.carrier ?: return
        if (!snapshot.canHide) return
        val strings = container.strings
        val vault = container.unlocked.value ?: return fail(strings.vaultLockedTryAgain)

        val title = snapshot.title.trim().ifBlank { snapshot.defaultTitle(strings) }
        setStage(EmbedStage.Working(EmbedStep.READING))

        job = viewModelScope.launch(Dispatchers.Default) {
            var data: ByteArray? = null
            var containerKey: ByteArray? = null
            try {
                // 1. What to hide.
                val payload: PayloadContainer
                if (snapshot.kind == PayloadKind.NOTE) {
                    data = snapshot.note.toByteArray(Charsets.UTF_8)
                    payload = PayloadContainer(PayloadType.TEXT, "text/plain", fitUtf8(title, PayloadContainer.MAX_NAME_BYTES), now(), data)
                } else {
                    val file = snapshot.file ?: return@launch fail(strings.embedErrChooseFirst)
                    data = withContext(Dispatchers.IO) { readFile(file) }
                    if (data.isEmpty()) return@launch fail(strings.embedErrEmpty)
                    payload = PayloadContainer(snapshot.kind.type, file.mimeType, file.name, now(), data)
                }

                // 2. The picture, decoded fresh so a retry never starts from already-modified pixels.
                setStage(EmbedStage.Working(EmbedStep.PREPARING))
                val picture = CarrierBitmap.decodeForEmbedding(context, carrier.uri)

                // 3. A new key for this picture only. It never leaves the encrypted database.
                val key = ByteArray(AesGcm.KEY_BYTES).also(SecureRandom()::nextBytes)
                containerKey = key // wiped in finally
                setStage(EmbedStage.Working(EmbedStep.HIDING))
                val embedded = when (val outcome = container.stego.embed(picture.pixels, picture.width, picture.height, payload, key)) {
                    is EmbedOutcome.Embedded -> outcome
                    is EmbedOutcome.TooLarge -> return@launch fail(
                        strings.embedErrTooSmall(strings.bytes(outcome.capacityBytes), strings.bytes(outcome.neededBytes)),
                    )
                }

                // 4. Prove it can be read back from the actual PNG bytes before anything is saved.
                setStage(EmbedStage.Working(EmbedStep.VERIFYING))
                val png = picture.toPng()
                val check = CarrierBitmap.decodeForExtraction(png)
                val readBack = container.stego.extract(check.pixels, check.width, check.height, key)
                val verified = readBack is ExtractOutcome.Found && readBack.container == payload
                if (readBack is ExtractOutcome.Found) readBack.container.data.wipe()
                if (!verified) return@launch fail(strings.embedErrVerify)

                // 5. Save the picture, then the database row. If the row cannot be written, the picture is removed again.
                setStage(EmbedStage.Working(EmbedStep.SAVING))
                val folder = vault.settings.currentSaveFolder()
                    .takeIf { runCatching { CarrierMediaStore.relativePath(it) }.isSuccess } ?: CarrierMediaStore.DEFAULT_FOLDER
                // Not cancellable: a picture that was written must be seen by the code that can roll it back.
                val saved = withContext(Dispatchers.IO + NonCancellable) { container.carrierStore.save(png, folder) }
                val itemId = try {
                    withContext(NonCancellable) {
                        vault.repository.addItem(
                            CarrierEntity(
                                mediaUri = saved.uri.toString(),
                                displayName = saved.displayName,
                                widthPx = picture.width,
                                heightPx = picture.height,
                                capacityBytes = CapacityCalculator.maxPayloadBytes(picture.width, picture.height),
                                fileSha256 = saved.sha256,
                                containerKey = key,
                                createdAt = now(),
                            ),
                            VaultItemEntity(
                                carrierId = 0, // replaced by the repository
                                title = title,
                                payloadType = payload.type,
                                mimeType = payload.mimeType,
                                sizeBytes = payload.data.size.toLong(),
                                createdAt = payload.createdAtEpochMs,
                            ),
                        )
                    }
                } catch (e: Throwable) {
                    withContext(NonCancellable + Dispatchers.IO) { container.carrierStore.delete(saved.uri) }
                    throw e
                }

                mutableState.update {
                    it.copy(
                        // The secret has served its purpose; do not keep it in the form.
                        note = "",
                        noteBytes = 0,
                        file = null,
                        title = "",
                        stage = EmbedStage.Done(EmbedResult(itemId, saved.displayName, folder, embedded.streamBytes, embedded.capacityBytes)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsupportedCarrierException) {
                fail(strings.embedErrPictureGone)
            } catch (e: PayloadTooLargeException) {
                fail(strings.embedErrFileTooBig(MAX_PAYLOAD_MB))
            } catch (e: OutOfMemoryError) {
                fail(strings.embedErrMemory)
            } catch (e: IOException) {
                fail(strings.embedErrIo(e.message ?: e::class.java.simpleName))
            } catch (e: IllegalStateException) {
                // The session closes the database when the vault locks mid-way.
                fail(if (container.unlocked.value == null) strings.embedErrLockedMidway else strings.embedErrFailed(e.message ?: e::class.java.simpleName))
            } catch (e: Exception) {
                fail(strings.embedErrFailed(e.message ?: e::class.java.simpleName))
            } finally {
                data?.wipe()
                containerKey?.wipe()
            }
        }
    }

    // -- helpers -----------------------------------------------------------------------------

    private fun readFile(file: FileSelection): ByteArray {
        val input = context.contentResolver.openInputStream(file.uri) ?: throw IOException("the file could not be opened")
        return input.use { readBounded(it, PayloadContainer.MAX_RAW_BYTES, file.sizeBytes) }
    }

    /** Display name and size from the provider; null where it does not say. */
    private fun queryMeta(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size
    }

    private fun setStage(stage: EmbedStage) = mutableState.update { it.copy(stage = stage) }

    private fun fail(message: String) = setStage(EmbedStage.Failed(message))

    private fun now() = System.currentTimeMillis()

    private fun wipeEverything() {
        job?.cancel()
        mutableState.value = EmbedState()
    }

    override fun onCleared() {
        wipeEverything()
        super.onCleared()
    }

    private companion object {
        const val MAX_NOTE_CHARS = 1_000_000
        const val MAX_TITLE_CHARS = 80
        const val MAX_PAYLOAD_MB = PayloadContainer.MAX_RAW_BYTES / (1024 * 1024)
    }
}
