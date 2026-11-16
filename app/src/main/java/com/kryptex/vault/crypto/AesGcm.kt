package com.kryptex.vault.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a random 96-bit nonce and a 128-bit tag.
 *
 * Sealed layout: `nonce(12) || ciphertext || tag(16)`. Associated data is authenticated but
 * not stored; callers rebuild it when opening, which binds ciphertexts to their context.
 */
object AesGcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val OVERHEAD_BYTES = NONCE_BYTES + TAG_BYTES

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val EMPTY = ByteArray(0)
    private val sharedRandom = SecureRandom()

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = EMPTY,
        random: SecureRandom = sharedRandom,
    ): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    /** @throws GeneralSecurityException (AEADBadTagException) on a wrong key, wrong AAD or tampering. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = EMPTY): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        if (sealed.size < OVERHEAD_BYTES) throw GeneralSecurityException("sealed data too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BYTES * 8, sealed, 0, NONCE_BYTES),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    /** Same as [open] but returns null on any authentication failure. */
    fun openOrNull(key: ByteArray, sealed: ByteArray, aad: ByteArray = EMPTY): ByteArray? =
        try {
            open(key, sealed, aad)
        } catch (_: GeneralSecurityException) {
            null
        }
}
