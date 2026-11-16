package com.kryptex.vault.stego

/**
 * Kind of data hidden in a carrier. The numeric [id] is part of the on-disk formats (database
 * and payload container) and must never be reused or renumbered.
 */
enum class PayloadType(val id: Int) {
    TEXT(1),
    AUDIO(2),
    IMAGE(3),
    FILE(4),
    ;

    companion object {
        fun fromId(id: Int): PayloadType =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown payload type id $id")
    }
}
