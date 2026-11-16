package com.kryptex.vault.crypto

/**
 * Limits consecutive failed gesture unlocks. The counter lives in the header and is persisted
 * *before* an attempt is evaluated, so killing the app mid-attempt cannot buy a free guess.
 *
 * This is enforced by app code: an attacker with root and an unlocked bootloader can edit the
 * header. The device-bound Keystore layer and Argon2id cost remain as defence in depth.
 */
class AttemptLimiter(
    private val store: VaultHeaderStore,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    fun isLocked(header: VaultHeader): Boolean = header.failedGestureAttempts >= maxAttempts

    fun remaining(header: VaultHeader): Int = (maxAttempts - header.failedGestureAttempts).coerceAtLeast(0)

    /** Records an attempt and persists it. @return the header as persisted, counter already incremented. */
    fun registerAttempt(): VaultHeader {
        val header = checkNotNull(store.read()) { "no vault header" }
        check(!isLocked(header)) { "gesture slot is locked" }
        return header.copy(failedGestureAttempts = header.failedGestureAttempts + 1).also(store::write)
    }

    /** Clears the counter after a successful unlock (gesture or passphrase). */
    fun reset(): VaultHeader {
        val header = checkNotNull(store.read()) { "no vault header" }
        if (header.failedGestureAttempts == 0) return header
        return header.copy(failedGestureAttempts = 0).also(store::write)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}
