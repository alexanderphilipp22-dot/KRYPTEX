package com.kryptex.vault.crypto

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultKeyManagerTest {
    private lateinit var dir: File
    private lateinit var store: VaultHeaderStore
    private lateinit var kdf: FakeArgon2Kdf
    private lateinit var deviceKey: FakeDeviceKeyWrapper
    private lateinit var manager: VaultKeyManager

    private val params = Argon2Params(memoryKiB = 8, iterations = 1, parallelism = 1)
    private val passphrase get() = "correct horse battery staple".toCharArray()
    private val gesture = byteArrayOf(1, 4, 2, 7, 3, 9)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("kryptex-km").toFile()
        store = VaultHeaderStore(dir)
        kdf = FakeArgon2Kdf()
        deviceKey = FakeDeviceKeyWrapper()
        manager = VaultKeyManager(store, kdf, deviceKey, AttemptLimiter(store))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun success(result: UnlockResult): ByteArray {
        assertTrue("expected Success but was $result", result is UnlockResult.Success)
        return (result as UnlockResult.Success).dek
    }

    @Test
    fun noVaultBeforeCreation() {
        assertFalse(manager.vaultExists())
        assertEquals(UnlockResult.NoVault, manager.unlockWithPassphrase(passphrase))
        assertEquals(GestureSlotInfo.NotEnrolled, manager.gestureSlotInfo())
    }

    @Test
    fun createThenUnlockWithPassphraseReturnsTheSameDek() {
        val created = manager.createVault(passphrase, params)
        assertEquals(32, created.size)
        assertTrue(manager.vaultExists())

        assertArrayEquals(created, success(manager.unlockWithPassphrase(passphrase)))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        manager.createVault(passphrase, params).wipe()
        assertEquals(UnlockResult.WrongCredential(), manager.unlockWithPassphrase("wrong".toCharArray()))
        assertEquals(UnlockResult.WrongCredential(), manager.unlockWithPassphrase("".toCharArray()))
    }

    @Test
    fun passphraseIsUnicodeNormalised() {
        val composed = "café".toCharArray() // e-acute as one code point
        val decomposed = "café".toCharArray() // e + combining acute
        val created = manager.createVault(composed, params)
        assertArrayEquals(created, success(manager.unlockWithPassphrase(decomposed)))
    }

    @Test
    fun cannotCreateTwoVaults() {
        manager.createVault(passphrase, params).wipe()
        try {
            manager.createVault(passphrase, params)
            error("second createVault must fail")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun storedHeaderContainsNeitherDekNorPassphrase() {
        val dek = manager.createVault(passphrase, params)
        val bytes = File(dir, VaultHeaderStore.DEFAULT_NAME).readBytes()
        assertFalse(bytes.toHex().contains(dek.toHex()))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("correct horse"))
    }

    // ---- gesture slot ----------------------------------------------------------------------

    @Test
    fun gestureUnlockReturnsTheSameDek() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, steps = 6, params = params)

        assertEquals(GestureSlotInfo.Ready(steps = 6, attemptsRemaining = 5), manager.gestureSlotInfo())
        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture)))
    }

    @Test
    fun gestureNotEnrolled() {
        manager.createVault(passphrase, params).wipe()
        assertEquals(UnlockResult.GestureNotEnrolled, manager.unlockWithGesture(gesture))
    }

    @Test
    fun wrongGestureCountsDownAndLocksAfterFiveFailures() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)
        val wrong = byteArrayOf(9, 9, 9, 9, 9, 9)

        for (expectedRemaining in 4 downTo 0) {
            assertEquals(UnlockResult.WrongCredential(expectedRemaining), manager.unlockWithGesture(wrong))
        }

        assertEquals(GestureSlotInfo.Locked, manager.gestureSlotInfo())
        // Even the correct sequence is refused once locked.
        assertEquals(UnlockResult.GestureLocked, manager.unlockWithGesture(gesture))
    }

    @Test
    fun attemptIsPersistedBeforeItIsEvaluated() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        manager.unlockWithGesture(byteArrayOf(0, 0, 0, 0, 0, 0))
        assertEquals(1, store.read()!!.failedGestureAttempts)
    }

    @Test
    fun successfulGestureResetsTheCounter() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        manager.unlockWithGesture(byteArrayOf(0, 0, 0, 0, 0, 0))
        manager.unlockWithGesture(byteArrayOf(0, 0, 0, 0, 0, 1))
        assertEquals(2, store.read()!!.failedGestureAttempts)

        success(manager.unlockWithGesture(gesture)).wipe()
        assertEquals(0, store.read()!!.failedGestureAttempts)
    }

    @Test
    fun passphraseUnlockReEnablesALockedGestureSlot() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)
        repeat(5) { manager.unlockWithGesture(byteArrayOf(5, 5, 5, 5, 5, 5)) }
        assertEquals(UnlockResult.GestureLocked, manager.unlockWithGesture(gesture))

        success(manager.unlockWithPassphrase(passphrase)).wipe()

        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture)))
    }

    @Test
    fun gestureIsUnavailableWhenTheDeviceKeyIsGone() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)
        deviceKey.deleteKey() // e.g. app data restored onto another phone

        assertEquals(UnlockResult.GestureUnavailable, manager.unlockWithGesture(gesture))
        assertArrayEquals(dek, success(manager.unlockWithPassphrase(passphrase))) // passphrase still works
    }

    @Test
    fun tamperingWithTheGestureLengthBreaksTheSlot() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        val header = store.read()!!
        store.write(header.copy(gestureSlot = header.gestureSlot!!.copy(steps = 5)))

        // The length is part of the AAD of both wrapping layers, so the outer (device-key) layer
        // already refuses it; the correct gesture no longer opens the slot.
        assertEquals(UnlockResult.GestureUnavailable, manager.unlockWithGesture(gesture))
        assertArrayEquals(dek, success(manager.unlockWithPassphrase(passphrase)))
    }

    @Test
    fun removingTheGestureSlotDeletesTheDeviceKey() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        manager.removeGesture()

        assertEquals(UnlockResult.GestureNotEnrolled, manager.unlockWithGesture(gesture))
        assertFalse(deviceKey.keyPresent)
        assertNotNull(store.read())
    }

    @Test
    fun enrolRejectsOutOfRangeLengths() {
        val dek = manager.createVault(passphrase, params)
        for (steps in listOf(0, 2, 13)) {
            try {
                manager.enrollGesture(dek, gesture, steps, params)
                error("steps=$steps must be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    // ---- face template in the gesture slot -------------------------------------------------

    private val faceTemplate get() = ByteArray(42) { (it * 3 + 11).toByte() }

    private fun enrolWithFace(): ByteArray {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params, faceTemplate)
        return dek
    }

    @Test
    fun aMatchingFaceUnlocksAndTheCheckSeesTheStoredTemplate() {
        val dek = enrolWithFace()
        var seen: ByteArray? = null

        val result = manager.unlockWithGesture(gesture) { template -> seen = template.copyOf(); true }

        assertArrayEquals(dek, success(result))
        assertArrayEquals(faceTemplate, seen)
        assertEquals(0, store.read()!!.failedGestureAttempts)
    }

    @Test
    fun theKeyIsExactlyTheDekEvenWhenATemplateRidesAlong() {
        val dek = enrolWithFace()
        assertEquals(VaultKeyManager.DEK_BYTES, success(manager.unlockWithGesture(gesture) { true }).size)
        // ...and the passphrase path is untouched by the template.
        assertArrayEquals(dek, success(manager.unlockWithPassphrase(passphrase)))
    }

    @Test
    fun aMismatchedFaceIsRefusedAndCountsAsAnAttempt() {
        enrolWithFace()

        assertEquals(UnlockResult.FaceMismatch(4), manager.unlockWithGesture(gesture) { false })
        assertEquals(1, store.read()!!.failedGestureAttempts)
        assertEquals(UnlockResult.FaceMismatch(3), manager.unlockWithGesture(gesture) { false })
    }

    @Test
    fun fiveMismatchesLockTheGestureSlotEvenWithTheRightSequence() {
        enrolWithFace()

        for (expectedRemaining in 4 downTo 0) {
            assertEquals(UnlockResult.FaceMismatch(expectedRemaining), manager.unlockWithGesture(gesture) { false })
        }
        assertEquals(GestureSlotInfo.Locked, manager.gestureSlotInfo())
        assertEquals(UnlockResult.GestureLocked, manager.unlockWithGesture(gesture) { true })
    }

    @Test
    fun aMatchAfterAMismatchResetsTheCounter() {
        val dek = enrolWithFace()
        manager.unlockWithGesture(gesture) { false }
        manager.unlockWithGesture(gesture) { false }
        assertEquals(2, store.read()!!.failedGestureAttempts)

        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture) { true }))
        assertEquals(0, store.read()!!.failedGestureAttempts)
    }

    @Test
    fun aFaceCheckThatThrowsFailsClosed() {
        enrolWithFace()
        val result = manager.unlockWithGesture(gesture) { error("vision layer exploded") }
        assertEquals(UnlockResult.FaceMismatch(4), result)
    }

    @Test
    fun aWrongSequenceIsStillAWrongSequenceAndNeverReachesTheFaceCheck() {
        enrolWithFace()
        var calls = 0

        val result = manager.unlockWithGesture(byteArrayOf(9, 9, 9, 9, 9, 9)) { calls++; true }

        assertEquals(UnlockResult.WrongCredential(4), result)
        assertEquals("the template is only unsealed by the right sequence", 0, calls)
    }

    @Test
    fun withoutACheckTheTemplateIsIgnored() {
        val dek = enrolWithFace()
        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture)))
        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture, faceCheck = null)))
    }

    @Test
    fun aSlotWithoutATemplateHasNothingToCheckAndSimplyUnlocks() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params) // as slots made before the face check
        var calls = 0

        val result = manager.unlockWithGesture(gesture) { calls++; false }

        assertArrayEquals(dek, success(result))
        assertEquals(0, calls)
    }

    @Test
    fun enrollingAgainWithoutATemplateDropsTheOldOne() {
        val dek = enrolWithFace()
        manager.enrollGesture(dek, gesture, 6, params)

        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture) { false }))
    }

    @Test
    fun enrollingAgainWithANewTemplateReplacesTheOldOne() {
        val dek = enrolWithFace()
        val replacement = ByteArray(42) { (it + 100).toByte() }
        manager.enrollGesture(dek, gesture, 6, params, replacement)
        var seen: ByteArray? = null

        manager.unlockWithGesture(gesture) { template -> seen = template.copyOf(); true }.also { success(it).wipe() }

        assertArrayEquals(replacement, seen)
    }

    @Test
    fun theTemplateIsNeverInTheHeaderInTheClear() {
        enrolWithFace()
        val bytes = File(dir, VaultHeaderStore.DEFAULT_NAME).readBytes()
        assertFalse(bytes.toHex().contains(faceTemplate.toHex()))
        // Not even a recognisable run of it, as a partial leak would reveal:
        assertFalse(bytes.toHex().contains(faceTemplate.copyOfRange(0, 12).toHex()))
    }

    @Test
    fun theTemplateCannotBeReadWithoutTheRightSequence() {
        enrolWithFace()
        var calls = 0
        repeat(3) { manager.unlockWithGesture(byteArrayOf(1, 1, 1, 1, 1, it.toByte())) { calls++; true } }
        assertEquals(0, calls)
    }

    @Test
    fun enrolRejectsEmptyAndOversizedTemplates() {
        val dek = manager.createVault(passphrase, params)
        for (size in listOf(0, VaultKeyManager.MAX_FACE_TEMPLATE_BYTES + 1)) {
            try {
                manager.enrollGesture(dek, gesture, 6, params, ByteArray(size))
                error("template of $size bytes must be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        assertEquals(GestureSlotInfo.NotEnrolled, manager.gestureSlotInfo())
    }

    @Test
    fun theLargestAllowedTemplateStillFitsTheHeader() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params, ByteArray(VaultKeyManager.MAX_FACE_TEMPLATE_BYTES) { 7 })
        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture) { true }))
    }

    @Test
    fun changingThePassphraseKeepsTheFaceCheckWorking() {
        val dek = enrolWithFace()
        manager.changePassphrase(dek, "a brand new passphrase".toCharArray(), params)
        assertEquals(UnlockResult.FaceMismatch(4), manager.unlockWithGesture(gesture) { false })
    }

    // ---- passphrase change / wipe ----------------------------------------------------------

    @Test
    fun changePassphraseKeepsTheDekAndTheGestureSlot() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        manager.changePassphrase(dek, "a brand new passphrase".toCharArray(), params)

        assertEquals(UnlockResult.WrongCredential(), manager.unlockWithPassphrase(passphrase))
        assertArrayEquals(dek, success(manager.unlockWithPassphrase("a brand new passphrase".toCharArray())))
        assertArrayEquals(dek, success(manager.unlockWithGesture(gesture)))
    }

    @Test
    fun verifyPassphrase() {
        manager.createVault(passphrase, params).wipe()
        assertTrue(manager.verifyPassphrase(passphrase))
        assertFalse(manager.verifyPassphrase("nope".toCharArray()))
    }

    @Test
    fun destroyKeysRemovesHeaderAndDeviceKey() {
        val dek = manager.createVault(passphrase, params)
        manager.enrollGesture(dek, gesture, 6, params)

        manager.destroyKeys()

        assertFalse(manager.vaultExists())
        assertFalse(deviceKey.keyPresent)
        assertEquals(UnlockResult.NoVault, manager.unlockWithPassphrase(passphrase))
    }

    @Test
    fun corruptHeaderIsReportedNotCrashed() {
        manager.createVault(passphrase, params).wipe()
        val file = File(dir, VaultHeaderStore.DEFAULT_NAME)
        file.writeBytes(file.readBytes().also { it[10] = (it[10].toInt() xor 1).toByte() })

        assertEquals(UnlockResult.Corrupt, manager.unlockWithPassphrase(passphrase))
        assertEquals(UnlockResult.Corrupt, manager.unlockWithGesture(gesture))
    }
}
