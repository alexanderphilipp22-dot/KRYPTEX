package com.kryptex.vault.stego

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LsbCodecTest {
    private val key = ByteArray(32) { (it + 11).toByte() }

    private fun photo(width: Int, height: Int, seed: Long = 7): IntArray {
        val random = Random(seed)
        return IntArray(width * height) { i ->
            val x = i % width
            val r = (x * 255 / width + random.nextInt(12)).coerceIn(0, 255)
            val g = (i / width * 255 / height + random.nextInt(12)).coerceIn(0, 255)
            val b = random.nextInt(256)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun randomBytes(n: Int, seed: Long = 3) = ByteArray(n).also { Random(seed).nextBytes(it) }

    private fun permutationFor(pixels: IntArray) = FeistelPermutation(LsbCodec.slotCount(pixels.size), key)

    @Test
    fun embeddedBitsReadBackExactly() {
        val pixels = photo(200, 150)
        val data = randomBytes(1200)
        val permutation = permutationFor(pixels)

        LsbCodec.embed(pixels, data, data.size * 8, 0, permutation, NoiseSource(42))

        assertArrayEquals(data, LsbCodec.read(pixels, data.size * 8, 0, permutation))
    }

    @Test
    fun anyRangeOfTheStreamCanBeReadSeparately() {
        val pixels = photo(120, 90)
        val data = randomBytes(300)
        val permutation = permutationFor(pixels)
        LsbCodec.embed(pixels, data, data.size * 8, 0, permutation, NoiseSource(1))

        // Bytes 8..24 are stream bits 64..192.
        assertArrayEquals(data.copyOfRange(8, 24), LsbCodec.read(pixels, 128, 64, permutation))
    }

    @Test
    fun everyChannelChangesByAtMostOneAndAlphaIsNeverTouched() {
        val original = photo(160, 120)
        val pixels = original.copyOf()
        val data = randomBytes(2000)
        LsbCodec.embed(pixels, data, data.size * 8, 0, permutationFor(pixels), NoiseSource(5))

        var changedChannels = 0
        for (i in pixels.indices) {
            assertEquals("alpha changed at pixel $i", 0xFF, pixels[i] ushr 24)
            for (shift in intArrayOf(16, 8, 0)) {
                val delta = ((pixels[i] ushr shift) and 0xFF) - ((original[i] ushr shift) and 0xFF)
                assertTrue("delta $delta at pixel $i", delta in -1..1)
                if (delta != 0) changedChannels++
            }
        }
        // Random data matches the existing low bit half of the time, so about half the bits change a channel.
        val bits = data.size * 8
        assertTrue("changed $changedChannels of $bits embedded bits", changedChannels in (bits * 0.45).toInt()..(bits * 0.55).toInt())
    }

    @Test
    fun blackAndWhiteImagesNeverOverflow() {
        for (value in intArrayOf(0x000000, 0xFFFFFF)) {
            val pixels = IntArray(100 * 100) { (0xFF shl 24) or value }
            val data = randomBytes(500)
            val permutation = permutationFor(pixels)

            LsbCodec.embed(pixels, data, data.size * 8, 0, permutation, NoiseSource(9))

            assertArrayEquals(data, LsbCodec.read(pixels, data.size * 8, 0, permutation))
            val allowed = if (value == 0) setOf(0, 1) else setOf(254, 255)
            for (p in pixels) {
                for (shift in intArrayOf(16, 8, 0)) {
                    assertTrue("channel left $allowed", ((p ushr shift) and 0xFF) in allowed)
                }
            }
        }
    }

    @Test
    fun embeddingIsDeterministicForTheSameInputs() {
        val a = photo(100, 100)
        val b = a.copyOf()
        val data = randomBytes(400)
        LsbCodec.embed(a, data, data.size * 8, 0, permutationFor(a), NoiseSource(77))
        LsbCodec.embed(b, data, data.size * 8, 0, permutationFor(b), NoiseSource(77))
        assertArrayEquals(a, b)
    }

    @Test
    fun partialBitCountsAreSupported() {
        val pixels = photo(60, 60)
        val permutation = permutationFor(pixels)
        val data = byteArrayOf(0b1011_0000.toByte())
        LsbCodec.embed(pixels, data, bitCount = 4, startBit = 0, permutation = permutation, noise = NoiseSource(2))
        assertArrayEquals(byteArrayOf(0b1011_0000.toByte()), LsbCodec.read(pixels, 4, 0, permutation))
    }

    @Test
    fun refusesToWritePastTheEndOrWithTheWrongPermutation() {
        val pixels = photo(10, 10) // 300 slots
        val permutation = permutationFor(pixels)
        assertThrows(IllegalArgumentException::class.java) {
            LsbCodec.embed(pixels, ByteArray(40), 320, 0, permutation, NoiseSource(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LsbCodec.read(pixels, 8, 296, permutation)
        }
        val wrongSize = FeistelPermutation(299, key)
        assertThrows(IllegalArgumentException::class.java) {
            LsbCodec.embed(pixels, ByteArray(1), 8, 0, wrongSize, NoiseSource(1))
        }
    }
}
