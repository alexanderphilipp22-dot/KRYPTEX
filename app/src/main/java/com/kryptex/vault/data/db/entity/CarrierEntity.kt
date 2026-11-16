package com.kryptex.vault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Integrity state of a carrier file in shared storage. Ids are persisted: never renumber. */
enum class CarrierStatus(val id: Int) {
    OK(0),

    /** The file was deleted or moved outside the app. */
    MISSING(1),

    /** The file exists but its hash no longer matches (re-encoded, edited, truncated). */
    MODIFIED(2),
    ;

    companion object {
        fun fromId(id: Int): CarrierStatus = entries.firstOrNull { it.id == id } ?: MODIFIED
    }
}

/** A PNG in the user's media library that carries one hidden payload. */
@Entity(tableName = "carriers")
data class CarrierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MediaStore content URI of the saved PNG. */
    val mediaUri: String,
    val displayName: String,
    val widthPx: Int,
    val heightPx: Int,
    /** Usable payload bytes at the configured fill cap. */
    val capacityBytes: Long,
    /** SHA-256 (hex) of the saved file, used to detect external modification. */
    val fileSha256: String,
    /** Random 256-bit root of the per-container keys; never leaves the encrypted database. */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val containerKey: ByteArray,
    val createdAt: Long,
    val status: CarrierStatus = CarrierStatus.OK,
) {
    override fun equals(other: Any?): Boolean =
        other is CarrierEntity && id == other.id && mediaUri == other.mediaUri &&
            displayName == other.displayName && widthPx == other.widthPx && heightPx == other.heightPx &&
            capacityBytes == other.capacityBytes && fileSha256 == other.fileSha256 &&
            containerKey.contentEquals(other.containerKey) && createdAt == other.createdAt &&
            status == other.status

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mediaUri.hashCode()
        result = 31 * result + containerKey.contentHashCode()
        result = 31 * result + fileSha256.hashCode()
        return result
    }

    // Never print key material, even by accident in logs.
    override fun toString(): String = "CarrierEntity(id=$id, displayName=$displayName, status=$status)"
}
