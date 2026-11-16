package com.kryptex.vault.ui.vault

import android.media.MediaDataSource

/**
 * Lets MediaPlayer play a revealed audio payload straight from memory. No temporary file is ever
 * written, so decrypted audio never reaches storage.
 */
class InMemoryAudioSource(private val bytes: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= bytes.size) return -1
        val count = minOf(size.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}
