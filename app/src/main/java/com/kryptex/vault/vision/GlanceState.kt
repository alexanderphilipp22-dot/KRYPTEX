package com.kryptex.vault.vision

/**
 * What the Gesture Auth screen renders. Terminal states ([Completed], [Failed]) mean the camera is
 * already being released.
 */
sealed interface GlanceState {
    /** Nothing running; the camera is off. */
    data object Idle : GlanceState

    /** Loading the model and opening the camera after an explicit tap. */
    data object Starting : GlanceState

    /** Measuring the resting face for about half a second. */
    data class Calibrating(val progress: Float, val faceDetected: Boolean) : GlanceState

    /**
     * Waiting for gestures.
     *
     * [committed] and [candidate] are only filled when the session was started with `reveal = true`
     * (enrolment). During unlock they stay empty/null so the screen can only show anonymous dots.
     */
    data class Capturing(
        val steps: Int,
        val committedCount: Int,
        val committed: List<Gesture>,
        val candidate: Gesture?,
        val holdProgress: Float,
        val awaitingNeutral: Boolean,
        val faceDetected: Boolean,
        /** Time left for the next gesture (or the whole sequence, if that runs out first). */
        val secondsLeft: Int,
        /** Length of the per-gesture window that [secondsLeft] counts down from. */
        val totalSeconds: Int,
    ) : GlanceState

    /**
     * @param face the proportions of the face the session saw, for the optional face check; null if
     *   too few calm frames were measured. Whoever consumes the state owns it and wipes it.
     */
    data class Completed(val sequence: GestureSequence, val face: FaceDescriptor? = null) : GlanceState

    data class Failed(val reason: FailureReason) : GlanceState
}
