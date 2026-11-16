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
