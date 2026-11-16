package com.kryptex.vault.crypto

/**
 * Argon2id cost parameters. Stored per key slot in the vault header so they can be raised later
 * without breaking existing slots.
 */
data class Argon2Params(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    init {
        require(parallelism in 1..MAX_PARALLELISM) { "parallelism out of range: $parallelism" }
        require(iterations in 1..MAX_ITERATIONS) { "iterations out of range: $iterations" }
        require(memoryKiB in (8 * parallelism)..MAX_MEMORY_KIB) { "memory out of range: $memoryKiB KiB" }
    }

    companion object {
        /** Floor enforced by the calibrator when creating slots: 64 MiB, 3 passes. */
        const val MIN_MEMORY_KIB = 64 * 1024
        const val MIN_ITERATIONS = 3

        // Upper bounds exist so a corrupted header cannot make the app allocate gigabytes.
        const val MAX_MEMORY_KIB = 1024 * 1024
        const val MAX_ITERATIONS = 64
        const val MAX_PARALLELISM = 16
    }
}
