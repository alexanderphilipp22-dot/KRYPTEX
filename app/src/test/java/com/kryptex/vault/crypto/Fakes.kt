package com.kryptex.vault.crypto

import java.security.GeneralSecurityException

/** Instant, deterministic stand-in for Argon2id so key-management logic runs on the JVM. */
class FakeArgon2Kdf : Argon2Kdf {
    var derivations = 0
        private set

    override fun derive(password: ByteArray, salt: ByteArray, params: Argon2Params, outputLength: Int): ByteArray {
        derivations++
        return Hkdf.derive(
            ikm = password,
            salt = salt,
            info = "fake-argon2/${params.memoryKiB}/${params.iterations}/${params.parallelism}",
            length = outputLength,
        )
    }
}

/** Stand-in for the Android Keystore: a fixed AES key that can be "deleted" like the real one. */
class FakeDeviceKeyWrapper : DeviceKeyWrapper {
    private val key = ByteArray(32) { (it * 7 + 1).toByte() }
    var keyPresent = true
        private set

    override fun wrap(plaintext: ByteArray, aad: ByteArray): ByteArray {
        keyPresent = true // the real wrapper (re)generates its key on demand
        return AesGcm.seal(key, plaintext, aad)
    }

    override fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray {
        if (!keyPresent) throw GeneralSecurityException("device key missing")
        return AesGcm.open(key, wrapped, aad)
    }

    override fun deleteKey() {
        keyPresent = false
    }
}
