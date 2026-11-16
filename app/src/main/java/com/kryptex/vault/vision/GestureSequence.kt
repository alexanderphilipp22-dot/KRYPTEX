package com.kryptex.vault.vision

import kotlin.math.log2

/**
 * An ordered list of gestures that acts as a quick-unlock secret.
 *
 * It is a secret *performed* by the user, not a biometric template: anyone who learns the order
 * can repeat it. Length is therefore the only strength lever, see [entropyBits].
 *
 * The optional face check ([FaceGeometry]) is a gate on top of this, not part of the key: the key
 * comes from the sequence alone, so the face check adds no entropy and no cryptographic strength.
 */
class GestureSequence(val gestures: List<Gesture>) {
    init {
        require(gestures.size in MIN_STEPS..MAX_STEPS) {
            "a gesture sequence needs $MIN_STEPS..$MAX_STEPS gestures, got ${gestures.size}"
        }
    }

    val size: Int get() = gestures.size

    /** Upper bound of the guessing entropy: every position drawn uniformly from the vocabulary. */
    val entropyBits: Double get() = size * log2(Gesture.entries.size.toDouble())

    /**
     * Canonical secret fed to the key-derivation function: `version | length | id...`.
     * Built from stable gesture ids, never from enum names or ordinals.
     */
    fun toSecret(): ByteArray {
        val out = ByteArray(2 + gestures.size)
        out[0] = ENCODING_VERSION
        out[1] = gestures.size.toByte()
        gestures.forEachIndexed { i, g -> out[2 + i] = g.id.toByte() }
        return out
    }

    override fun equals(other: Any?): Boolean = other is GestureSequence && gestures == other.gestures

    override fun hashCode(): Int = gestures.hashCode()

    // The content is a secret: keep it out of logs and crash reports.
    override fun toString(): String = "GestureSequence(size=$size)"

    companion object {
        const val MIN_STEPS = 3
        const val MAX_STEPS = 12
        const val DEFAULT_STEPS = 6
        private const val ENCODING_VERSION: Byte = 1
    }
}
