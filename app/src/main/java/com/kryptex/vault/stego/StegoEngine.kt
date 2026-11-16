package com.kryptex.vault.stego

import com.kryptex.vault.crypto.wipe

/**
 * Hides an encrypted [PayloadContainer] in the pixels of an image and finds it again. Works on
 * plain ARGB `IntArray`s (see `CarrierBitmap` for the Android side), entirely in memory.
 *
 * The pixel array must be opaque (alpha 255): translucent pixels would be premultiplied by
 * Android and silently corrupt the low bits.
 */
class StegoEngine {

    /**
     * Embeds [container] into [pixels] **in place**; pass a working copy. The container is sealed
     * with keys derived from [containerKey] (32 random bytes kept in the encrypted database).
     * If it does not fit, [pixels] is left untouched.
     */
    fun embed(
        pixels: IntArray,
        width: Int,
        height: Int,
        container: PayloadContainer,
        containerKey: ByteArray,
        compress: Boolean = true,
    ): EmbedOutcome {
        require(pixels.size == width * height) { "pixel array does not match ${width}x$height" }

        val containerBytes = container.encode(compress)
        val streamBytes = StegoCrypto.streamBytes(containerBytes.size)
        val capacity = CapacityCalculator.maxStreamBytes(width, height)
        if (streamBytes > capacity || LsbCodec.slotCount(pixels.size) < MIN_SLOTS) {
            containerBytes.wipe()
            return EmbedOutcome.TooLarge(streamBytes, capacity)
        }

        val keys = StegoKeys.derive(containerKey)
        try {
            val stream = StegoCrypto.seal(containerBytes, keys)
            val permutation = FeistelPermutation(LsbCodec.slotCount(pixels.size), keys.path)
            LsbCodec.embed(
                pixels = pixels,
                bits = stream,
                bitCount = stream.size * 8,
                startBit = 0,
                permutation = permutation,
                noise = NoiseSource(noiseSeed(keys.path)),
            )
            return EmbedOutcome.Embedded(streamBytes, capacity)
        } finally {
            keys.wipe()
            containerBytes.wipe()
        }
    }

    /** Looks for a payload sealed with [containerKey]. Never modifies [pixels]. */
    fun extract(pixels: IntArray, width: Int, height: Int, containerKey: ByteArray): ExtractOutcome {
        require(pixels.size == width * height) { "pixel array does not match ${width}x$height" }
        val slots = LsbCodec.slotCount(pixels.size)
        if (slots < MIN_SLOTS || slots < StegoCrypto.HEADER_BYTES * 8L) return ExtractOutcome.NothingFound

        val keys = StegoKeys.derive(containerKey)
        try {
            val permutation = FeistelPermutation(slots, keys.path)

            val header = LsbCodec.read(pixels, StegoCrypto.HEADER_BYTES * 8, 0, permutation)
            val bodyLength = StegoCrypto.openHeader(header, keys) ?: return ExtractOutcome.NothingFound

            // An authentic header can still lie about size if the image was crafted; stay inside the cap.
            val streamBytes = StegoCrypto.HEADER_BYTES.toLong() + bodyLength
            if (streamBytes > CapacityCalculator.maxStreamBytes(width, height)) return ExtractOutcome.NothingFound

            val body = LsbCodec.read(pixels, bodyLength * 8, StegoCrypto.HEADER_BYTES * 8L, permutation)
            val containerBytes = StegoCrypto.openBody(body, header, keys) ?: return ExtractOutcome.NothingFound
            return try {
                ExtractOutcome.Found(PayloadContainer.decode(containerBytes))
            } catch (_: MalformedContainerException) {
                ExtractOutcome.NothingFound
            } finally {
                containerBytes.wipe()
            }
        } finally {
            keys.wipe()
        }
    }

    private fun noiseSeed(pathKey: ByteArray): Long {
        var seed = 0L
        for (i in 0 until 8) seed = (seed shl 8) or (pathKey[i].toLong() and 0xFF)
        return seed
    }

    private companion object {
        /** Below this the permutation domain degenerates; no real image is this small. */
        const val MIN_SLOTS = 8L * StegoCrypto.HEADER_BYTES
    }
}
