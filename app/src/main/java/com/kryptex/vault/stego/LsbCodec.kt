package com.kryptex.vault.stego

/**
 * Deterministic bit source for the +1/-1 choice of LSB matching. It only has to look random, so a
 * SplitMix64 stream seeded from the key is enough. Extraction never needs it.
 */
class NoiseSource(seed: Long) {
    private var state = seed
    private var buffer = 0L
    private var remaining = 0

    fun nextBit(): Boolean {
        if (remaining == 0) {
            state += GOLDEN_GAMMA
            var z = state
            z = (z xor (z ushr 30)) * MULTIPLIER_1
            z = (z xor (z ushr 27)) * MULTIPLIER_2
            buffer = z xor (z ushr 31)
            remaining = 64
        }
        val bit = (buffer and 1L) != 0L
        buffer = buffer ushr 1
        remaining--
        return bit
    }

    private companion object {
        val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MULTIPLIER_1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MULTIPLIER_2 = 0x94D049BB133111EBuL.toLong()
    }
}

/**
 * LSB steganography over ARGB pixels.
 *
 * Every pixel offers three slots (R, G, B); the alpha channel is never touched. Slot `s` is
 * channel `s % 3` of pixel `s / 3`. Embedded bit `i` goes to slot `permutation.permute(i)`.
 *
 * Embedding uses LSB *matching*: when a channel's low bit differs from the wanted bit, the value
 * is changed by +1 or -1 at random instead of having its low bit overwritten. Replacement makes
 * pairs of values (2k, 2k+1) equally frequent, which is the classic fingerprint of naive LSB tools.
 *
 * Bits are packed most-significant-bit first inside each byte.
 */
object LsbCodec {
    const val SLOTS_PER_PIXEL = 3

    fun slotCount(pixelCount: Int): Long = SLOTS_PER_PIXEL.toLong() * pixelCount

    /**
     * Writes the first [bitCount] bits of [bits] into [pixels], starting at stream bit [startBit].
     * Modifies [pixels] in place; every touched channel changes by at most 1.
     */
    fun embed(
        pixels: IntArray,
        bits: ByteArray,
        bitCount: Int,
        startBit: Long,
        permutation: FeistelPermutation,
        noise: NoiseSource,
    ) {
        require(bitCount in 0..bits.size * 8) { "bitCount exceeds the data" }
        require(startBit >= 0 && startBit + bitCount <= permutation.domainSize) { "stream does not fit in the image" }
        require(permutation.domainSize == slotCount(pixels.size)) { "permutation does not match the image" }

        for (i in 0 until bitCount) {
            val want = (bits[i ushr 3].toInt() ushr (7 - (i and 7))) and 1
            val slot = permutation.permute(startBit + i)
            val pixelIndex = (slot / SLOTS_PER_PIXEL).toInt()
            val shift = shiftOf((slot % SLOTS_PER_PIXEL).toInt())

            val pixel = pixels[pixelIndex]
            val value = (pixel ushr shift) and 0xFF
            if ((value and 1) == want) continue

            val changed = when {
                value == 0 -> 1
                value == 255 -> 254
                noise.nextBit() -> value + 1
                else -> value - 1
            }
            pixels[pixelIndex] = (pixel and (0xFF shl shift).inv()) or (changed shl shift)
        }
    }

    /** Reads [bitCount] bits starting at stream bit [startBit]. Trailing bits of the last byte are zero. */
    fun read(
        pixels: IntArray,
        bitCount: Int,
        startBit: Long,
        permutation: FeistelPermutation,
    ): ByteArray {
        require(bitCount >= 0) { "negative bit count" }
        require(startBit >= 0 && startBit + bitCount <= permutation.domainSize) { "stream does not fit in the image" }
        require(permutation.domainSize == slotCount(pixels.size)) { "permutation does not match the image" }

        val out = ByteArray((bitCount + 7) ushr 3)
        for (i in 0 until bitCount) {
            val slot = permutation.permute(startBit + i)
            val pixelIndex = (slot / SLOTS_PER_PIXEL).toInt()
            val shift = shiftOf((slot % SLOTS_PER_PIXEL).toInt())
            val bit = ((pixels[pixelIndex] ushr shift) and 1)
            if (bit != 0) out[i ushr 3] = (out[i ushr 3].toInt() or (0x80 ushr (i and 7))).toByte()
        }
        return out
    }

    /** R = bits 16..23, G = 8..15, B = 0..7 of an ARGB int. */
    private fun shiftOf(channel: Int): Int = 16 - 8 * channel
}
