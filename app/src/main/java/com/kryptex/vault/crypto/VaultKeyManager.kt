package com.kryptex.vault.crypto

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** Outcome of an unlock attempt. [Success.dek] is owned by the receiver, who must wipe it or hand it to a session. */
sealed interface UnlockResult {
    class Success(val dek: ByteArray) : UnlockResult

    /** @param attemptsRemaining only set for gestures: tries left before the gesture slot locks. */
    data class WrongCredential(val attemptsRemaining: Int? = null) : UnlockResult

    /**
     * The gesture sequence was right but the face check did not accept the face. Counted like a
     * wrong sequence: it uses up one of the attempts.
     */
    data class FaceMismatch(val attemptsRemaining: Int) : UnlockResult

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
 * gesture    --Argon2id--> K_g --AES-GCM--> (Keystore wrap) --> DEK [|| face template]
 * ```
 * The DEK (32 random bytes) is created once, never stored in the clear, and is the root of the
 * SQLCipher key. Every method that runs Argon2id blocks for about a second - call from a
 * background dispatcher.
 *
 * **Face template.** A gesture slot made with the face check carries an opaque template (bytes
 * this class never interprets) after the DEK inside the same encrypted payload. It is therefore
 * readable only with the right sequence, on this device, and it is compared *before* the DEK is
 * handed out. It cannot live in the database: that is encrypted with a key derived from the DEK,
 * so it is locked exactly when the face has to be checked. Slots made before the face check hold
 * the DEK alone and simply have no face check.
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

    /**
     * [gestureSecret] is the canonical byte encoding of the gesture sequence (see the vision package).
     *
     * @param faceCheck decides whether the face seen just now fits the stored template, which it is
     *   handed (and which this method wipes afterwards). Null = do not check. It is only called
     *   when the sequence was right and the slot has a template; if it returns false or throws, the
     *   result is [UnlockResult.FaceMismatch] and the key never leaves this method. It runs inside
     *   the key manager's lock, so keep it quick and free of blocking work.
     */
    @Synchronized
    fun unlockWithGesture(gestureSecret: ByteArray, faceCheck: ((ByteArray) -> Boolean)? = null): UnlockResult {
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
        val opened = try {
            AesGcm.openOrNull(kg, inner, aad)
        } finally {
            kg.wipe()
            inner.wipe()
        } ?: return UnlockResult.WrongCredential(limiter.remaining(afterIncrement))

        // The payload is the DEK, then (for slots made with the face check) the face template.
        if (opened.size < DEK_BYTES) {
            opened.wipe()
            return UnlockResult.Corrupt
        }
        val dek = opened.copyOfRange(0, DEK_BYTES)
        val template = if (opened.size > DEK_BYTES) opened.copyOfRange(DEK_BYTES, opened.size) else null
        opened.wipe()

        val faceAccepted = try {
            template == null || faceCheck == null || runFaceCheck(faceCheck, template)
        } finally {
            template?.wipe()
        }
        if (!faceAccepted) {
            // The sequence was right, so this attempt stays counted: a copied sequence gets five tries, not unlimited ones.
            dek.wipe()
            return UnlockResult.FaceMismatch(limiter.remaining(afterIncrement))
        }

        limiter.reset()
        return UnlockResult.Success(dek)
    }

    /** A check that throws is a check that failed: nobody gets in on an error. */
    private fun runFaceCheck(check: (ByteArray) -> Boolean, template: ByteArray): Boolean =
        try {
            check(template)
        } catch (_: Exception) {
            false
        }

    /**
     * Adds or replaces the gesture slot. Needs the DEK, so the vault must be unlocked.
     *
     * @param faceTemplate opaque bytes to be handed to the face check at unlock, or null for a slot
     *   without one. At most [MAX_FACE_TEMPLATE_BYTES]. It is sealed together with the DEK and
     *   never stored in the clear; the caller keeps ownership of the array and wipes it.
     */
    @Synchronized
    fun enrollGesture(
        dek: ByteArray,
        gestureSecret: ByteArray,
        steps: Int,
        params: Argon2Params,
        faceTemplate: ByteArray? = null,
    ) {
        require(steps in MIN_GESTURE_STEPS..MAX_GESTURE_STEPS) { "gesture length out of range" }
        require(faceTemplate == null || faceTemplate.size in 1..MAX_FACE_TEMPLATE_BYTES) { "face template size out of range" }
        val header = requireHeader()
        val salt = randomBytes(SALT_BYTES)
        val kg = kdf.derive(gestureSecret, salt, params)
        try {
            val draft = GestureSlot(steps, params, salt, ByteArray(0))
            val aad = gestureAad(draft)
            val payload = if (faceTemplate == null) dek else dek + faceTemplate
            val inner = try {
                AesGcm.seal(kg, payload, aad, random)
            } finally {
                if (payload !== dek) payload.wipe()
            }
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

        /** Far above what the face check stores, far below the header's limit for a wrapped key. */
        const val MAX_FACE_TEMPLATE_BYTES = 64

        private val AAD_PREFIX = "KRX1".toByteArray(Charsets.US_ASCII)
        private const val SLOT_PASSPHRASE: Byte = 1
        private const val SLOT_GESTURE: Byte = 2
    }
}
