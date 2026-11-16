package com.kryptex.vault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException

class AesGcmTest {
    private val key = ByteArray(32) { it.toByte() }
    private val aad = "context".toByteArray()
    private val message = "attack at dawn".toByteArray()

    @Test
    fun roundTrip() {
        val sealed = AesGcm.seal(key, message, aad)
        assertEquals(message.size + AesGcm.OVERHEAD_BYTES, sealed.size)
        assertArrayEquals(message, AesGcm.open(key, sealed, aad))
    }

    @Test
    fun emptyPlaintextRoundTrips() {
        val sealed = AesGcm.seal(key, ByteArray(0))
        assertEquals(AesGcm.OVERHEAD_BYTES, sealed.size)
        assertEquals(0, AesGcm.open(key, sealed).size)
    }

    @Test
    fun nonceIsFreshEverySeal() {
        val a = AesGcm.seal(key, message, aad)
        val b = AesGcm.seal(key, message, aad)
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun wrongKeyIsRejected() {
        val sealed = AesGcm.seal(key, message, aad)
        val other = key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { AesGcm.open(other, sealed, aad) }
        assertNull(AesGcm.openOrNull(other, sealed, aad))
    }

    @Test
    fun wrongAadIsRejected() {
        val sealed = AesGcm.seal(key, message, aad)
        assertThrows(GeneralSecurityException::class.java) { AesGcm.open(key, sealed, "other".toByteArray()) }
    }

    @Test
    fun everyByteOfTheSealedBlobIsAuthenticated() {
        val sealed = AesGcm.seal(key, message, aad)
        for (i in sealed.indices) {
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertNull("flip at byte $i must be detected", AesGcm.openOrNull(key, tampered, aad))
        }
        assertNotNull(AesGcm.openOrNull(key, sealed, aad))
    }

    @Test
    fun truncatedInputIsRejected() {
        assertThrows(GeneralSecurityException::class.java) { AesGcm.open(key, ByteArray(10), aad) }
        val sealed = AesGcm.seal(key, message, aad)
        assertNull(AesGcm.openOrNull(key, sealed.copyOf(sealed.size - 1), aad))
    }

    @Test
    fun onlyAes256KeysAreAccepted() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.seal(ByteArray(16), message) }
        assertThrows(IllegalArgumentException::class.java) { AesGcm.open(ByteArray(24), ByteArray(40)) }
    }
}
