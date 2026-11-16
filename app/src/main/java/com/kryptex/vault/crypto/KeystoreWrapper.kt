package com.kryptex.vault.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Binds a secret to this physical device. The gesture key slot is wrapped by a second,
 * non-exportable key so a copied app-data directory cannot be brute-forced offline.
 */
interface DeviceKeyWrapper {
    fun wrap(plaintext: ByteArray, aad: ByteArray): ByteArray

    /** @throws GeneralSecurityException if the device key is missing or the data was altered. */
    fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray

    fun deleteKey()
}

/**
 * Android Keystore AES-256-GCM key, StrongBox-backed when the device has it. The key never
 * requires user authentication: its job is device binding, not user presence.
 *
 * Layout: `iv(12) || ciphertext || tag(16)`.
 */
class KeystoreWrapper(private val alias: String = DEFAULT_ALIAS) : DeviceKeyWrapper {

    override fun wrap(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray {
        if (wrapped.size < AesGcm.OVERHEAD_BYTES) throw GeneralSecurityException("wrapped data too short")
        val key = existingKey() ?: throw GeneralSecurityException("device key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(AesGcm.TAG_BYTES * 8, wrapped, 0, AesGcm.NONCE_BYTES),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(wrapped, AesGcm.NONCE_BYTES, wrapped.size - AesGcm.NONCE_BYTES)
    }

    override fun deleteKey() {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        return try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "kryptex.device-binding.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
