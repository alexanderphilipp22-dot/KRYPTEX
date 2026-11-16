package com.kryptex.vault.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kryptex.vault.stego.PayloadType

/** Catalogue entry for one hidden payload. The payload itself lives only inside the carrier image. */
@Entity(
    tableName = "vault_items",
    foreignKeys = [
        ForeignKey(
            entity = CarrierEntity::class,
            parentColumns = ["id"],
            childColumns = ["carrierId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("carrierId")],
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carrierId: Long,
    val title: String,
    val payloadType: PayloadType,
    val mimeType: String,
    /** Plaintext payload size in bytes. */
    val sizeBytes: Long,
    val createdAt: Long,
)
