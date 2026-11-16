package com.kryptex.vault.stego

import com.kryptex.vault.crypto.Hkdf
import java.nio.ByteBuffer

/**
 * A keyed pseudo-random permutation of `0 until domainSize`, computed one index at a time in O(1)
 * memory (a Feistel network over the next even power of two, with cycle-walking back into range).
 *
 * It decides *where* in the image each embedded bit goes, so the changed pixels are spread over
 * the whole picture instead of filling its top rows. The payload's confidentiality and integrity
 * come from AES-GCM, not from this permutation; the round function is therefore a fast keyed
 * integer mixer (SplitMix64 finaliser) and not a cipher, to keep embedding of millions of bits
 * well under a second.
 */
class FeistelPermutation(val domainSize: Long, key: ByteArray, private val rounds: Int = DEFAULT_ROUNDS) {
    private val halfBits: Int
    private val halfMask: Long
    private val roundKeys: LongArray

    init {
        require(domainSize in 2..MAX_DOMAIN) { "domain size out of range: $domainSize" }
        require(rounds >= 3) { "a Feistel network needs at least 3 rounds" }

        var bits = 64 - java.lang.Long.numberOfLeadingZeros(domainSize - 1) // bits to hold domainSize-1
        if (bits % 2 == 1) bits++
        halfBits = bits / 2
        halfMask = (1L shl halfBits) - 1

        val material = Hkdf.derive(key, null, "kryptex.stego.v1.feistel", rounds * 8)
        val buffer = ByteBuffer.wrap(material)
        roundKeys = LongArray(rounds) { buffer.long }
        material.fill(0)
    }

    /** Maps [index] to its slot. A bijection on `0 until domainSize`. */
    fun permute(index: Long): Long {
        require(index in 0 until domainSize) { "index out of range: $index" }
        var x = index
        do {
            x = encrypt(x)
        } while (x >= domainSize) // cycle-walk until back inside the domain
        return x
    }

    /** Inverse of [permute]. */
    fun invert(slot: Long): Long {
        require(slot in 0 until domainSize) { "slot out of range: $slot" }
        var x = slot
        do {
            x = decrypt(x)
        } while (x >= domainSize)
        return x
    }

    private fun encrypt(x: Long): Long {
        var left = x ushr halfBits
        var right = x and halfMask
        for (i in 0 until rounds) {
            val next = left xor (mix(right, roundKeys[i]) and halfMask)
            left = right
            right = next
        }
        return (left shl halfBits) or right
    }

    private fun decrypt(x: Long): Long {
        var left = x ushr halfBits
        var right = x and halfMask
        for (i in rounds - 1 downTo 0) {
            val previousRight = left
            val previousLeft = right xor (mix(left, roundKeys[i]) and halfMask)
            left = previousLeft
            right = previousRight
        }
        return (left shl halfBits) or right
    }

    private fun mix(value: Long, roundKey: Long): Long {
        var z = value + roundKey
        z = (z xor (z ushr 30)) * MULTIPLIER_1
        z = (z xor (z ushr 27)) * MULTIPLIER_2
        return z xor (z ushr 31)
    }

    companion object {
        const val DEFAULT_ROUNDS = 4
        const val MAX_DOMAIN = 1L shl 40
        private val MULTIPLIER_1 = 0xBF58476D1CE4E5B9uL.toLong()
        private val MULTIPLIER_2 = 0x94D049BB133111EBuL.toLong()
    }
}
