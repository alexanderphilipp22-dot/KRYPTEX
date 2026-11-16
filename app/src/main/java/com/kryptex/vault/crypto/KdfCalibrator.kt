package com.kryptex.vault.crypto

import kotlin.math.roundToInt

/**
 * Picks Argon2id parameters for this device at vault-creation time: the largest memory tier
 * that fits the budget, then as many passes as fit in roughly [targetMillis].
 *
 * Floor: 64 MiB and 3 passes, even on devices that are slower than the target.
 */
class KdfCalibrator(
    private val kdf: Argon2Kdf,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun calibrate(
        maxMemoryKiB: Int,
        targetMillis: Long = DEFAULT_TARGET_MILLIS,
        parallelism: Int = defaultParallelism(),
    ): Argon2Params {
        val tiers = MEMORY_TIERS_KIB.filter { it <= maxMemoryKiB }
            .ifEmpty { listOf(Argon2Params.MIN_MEMORY_KIB) }

        var lastError: Throwable? = null
        for (memory in tiers) {
            try {
                return calibrateAt(memory, targetMillis, parallelism)
            } catch (e: Exception) {
                lastError = e // allocation failure at this tier: try the next smaller one
            } catch (e: OutOfMemoryError) {
                lastError = e
            }
            // Linker errors and other Errors are real faults and propagate.
        }
        throw IllegalStateException("Argon2id cannot run even at ${tiers.last() / 1024} MiB", lastError)
    }

    private fun calibrateAt(memoryKiB: Int, targetMillis: Long, parallelism: Int): Argon2Params {
        val probe = Argon2Params(memoryKiB, Argon2Params.MIN_ITERATIONS, parallelism)
        val password = ByteArray(16) { it.toByte() }
        val salt = ByteArray(16) { (it + 100).toByte() }

        val start = nanoTime()
        kdf.derive(password, salt, probe).wipe()
        val elapsedMillis = (nanoTime() - start) / 1_000_000.0

        val perPassMillis = (elapsedMillis / probe.iterations).coerceAtLeast(1.0)
        val iterations = (targetMillis / perPassMillis).roundToInt()
            .coerceIn(Argon2Params.MIN_ITERATIONS, MAX_CALIBRATED_ITERATIONS)
        return Argon2Params(memoryKiB, iterations, parallelism)
    }

    companion object {
        const val DEFAULT_TARGET_MILLIS = 1000L
        const val MAX_CALIBRATED_ITERATIONS = 24

        /** Largest first: 256, 128, 64 MiB. */
        val MEMORY_TIERS_KIB = listOf(256 * 1024, 128 * 1024, 64 * 1024)

        fun defaultParallelism(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
