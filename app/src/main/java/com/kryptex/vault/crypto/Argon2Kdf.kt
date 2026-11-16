package com.kryptex.vault.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version

/**
 * Password-based key derivation. An interface so the native backend can be swapped and so the
 * key-management logic can be unit-tested on the JVM with a fake.
 *
 * Calls are blocking and deliberately slow (about one second at calibrated cost): never call
 * from the main thread.
 */
interface Argon2Kdf {
    fun derive(
        password: ByteArray,
        salt: ByteArray,
        params: Argon2Params,
        outputLength: Int = DEFAULT_OUTPUT_BYTES,
    ): ByteArray

    companion object {
        const val DEFAULT_OUTPUT_BYTES = 32
        const val MIN_SALT_BYTES = 8
    }
}

/** Argon2id (version 1.3) backed by the reference C implementation via argon2kt. */
class Argon2ktKdf : Argon2Kdf {
    private val argon2 = Argon2Kt()

    override fun derive(
        password: ByteArray,
        salt: ByteArray,
        params: Argon2Params,
        outputLength: Int,
    ): ByteArray {
        require(salt.size >= Argon2Kdf.MIN_SALT_BYTES) { "salt too short" }
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = params.iterations,
            mCostInKibibyte = params.memoryKiB,
            parallelism = params.parallelism,
            hashLengthInBytes = outputLength,
            version = Argon2Version.V13,
        )
        return result.rawHashAsByteArray()
    }
}
