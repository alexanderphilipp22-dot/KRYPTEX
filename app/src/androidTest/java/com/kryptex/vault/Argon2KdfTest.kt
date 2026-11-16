package com.kryptex.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptex.vault.crypto.Argon2Params
import com.kryptex.vault.crypto.Argon2ktKdf
import com.kryptex.vault.crypto.KdfCalibrator
import com.kryptex.vault.crypto.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Argon2KdfTest {
    private val kdf = Argon2ktKdf()

    /**
     * Known-answer test from the reference implementation's own test suite (phc-winner-argon2,
     * Argon2id v1.3, password "password", salt "somesalt", t=2, m=64 MiB, p=1, 32-byte tag).
     */
    @Test
    fun matchesReferenceImplementationVector() {
        val tag = kdf.derive(
            password = "password".toByteArray(),
            salt = "somesalt".toByteArray(),
            params = Argon2Params(memoryKiB = 65536, iterations = 2, parallelism = 1),
            outputLength = 32,
        )
        assertEquals("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7", tag.toHex())
    }

    @Test
    fun isDeterministicAndSensitiveToEveryInput() {
        val base = Argon2Params(4096, 2, 1)
        val pw = "hunter2".toByteArray()
        val salt = ByteArray(16) { it.toByte() }

        val a = kdf.derive(pw, salt, base)
        assertArrayEquals(a, kdf.derive(pw, salt, base))

        assertFalse(a.contentEquals(kdf.derive("hunter3".toByteArray(), salt, base)))
        assertFalse(a.contentEquals(kdf.derive(pw, ByteArray(16) { (it + 1).toByte() }, base)))
        assertFalse(a.contentEquals(kdf.derive(pw, salt, base.copy(iterations = 3))))
        assertFalse(a.contentEquals(kdf.derive(pw, salt, base.copy(memoryKiB = 8192))))
    }

    @Test
    fun calibrationOnThisDeviceMeetsTheFloorAndStaysNearTheTarget() {
        val started = System.nanoTime()
        val params = KdfCalibrator(kdf).calibrate(maxMemoryKiB = 512 * 1024)
        val calibrationMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("memory floor", params.memoryKiB >= Argon2Params.MIN_MEMORY_KIB)
        assertTrue("iteration floor", params.iterations >= Argon2Params.MIN_ITERATIONS)

        // One real derivation with the chosen parameters should take on the order of a second.
        val s = System.nanoTime()
        kdf.derive("pw".toByteArray(), ByteArray(16), params)
        val deriveMillis = (System.nanoTime() - s) / 1_000_000
        println("calibrated=$params calibrationMs=$calibrationMillis deriveMs=$deriveMillis")
        assertTrue("derivation took ${deriveMillis}ms", deriveMillis in 100..10_000)
    }
}
