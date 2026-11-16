package com.kryptex.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.CarrierStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CarrierDao {
    @Insert
    suspend fun insert(carrier: CarrierEntity): Long

    @Update
    suspend fun update(carrier: CarrierEntity)

    @Delete
    suspend fun delete(carrier: CarrierEntity)

    @Query("SELECT * FROM carriers WHERE id = :id")
    suspend fun getById(id: Long): CarrierEntity?

    @Query("SELECT * FROM carriers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CarrierEntity>>

    @Query("SELECT * FROM carriers")
    suspend fun getAll(): List<CarrierEntity>

    @Query("UPDATE carriers SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: CarrierStatus)

    @Query("SELECT COUNT(*) FROM carriers")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(capacityBytes), 0) FROM carriers")
    fun observeTotalCapacity(): Flow<Long>
}
