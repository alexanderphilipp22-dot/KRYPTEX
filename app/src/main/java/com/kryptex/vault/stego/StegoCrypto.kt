package com.kryptex.vault.stego

import com.kryptex.vault.crypto.AesGcm
import com.kryptex.vault.crypto.Hkdf
import com.kryptex.vault.crypto.wipe
import java.nio.ByteBuffer

/** The three independent keys derived from one container key. */
class StegoKeys private constructor(
    /** Seals the length header. */
    val header: ByteArray,
    /** Seals the payload container. */
    val body: ByteArray,
    /** Drives the pixel permutation and the +1/-1 noise. */
    val path: ByteArray,
) {
    fun wipe() {
        header.wipe()
        body.wipe()
        path.wipe()
    }

    companion object {
        private val SALT = "kryptex.stego.v1".toByteArray(Charsets.US_ASCII)

        fun derive(containerKey: ByteArray): StegoKeys {
            require(containerKey.size == AesGcm.KEY_BYTES) { "container key must be 32 bytes" }
            return StegoKeys(
                header = Hkdf.derive(containerKey, SALT, "header", AesGcm.KEY_BYTES),
                body = Hkdf.derive(containerKey, SALT, "body", AesGcm.KEY_BYTES),
                path = Hkdf.derive(containerKey, SALT, "path", AesGcm.KEY_BYTES),
            )
        }
    }
}

/**
 * The byte stream that is hidden in the pixels. It contains no magic number, no plaintext field
 * and no fixed pattern: every byte is AES-GCM output, so a carrier with a payload, a carrier
 * with the wrong key and a carrier with nothing at all behave identically to anyone without the key.
 *
 * ```
 * header (36 bytes)  nonce(12) || GCM(K_header, version | flags | reserved(2) | bodyLength(4)) [8 + 16 tag]
 * body               nonce(12) || GCM(K_body, container, aad = header) || tag(16)
 * ```
 * Binding the body to the header through the AAD prevents splicing a body from one stream onto
 * the header of another.
 */
object StegoCrypto {
    const val FORMAT_VERSION = 1
    private const val HEADER_PLAINTEXT_BYTES = 8

    /** Size of the sealed header. */
    const val HEADER_BYTES = AesGcm.OVERHEAD_BYTES + HEADER_PLAINTEXT_BYTES

    /** Added to the container size by the body seal. */
    const val BODY_OVERHEAD_BYTES = AesGcm.OVERHEAD_BYTES

    /** Total stream bytes for a container of [containerBytes] bytes. */
    fun streamBytes(containerBytes: Int): Long = HEADER_BYTES.toLong() + BODY_OVERHEAD_BYTES + containerBytes

    fun seal(container: ByteArray, keys: StegoKeys): ByteArray {
        val bodyLength = container.size + BODY_OVERHEAD_BYTES
        val headerPlain = ByteBuffer.allocate(HEADER_PLAINTEXT_BYTES)
            .put(FORMAT_VERSION.toByte())
            .put(0) // flags
            .putShort(0) // reserved
            .putInt(bodyLength)
            .array()
        val header = AesGcm.seal(keys.header, headerPlain)
        val body = AesGcm.seal(keys.body, container, aad = header)
        return header + body
    }

    /** @return the body length announced by an authentic header, or null if [header] is not one. */
    fun openHeader(header: ByteArray, keys: StegoKeys): Int? {
        if (header.size != HEADER_BYTES) return null
        val plain = AesGcm.openOrNull(keys.header, header) ?: return null
        if (plain.size != HEADER_PLAINTEXT_BYTES) return null
        val buffer = ByteBuffer.wrap(plain)
        val version = buffer.get().toInt() and 0xFF
        buffer.get() // flags
        buffer.short // reserved
        val bodyLength = buffer.int
        if (version != FORMAT_VERSION || bodyLength < BODY_OVERHEAD_BYTES) return null
        return bodyLength
    }

    /** @return the container bytes, or null if [body] was altered or does not belong to [header]. */
    fun openBody(body: ByteArray, header: ByteArray, keys: StegoKeys): ByteArray? =
        AesGcm.openOrNull(keys.body, body, aad = header)
}
