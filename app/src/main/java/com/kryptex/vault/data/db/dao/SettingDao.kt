package com.kryptex.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kryptex.vault.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun get(name: String): String?

    @Query("SELECT value FROM settings WHERE name = :name")
    fun observe(name: String): Flow<String?>
}
