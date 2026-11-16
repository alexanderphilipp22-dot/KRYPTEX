package com.kryptex.vault.crypto

import com.kryptex.vault.data.db.DatabaseFactory
import com.kryptex.vault.data.db.KryptexDatabase
import com.kryptex.vault.data.db.OpenDatabase
import com.kryptex.vault.data.db.VaultOpenException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An unlocked vault: the DEK and the open database. Exists only between a successful unlock and
 * [SessionManager.lock]; after that every accessor fails fast.
 */
class VaultSession internal constructor(
    private var dek: ByteArray?,
    private var opened: OpenDatabase?,
) {
    val isOpen: Boolean @Synchronized get() = opened != null

    val database: KryptexDatabase
        @Synchronized get() = checkNotNull(opened) { "vault is locked" }.database

    /**
     * Exposes the DEK to [block] without copying it. The array is wiped on lock, so [block] must
     * not keep a reference to it.
     */
    @Synchronized
    fun <T> withDek(block: (ByteArray) -> T): T = block(checkNotNull(dek) { "vault is locked" })

    @Synchronized
    internal fun close() {
        try {
            opened?.close()
        } finally {
            opened = null
            dek?.wipe()
            dek = null
        }
    }
}

/** Single point of truth for locked / unlocked. Observers react to [session] becoming null. */
class SessionManager(private val databaseFactory: DatabaseFactory) {
    private val state = MutableStateFlow<VaultSession?>(null)

    /** Non-null while the vault is unlocked. */
    val session: StateFlow<VaultSession?> = state.asStateFlow()

    val isUnlocked: Boolean get() = state.value != null

    /**
     * Takes ownership of [dek] (it is wiped on [lock] or on failure) and opens the database.
     * @throws VaultOpenException if the database cannot be opened with the derived key.
     */
    @Synchronized
    fun open(dek: ByteArray): VaultSession {
        lock()
        val dbKey = Hkdf.derive(dek, DB_KEY_SALT, DB_KEY_INFO, DatabaseFactory.KEY_BYTES)
        try {
            val session = VaultSession(dek, databaseFactory.open(dbKey))
            state.value = session
            return session
        } catch (e: VaultOpenException) {
            dek.wipe()
            throw e
        } finally {
            dbKey.wipe()
        }
    }

    @Synchronized
    fun lock() {
        val current = state.value ?: return
        state.value = null // observers drop their references first
        try {
            current.close()
        } catch (_: Exception) {
            // The DEK and the key literal are wiped in close()'s finally blocks either way. A database
            // that refuses to close cleanly must not crash the app on its way to the background.
        }
    }

    private companion object {
        val DB_KEY_SALT = "kryptex.v1.salt".toByteArray(Charsets.US_ASCII)
        const val DB_KEY_INFO = "kryptex.v1.sqlcipher-key"
    }
}
