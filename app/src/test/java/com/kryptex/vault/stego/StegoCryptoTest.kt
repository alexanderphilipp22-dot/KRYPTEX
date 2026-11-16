package com.kryptex.vault.stego

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StegoCryptoTest {
    private val containerKey = ByteArray(32) { (it * 3 + 1).toByte() }
    private val keys = StegoKeys.derive(containerKey)
    private fun random(n: Int, seed: Long = 5) = ByteArray(n).also { Random(seed).nextBytes(it) }

    @Test
    fun streamSizeMatchesTheDocumentedLayout() {
        val container = random(1000)
        val stream = StegoCrypto.seal(container, keys)
        assertEquals(36, StegoCrypto.HEADER_BYTES)
        assertEquals(StegoCrypto.streamBytes(container.size), stream.size.toLong())
        assertEquals(36L + 28 + 1000, stream.size.toLong())
    }

    @Test
    fun headerAnnouncesTheBodyLengthAndBodyOpens() {
        val container = random(777)
        val stream = StegoCrypto.seal(container, keys)
        val header = stream.copyOfRange(0, StegoCrypto.HEADER_BYTES)

        val bodyLength = StegoCrypto.openHeader(header, keys)
        assertEquals(777 + 28, bodyLength)

        val body = stream.copyOfRange(StegoCrypto.HEADER_BYTES, stream.size)
        assertEquals(bodyLength, body.size)
        assertArrayEquals(container, StegoCrypto.openBody(body, header, keys))
    }

    @Test
    fun aDifferentKeyFindsNothing() {
        val stream = StegoCrypto.seal(random(100), keys)
        val header = stream.copyOfRange(0, 36)
        val other = StegoKeys.derive(ByteArray(32) { 99 })

        assertNull(StegoCrypto.openHeader(header, other))
        assertNull(StegoCrypto.openBody(stream.copyOfRange(36, stream.size), header, other))
    }

    @Test
    fun everyByteOfTheHeaderIsAuthenticated() {
        val stream = StegoCrypto.seal(random(100), keys)
        val header = stream.copyOfRange(0, 36)
        assertNotNull(StegoCrypto.openHeader(header, keys))
        for (i in header.indices) {
            val damaged = header.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull("header byte $i", StegoCrypto.openHeader(damaged, keys))
        }
    }

    @Test
    fun everyByteOfTheBodyIsAuthenticated() {
        val stream = StegoCrypto.seal(random(120), keys)
        val header = stream.copyOfRange(0, 36)
        val body = stream.copyOfRange(36, stream.size)
        for (i in body.indices) {
            val damaged = body.copyOf().also { it[i] = (it[i].toInt() xor 0x10).toByte() }
            assertNull("body byte $i", StegoCrypto.openBody(damaged, header, keys))
        }
    }

    @Test
    fun aBodyCannotBeSplicedOntoAnotherStreamsHeader() {
        // Same key, two different containers of the same length: the bodies are individually valid,
        // but each is bound to its own header.
        val a = StegoCrypto.seal(random(64, seed = 1), keys)
        val b = StegoCrypto.seal(random(64, seed = 2), keys)
        val headerA = a.copyOfRange(0, 36)
        val bodyB = b.copyOfRange(36, b.size)

        assertNotNull(StegoCrypto.openBody(b.copyOfRange(36, b.size), b.copyOfRange(0, 36), keys))
        assertNull(StegoCrypto.openBody(bodyB, headerA, keys))
    }

    @Test
    fun headerOfWrongSizeIsRejected() {
        assertNull(StegoCrypto.openHeader(ByteArray(35), keys))
        assertNull(StegoCrypto.openHeader(ByteArray(37), keys))
    }

    @Test
    fun derivedKeysAreIndependentAndDeterministic() {
        assertFalse(keys.header.contentEquals(keys.body))
        assertFalse(keys.body.contentEquals(keys.path))
        assertFalse(keys.header.contentEquals(keys.path))
        assertArrayEquals(keys.path, StegoKeys.derive(containerKey).path)
        assertFalse(keys.path.contentEquals(StegoKeys.derive(ByteArray(32) { 1 }).path))
        assertThrows(IllegalArgumentException::class.java) { StegoKeys.derive(ByteArray(16)) }
    }

    @Test
    fun theStreamLooksLikeRandomNoise() {
        // Highly regular plaintext must not show through: no magic bytes, balanced bits.
        val stream = StegoCrypto.seal(ByteArray(8000), keys)
        val ones = stream.sumOf { Integer.bitCount(it.toInt() and 0xFF) }
        val ratio = ones.toDouble() / (stream.size * 8)
        assertTrue("one-bit ratio $ratio", ratio in 0.48..0.52)
        // No byte value may dominate.
        val histogram = IntArray(256).also { h -> stream.forEach { h[it.toInt() and 0xFF]++ } }
        assertTrue("most common byte appears ${histogram.max()} times", histogram.max() < 70)
    }

    @Test
    fun twoSealsOfTheSameDataDiffer() {
        val container = random(100)
        assertFalse(StegoCrypto.seal(container, keys).contentEquals(StegoCrypto.seal(container, keys)))
    }
}
