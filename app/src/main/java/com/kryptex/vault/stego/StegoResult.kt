package com.kryptex.vault.stego

sealed interface EmbedOutcome {
    /** The pixels now carry the payload. */
    data class Embedded(val streamBytes: Long, val capacityBytes: Long) : EmbedOutcome {
        val usage: Double get() = if (capacityBytes == 0L) 1.0 else streamBytes.toDouble() / capacityBytes
    }

    /** The carrier is too small for this payload at the allowed fill level; nothing was changed. */
    data class TooLarge(val neededBytes: Long, val capacityBytes: Long) : EmbedOutcome
}

sealed interface ExtractOutcome {
    class Found(val container: PayloadContainer) : ExtractOutcome

    /**
     * No payload for this key. Deliberately covers three indistinguishable cases: the image never
     * held a payload, it holds one for a different key, or it was modified or re-encoded.
     */
    data object NothingFound : ExtractOutcome
}
