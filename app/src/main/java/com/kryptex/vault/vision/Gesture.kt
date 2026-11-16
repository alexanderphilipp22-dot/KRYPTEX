package com.kryptex.vault.vision

/**
 * The gesture vocabulary. The numeric [id] is part of the secret derived from a gesture sequence
 * and therefore of every enrolled gesture slot: never renumber or reuse an id.
 *
 * "Left" and "right" are from the *user's* point of view (their own left eye, the direction of
 * their own gaze), not the camera's.
 */
enum class Gesture(val id: Int, val holdMillis: Long) {
    /** Both eyes closed deliberately. Held longer than a natural blink (about 100-150 ms). */
    LONG_BLINK(1, holdMillis = 400),
    WINK_LEFT(2, holdMillis = 300),
    WINK_RIGHT(3, holdMillis = 300),
    GAZE_LEFT(4, holdMillis = 300),
    GAZE_RIGHT(5, holdMillis = 300),
    GAZE_UP(6, holdMillis = 300),
    GAZE_DOWN(7, holdMillis = 300),
    BROW_RAISE(8, holdMillis = 300),
    MOUTH_OPEN(9, holdMillis = 300),
    SMILE(10, holdMillis = 300),
    ;

    companion object {
        fun fromId(id: Int): Gesture? = entries.firstOrNull { it.id == id }
    }
}
