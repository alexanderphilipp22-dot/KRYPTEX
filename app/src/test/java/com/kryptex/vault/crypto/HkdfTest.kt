package com.kryptex.vault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** RFC 5869 appendix A test vectors for HKDF-SHA256. */
class HkdfTest {

    @Test
    fun rfc5869_testCase1_basic() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToBytes()
        val salt = "000102030405060708090a0b0c".hexToBytes()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes()

        val prk = Hkdf.extract(salt, ikm)
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", prk.toHex())

        val okm = Hkdf.expand(prk, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun rfc5869_testCase2_longInputsAndOutputs() {
        val ikm = ByteArray(80) { it.toByte() }
        val salt = ByteArray(80) { (0x60 + it).toByte() }
        val info = ByteArray(80) { (0xb0 + it).toByte() }

        val prk = Hkdf.extract(salt, ikm)
        assertEquals("06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244", prk.toHex())

        val okm = Hkdf.expand(prk, info, 82)
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.toHex(),
        )
    }

    @Test
    fun rfc5869_testCase3_emptySaltAndInfo() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToBytes()

        val prk = Hkdf.extract(ByteArray(0), ikm)
        assertEquals("19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04", prk.toHex())

        val okm = Hkdf.derive(ikm, null, ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex(),
        )
    }

    @Test
    fun rejectsInvalidOutputLengths() {
        val prk = ByteArray(32) { 1 }
        assertThrows(IllegalArgumentException::class.java) { Hkdf.expand(prk, ByteArray(0), 0) }
        assertThrows(IllegalArgumentException::class.java) { Hkdf.expand(prk, ByteArray(0), 255 * 32 + 1) }
    }

    @Test
    fun differentInfoGivesIndependentKeys() {
        val ikm = ByteArray(32) { 7 }
        val a = Hkdf.derive(ikm, null, "purpose-a", 32)
        val b = Hkdf.derive(ikm, null, "purpose-b", 32)
        assert(!a.contentEquals(b))
    }
}
