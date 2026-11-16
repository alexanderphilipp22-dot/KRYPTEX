package com.kryptex.vault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.util.zip.CRC32

class VaultHeaderCodecTest {
    private val params = Argon2Params(memoryKiB = 65536, iterations = 4, parallelism = 2)

    private fun passphraseSlot() = PassphraseSlot(params, ByteArray(16) { it.toByte() }, ByteArray(60) { (it * 3).toByte() })
    private fun gestureSlot() = GestureSlot(6, params, ByteArray(16) { (it + 50).toByte() }, ByteArray(88) { (it * 5).toByte() })

    @Test
    fun roundTripWithoutGestureSlot() {
        val header = VaultHeader(1_700_000_000_000L, passphraseSlot(), null, 0)
        assertEquals(header, VaultHeaderCodec.decode(VaultHeaderCodec.encode(header)))
    }

    @Test
    fun roundTripWithGestureSlotAndCounter() {
        val header = VaultHeader(42L, passphraseSlot(), gestureSlot(), 3)
        val decoded = VaultHeaderCodec.decode(VaultHeaderCodec.encode(header))
        assertEquals(header, decoded)
        assertEquals(6, decoded.gestureSlot?.steps)
        assertEquals(3, decoded.failedGestureAttempts)
    }

    @Test
    fun anySingleByteCorruptionIsDetected() {
        val encoded = VaultHeaderCodec.encode(VaultHeader(1L, passphraseSlot(), gestureSlot(), 1))
        for (i in encoded.indices) {
            val damaged = encoded.copyOf().also { it[i] = (it[i].toInt() xor 0x40).toByte() }
            assertThrows("byte $i", CorruptHeaderException::class.java) { VaultHeaderCodec.decode(damaged) }
        }
    }

    @Test
    fun truncationIsDetected() {
        val encoded = VaultHeaderCodec.encode(VaultHeader(1L, passphraseSlot(), null, 0))
        for (len in listOf(0, 3, 8, encoded.size / 2, encoded.size - 1)) {
            assertThrows("len $len", CorruptHeaderException::class.java) {
                VaultHeaderCodec.decode(encoded.copyOf(len))
            }
        }
    }

    @Test
    fun unknownFormatVersionIsRejectedEvenWithValidChecksum() {
        val encoded = VaultHeaderCodec.encode(VaultHeader(1L, passphraseSlot(), null, 0))
        val body = encoded.copyOf(encoded.size - 4).also { it[4] = 99 }
        val crc = CRC32().apply { update(body) }.value
        val forged = body + ByteArray(4) { i -> (crc ushr (24 - 8 * i)).toByte() }
        assertThrows(CorruptHeaderException::class.java) { VaultHeaderCodec.decode(forged) }
    }

    @Test
    fun absurdArgon2ParametersAreRejectedEvenWithValidChecksum() {
        // A damaged or hostile header must not be able to request gigabytes of memory.
        val encoded = VaultHeaderCodec.encode(VaultHeader(1L, passphraseSlot(), null, 0))
        val body = encoded.copyOf(encoded.size - 4)
        val memoryOffset = 4 + 1 + 1 + 8 + 4
        body[memoryOffset] = 0x7F // memory ~2 TiB
        val crc = CRC32().apply { update(body) }.value
        val forged = body + ByteArray(4) { i -> (crc ushr (24 - 8 * i)).toByte() }
        assertThrows(CorruptHeaderException::class.java) { VaultHeaderCodec.decode(forged) }
    }

    @Test
    fun headerStoreWritesAtomicallyAndReadsBack() {
        val dir = Files.createTempDirectory("kryptex-hdr").toFile()
        try {
            val store = VaultHeaderStore(dir)
            assertNull(store.read())

            val first = VaultHeader(1L, passphraseSlot(), null, 0)
            store.write(first)
            assertEquals(first, store.read())

            val second = first.copy(failedGestureAttempts = 2, gestureSlot = gestureSlot())
            store.write(second)
            assertEquals(second, store.read())
            assertEquals("no temp file may be left behind", listOf("vault.hdr"), dir.list()!!.toList())

            store.delete()
            assertNull(store.read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
