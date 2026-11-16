package com.kryptex.vault.data.db

import androidx.room.TypeConverter
import com.kryptex.vault.data.db.entity.AuthEventType
import com.kryptex.vault.data.db.entity.CarrierStatus
import com.kryptex.vault.stego.PayloadType

/** Enums are stored as their stable numeric ids, never by ordinal or name. */
class Converters {
    @TypeConverter
    fun payloadTypeToId(value: PayloadType): Int = value.id

    @TypeConverter
    fun idToPayloadType(id: Int): PayloadType = PayloadType.fromId(id)

    @TypeConverter
    fun carrierStatusToId(value: CarrierStatus): Int = value.id

    @TypeConverter
    fun idToCarrierStatus(id: Int): CarrierStatus = CarrierStatus.fromId(id)

    @TypeConverter
    fun authEventTypeToId(value: AuthEventType): Int = value.id

    @TypeConverter
    fun idToAuthEventType(id: Int): AuthEventType = AuthEventType.fromId(id)
}
