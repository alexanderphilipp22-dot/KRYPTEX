package com.kryptex.vault.vision

import kotlin.math.max

enum class FailureReason {
    /** The overall time budget for the sequence ran out. */
    TIMEOUT,

    /** No gesture was completed for too long. */
    IDLE,

    /** No analysable frames arrived (camera stalled). */
    NO_FRAMES,
    PERMISSION_DENIED,
    NO_FRONT_CAMERA,
    CAMERA_ERROR,
    MODEL_ERROR,
}

data class DetectorConfig(
    val calibrationMillis: Long = 500,
    val minCalibrationFrames: Int = 8,
    /** A lost face for longer than this restarts calibration. */
    val calibrationFaceLossMillis: Long = 300,
    /**
     * After a gesture is accepted the face must rest for this long, with that gesture clearly let
     * go, before the next one counts.
     */
    val neutralGapMillis: Long = 250,
    /**
     * Unreadable frames (face lost, head turned) and brief dips shorter than this do not reset a
     * hold. A frame that clearly shows the gesture has ended always does.
     */
    val flickerToleranceMillis: Long = 100,
    /** Maximum time without completing a gesture. */
    val idleTimeoutMillis: Long = 5_000,
    /** Overall limit; scales with the number of steps so long sequences stay feasible. */
    val totalTimeoutBaseMillis: Long = 20_000,
    val totalTimeoutPerStepMillis: Long = 3_000,
    /**
     * Resting face sanity limits: a baseline taken mid-expression would hide that expression.
     * A calibration frame whose eyes are more closed than [maxRestingBlink] on average is a blink
     * and is left out of the baseline; most frames must be open-eyed.
     */
    val maxRestingBlink: Float = 0.5f,
    val maxRestingJaw: Float = 0.35f,
    val maxRestingSmile: Float = 0.6f,
)

enum class DetectorPhase { CALIBRATING, CAPTURING, COMPLETE, FAILED }

/** What the UI needs after every frame. [committed] lists accepted gestures in order. */
data class DetectorSnapshot(
    val phase: DetectorPhase,
    val faceDetected: Boolean,
    val calibrationProgress: Float,
    val committed: List<Gesture>,
    val candidate: Gesture?,
    val holdProgress: Float,
    val awaitingNeutral: Boolean,
    val millisRemaining: Long,
    val failure: FailureReason?,
)

/**
 * Turns a stream of [FaceFrame]s into a [GestureSequence]. All time comes from frame timestamps,
 * so behaviour is deterministic and testable. Not thread-safe: feed it from one thread.
 *
 * Rules:
 *  - The first ~0.5 s of face data defines the neutral baseline (then discarded with the detector).
 *  - A gesture counts after being held for its [Gesture.holdMillis]; natural blinks are too short.
 *    Unreadable frames and brief dips are bridged, but a frame that clearly shows the gesture has
 *    ended is not: two quick natural blinks never add up to one long blink.
 *  - After each accepted gesture the face must return to neutral, and that gesture must clearly
 *    let go, before the next one is accepted: one sagging smile is never counted twice.
 */
class GestureSequenceDetector(
    private val steps: Int,
    private val classifier: GestureClassifier = GestureClassifier(),
    private val config: DetectorConfig = DetectorConfig(),
) {
    init {
        require(steps in GestureSequence.MIN_STEPS..GestureSequence.MAX_STEPS) { "steps out of range: $steps" }
    }

    private var phase = DetectorPhase.CALIBRATING
    private var failure: FailureReason? = null

    // Calibration
    private val calibrationFrames = ArrayList<FaceFrame>()
    private var calibrationStart = -1L
    private var lastFaceSeen = -1L
    private var baseline = NeutralBaseline.ZERO

    // Capture
    private var captureStart = 0L
    private var lastProgress = 0L
    private val committed = ArrayList<Gesture>()
    private var candidate: Gesture? = null
    private var candidateStart = 0L
    private var candidateLastSeen = 0L
    private var holdProgress = 0f
    private var awaitingNeutral = false
    private var neutralSince = -1L

    private val totalTimeoutMillis = config.totalTimeoutBaseMillis + config.totalTimeoutPerStepMillis * max(0, steps - 3)
    private var lastFrame: FaceFrame? = null

    fun sequenceOrNull(): GestureSequence? =
        if (phase == DetectorPhase.COMPLETE && committed.size == steps) GestureSequence(committed.toList()) else null

    /**
     * Forgets everything recognised so far (the sequence is the secret) and ignores later frames.
     * Called when the session ends; the caller keeps its own copy of a completed sequence.
     */
    fun wipe() {
        committed.clear()
        calibrationFrames.clear()
        candidate = null
        lastFrame = null
        if (phase != DetectorPhase.COMPLETE) phase = DetectorPhase.FAILED
    }

    fun onFrame(frame: FaceFrame): DetectorSnapshot {
        lastFrame = frame
        when (phase) {
            DetectorPhase.CALIBRATING -> calibrate(frame)
            DetectorPhase.CAPTURING -> capture(frame)
            DetectorPhase.COMPLETE, DetectorPhase.FAILED -> Unit
        }
        return snapshot(frame)
    }

    /** Lets an external watchdog end the session with a specific reason. */
    fun fail(reason: FailureReason): DetectorSnapshot {
        if (phase != DetectorPhase.COMPLETE && phase != DetectorPhase.FAILED) {
            phase = DetectorPhase.FAILED
            failure = reason
        }
        return snapshot(lastFrame ?: FaceFrame.noFace(0))
    }

    // ---- calibration -----------------------------------------------------------------------

    private fun calibrate(frame: FaceFrame) {
        val ts = frame.timestampMs
        if (!frame.faceDetected) {
            if (calibrationStart >= 0 && ts - lastFaceSeen > config.calibrationFaceLossMillis) restartCalibration()
            return
        }
        lastFaceSeen = ts
        if (calibrationStart < 0) calibrationStart = ts
        calibrationFrames += frame

        if (ts - calibrationStart < config.calibrationMillis || calibrationFrames.size < config.minCalibrationFrames) return

        // People blink while holding still. Blink frames would drag the resting gaze (closing lids
        // also read as looking down) and the resting lid level, so they are left out; if most of
        // the window was eyes-closed, the user is not at rest yet.
        val openEyed = calibrationFrames.filter { (it.blinkLeft + it.blinkRight) / 2f <= config.maxRestingBlink }
        val resting = if (openEyed.size * 2 >= calibrationFrames.size) NeutralBaseline.median(openEyed) else null
        if (resting == null || resting.jawOpen > config.maxRestingJaw || resting.smile > config.maxRestingSmile) {
            restartCalibration() // "relax your face"
            return
        }

        baseline = resting
        calibrationFrames.clear()
        phase = DetectorPhase.CAPTURING
        captureStart = ts
        lastProgress = ts
        // The face is at rest now, so the first gesture may start immediately.
        awaitingNeutral = false
    }

    private fun restartCalibration() {
        calibrationFrames.clear()
        calibrationStart = -1L
    }

    // ---- capture ---------------------------------------------------------------------------

    private fun capture(frame: FaceFrame) {
        val ts = frame.timestampMs
        if (ts - captureStart > totalTimeoutMillis) {
            fail(FailureReason.TIMEOUT)
            return
        }
        if (ts - lastProgress > config.idleTimeoutMillis) {
            fail(FailureReason.IDLE)
            return
        }

        val seen = classifier.classify(frame, baseline, candidate)

        if (awaitingNeutral) {
            // Neutral = a readable frame with no gesture in it, where the last gesture has clearly
            // let go (not merely sagged below its threshold), continuously for neutralGapMillis.
            // Anything else, including an unreadable frame, starts the gap over.
            val last = committed.lastOrNull()
            val neutral = seen == null &&
                classifier.isReliable(frame, baseline) &&
                (last == null || classifier.isReleased(last, frame, baseline))
            if (neutral) {
                if (neutralSince < 0) neutralSince = ts
                if (ts - neutralSince >= config.neutralGapMillis) {
                    awaitingNeutral = false
                    neutralSince = -1L
                }
            } else {
                neutralSince = -1L
            }
            holdProgress = 0f
            return
        }

        if (seen != null) {
            if (seen != candidate) {
                candidate = seen
                candidateStart = ts
            }
            candidateLastSeen = ts
        } else {
            val current = candidate
            // Unreadable frames and brief dips are bridged for flickerToleranceMillis. A frame that
            // clearly shows the gesture has ended is not: bridging the eyes opening between two
            // quick natural blinks would add them up to one long blink.
            if (current != null &&
                (classifier.isReleased(current, frame, baseline) || ts - candidateLastSeen > config.flickerToleranceMillis)
            ) {
                candidate = null
            }
        }

        val current = candidate
        if (current == null) {
            holdProgress = 0f
            return
        }
        // Measured up to the last frame in which the gesture was actually seen: bridging a short
        // dropout keeps the hold alive, but the dropout itself must never count as holding.
        val held = candidateLastSeen - candidateStart
        holdProgress = (held.toFloat() / current.holdMillis).coerceIn(0f, 1f)
        if (held >= current.holdMillis) commit(current, ts)
    }

    private fun commit(gesture: Gesture, ts: Long) {
        committed += gesture
        candidate = null
        holdProgress = 0f
        lastProgress = ts
        if (committed.size >= steps) {
            phase = DetectorPhase.COMPLETE
        } else {
            awaitingNeutral = true
            neutralSince = -1L
        }
    }

    private fun snapshot(frame: FaceFrame): DetectorSnapshot {
        val ts = frame.timestampMs
        val calibrationProgress = when {
            phase != DetectorPhase.CALIBRATING -> 1f
            calibrationStart < 0 -> 0f
            else -> ((ts - calibrationStart).toFloat() / config.calibrationMillis).coerceIn(0f, 1f)
        }
        val remaining = if (phase == DetectorPhase.CAPTURING) {
            max(0L, minOf(totalTimeoutMillis - (ts - captureStart), config.idleTimeoutMillis - (ts - lastProgress)))
        } else {
            0L
        }
        return DetectorSnapshot(
            phase = phase,
            faceDetected = frame.faceDetected,
            calibrationProgress = calibrationProgress,
            committed = committed.toList(),
            candidate = candidate,
            holdProgress = holdProgress,
            awaitingNeutral = awaitingNeutral,
            millisRemaining = remaining,
            failure = failure,
        )
    }
}
