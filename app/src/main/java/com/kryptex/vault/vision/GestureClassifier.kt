package com.kryptex.vault.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decision thresholds. They are starting values from MediaPipe's blendshape ranges and must be
 * tuned on real faces and devices; nothing else in the pipeline depends on the exact numbers.
 *
 * Every gesture is measured as a *level*: 0 at the resting face, 1 at its entry threshold. Three
 * bands follow from that (a Schmitt trigger with a dead zone):
 *  - level >= 1 starts a gesture;
 *  - an active gesture continues while level >= [hysteresis];
 *  - level < [release] means it has clearly ended. In between is a dip, which a short dropout
 *    tolerance may bridge; a clear end is never bridged.
 */
data class GestureThresholds(
    /** Both eyes at least this closed for a long blink. */
    val blinkClosed: Float = 0.55f,
    /** Winking eye at least this closed... */
    val winkClosed: Float = 0.60f,
    /** ...while the other eye stays below this. */
    val winkOpen: Float = 0.35f,
    /** Eyes count as open for gaze gestures below this closure. */
    val eyesOpen: Float = 0.35f,
    /**
     * The eye thresholds above are floors for wide-open eyes. Lids that rest lower (some eye
     * shapes, and everyone looking down at a phone) raise them: "closed" then means at least this
     * much more closed than at rest...
     */
    val closedAboveRest: Float = 0.30f,
    /** ...and "still open" reaches at least this far above rest. */
    val openAboveRest: Float = 0.12f,
    /** A closure threshold never rises above this, so a real blink can always reach it. */
    val maxClosed: Float = 0.80f,
    val gaze: Float = 0.35f,
    val brow: Float = 0.45f,
    val jaw: Float = 0.40f,
    val smile: Float = 0.50f,
    /**
     * Frames where the head has turned or tilted more than this from the neutral pose are ignored
     * (landmark-ratio units; roughly 20 degrees), so "look left" means eyes, not head.
     */
    val maxHeadOffset: Float = 0.07f,
    /** An already-active gesture only has to stay at this level. */
    val hysteresis: Float = 0.8f,
    /** Below this level a gesture has clearly ended, as opposed to briefly dipping. */
    val release: Float = 0.6f,
    /** The runner-up must score below this fraction of the winner, otherwise the frame is ambiguous. */
    val ambiguityRatio: Float = 0.85f,
)

/** The user's resting face, measured at the start of each session and discarded afterwards. */
data class NeutralBaseline(
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val headYaw: Float = 0f,
    val headPitch: Float = 0f,
    val browRaise: Float = 0f,
    val jawOpen: Float = 0f,
    val smile: Float = 0f,
    /** Resting closure of each eye; near 0 for wide-open eyes. */
    val blinkLeft: Float = 0f,
    val blinkRight: Float = 0f,
) {
    companion object {
        val ZERO = NeutralBaseline()

        /**
         * Per-signal median of the resting frames. Unlike a mean it shrugs off the odd outlier
         * (a twitch, a tracking glitch) that would otherwise shift every later decision.
         */
        fun median(frames: List<FaceFrame>): NeutralBaseline {
            require(frames.isNotEmpty()) { "no frames" }
            val buffer = FloatArray(frames.size)
            fun med(signal: (FaceFrame) -> Float): Float {
                for (i in frames.indices) buffer[i] = signal(frames[i])
                buffer.sort()
                val mid = buffer.size / 2
                return if (buffer.size % 2 == 1) buffer[mid] else (buffer[mid - 1] + buffer[mid]) / 2f
            }
            return NeutralBaseline(
                gazeX = med { it.gazeX },
                gazeY = med { it.gazeY },
                headYaw = med { it.headYaw },
                headPitch = med { it.headPitch },
                browRaise = med { it.browRaise },
                jawOpen = med { it.jawOpen },
                smile = med { it.smile },
                blinkLeft = med { it.blinkLeft },
                blinkRight = med { it.blinkRight },
            )
        }
    }
}

/**
 * Maps a single frame to at most one gesture, and says how strongly a given gesture is present.
 * Timing (hold time, neutral gaps, dropouts) is not its job; that belongs to
 * [GestureSequenceDetector].
 */
class GestureClassifier(private val t: GestureThresholds = GestureThresholds()) {

    /**
     * Whether the frame can be judged at all: a face in view, with the head close to its resting
     * pose. Other frames neither show a gesture nor prove that one has ended.
     */
    fun isReliable(frame: FaceFrame, baseline: NeutralBaseline): Boolean =
        frame.faceDetected &&
            abs(frame.headYaw - baseline.headYaw) <= t.maxHeadOffset &&
            abs(frame.headPitch - baseline.headPitch) <= t.maxHeadOffset

    /** @param active the gesture recognised in the previous frame, which gets hysteresis. */
    fun classify(frame: FaceFrame, baseline: NeutralBaseline, active: Gesture?): Gesture? {
        if (!isReliable(frame, baseline)) return null
        val eyes = Eyes(baseline)

        var best: Gesture? = null
        var bestScore = 0f
        var secondScore = 0f
        fun offer(g: Gesture, level: Float) {
            val bar = if (g == active) t.hysteresis else 1f
            if (level < bar) return
            val score = level / bar
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                best = g
            } else if (score > secondScore) {
                secondScore = score
            }
        }
        // Relaxed "other eye is open" limit while a wink is already being held.
        fun openLimit(g: Gesture, limit: Float) = if (g == active) limit / t.hysteresis else limit

        offer(Gesture.LONG_BLINK, eyes.blinkLevel(frame))
        if (frame.blinkRight <= openLimit(Gesture.WINK_LEFT, eyes.winkOpenRight)) offer(Gesture.WINK_LEFT, eyes.winkLeftLevel(frame))
        if (frame.blinkLeft <= openLimit(Gesture.WINK_RIGHT, eyes.winkOpenLeft)) offer(Gesture.WINK_RIGHT, eyes.winkRightLevel(frame))

        // Gaze needs both eyes open, otherwise the lids distort the iris position.
        if (eyes.openForGaze(frame, relaxed = active in GAZE_GESTURES)) {
            val dx = frame.gazeX - baseline.gazeX
            val dy = frame.gazeY - baseline.gazeY
            if (abs(dx) >= abs(dy)) {
                offer(Gesture.GAZE_LEFT, dx / t.gaze)
                offer(Gesture.GAZE_RIGHT, -dx / t.gaze)
            } else {
                offer(Gesture.GAZE_UP, dy / t.gaze)
                offer(Gesture.GAZE_DOWN, -dy / t.gaze)
            }
        }

        offer(Gesture.BROW_RAISE, (frame.browRaise - baseline.browRaise) / t.brow)
        offer(Gesture.MOUTH_OPEN, (frame.jawOpen - baseline.jawOpen) / t.jaw)
        offer(Gesture.SMILE, (frame.smile - baseline.smile) / t.smile)

        // Two expressions at once (a wide grin also opens the mouth) are not committed as either.
        if (secondScore > bestScore * t.ambiguityRatio) return null
        return best
    }

    /**
     * How strongly [gesture] shows in [frame]: 0 at the resting face, 1 at its entry threshold.
     * Null when the frame cannot tell (no face, head turned away, gaze behind closing lids).
     */
    fun level(gesture: Gesture, frame: FaceFrame, baseline: NeutralBaseline): Float? {
        if (!isReliable(frame, baseline)) return null
        val eyes = Eyes(baseline)
        return when (gesture) {
            Gesture.LONG_BLINK -> eyes.blinkLevel(frame)
            Gesture.WINK_LEFT -> eyes.winkLeftLevel(frame)
            Gesture.WINK_RIGHT -> eyes.winkRightLevel(frame)
            Gesture.GAZE_LEFT, Gesture.GAZE_RIGHT, Gesture.GAZE_UP, Gesture.GAZE_DOWN -> {
                if (!eyes.openForGaze(frame, relaxed = true)) return null
                when (gesture) {
                    Gesture.GAZE_LEFT -> (frame.gazeX - baseline.gazeX) / t.gaze
                    Gesture.GAZE_RIGHT -> (baseline.gazeX - frame.gazeX) / t.gaze
                    Gesture.GAZE_UP -> (frame.gazeY - baseline.gazeY) / t.gaze
                    else -> (baseline.gazeY - frame.gazeY) / t.gaze
                }
            }
            Gesture.BROW_RAISE -> (frame.browRaise - baseline.browRaise) / t.brow
            Gesture.MOUTH_OPEN -> (frame.jawOpen - baseline.jawOpen) / t.jaw
            Gesture.SMILE -> (frame.smile - baseline.smile) / t.smile
        }
    }

    /**
     * True when [frame] clearly shows that [gesture] has ended (eyes open again after a blink, a
     * smile relaxed), as opposed to a brief dip or a frame that cannot be read.
     */
    fun isReleased(gesture: Gesture, frame: FaceFrame, baseline: NeutralBaseline): Boolean =
        level(gesture, frame, baseline)?.let { it < t.release } ?: false

    /** Eye thresholds for this user's resting lids (see [GestureThresholds.closedAboveRest]). */
    private inner class Eyes(baseline: NeutralBaseline) {
        private val restLeft = baseline.blinkLeft
        private val restRight = baseline.blinkRight
        private val blinkLeft = closed(t.blinkClosed, restLeft)
        private val blinkRight = closed(t.blinkClosed, restRight)
        private val winkLeft = closed(t.winkClosed, restLeft)
        private val winkRight = closed(t.winkClosed, restRight)
        val winkOpenLeft = open(t.winkOpen, restLeft)
        val winkOpenRight = open(t.winkOpen, restRight)
        private val gazeOpenLeft = open(t.eyesOpen, restLeft)
        private val gazeOpenRight = open(t.eyesOpen, restRight)

        /** Both eyes must close, so the less closed one decides. */
        fun blinkLevel(f: FaceFrame) = min(eyeLevel(f.blinkLeft, restLeft, blinkLeft), eyeLevel(f.blinkRight, restRight, blinkRight))
        fun winkLeftLevel(f: FaceFrame) = eyeLevel(f.blinkLeft, restLeft, winkLeft)
        fun winkRightLevel(f: FaceFrame) = eyeLevel(f.blinkRight, restRight, winkRight)

        fun openForGaze(f: FaceFrame, relaxed: Boolean): Boolean {
            val factor = if (relaxed) 1f / t.hysteresis else 1f
            return f.blinkLeft <= gazeOpenLeft * factor && f.blinkRight <= gazeOpenRight * factor
        }

        private fun closed(floor: Float, rest: Float) = min(max(floor, rest + t.closedAboveRest), max(floor, t.maxClosed))
        private fun open(floor: Float, rest: Float) = max(floor, rest + t.openAboveRest)

        /** 0 at the eye's resting closure, 1 at [threshold]. */
        private fun eyeLevel(value: Float, rest: Float, threshold: Float) = (value - rest) / max(threshold - rest, MIN_EYE_SPAN)
    }

    private companion object {
        val GAZE_GESTURES = setOf(Gesture.GAZE_LEFT, Gesture.GAZE_RIGHT, Gesture.GAZE_UP, Gesture.GAZE_DOWN)

        /** Keeps the eye level finite for an eye that rests almost closed. */
        const val MIN_EYE_SPAN = 0.1f
    }
}
