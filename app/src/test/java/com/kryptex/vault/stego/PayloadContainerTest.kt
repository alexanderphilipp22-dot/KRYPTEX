package com.kryptex.vault.stego

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Random
import java.util.zip.Deflater

class PayloadContainerTest {
    private fun container(
        data: ByteArray,
        type: PayloadType = PayloadType.FILE,
        mime: String = "application/octet-stream",
        name: String = "file.bin",
    ) = PayloadContainer(type, mime, name, createdAtEpochMs = 1_700_000_000_123L, data = data)

    private fun random(n: Int) = ByteArray(n).also { Random(11).nextBytes(it) }

    /** Offset of the u32 rawLength field in an encoded container. */
    private fun rawLengthOffset(encoded: ByteArray): Int {
        val buffer = ByteBuffer.wrap(encoded)
        val mimeLen = buffer.getShort(12).toInt() and 0xFFFF
        val nameLen = buffer.getShort(12 + 2 + mimeLen).toInt() and 0xFFFF
        return 12 + 2 + mimeLen + 2 + nameLen
    }

    @Test
    fun everyPayloadTypeRoundTripsWithUnicodeMetadata() {
        for (type in PayloadType.entries) {
            val original = container(random(300), type, "text/plain; charset=utf-8", "Geheimnis äöü ☃ 🔒.txt")
            for (compress in listOf(true, false)) {
                assertEquals(original, PayloadContainer.decode(original.encode(compress)))
            }
        }
    }

    @Test
    fun compressibleDataIsDeflatedAndRandomDataIsNot() {
        val text = ("The quick brown fox jumps over the lazy dog. ".repeat(200)).toByteArray()
        val c = container(text, PayloadType.TEXT)
        assertTrue("encoded ${c.encode().size} vs raw ${text.size}", c.encode().size < text.size / 4)
        assertEquals(c, PayloadContainer.decode(c.encode()))

        val noise = container(random(4000))
        assertTrue(noise.encode().size >= 4000) // never grows beyond data + a small header
        assertTrue(noise.encode().size < 4000 + 100)
        assertEquals(noise, PayloadContainer.decode(noise.encode()))
    }

    @Test
    fun compressionCanBeSwitchedOff() {
        val text = ByteArray(5000) { 'a'.code.toByte() }
        assertTrue(container(text).encode(compress = false).size > 5000)
    }

    @Test
    fun emptyAndTinyPayloadsWork() {
        for (n in listOf(0, 1, 63, 64, 65)) {
            val c = container(random(n))
            assertEquals("size $n", c, PayloadContainer.decode(c.encode()))
        }
    }

    @Test
    fun truncationAtAnyLengthIsRejectedCleanly() {
        val encoded = container(random(100)).encode()
        for (len in 0 until encoded.size) {
            assertThrows("length $len", MalformedContainerException::class.java) {
                PayloadContainer.decode(encoded.copyOf(len))
            }
        }
    }

    @Test
    fun trailingGarbageAndBadHeaderFieldsAreRejected() {
        val encoded = container(random(50)).encode(compress = false)

        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(encoded + 0) }

        fun patched(index: Int, value: Int) = encoded.copyOf().also { it[index] = value.toByte() }
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(patched(0, 9)) } // version
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(patched(1, 0x80)) } // flags
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(patched(2, 99)) } // type
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(patched(3, 1)) } // reserved
    }

    @Test
    fun aDeflatedContainerThatLiesAboutItsSizeIsRejected() {
        val data = ByteArray(10_000) { 'x'.code.toByte() }
        val encoded = container(data).encode()
        val offset = rawLengthOffset(encoded)

        fun withRawLength(n: Int) = encoded.copyOf().also { ByteBuffer.wrap(it).putInt(offset, n) }
        assertEquals(data.size, ByteBuffer.wrap(encoded).getInt(offset))

        // Declares less than the stream really holds, or more.
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(withRawLength(9_999)) }
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(withRawLength(10_001)) }
    }

    @Test
    fun aDecompressionBombIsStoppedByTheDeclaredSizeLimit() {
        // 100 MB of zeros deflates to about 100 KB: tiny on the wire, huge in memory.
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(ByteArray(100 * 1024 * 1024))
        deflater.finish()
        val bomb = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65_536)
        while (!deflater.finished()) bomb.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        assertTrue("bomb should be small, was ${bomb.size()}", bomb.size() < 200_000)

        val mime = "application/octet-stream".toByteArray()
        val name = "x".toByteArray()
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).apply {
            writeByte(1); writeByte(1); writeByte(PayloadType.FILE.id); writeByte(0)
            writeLong(0)
            writeShort(mime.size); write(mime)
            writeShort(name.size); write(name)
            writeInt(100 * 1024 * 1024) // declared raw size, over the 64 MiB limit
            writeInt(bomb.size()); write(bomb.toByteArray())
        }
        assertThrows(MalformedContainerException::class.java) { PayloadContainer.decode(bytes.toByteArray()) }
    }

    @Test
    fun oversizedMetadataIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { container(ByteArray(1), name = "n".repeat(256)) }
        assertThrows(IllegalArgumentException::class.java) { container(ByteArray(1), mime = "m".repeat(128)) }
        // The limit is on UTF-8 bytes, not characters.
        assertThrows(IllegalArgumentException::class.java) { container(ByteArray(1), name = "ä".repeat(128)) }
        container(ByteArray(1), name = "n".repeat(255)) // exactly at the limit is fine
    }

    @Test
    fun theContentNeverAppearsInToString() {
        val text = container("my secret diary".toByteArray(), PayloadType.TEXT, name = "diary").toString()
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("diary"))
    }

    @Test
    fun dataIsPreservedByteForByte() {
        val data = ByteArray(256) { it.toByte() }
        assertArrayEquals(data, PayloadContainer.decode(container(data).encode()).data)
    }
}
