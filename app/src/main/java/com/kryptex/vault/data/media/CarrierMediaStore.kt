package com.kryptex.vault.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.kryptex.vault.crypto.toHex
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.CarrierStatus
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedCarrier(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    /** SHA-256 (hex) of the bytes that were written, to detect later modification. */
    val sha256: String,
)

/**
 * Saves carrier PNGs into the shared photo library and keeps track of them. They are written under
 * a camera-style name (`IMG_yyyyMMdd_HHmmss.png`) in a plain folder such as `Pictures`: no app-named
 * album that would give them away. Because the app created the files, scoped storage lets it read
 * and delete them without any storage permission.
 */
class CarrierMediaStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val resolver = context.contentResolver

    /** @throws IOException if the picture could not be written; nothing is left behind in that case. */
    fun save(png: ByteArray, folder: String = DEFAULT_FOLDER, displayName: String = defaultName(now())): SavedCarrier {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath(folder))
            put(MediaStore.Images.Media.DATE_TAKEN, now())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("the photo library refused the new picture")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(png) } ?: throw IOException("could not open the picture for writing")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (e is IOException) e else IOException("could not save the picture", e)
        }
        return SavedCarrier(uri, queryDisplayName(uri) ?: displayName, png.size.toLong(), sha256Hex(png))
    }

    /** The saved file's bytes, or null if it no longer exists. */
    fun readBytes(uri: Uri): ByteArray? =
        try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            null // the entry now belongs to somebody else
        }

    /** SHA-256 of the file as it is now, or null if missing. */
    fun currentSha256(uri: Uri): String? =
        try {
            resolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
                digest.digest().toHex()
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }

    /** Compares the file in the photo library with what was saved. */
    fun check(carrier: CarrierEntity): CarrierStatus {
        val current = currentSha256(Uri.parse(carrier.mediaUri)) ?: return CarrierStatus.MISSING
        return if (current == carrier.fileSha256) CarrierStatus.OK else CarrierStatus.MODIFIED
    }

    /** @return true if a picture was removed. */
    fun delete(uri: Uri): Boolean =
        try {
            resolver.delete(uri, null, null) > 0
        } catch (_: SecurityException) {
            false
        }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    companion object {
        const val DEFAULT_FOLDER = "Pictures"
        private val ALLOWED_ROOTS = setOf("Pictures", "DCIM")

        /** `IMG_20260115_183042.png`, the pattern of a phone camera. */
        fun defaultName(timeMillis: Long): String =
            "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timeMillis)) + ".png"

        /**
         * Turns the user's folder setting into a MediaStore relative path. Only the standard photo
         * roots are allowed and `..` is refused, so a setting can never point outside them.
         */
        fun relativePath(folder: String): String {
            val segments = folder.trim().trim('/').split('/').filter { it.isNotBlank() }
            require(segments.isNotEmpty() && segments.first() in ALLOWED_ROOTS) {
                "folder must be inside Pictures or DCIM"
            }
            require(segments.none { it == "." || it == ".." }) { "invalid folder" }
            return segments.joinToString("/") + "/"
        }

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        /** For tests: the MediaStore row id of a saved picture. */
        fun idOf(uri: Uri): Long = ContentUris.parseId(uri)
    }
}
