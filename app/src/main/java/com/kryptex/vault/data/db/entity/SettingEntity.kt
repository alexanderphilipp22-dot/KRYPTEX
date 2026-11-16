package com.kryptex.vault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Simple key-value store for user preferences that should live inside the encrypted vault. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val name: String,
    val value: String,
)
