package com.kryptex.vault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kryptex.vault.data.db.dao.AuthEventDao
import com.kryptex.vault.data.db.dao.CarrierDao
import com.kryptex.vault.data.db.dao.SettingDao
import com.kryptex.vault.data.db.dao.VaultItemDao
import com.kryptex.vault.data.db.entity.AuthEventEntity
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.SettingEntity
import com.kryptex.vault.data.db.entity.VaultItemEntity

/** Room schema, always opened through SQLCipher with a raw key (see [DatabaseFactory]). */
@Database(
    entities = [
        CarrierEntity::class,
        VaultItemEntity::class,
        AuthEventEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KryptexDatabase : RoomDatabase() {
    abstract fun carrierDao(): CarrierDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun authEventDao(): AuthEventDao
    abstract fun settingDao(): SettingDao
}
