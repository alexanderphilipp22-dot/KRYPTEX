package com.kryptex.vault.vision

/**
 * Notices the moment another gesture of the sequence has been accepted, from the stream of
 * [GlanceState]s. Plain Kotlin so the rule can be tested on the JVM.
 *
 * The last gesture of a sequence never shows up as a [GlanceState.Capturing] with a higher count:
 * the engine goes straight to [GlanceState.Completed], so that counts as well.
 */
class GestureProgressTracker {
    private var seen = 0

    /** @return true exactly once for every newly accepted gesture. */
    fun accepted(state: GlanceState): Boolean = when (state) {
        is GlanceState.Capturing -> advance(state.committedCount)
        is GlanceState.Completed -> advance(state.sequence.size)
        else -> {
            seen = 0 // a new session starts from nothing
            false
        }
    }

    private fun advance(count: Int): Boolean {
        val isNew = count > seen
        seen = count
        return isNew
    }
}
