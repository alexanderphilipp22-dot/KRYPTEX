package com.kryptex.vault.data.repo

import androidx.room.withTransaction
import com.kryptex.vault.data.db.KryptexDatabase
import com.kryptex.vault.data.db.dao.VaultItemWithCarrier
import com.kryptex.vault.data.db.entity.AuthEventEntity
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.db.entity.CarrierEntity
import com.kryptex.vault.data.db.entity.CarrierStatus
import com.kryptex.vault.data.db.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class VaultStats(
    val carrierCount: Int,
    val itemCount: Int,
    val capacityBytes: Long,
    val storedBytes: Long,
)

/** All reads and writes of vault content. Bound to one unlocked database; discard it on lock. */
class VaultRepository(
    private val db: KryptexDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val carriers = db.carrierDao()
    private val items = db.vaultItemDao()
    private val events = db.authEventDao()

    fun observeItems(): Flow<List<VaultItemWithCarrier>> = items.observeAllWithCarrier()

    fun observeRecentItems(limit: Int = 5): Flow<List<VaultItemWithCarrier>> = items.observeRecent(limit)

    fun observeStats(): Flow<VaultStats> =
        combine(
            carriers.observeCount(),
            items.observeCount(),
            carriers.observeTotalCapacity(),
            items.observeTotalSize(),
        ) { carrierCount, itemCount, capacity, stored ->
            VaultStats(carrierCount, itemCount, capacity, stored)
        }

    suspend fun getItem(id: Long): VaultItemWithCarrier? = items.getWithCarrier(id)

    /** Stores a carrier and its catalogue entry atomically. @return the new item id. */
    suspend fun addItem(carrier: CarrierEntity, item: VaultItemEntity): Long =
        db.withTransaction {
            val carrierId = carriers.insert(carrier)
            items.insert(item.copy(carrierId = carrierId))
        }

    /** Removes the catalogue entry and its carrier row. The caller deletes the image file. */
    suspend fun deleteItem(entry: VaultItemWithCarrier) {
        db.withTransaction {
            items.deleteById(entry.item.id)
            carriers.delete(entry.carrier)
        }
    }

    suspend fun allCarriers(): List<CarrierEntity> = carriers.getAll()

    suspend fun setCarrierStatus(id: Long, status: CarrierStatus) = carriers.setStatus(id, status)

    suspend fun recordEvent(type: AuthEventType) {
        events.insert(AuthEventEntity(timestamp = clock(), type = type))
        events.prune(keep = MAX_EVENTS)
    }

    /** The most recent unlock before the current one, for the dashboard security card. */
    suspend fun previousUnlockTimestamp(): Long? = events.recentUnlocks(2).getOrNull(1)?.timestamp

    private companion object {
        const val MAX_EVENTS = 200
    }
}
