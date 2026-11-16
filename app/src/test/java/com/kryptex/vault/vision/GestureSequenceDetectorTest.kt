package com.kryptex.vault.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSequenceDetectorTest {

    /** Feeds synthetic ~30 fps frames and keeps the clock. */
    private class Driver(steps: Int, config: DetectorConfig = DetectorConfig()) {
        val detector = GestureSequenceDetector(steps, config = config)
        var now = 0L
        lateinit var last: DetectorSnapshot

        fun feed(durationMs: Long, make: (Long) -> FaceFrame) {
            val end = now + durationMs
            do {
                last = detector.onFrame(make(now))
                now += FRAME_MS
            } while (now < end)
        }

        fun rest(ms: Long) = feed(ms) { FaceFrame(it, true) }
        fun noFace(ms: Long) = feed(ms) { FaceFrame.noFace(it) }
        fun calibrate() = rest(700)

        fun hold(ms: Long, make: (Long) -> FaceFrame) = feed(ms, make)

        companion object {
            const val FRAME_MS = 33L
        }
    }

    private val smile = { t: Long -> FaceFrame(t, true, smile = 0.8f) }
    private val mouth = { t: Long -> FaceFrame(t, true, jawOpen = 0.8f) }
    private val brow = { t: Long -> FaceFrame(t, true, browRaise = 0.8f) }
    private val blink = { t: Long -> FaceFrame(t, true, blinkLeft = 0.9f, blinkRight = 0.9f) }
    private val gazeLeft = { t: Long -> FaceFrame(t, true, gazeX = 0.8f) }

    // ---- happy path ------------------------------------------------------------------------

    @Test
    fun calibratesThenAcceptsAFullSequenceInOrder() {
        val d = Driver(steps = 3)
        d.calibrate()
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)

        d.hold(400, smile); d.rest(300)
        d.hold(400, mouth); d.rest(300)
        d.hold(400, brow)

        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(
            GestureSequence(listOf(Gesture.SMILE, Gesture.MOUTH_OPEN, Gesture.BROW_RAISE)),
            d.detector.sequenceOrNull(),
        )
    }

    @Test
    fun progressIsReportedWhileHolding() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(150, smile)
        assertEquals(Gesture.SMILE, d.last.candidate)
        assertTrue("hold progress ${d.last.holdProgress}", d.last.holdProgress in 0.3f..0.8f)
        assertTrue(d.last.committed.isEmpty())
    }

    // ---- timing rules ----------------------------------------------------------------------

    @Test
    fun aNaturalBlinkIsNotAGestureButADeliberateOneIs() {
        val d = Driver(steps = 3)
        d.calibrate()

        d.hold(150, blink) // a normal blink lasts about 100-150 ms
        d.rest(300)
        assertTrue(d.last.committed.isEmpty())

        d.hold(350, blink) // still shorter than the 400 ms hold
        d.rest(300)
        assertTrue(d.last.committed.isEmpty())

        d.hold(480, blink)
        assertEquals(listOf(Gesture.LONG_BLINK), d.last.committed)
    }

    @Test
    fun holdingOneGestureLongerCountsOnlyOnce() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(2_000, smile)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
        assertTrue(d.last.awaitingNeutral)
    }

    @Test
    fun theFaceMustReturnToNeutralBeforeTheNextGesture() {
        val d = Driver(steps = 4)
        d.calibrate()
        d.hold(400, smile)
        // Jumping straight to another expression without resting is ignored...
        d.hold(600, mouth)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
        // ...until the face has rested.
        d.rest(300)
        d.hold(400, mouth)
        assertEquals(listOf(Gesture.SMILE, Gesture.MOUTH_OPEN), d.last.committed)
    }

    @Test
    fun theSameGestureTwiceInARowIsAllowedWithANeutralBetween() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(400, smile); d.rest(300)
        d.hold(400, smile); d.rest(300)
        d.hold(400, smile)
        assertEquals(List(3) { Gesture.SMILE }, d.detector.sequenceOrNull()?.gestures)
    }

    @Test
    fun aBrieflyLostFaceDoesNotBreakAHold() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(150, smile)
        d.noFace(66) // two frames where tracking dropped out
        d.hold(250, smile)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
    }

    @Test
    fun aBriefDipDoesNotBreakAHold() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(150, smile)
        d.hold(66) { FaceFrame(it, true, smile = 0.35f) } // sags, but is still clearly a smile
        d.hold(250, smile)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
    }

    @Test
    fun twoQuickNaturalBlinksDoNotAddUpToALongBlink() {
        val d = Driver(steps = 3)
        d.calibrate()
        // Each blink alone is short. Bridged across the gap they would span more than the 400 ms
        // hold, but the eyes visibly opened in between, so nothing may be counted.
        d.hold(200, blink)
        d.rest(66)
        d.hold(250, blink)
        d.rest(300)
        assertTrue(d.last.committed.isEmpty())
    }

    @Test
    fun aSustainedExpressionThatOnlySagsIsNotCountedTwice() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(400, smile)
        // Below the start threshold, but the smile never lets go: this is not a rest.
        d.hold(500) { FaceFrame(it, true, smile = 0.4f) }
        d.hold(400, smile)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
    }

    @Test
    fun turningTheHeadAwayIsNotARest() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(400, smile)
        // Still smiling with the head turned: unreadable, so it cannot count as the neutral gap.
        d.hold(500) { FaceFrame(it, true, smile = 0.8f, headYaw = 0.2f) }
        d.hold(400, smile)
        assertEquals(listOf(Gesture.SMILE), d.last.committed)
    }

    @Test
    fun aLongerInterruptionRestartsTheHold() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(200, smile)
        d.rest(200) // well beyond the flicker tolerance
        d.hold(200, smile)
        assertTrue(d.last.committed.isEmpty())
    }

    // ---- head and baseline -----------------------------------------------------------------

    @Test
    fun gesturesWithATurnedHeadAreIgnored() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(800) { FaceFrame(it, true, gazeX = 0.8f, headYaw = 0.15f) }
        assertTrue(d.last.committed.isEmpty())
        d.rest(300)
        d.hold(400, gazeLeft)
        assertEquals(listOf(Gesture.GAZE_LEFT), d.last.committed)
    }

    @Test
    fun theRestingFaceBecomesTheBaseline() {
        val d = Driver(steps = 3)
        // This user's eyes rest a little to the left and they have a slight smile.
        d.feed(700) { FaceFrame(it, true, gazeX = 0.3f, smile = 0.3f) }
        d.feed(600) { FaceFrame(it, true, gazeX = 0.3f, smile = 0.3f) }
        assertTrue("resting expression must not count as a gesture", d.last.committed.isEmpty())
        assertNull(d.last.candidate)

        d.feed(400) { FaceFrame(it, true, gazeX = 0.8f, smile = 0.3f) }
        assertEquals(listOf(Gesture.GAZE_LEFT), d.last.committed)
    }

    // ---- calibration -----------------------------------------------------------------------

    @Test
    fun calibrationWaitsForAFace() {
        val d = Driver(steps = 3)
        d.noFace(2_000)
        assertEquals(DetectorPhase.CALIBRATING, d.last.phase)
        assertFalse(d.last.faceDetected)
        d.calibrate()
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)
    }

    @Test
    fun losingTheFaceDuringCalibrationRestartsIt() {
        val d = Driver(steps = 3)
        d.rest(300)
        d.noFace(500)
        d.rest(300)
        assertEquals("only 300 ms of fresh data", DetectorPhase.CALIBRATING, d.last.phase)
        d.rest(400)
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)
    }

    @Test
    fun aBaselineTakenWithEyesShutIsRejected() {
        val d = Driver(steps = 3)
        d.feed(700, blink)
        assertEquals(DetectorPhase.CALIBRATING, d.last.phase)
        d.calibrate()
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)
    }

    @Test
    fun aBlinkDuringCalibrationDoesNotSkewTheRestingGaze() {
        val d = Driver(steps = 3)
        // Five of the resting frames are a natural blink; closing lids also read as looking down.
        var n = 0
        d.feed(700) { t ->
            if (n++ in 8..12) FaceFrame(t, true, blinkLeft = 0.9f, blinkRight = 0.9f, gazeY = -0.6f) else FaceFrame(t, true)
        }
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)
        // So a slight upward glance afterwards is still no gesture.
        d.hold(600) { FaceFrame(it, true, gazeY = 0.25f) }
        assertTrue(d.last.committed.isEmpty())
    }

    @Test
    fun lidsThatRestLowStillWorkAndDoNotBlinkByAccident() {
        val d = Driver(steps = 3)
        // Looking down at the phone: the lids rest at 0.4.
        val resting = { t: Long -> FaceFrame(t, true, blinkLeft = 0.4f, blinkRight = 0.4f) }
        d.feed(700, resting)
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)

        // Lids drooping a little further is not a blink...
        d.hold(600) { FaceFrame(it, true, blinkLeft = 0.6f, blinkRight = 0.6f) }
        d.feed(300, resting)
        assertTrue(d.last.committed.isEmpty())

        // ...a real blink is, and so is a glance made with the lids at rest.
        d.hold(480, blink)
        d.feed(300, resting)
        d.hold(400) { FaceFrame(it, true, blinkLeft = 0.4f, blinkRight = 0.4f, gazeX = 0.8f) }
        assertEquals(listOf(Gesture.LONG_BLINK, Gesture.GAZE_LEFT), d.last.committed)
    }

    @Test
    fun wipingForgetsTheSequence() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(400, smile); d.rest(300)
        d.hold(400, mouth); d.rest(300)
        d.hold(400, brow)
        assertNotNull(d.detector.sequenceOrNull())

        d.detector.wipe()
        assertNull(d.detector.sequenceOrNull())
        assertTrue(d.detector.onFrame(FaceFrame(d.now, true)).committed.isEmpty())
    }

    // ---- timeouts --------------------------------------------------------------------------

    @Test
    fun noGestureForTooLongEndsTheSession() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.rest(5_500)
        assertEquals(DetectorPhase.FAILED, d.last.phase)
        assertEquals(FailureReason.IDLE, d.last.failure)
        assertNull(d.detector.sequenceOrNull())
    }

    @Test
    fun theOverallTimeLimitEndsTheSession() {
        val d = Driver(
            steps = 3,
            config = DetectorConfig(totalTimeoutBaseMillis = 2_000, idleTimeoutMillis = 60_000),
        )
        d.calibrate()
        d.rest(2_500)
        assertEquals(DetectorPhase.FAILED, d.last.phase)
        assertEquals(FailureReason.TIMEOUT, d.last.failure)
    }

    @Test
    fun theTimeLimitGrowsWithTheSequenceLength() {
        // Budget = base + perStep * (steps - 3): 3000 ms for 3 steps, 7000 ms for 5 steps.
        val config = DetectorConfig(totalTimeoutBaseMillis = 3_000, totalTimeoutPerStepMillis = 2_000, idleTimeoutMillis = 60_000)
        val short = Driver(steps = 3, config = config)
        val long = Driver(steps = 5, config = config)
        short.calibrate(); long.calibrate()

        short.rest(4_000)
        long.rest(4_000)

        assertEquals(DetectorPhase.FAILED, short.last.phase)
        assertEquals(FailureReason.TIMEOUT, short.last.failure)
        assertEquals(DetectorPhase.CAPTURING, long.last.phase)
    }

    @Test
    fun anExternalWatchdogCanFailTheSessionOnce() {
        val d = Driver(steps = 3)
        d.calibrate()
        val failed = d.detector.fail(FailureReason.NO_FRAMES)
        assertEquals(DetectorPhase.FAILED, failed.phase)
        assertEquals(FailureReason.NO_FRAMES, failed.failure)

        // A later failure must not overwrite the first reason.
        assertEquals(FailureReason.NO_FRAMES, d.detector.fail(FailureReason.TIMEOUT).failure)
    }

    // ---- terminal state --------------------------------------------------------------------

    @Test
    fun framesAfterCompletionAreIgnored() {
        val d = Driver(steps = 3)
        d.calibrate()
        d.hold(400, smile); d.rest(300)
        d.hold(400, mouth); d.rest(300)
        d.hold(400, brow)
        val done = d.detector.sequenceOrNull()
        assertNotNull(done)

        d.rest(300)
        d.hold(600, blink)
        assertEquals(done, d.detector.sequenceOrNull())
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
    }
}
