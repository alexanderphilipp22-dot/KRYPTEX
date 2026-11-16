package com.kryptex.vault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class KdfCalibratorTest {
    private val clock = AtomicLong(0)

    /** A KDF whose cost is simulated by advancing the fake clock. */
    private inner class TimedKdf(
        private val millisPerPass: Long,
        private val failingMemory: Set<Int> = emptySet(),
    ) : Argon2Kdf {
        val attemptedMemory = mutableListOf<Int>()

        override fun derive(password: ByteArray, salt: ByteArray, params: Argon2Params, outputLength: Int): ByteArray {
            attemptedMemory += params.memoryKiB
            if (params.memoryKiB in failingMemory) throw IllegalStateException("cannot allocate")
            clock.addAndGet(millisPerPass * params.iterations * 1_000_000)
            return ByteArray(outputLength)
        }
    }

    private fun calibrator(kdf: Argon2Kdf) = KdfCalibrator(kdf) { clock.get() }

    private val plenty = 1024 * 1024

    @Test
    fun fastDevice_getsLargestMemoryAndMorePasses() {
        val params = calibrator(TimedKdf(millisPerPass = 100)).calibrate(plenty, parallelism = 2)
        assertEquals(Argon2Params(256 * 1024, 10, 2), params)
    }

    @Test
    fun slowDevice_stillGetsTheFloorOfThreePasses() {
        val params = calibrator(TimedKdf(millisPerPass = 800)).calibrate(plenty, parallelism = 1)
        assertEquals(Argon2Params.MIN_ITERATIONS, params.iterations)
    }

    @Test
    fun veryFastDevice_isCappedSoSetupCannotRunAway() {
        val params = calibrator(TimedKdf(millisPerPass = 1)).calibrate(plenty, parallelism = 1)
        assertEquals(KdfCalibrator.MAX_CALIBRATED_ITERATIONS, params.iterations)
    }

    @Test
    fun memoryBudgetSelectsTheTier() {
        assertEquals(128 * 1024, calibrator(TimedKdf(100)).calibrate(200 * 1024, parallelism = 1).memoryKiB)
        assertEquals(64 * 1024, calibrator(TimedKdf(100)).calibrate(100 * 1024, parallelism = 1).memoryKiB)
    }

    @Test
    fun budgetBelowTheFloorStillUsesSixtyFourMiB() {
        assertEquals(64 * 1024, calibrator(TimedKdf(100)).calibrate(10 * 1024, parallelism = 1).memoryKiB)
    }

    @Test
    fun allocationFailureFallsBackToTheNextSmallerTier() {
        val kdf = TimedKdf(100, failingMemory = setOf(256 * 1024))
        val params = calibrator(kdf).calibrate(plenty, parallelism = 1)
        assertEquals(128 * 1024, params.memoryKiB)
        assertEquals(listOf(256 * 1024, 128 * 1024), kdf.attemptedMemory)
    }

    @Test
    fun failureAtEveryTierIsReportedNotSwallowed() {
        val kdf = TimedKdf(100, failingMemory = setOf(256 * 1024, 128 * 1024, 64 * 1024))
        assertThrows(IllegalStateException::class.java) { calibrator(kdf).calibrate(plenty) }
    }
}
