package com.kryptex.vault.ui.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kryptex.vault.stego.PayloadContainer
import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.i18n.LocalStrings
import com.kryptex.vault.ui.components.graphiteCardColors
import com.kryptex.vault.ui.components.hairline

/**
 * Shows a revealed item. Text, pictures and audio are rendered from memory only; "Save a copy" is
 * the one deliberate way to take content out of the vault.
 */
@Composable
fun PayloadViewer(payload: PayloadContainer, onExport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (payload.type) {
            PayloadType.TEXT -> TextPayload(String(payload.data, Charsets.UTF_8))
            PayloadType.IMAGE -> ImagePayload(payload.data)
            PayloadType.AUDIO -> AudioPayload(payload.data)
            PayloadType.FILE -> Text(
                "${payload.name} · ${LocalStrings.current.bytes(payload.data.size.toLong())}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        OutlinedButton(onClick = onExport, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(LocalStrings.current.payloadSaveCopy)
        }
    }
}

@Composable
private fun TextPayload(text: String) {
    Card(Modifier.fillMaxWidth(), colors = graphiteCardColors(), border = hairline()) {
        SelectionContainer {
            Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ImagePayload(data: ByteArray) {
    val bitmap = remember(data) { decodeScaled(data, maxSide = 2048) }
    if (bitmap == null) {
        Text(LocalStrings.current.payloadImageFailed)
    } else {
        Image(bitmap.asImageBitmap(), contentDescription = LocalStrings.current.payloadImageDescription, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
    }
}

private fun decodeScaled(data: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun AudioPayload(data: ByteArray) {
    var playing by remember(data) { mutableStateOf(false) }
    var failed by remember(data) { mutableStateOf(false) }
    val player = remember(data) { MediaPlayer() }

    DisposableEffect(data) {
        try {
            player.setDataSource(InMemoryAudioSource(data))
            player.setOnCompletionListener { playing = false }
            player.prepare()
        } catch (_: Exception) {
            failed = true
        }
        onDispose { runCatching { player.release() } }
    }

    if (failed) {
        Text(LocalStrings.current.payloadAudioFailed)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (playing) player.pause() else player.start()
                playing = !playing
            }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (playing) LocalStrings.current.payloadPause else LocalStrings.current.payloadPlay)
            }
        }
    }
}
