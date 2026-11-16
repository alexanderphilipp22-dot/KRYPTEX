package com.kryptex.vault.stego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class FeistelPermutationTest {
    private val key = ByteArray(32) { (it * 5 + 3).toByte() }

    @Test
    fun isABijectionForAwkwardDomainSizes() {
        // Sizes that are not powers of two (cycle-walking), tiny, and just around a power of four.
        for (n in longArrayOf(2, 3, 7, 100, 1000, 4096, 4097, 5000, 65_537, 300_001)) {
            val permutation = FeistelPermutation(n, key)
            val seen = BooleanArray(n.toInt())
            for (i in 0 until n) {
                val slot = permutation.permute(i)
                assertTrue("slot $slot out of range for n=$n", slot in 0 until n)
                assertFalse("slot $slot produced twice for n=$n", seen[slot.toInt()])
                seen[slot.toInt()] = true
            }
        }
    }

    @Test
    fun invertUndoesPermute() {
        val n = 36_000_000L // a 12 MP carrier has 36 M slots
        val permutation = FeistelPermutation(n, key)
        val random = Random(1)
        repeat(20_000) {
            val i = (random.nextLong() and Long.MAX_VALUE) % n
            assertEquals(i, permutation.invert(permutation.permute(i)))
        }
    }

    @Test
    fun sameKeyGivesTheSameMappingAndAnotherKeyAnotherOne() {
        val n = 1_000_000L
        val a = FeistelPermutation(n, key)
        val b = FeistelPermutation(n, key.copyOf())
        val other = FeistelPermutation(n, ByteArray(32) { 9 })

        var differing = 0
        for (i in 0 until 1000L) {
            assertEquals(a.permute(i), b.permute(i))
            if (a.permute(i) != other.permute(i)) differing++
        }
        assertTrue("another key must give a different mapping, only $differing of 1000 differ", differing > 990)
    }

    @Test
    fun consecutiveBitsLandFarApartAcrossTheWholeImage() {
        val n = 3_000_000L
        val permutation = FeistelPermutation(n, key)
        val outputs = LongArray(10_000) { permutation.permute(it.toLong()) }

        assertEquals("slots must be distinct", outputs.size, outputs.toSet().size)

        val quartiles = IntArray(4)
        outputs.forEach { quartiles[(it * 4 / n).toInt()]++ }
        quartiles.forEach { assertTrue("quartile counts ${quartiles.toList()}", it in 2200..2800) }

        val adjacent = (1 until outputs.size).count { kotlin.math.abs(outputs[it] - outputs[it - 1]) < 100 }
        assertTrue("$adjacent consecutive bits landed next to each other", adjacent < 20)
    }

    @Test
    fun rejectsBadArguments() {
        assertThrows(IllegalArgumentException::class.java) { FeistelPermutation(1, key) }
        assertThrows(IllegalArgumentException::class.java) { FeistelPermutation(10, key, rounds = 2) }
        val p = FeistelPermutation(10, key)
        assertThrows(IllegalArgumentException::class.java) { p.permute(10) }
        assertThrows(IllegalArgumentException::class.java) { p.permute(-1) }
        assertThrows(IllegalArgumentException::class.java) { p.invert(10) }
    }
}
