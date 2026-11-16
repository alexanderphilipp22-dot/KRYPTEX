package com.kryptex.vault.crypto

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** Outcome of an unlock attempt. [Success.dek] is owned by the receiver, who must wipe it or hand it to a session. */
sealed interface UnlockResult {
    class Success(val dek: ByteArray) : UnlockResult

    /** @param attemptsRemaining only set for gestures: tries left before the gesture slot locks. */
    data class WrongCredential(val attemptsRemaining: Int? = null) : UnlockResult

    /** Too many failed gestures; unlock with the passphrase to re-enable. */
    data object GestureLocked : UnlockResult

    data object GestureNotEnrolled : UnlockResult

    /** The device-bound key is gone (factory reset, restore to a new phone). Passphrase still works. */
    data object GestureUnavailable : UnlockResult

    data object NoVault : UnlockResult

    data object Corrupt : UnlockResult
}

/** What the lock screen needs to know about the gesture slot without unlocking anything. */
sealed interface GestureSlotInfo {
    data object NotEnrolled : GestureSlotInfo
    data class Ready(val steps: Int, val attemptsRemaining: Int) : GestureSlotInfo
    data object Locked : GestureSlotInfo
}

/** Read-only facts about how the vault is protected, for the dashboard's security card. */
data class SecuritySummary(
    val createdAtEpochMs: Long,
    val passphraseParams: Argon2Params,
    val gestureEnrolled: Boolean,
    val gestureSteps: Int?,
    val gestureLocked: Boolean,
    val gestureAttemptsRemaining: Int?,
)

/**
 * Owns the key hierarchy:
 * ```
 * passphrase --Argon2id--> KEK_p --AES-GCM--> DEK
 * gesture    --Argon2id--> K_g --AES-GCM--> (Keystore wrap) --> DEK
 * ```
 * The DEK (32 random bytes) is created once, never stored in the clear, and is the root of the
 * SQLCipher key. Every method that runs Argon2id blocks for about a second - call from a
 * background dispatcher.
 */
class VaultKeyManager(
    private val headerStore: VaultHeaderStore,
    private val kdf: Argon2Kdf,
    private val deviceKey: DeviceKeyWrapper,
    private val limiter: AttemptLimiter,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun vaultExists(): Boolean = headerStore.exists()

    fun gestureSlotInfo(): GestureSlotInfo {
        val header = try {
            headerStore.read()
        } catch (_: CorruptHeaderException) {
            null
        } ?: return GestureSlotInfo.NotEnrolled
        val slot = header.gestureSlot ?: return GestureSlotInfo.NotEnrolled
        return if (limiter.isLocked(header)) {
            GestureSlotInfo.Locked
        } else {
            GestureSlotInfo.Ready(slot.steps, limiter.remaining(header))
        }
    }

    /** @return null if there is no readable vault header. */
    fun securitySummary(): SecuritySummary? {
        val header = try {
            headerStore.read()
        } catch (_: CorruptHeaderException) {
            null
        } ?: return null
        val gesture = header.gestureSlot
        return SecuritySummary(
            createdAtEpochMs = header.createdAtEpochMs,
            passphraseParams = header.passphraseSlot.params,
            gestureEnrolled = gesture != null,
            gestureSteps = gesture?.steps,
            gestureLocked = gesture != null && limiter.isLocked(header),
            gestureAttemptsRemaining = gesture?.let { limiter.remaining(header) },
        )
    }

    /** Creates a new vault protected by [passphrase]. @return the fresh DEK (caller-owned). */
    @Synchronized
    fun createVault(passphrase: CharArray, params: Argon2Params): ByteArray {
        check(!headerStore.exists()) { "a vault already exists" }
        val dek = randomBytes(DEK_BYTES)
        try {
            val slot = wrapPassphraseSlot(passphrase, params, dek)
            headerStore.write(
                VaultHeader(
                    createdAtEpochMs = clock(),
                    passphraseSlot = slot,
                    gestureSlot = null,
                    failedGestureAttempts = 0,
                ),
            )
        } catch (t: Throwable) {
            dek.wipe()
            throw t
        }
        return dek
    }

    @Synchronized
    fun unlockWithPassphrase(passphrase: CharArray): UnlockResult {
        val header = when (val read = readHeader()) {
            is HeaderRead.Ok -> read.header
            HeaderRead.Missing -> return UnlockResult.NoVault
            HeaderRead.Corrupt -> return UnlockResult.Corrupt
        }
        val slot = header.passphraseSlot
        val password = passphrase.toKeyMaterial()
        val kek = try {
            kdf.derive(password, slot.salt, slot.params)
        } finally {
            password.wipe()
        }
        val dek = try {
            AesGcm.openOrNull(kek, slot.wrappedDek, slotAad(SLOT_PASSPHRASE, slot.params, slot.salt))
        } finally {
            kek.wipe()
        } ?: return UnlockResult.WrongCredential()

        // The passphrase is the recovery path for a locked gesture slot.
        if (header.failedGestureAttempts != 0) limiter.reset()
        return UnlockResult.Success(dek)
    }

    /** [gestureSecret] is the canonical byte encoding of the gesture sequence (see the vision package). */
    @Synchronized
    fun unlockWithGesture(gestureSecret: ByteArray): UnlockResult {
        val header = when (val read = readHeader()) {
            is HeaderRead.Ok -> read.header
            HeaderRead.Missing -> return UnlockResult.NoVault
            HeaderRead.Corrupt -> return UnlockResult.Corrupt
        }
        val slot = header.gestureSlot ?: return UnlockResult.GestureNotEnrolled
        if (limiter.isLocked(header)) return UnlockResult.GestureLocked

        // Persist the attempt BEFORE evaluating it.
        val afterIncrement = limiter.registerAttempt()
        val aad = gestureAad(slot)

        val inner = try {
            deviceKey.unwrap(slot.wrappedDek, aad)
        } catch (_: GeneralSecurityException) {
            return UnlockResult.GestureUnavailable
        }

        val kg = try {
            kdf.derive(gestureSecret, slot.salt, slot.params)
        } catch (t: Throwable) {
            inner.wipe()
            throw t
        }
        val dek = try {
            AesGcm.openOrNull(kg, inner, aad)
        } finally {
            kg.wipe()
            inner.wipe()
        } ?: return UnlockResult.WrongCredential(limiter.remaining(afterIncrement))

        limiter.reset()
        return UnlockResult.Success(dek)
    }

    /** Adds or replaces the gesture slot. Needs the DEK, so the vault must be unlocked. */
    @Synchronized
    fun enrollGesture(dek: ByteArray, gestureSecret: ByteArray, steps: Int, params: Argon2Params) {
        require(steps in MIN_GESTURE_STEPS..MAX_GESTURE_STEPS) { "gesture length out of range" }
        val header = requireHeader()
        val salt = randomBytes(SALT_BYTES)
        val kg = kdf.derive(gestureSecret, salt, params)
        try {
            val draft = GestureSlot(steps, params, salt, ByteArray(0))
            val aad = gestureAad(draft)
            val inner = AesGcm.seal(kg, dek, aad, random)
            val wrapped = try {
                deviceKey.wrap(inner, aad)
            } finally {
                inner.wipe()
            }
            headerStore.write(
                header.copy(
                    gestureSlot = GestureSlot(steps, params, salt, wrapped),
                    failedGestureAttempts = 0,
                ),
            )
        } finally {
            kg.wipe()
        }
    }

    @Synchronized
    fun removeGesture() {
        val header = requireHeader()
        headerStore.write(header.copy(gestureSlot = null, failedGestureAttempts = 0))
        deviceKey.deleteKey()
    }

    /** Re-wraps the DEK under a new passphrase. Callers must verify the old passphrase first. */
    @Synchronized
    fun changePassphrase(dek: ByteArray, newPassphrase: CharArray, params: Argon2Params) {
        val header = requireHeader()
        headerStore.write(header.copy(passphraseSlot = wrapPassphraseSlot(newPassphrase, params, dek)))
    }

    /** True if [passphrase] opens the vault. Costs one Argon2id derivation. */
    fun verifyPassphrase(passphrase: CharArray): Boolean =
        when (val result = unlockWithPassphrase(passphrase)) {
            is UnlockResult.Success -> {
                result.dek.wipe()
                true
            }
            else -> false
        }

    /** Removes header and device key. The caller deletes the database file. */
    @Synchronized
    fun destroyKeys() {
        headerStore.delete()
        deviceKey.deleteKey()
    }

    // ---------------------------------------------------------------------------------------

    private sealed interface HeaderRead {
        data class Ok(val header: VaultHeader) : HeaderRead
        data object Missing : HeaderRead
        data object Corrupt : HeaderRead
    }

    private fun readHeader(): HeaderRead =
        try {
            headerStore.read()?.let { HeaderRead.Ok(it) } ?: HeaderRead.Missing
        } catch (_: CorruptHeaderException) {
            HeaderRead.Corrupt
        }

    private fun requireHeader(): VaultHeader = checkNotNull(headerStore.read()) { "no vault header" }

    private fun wrapPassphraseSlot(passphrase: CharArray, params: Argon2Params, dek: ByteArray): PassphraseSlot {
        val salt = randomBytes(SALT_BYTES)
        val password = passphrase.toKeyMaterial()
        val kek = try {
            kdf.derive(password, salt, params)
        } finally {
            password.wipe()
        }
        try {
            val wrapped = AesGcm.seal(kek, dek, slotAad(SLOT_PASSPHRASE, params, salt), random)
            return PassphraseSlot(params, salt, wrapped)
        } finally {
            kek.wipe()
        }
    }

    private fun gestureAad(slot: GestureSlot): ByteArray = slotAad(SLOT_GESTURE, slot.params, slot.salt, slot.steps)

    /** Binds a wrapped DEK to its slot type, cost parameters, salt and (for gestures) length. */
    private fun slotAad(kind: Byte, params: Argon2Params, salt: ByteArray, extra: Int = 0): ByteArray =
        ByteBuffer.allocate(AAD_PREFIX.size + 1 + 12 + 4 + salt.size)
            .put(AAD_PREFIX)
            .put(kind)
            .putInt(params.memoryKiB)
            .putInt(params.iterations)
            .putInt(params.parallelism)
            .putInt(extra)
            .put(salt)
            .array()

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    companion object {
        const val DEK_BYTES = 32
        const val SALT_BYTES = 16
        const val MIN_GESTURE_STEPS = 3
        const val MAX_GESTURE_STEPS = 12

        private val AAD_PREFIX = "KRX1".toByteArray(Charsets.US_ASCII)
        private const val SLOT_PASSPHRASE: Byte = 1
        private const val SLOT_GESTURE: Byte = 2
    }
}
