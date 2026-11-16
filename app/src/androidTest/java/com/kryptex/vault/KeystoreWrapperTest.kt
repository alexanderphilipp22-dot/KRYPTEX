package com.kryptex.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptex.vault.crypto.KeystoreWrapper
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class KeystoreWrapperTest {
    // A dedicated alias: tests must never touch the key of a real vault on the same device.
    private val wrapper = KeystoreWrapper(alias = "kryptex.test.device-binding")
    private val aad = "slot-aad".toByteArray()
    private val secret = ByteArray(48) { (it * 3).toByte() }

    @Before
    fun clean() = wrapper.deleteKey()

    @After
    fun cleanUp() = wrapper.deleteKey()

    @Test
    fun roundTrip() {
        val wrapped = wrapper.wrap(secret, aad)
        assertEquals(secret.size + 12 + 16, wrapped.size)
        assertArrayEquals(secret, wrapper.unwrap(wrapped, aad))
    }

    @Test
    fun wrongAadIsRejected() {
        val wrapped = wrapper.wrap(secret, aad)
        assertThrows(GeneralSecurityException::class.java) { wrapper.unwrap(wrapped, "other".toByteArray()) }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val wrapped = wrapper.wrap(secret, aad)
        wrapped[wrapped.size / 2] = (wrapped[wrapped.size / 2].toInt() xor 1).toByte()
        assertThrows(GeneralSecurityException::class.java) { wrapper.unwrap(wrapped, aad) }
    }

    @Test
    fun deletedKeyMakesOldDataUnrecoverable() {
        val wrapped = wrapper.wrap(secret, aad)
        wrapper.deleteKey()
        assertThrows(GeneralSecurityException::class.java) { wrapper.unwrap(wrapped, aad) }

        // A new key is generated on demand; it must not open data wrapped by the old one.
        wrapper.wrap(secret, aad)
        assertThrows(GeneralSecurityException::class.java) { wrapper.unwrap(wrapped, aad) }
    }
}
