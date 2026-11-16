package com.kryptex.vault.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HKDF-SHA256 (RFC 5869) over `javax.crypto.Mac`. */
object Hkdf {
    private const val HMAC = "HmacSHA256"
    const val HASH_LENGTH = 32

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val key = if (salt == null || salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return mac.doFinal(ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LENGTH)) { "invalid HKDF output length" }
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(prk, HMAC))

        val out = ByteArray(length)
        var previous = ByteArray(0)
        var position = 0
        var counter = 1
        while (position < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val n = minOf(previous.size, length - position)
            System.arraycopy(previous, 0, out, position, n)
            position += n
            counter++
        }
        previous.wipe()
        return out
    }

    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        try {
            return expand(prk, info, length)
        } finally {
            prk.wipe()
        }
    }

    fun derive(ikm: ByteArray, salt: ByteArray?, info: String, length: Int): ByteArray =
        derive(ikm, salt, info.toByteArray(Charsets.UTF_8), length)
}
