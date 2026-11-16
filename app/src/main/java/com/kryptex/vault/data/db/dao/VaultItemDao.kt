package com.kryptex.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

data class VaultItemWithCarrier(
    @Embedded val item: VaultItemEntity,
    @Relation(parentColumn = "carrierId", entityColumn = "id") val carrier: CarrierEntity,
)

@Dao
interface VaultItemDao {
    @Insert
    suspend fun insert(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeAllWithCarrier(): Flow<List<VaultItemWithCarrier>>

    @Transaction
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VaultItemWithCarrier>>

    @Transaction
    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getWithCarrier(id: Long): VaultItemWithCarrier?

    @Query("SELECT COUNT(*) FROM vault_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_items")
    fun observeTotalSize(): Flow<Long>
}
