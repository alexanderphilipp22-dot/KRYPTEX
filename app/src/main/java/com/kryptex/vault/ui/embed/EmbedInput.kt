package com.kryptex.vault.ui.embed

import com.kryptex.vault.crypto.wipe
import java.io.IOException
import java.io.InputStream

/** The chosen file is larger than the vault format allows. */
class PayloadTooLargeException(val limitBytes: Int) : IOException("payload larger than $limitBytes bytes")

/** Number of bytes [text] takes in UTF-8, without allocating. */
internal fun utf8Size(text: CharSequence): Long {
    var bytes = 0L
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.code < 0x80 -> bytes += 1
            c.code < 0x800 -> bytes += 2
            Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                bytes += 4
                i++ // the low surrogate is part of the same code point
            }
            else -> bytes += 3
        }
        i++
    }
    return bytes
}

/** The longest prefix of [text] that is at most [maxBytes] in UTF-8, never splitting a character. */
internal fun fitUtf8(text: String, maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < text.length) {
        val codePoint = text.codePointAt(end)
        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(codePoint)
    }
    return text.substring(0, end)
}

/**
 * Reads [input] to the end, but never more than [limit] bytes. [sizeHint] (from the file's
 * metadata, or -1) lets the common case allocate once; intermediate buffers are wiped because
 * the content is secret.
 *
 * @throws PayloadTooLargeException if there is more than [limit] bytes of data.
 */
internal fun readBounded(input: InputStream, limit: Int, sizeHint: Long): ByteArray {
    // Never start larger than the limit, or data beyond it would be read without being noticed.
    var buffer = ByteArray(if (sizeHint in 1..limit.toLong()) sizeHint.toInt() else minOf(64 * 1024, limit).coerceAtLeast(1))
    var length = 0
    while (true) {
        if (length == buffer.size) {
            // Full (or exactly sized): only grow if there really is more data.
            val next = input.read()
            if (next < 0) break
            if (length >= limit) throw PayloadTooLargeException(limit).also { buffer.wipe() }
            val bigger = buffer.copyOf(minOf(buffer.size.toLong() * 2, limit.toLong()).toInt().coerceAtLeast(length + 1))
            buffer.wipe()
            buffer = bigger
            buffer[length++] = next.toByte()
            continue
        }
        val read = input.read(buffer, length, buffer.size - length)
        if (read < 0) break
        length += read
    }
    if (length == buffer.size) return buffer
    return buffer.copyOf(length).also { buffer.wipe() }
}
