package com.kryptex.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptex.vault.crypto.Argon2Params
import com.kryptex.vault.crypto.Argon2ktKdf
import com.kryptex.vault.crypto.AttemptLimiter
import com.kryptex.vault.crypto.KeystoreWrapper
import com.kryptex.vault.crypto.SessionManager
import com.kryptex.vault.crypto.UnlockResult
import com.kryptex.vault.crypto.VaultHeaderStore
import com.kryptex.vault.crypto.VaultKeyManager
import com.kryptex.vault.data.db.DatabaseFactory
import com.kryptex.vault.data.db.VaultOpenException
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.VaultItemEntity
import com.kryptex.vault.data.repo.VaultRepository
import com.kryptex.vault.stego.PayloadType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 1 gate: a vault can be created, locked and reopened with the passphrase, and the
 * database file on disk is real SQLCipher ciphertext, not readable as plain SQLite.
 *
 * Uses its own database name, header directory and Keystore alias so it can never touch a real vault.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private lateinit var context: Context
    private lateinit var headerDir: File
    private lateinit var factory: DatabaseFactory
    private lateinit var sessions: SessionManager
    private lateinit var keys: VaultKeyManager
    private lateinit var deviceKey: KeystoreWrapper

    // Cheap parameters: Argon2id cost is covered by Argon2KdfTest; this test is about plumbing.
    private val params = Argon2Params(memoryKiB = 4096, iterations = 1, parallelism = 1)
    private val passphrase get() = "correct horse battery staple".toCharArray()
    private val secretTitle = "TOP-SECRET-TITLE-7f3a9c"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        headerDir = File(context.cacheDir, "kryptex-test-header").apply { deleteRecursively(); mkdirs() }
        factory = DatabaseFactory(context, dbName = TEST_DB).also { it.delete() }
        sessions = SessionManager(factory)
        deviceKey = KeystoreWrapper(alias = "kryptex.test.vault-binding")
        val store = VaultHeaderStore(headerDir)
        keys = VaultKeyManager(store, Argon2ktKdf(), deviceKey, AttemptLimiter(store))
    }

    @After
    fun tearDown() {
        sessions.lock()
        factory.delete()
        deviceKey.deleteKey()
        headerDir.deleteRecursively()
    }

    private fun addSampleItem(session: com.kryptex.vault.crypto.VaultSession) = runBlocking {
        VaultRepository(session.database).addItem(
            carrier = CarrierEntity(
                mediaUri = "content://media/external/images/media/1",
                displayName = "IMG_20260101_120000.png",
                widthPx = 1000,
                heightPx = 800,
                capacityBytes = 150_000,
                fileSha256 = "00".repeat(32),
                containerKey = ByteArray(32) { it.toByte() },
                createdAt = 1L,
            ),
            item = VaultItemEntity(
                carrierId = 0,
                title = secretTitle,
                payloadType = PayloadType.TEXT,
                mimeType = "text/plain",
                sizeBytes = 42,
                createdAt = 1L,
            ),
        )
    }

    @Test
    fun createLockReopenWithPassphrase() {
        val dek = keys.createVault(passphrase, params)
        val dekCopy = dek.copyOf()
        val first = sessions.open(dek)
        addSampleItem(first)
        assertTrue(sessions.isUnlocked)

        sessions.lock()
        assertFalse(sessions.isUnlocked)
        assertFalse("session must be closed", first.isOpen)
        assertThrows(IllegalStateException::class.java) { first.database }
        assertTrue("DEK must be wiped on lock", dek.all { it == 0.toByte() })

        val result = keys.unlockWithPassphrase(passphrase)
        assertTrue(result is UnlockResult.Success)
        val dekAgain = (result as UnlockResult.Success).dek
        assertArrayEquals(dekCopy, dekAgain)

        val reopened = sessions.open(dekAgain)
        val items = runBlocking { VaultRepository(reopened.database).observeItems().first() }
        assertEquals(1, items.size)
        assertEquals(secretTitle, items.single().item.title)
        assertEquals(PayloadType.TEXT, items.single().item.payloadType)
        assertEquals("IMG_20260101_120000.png", items.single().carrier.displayName)
    }

    @Test
    fun databaseFileIsNotReadableAsPlainSqlite() {
        val session = sessions.open(keys.createVault(passphrase, params))
        addSampleItem(session)
        sessions.lock()

        val bytes = factory.databaseFile().readBytes()
        assertTrue("database must not be empty", bytes.size >= 4096)

        val plainHeader = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        assertFalse(
            "file starts with the plaintext SQLite magic",
            bytes.copyOf(plainHeader.size).contentEquals(plainHeader),
        )
        val asText = String(bytes, Charsets.ISO_8859_1)
        assertFalse("table name leaked", asText.contains("vault_items"))
        assertFalse("content leaked", asText.contains(secretTitle))
        assertFalse("file name leaked", asText.contains("IMG_20260101"))
    }

    @Test
    fun wrongPassphraseNeverReachesTheDatabase() {
        keys.createVault(passphrase, params).let { sessions.open(it) }
        sessions.lock()

        assertEquals(UnlockResult.WrongCredential(), keys.unlockWithPassphrase("wrong".toCharArray()))
        assertFalse(sessions.isUnlocked)
    }

    @Test
    fun wrongRawKeyIsRejectedByTheDatabaseItself() {
        val session = sessions.open(keys.createVault(passphrase, params))
        addSampleItem(session)
        sessions.lock()

        assertThrows(VaultOpenException::class.java) { factory.open(ByteArray(32) { 0x42 }) }
    }

    @Test
    fun gestureSlotUnlocksTheSameDatabase() {
        val dek = keys.createVault(passphrase, params)
        val gesture = byteArrayOf(1, 4, 2, 7, 3, 9)
        keys.enrollGesture(dek, gesture, steps = 6, params = params)
        addSampleItem(sessions.open(dek))
        sessions.lock()

        val result = keys.unlockWithGesture(gesture)
        assertTrue("gesture unlock: $result", result is UnlockResult.Success)
        val reopened = sessions.open((result as UnlockResult.Success).dek)
        val items = runBlocking { VaultRepository(reopened.database).observeItems().first() }
        assertEquals(secretTitle, items.single().item.title)
    }

    @Test
    fun openingTheDatabaseFailsCleanlyWhenKeyIsWrongLength() {
        assertThrows(IllegalArgumentException::class.java) { factory.open(ByteArray(16)) }
    }

    private companion object {
        const val TEST_DB = "kryptex-instrumented-test.db"
    }
}
