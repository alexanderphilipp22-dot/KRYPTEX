package com.kryptex.vault.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kryptex.vault.crypto.wipe
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** The database could not be opened: wrong key, damaged file, or a missing native library. */
class VaultOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An open database plus the key literal that SQLCipher keeps a reference to for the lifetime of
 * the connection. [close] closes the database and then zeroes that key literal.
 */
class OpenDatabase internal constructor(
    val database: KryptexDatabase,
    private val keyLiteral: ByteArray,
) : AutoCloseable {
    override fun close() {
        try {
            database.close()
        } finally {
            keyLiteral.wipe()
        }
    }
}

/**
 * Opens the Room database through SQLCipher with a raw 256-bit key (no SQLCipher-side PBKDF2).
 *
 * @param dbName file name inside the app's database directory. Instrumented tests pass their own
 * name so they can never touch a real vault on the same device.
 */
class DatabaseFactory(
    private val context: Context,
    private val dbName: String = DB_NAME,
) {

    fun databaseFile(): File = context.getDatabasePath(dbName)

    fun exists(): Boolean = databaseFile().isFile

    /**
     * @param dbKey 32 key bytes derived from the DEK. The array is not modified or retained.
     * @throws VaultOpenException if the database cannot be opened with this key.
     */
    fun open(dbKey: ByteArray): OpenDatabase {
        require(dbKey.size == KEY_BYTES) { "SQLCipher raw key must be 32 bytes" }
        try {
            loadNativeLibrary()
        } catch (e: UnsatisfiedLinkError) {
            throw VaultOpenException("SQLCipher native library unavailable", e)
        }

        val keyLiteral = rawKeyLiteral(dbKey)
        val database = Room.databaseBuilder(context, KryptexDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(keyLiteral))
            // Single-file journal; nothing lingers beside the main database file.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        try {
            // Room opens lazily; force it now so a wrong key fails here and not in a random query.
            database.openHelper.writableDatabase
        } catch (e: Exception) {
            runCatching { database.close() }
            keyLiteral.wipe()
            throw VaultOpenException("database could not be opened", e)
        }
        return OpenDatabase(database, keyLiteral)
    }

    /** Deletes the database and its journal files. Part of "wipe vault". */
    fun delete(): Boolean = context.deleteDatabase(dbName)

    companion object {
        const val DB_NAME = "kryptex.db"
        const val KEY_BYTES = 32

        /**
         * Loads SQLCipher's native code. Idempotent; called off the main thread at start-up so the
         * first unlock does not have to, and again before every open as a guard.
         */
        fun loadNativeLibrary() = System.loadLibrary("sqlcipher")

        /** `x'<64 hex digits>'` as bytes: SQLCipher's syntax for a raw key. Built without a String. */
        internal fun rawKeyLiteral(key: ByteArray): ByteArray {
            val digits = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
            val out = ByteArray(2 + key.size * 2 + 1)
            out[0] = 'x'.code.toByte()
            out[1] = '\''.code.toByte()
            for (i in key.indices) {
                val v = key[i].toInt() and 0xFF
                out[2 + i * 2] = digits[v ushr 4]
                out[3 + i * 2] = digits[v and 0x0F]
            }
            out[out.size - 1] = '\''.code.toByte()
            return out
        }
    }
}
