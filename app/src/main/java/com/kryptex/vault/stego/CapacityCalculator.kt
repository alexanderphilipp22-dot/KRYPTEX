package com.kryptex.vault.stego

/**
 * How much fits in a carrier. Capacity is capped at half of the theoretical maximum: the fewer
 * pixels are touched, the harder the change is to detect statistically.
 */
object CapacityCalculator {
    /** Carriers above this are downscaled when they are loaded, to bound memory use. */
    const val MAX_CARRIER_PIXELS = 12_000_000

    /** Share of the 3 bits per pixel that may be used. */
    const val MAX_FILL = 0.5

    /** Container fixed fields: header, timestamp, lengths. Names and MIME types add to it. */
    private const val CONTAINER_FIXED_BYTES = 4 + 8 + 2 + 2 + 4 + 4

    /** Everything three channels could carry, as bytes. */
    fun theoreticalBytes(width: Int, height: Int): Long = 3L * width * height / 8

    /** Largest hidden stream (header + sealed container) the app will write into this carrier. */
    fun maxStreamBytes(width: Int, height: Int): Long = (theoreticalBytes(width, height) * MAX_FILL).toLong()

    /**
     * Largest payload data size, before compression, for an item with the given [name] and
     * [mimeType]. Compression can only make more fit.
     */
    fun maxPayloadBytes(width: Int, height: Int, name: String = "", mimeType: String = ""): Long {
        val overhead = StegoCrypto.HEADER_BYTES + StegoCrypto.BODY_OVERHEAD_BYTES + CONTAINER_FIXED_BYTES +
            name.toByteArray(Charsets.UTF_8).size + mimeType.toByteArray(Charsets.UTF_8).size
        return (maxStreamBytes(width, height) - overhead).coerceAtLeast(0)
    }

    fun fits(width: Int, height: Int, containerBytes: Int): Boolean =
        StegoCrypto.streamBytes(containerBytes) <= maxStreamBytes(width, height)

    /** Fraction of the carrier's capacity that a stream of [containerBytes] would use, 0..1+. */
    fun usage(width: Int, height: Int, containerBytes: Int): Double {
        val max = maxStreamBytes(width, height)
        return if (max == 0L) Double.POSITIVE_INFINITY else StegoCrypto.streamBytes(containerBytes).toDouble() / max
    }
}
