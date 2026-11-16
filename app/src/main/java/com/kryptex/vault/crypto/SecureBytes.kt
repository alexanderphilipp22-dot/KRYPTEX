package com.kryptex.vault.crypto

import java.nio.CharBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Arrays

/** Best-effort zeroing. The JVM may still hold copies (GC moves, JIT temporaries), see plan limits. */
fun ByteArray.wipe() = Arrays.fill(this, 0.toByte())

fun CharArray.wipe() = Arrays.fill(this, '\u0000')

/** Constant-time comparison for secrets and MACs. */
fun ByteArray.constantTimeEquals(other: ByteArray): Boolean = MessageDigest.isEqual(this, other)

/** Runs [block] and zeroes this array afterwards, even on failure. */
inline fun <T> ByteArray.useWiping(block: (ByteArray) -> T): T =
    try {
        block(this)
    } finally {
        wipe()
    }

private const val HEX = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0F]
    }
    return String(out)
}

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd-length hex string" }
    return ByteArray(length / 2) { i ->
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex digit" }
        ((hi shl 4) or lo).toByte()
    }
}

/**
 * Turns a typed passphrase into key-derivation input: NFKC-normalised (so the same visible
 * characters from different keyboards derive the same key) and UTF-8 encoded.
 *
 * Normalisation goes through a [String], which cannot be wiped; this is the documented
 * best-effort limit of passphrase handling on the JVM.
 */
fun CharArray.toKeyMaterial(): ByteArray {
    val normalized = Normalizer.normalize(String(this), Normalizer.Form.NFKC)
    val chars = normalized.toCharArray()
    try {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(buffer.remaining())
        buffer.get(out)
        if (buffer.hasArray()) Arrays.fill(buffer.array(), 0.toByte())
        return out
    } finally {
        chars.wipe()
    }
}
