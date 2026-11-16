package com.kryptex.vault.ui.extract

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptex.vault.crypto.wipe
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.media.CarrierMediaStore
import com.kryptex.vault.di.AppContainer
import com.kryptex.vault.stego.CarrierBitmap
import com.kryptex.vault.stego.ExtractOutcome
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.stego.UnsupportedCarrierException
import com.kryptex.vault.ui.embed.PayloadTooLargeException
import com.kryptex.vault.ui.embed.readBounded
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The stages of revealing, in order. The wording is in the language files. */
enum class ExtractStep { READING, IDENTIFYING, DECRYPTING }

sealed interface ExtractStage {
    data object Idle : ExtractStage
    data class Working(val step: ExtractStep) : ExtractStage

    /** [payload] is secret and lives only here; it is wiped by [ExtractViewModel] when dropped. */
    class Done(val payload: PayloadContainer) : ExtractStage
    data class Failed(val message: String) : ExtractStage
}

data class ExtractState(
    val pictureName: String? = null,
    val stage: ExtractStage = ExtractStage.Idle,
) {
    val busy: Boolean get() = stage is ExtractStage.Working
}

/**
 * Reads a hidden item out of a picture the user selects, entirely on this device.
 *
 * Each carrier's random container key lives in the SQLCipher database. A picture is matched to its
 * key by size and SHA-256 first; if the hash is unknown (the file was re-saved) the other keys for
 * pictures of the same size are still tried, because decryption is authenticated: a wrong key or an
 * altered picture simply yields nothing. The decrypted payload exists only in memory and is wiped
 * when dismissed, when the screen is left and when the vault locks.
 */
class ExtractViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExtractState())
    val state: StateFlow<ExtractState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            container.sessionManager.session.collect { if (it == null) wipeEverything() }
        }
    }

    fun onPicked(uri: Uri?) {
        if (uri == null || mutableState.value.busy) return
        val strings = container.strings
        val vault = container.unlocked.value ?: return fail(strings.vaultLockedTryAgain)
        discardPayload()
        mutableState.update { ExtractState(pictureName = queryName(uri), stage = ExtractStage.Working(ExtractStep.READING)) }

        job = viewModelScope.launch(Dispatchers.Default) {
            var carriers: List<CarrierEntity> = emptyList()
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("the picture could not be opened")
                    input.use { readBounded(it, MAX_FILE_BYTES, -1) }
                }
                val picture = CarrierBitmap.decodeForExtraction(bytes)

                setStage(ExtractStage.Working(ExtractStep.IDENTIFYING))
                val sha = CarrierMediaStore.sha256Hex(bytes)
                carriers = vault.repository.allCarriers()
                // The permutation depends on the pixel count, so only same-sized pictures can match.
                val candidates = carriers
                    .filter { it.widthPx == picture.width && it.heightPx == picture.height }
                    .sortedByDescending { it.fileSha256 == sha }
                val knownFile = candidates.any { it.fileSha256 == sha }

                setStage(ExtractStage.Working(ExtractStep.DECRYPTING))
                for (carrier in candidates) {
                    val outcome = container.stego.extract(picture.pixels, picture.width, picture.height, carrier.containerKey)
                    if (outcome is ExtractOutcome.Found) {
                        setStage(ExtractStage.Done(outcome.container))
                        return@launch
                    }
                }
                fail(if (knownFile) strings.extractErrDamaged else strings.extractErrNothing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsupportedCarrierException) {
                fail(strings.extractErrNotPng)
            } catch (e: PayloadTooLargeException) {
                fail(strings.extractErrTooBig(MAX_FILE_BYTES / (1024 * 1024)))
            } catch (e: OutOfMemoryError) {
                fail(strings.extractErrMemory)
            } catch (e: IOException) {
                fail(strings.extractErrIo(e.message ?: e::class.java.simpleName))
            } catch (e: IllegalStateException) {
                fail(if (container.unlocked.value == null) strings.extractErrLockedMidway else strings.extractErrFailed(e.message ?: e::class.java.simpleName))
            } catch (e: Exception) {
                fail(strings.extractErrFailed(e.message ?: e::class.java.simpleName))
            } finally {
                // These are copies of key material loaded from the database.
                carriers.forEach { it.containerKey.wipe() }
            }
        }
    }

    /** Writes the revealed payload to a place the user chose. The one deliberate way out of the vault. */
    suspend fun export(target: Uri) {
        val payload = (mutableState.value.stage as? ExtractStage.Done)?.payload ?: return
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "w")?.use { it.write(payload.data) }
        }
    }

    /** Wipes the revealed payload and returns to the start. */
    fun reset() {
        if (mutableState.value.busy) return
        wipeEverything()
    }

    fun dismissError() = mutableState.update { if (it.stage is ExtractStage.Failed) it.copy(stage = ExtractStage.Idle) else it }

    private fun setStage(stage: ExtractStage) = mutableState.update { it.copy(stage = stage) }

    private fun fail(message: String) = setStage(ExtractStage.Failed(message))

    private fun discardPayload() {
        (mutableState.value.stage as? ExtractStage.Done)?.payload?.data?.wipe()
    }

    private fun queryName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun wipeEverything() {
        job?.cancel()
        discardPayload()
        mutableState.value = ExtractState()
    }

    override fun onCleared() {
        wipeEverything()
        super.onCleared()
    }

    private companion object {
        /** A carrier PNG of the largest supported picture is far below this. */
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}
