package com.kryptex.vault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kryptex.vault.data.db.entity.AuthEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthEventDao {
    @Insert
    suspend fun insert(event: AuthEventEntity)

    @Query("SELECT * FROM auth_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuthEventEntity>>

    /** Unlock events (passphrase = 1, gesture = 2) in reverse chronological order. */
    @Query("SELECT * FROM auth_events WHERE type IN (1, 2) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentUnlocks(limit: Int): List<AuthEventEntity>

    /** Keeps the audit trail bounded. */
    @Query(
        "DELETE FROM auth_events WHERE id NOT IN " +
            "(SELECT id FROM auth_events ORDER BY timestamp DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}
