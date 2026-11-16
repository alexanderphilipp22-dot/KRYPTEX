package com.kryptex.vault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local audit trail, visible only inside the unlocked vault. Failed attempts happen *before* the
 * database can be opened, so they are counted in the vault header instead of being logged here.
 */
enum class AuthEventType(val id: Int) {
    VAULT_CREATED(0),
    UNLOCK_PASSPHRASE(1),
    UNLOCK_GESTURE(2),
    LOCK(3),
    GESTURE_ENROLLED(4),
    GESTURE_REMOVED(5),
    PASSPHRASE_CHANGED(6),
    ;

    companion object {
        fun fromId(id: Int): AuthEventType = entries.firstOrNull { it.id == id } ?: LOCK
    }
}

@Entity(tableName = "auth_events")
data class AuthEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: AuthEventType,
)
