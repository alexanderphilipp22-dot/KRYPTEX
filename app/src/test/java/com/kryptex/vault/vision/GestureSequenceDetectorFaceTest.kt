package com.kryptex.vault.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The face the detector reports must be the face seen while the user was calm and facing the
 * camera, not one caught mid-gesture, turned away or mid-blink. These tests give gesture frames, turned
 * frames and so on a *different* face from the resting frames, and outnumber the calm ones, so a
 * sampler that let them in would visibly report the wrong face.
 */
class GestureSequenceDetectorFaceTest {

    private fun face(factor: Float) = FaceDescriptor(FloatArray(FaceGeometry.DIMENSIONS) { (0.3f + 0.05f * it) * factor })

    private val owner = face(1f)
    private val other = face(1.3f)

    private class Driver(steps: Int = 3, config: DetectorConfig = DetectorConfig()) {
        val detector = GestureSequenceDetector(steps, config = config)
        var now = 0L
        lateinit var last: DetectorSnapshot

        fun feed(durationMs: Long, make: (Long) -> FaceFrame) {
            val end = now + durationMs
            do {
                last = detector.onFrame(make(now))
                now += 33L
            } while (now < end)
        }
    }

    private fun rest(g: FaceDescriptor?) = { t: Long -> FaceFrame(t, true, geometry = g) }
    private fun smile(g: FaceDescriptor?) = { t: Long -> FaceFrame(t, true, smile = 0.8f, geometry = g) }
    private fun mouth(g: FaceDescriptor?) = { t: Long -> FaceFrame(t, true, jawOpen = 0.8f, geometry = g) }
    private fun brow(g: FaceDescriptor?) = { t: Long -> FaceFrame(t, true, browRaise = 0.8f, geometry = g) }

    /** Calibration, then smile, open mouth, raise brows with a rest in between. */
    private fun Driver.perform(
        restFace: FaceDescriptor?,
        gestureFace: FaceDescriptor? = restFace,
        longHold: Long = 400,
    ) {
        feed(700, rest(restFace))
        feed(longHold, smile(gestureFace)); feed(300, rest(restFace))
        feed(longHold, mouth(gestureFace)); feed(300, rest(restFace))
        feed(400, brow(gestureFace))
    }

    @Test
    fun aFinishedSessionReportsTheFaceItSaw() {
        val d = Driver()
        d.perform(owner)
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun noGeometryMeansNoFace() {
        val d = Driver()
        d.perform(null)
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertNull(d.detector.faceDescriptor())
    }

    @Test
    fun tooFewSamplesMeanNoFace() {
        val d = Driver(config = DetectorConfig(minFaceSamples = 10_000))
        d.perform(owner)
        assertNull(d.detector.faceDescriptor())
    }

    @Test
    fun theFaceIsOnlyAvailableOnceTheSequenceIsComplete() {
        val d = Driver()
        d.feed(700, rest(owner))
        d.feed(400, smile(owner)); d.feed(300, rest(owner))
        assertEquals(DetectorPhase.CAPTURING, d.last.phase)
        assertNull(d.detector.faceDescriptor())

        d.feed(400, mouth(owner)); d.feed(300, rest(owner)); d.feed(400, brow(owner))
        assertNotNull(d.detector.faceDescriptor())
    }

    @Test
    fun framesShowingAGestureAreNotSampled() {
        val d = Driver()
        // The gestures are held long and carry a different face: far more frames than the calm ones.
        d.feed(700, rest(owner))
        d.feed(1_500, smile(other)); d.feed(300, rest(owner))
        d.feed(1_500, mouth(other)); d.feed(300, rest(owner))
        d.feed(400, brow(other))
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun framesWithTheHeadTurnedAwayAreNotSampled() {
        val d = Driver()
        d.feed(700, rest(owner))
        // Turned a little beyond what the classifier reads, with another face in view: lots of frames, none usable.
        d.feed(2_500) { FaceFrame(it, true, headYaw = 0.09f, geometry = other) }
        d.feed(300, rest(owner))
        d.feed(400, smile(owner)); d.feed(300, rest(owner))
        d.feed(400, mouth(owner)); d.feed(300, rest(owner))
        d.feed(400, brow(owner))
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun aHeadFarFromStraightOnIsNeverMeasured() {
        val d = Driver()
        // The whole session is performed with the head turned 0.15: readable for the gestures, too far round to measure a face.
        val turned = { t: Long, base: FaceFrame -> base.copy(timestampMs = t, headYaw = 0.15f) }
        d.feed(700) { turned(it, FaceFrame(it, true, geometry = owner)) }
        d.feed(400) { turned(it, FaceFrame(it, true, smile = 0.8f, geometry = owner)) }
        d.feed(300) { turned(it, FaceFrame(it, true, geometry = owner)) }
        d.feed(400) { turned(it, FaceFrame(it, true, jawOpen = 0.8f, geometry = owner)) }
        d.feed(300) { turned(it, FaceFrame(it, true, geometry = owner)) }
        d.feed(400) { turned(it, FaceFrame(it, true, browRaise = 0.8f, geometry = owner)) }
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertNull(d.detector.faceDescriptor())
    }

    @Test
    fun halfShutEyesAreNotSampled() {
        val d = Driver()
        d.feed(700, rest(owner))
        // Both lids at 0.52: no blink gesture (that needs 0.55), but not an open face either.
        d.feed(1_500) { FaceFrame(it, true, blinkLeft = 0.52f, blinkRight = 0.52f, geometry = other) }
        d.feed(300, rest(owner))
        d.feed(400, smile(owner)); d.feed(300, rest(owner))
        d.feed(400, mouth(owner)); d.feed(300, rest(owner))
        d.feed(400, brow(owner))
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun aFaceInTheMiddleOfAnExpressionIsNotSampled() {
        val d = Driver()
        d.feed(700, rest(owner))
        // A smile at 0.45 is below the 0.5 that counts as a gesture, yet it moves the face.
        d.feed(1_500) { FaceFrame(it, true, smile = 0.45f, geometry = other) }
        d.feed(300, rest(owner))
        d.feed(400, smile(owner)); d.feed(300, rest(owner))
        d.feed(400, mouth(owner)); d.feed(300, rest(owner))
        d.feed(400, brow(owner))
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun aRestingExpressionIsNotHeldAgainstTheUser() {
        // Someone who rests with a slight smile still gets measured: "calm" is relative to their own baseline.
        val d = Driver()
        val smiling = { g: FaceDescriptor? -> { t: Long -> FaceFrame(t, true, smile = 0.3f, geometry = g) } }
        d.feed(700, smiling(owner))
        d.feed(400) { FaceFrame(it, true, smile = 0.9f, geometry = owner) }; d.feed(300, smiling(owner))
        d.feed(400, mouth(owner)); d.feed(300, smiling(owner))
        d.feed(400, brow(owner))
        assertEquals(DetectorPhase.COMPLETE, d.last.phase)
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun aFewWildFramesDoNotMoveTheFace() {
        val d = Driver()
        var n = 0
        // One frame in five is a tracking glitch.
        val glitchy = { t: Long -> FaceFrame(t, true, geometry = if (n++ % 5 == 0) other else owner) }
        d.feed(700, glitchy)
        d.feed(400, smile(owner)); d.feed(300, glitchy)
        d.feed(400, mouth(owner)); d.feed(300, glitchy)
        d.feed(400, brow(owner))
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun onlyTheMostRecentCalmFramesAreKept() {
        fun session(config: DetectorConfig): FaceDescriptor? {
            val d = Driver(config = config)
            d.feed(700, rest(other)); d.feed(1_500, rest(other)) // about 67 frames of one face...
            d.feed(1_000, rest(owner))                           // ...then about 30 of another
            d.feed(400, smile(owner)); d.feed(300, rest(owner))
            d.feed(400, mouth(owner)); d.feed(300, rest(owner))
            d.feed(400, brow(owner))
            return d.detector.faceDescriptor()
        }
        assertEquals("with a short memory the latest face wins", owner, session(DetectorConfig(maxFaceSamples = 20)))
        assertEquals("with the full memory the majority wins", other, session(DetectorConfig()))
    }

    @Test
    fun theDetectorKeepsItsOwnCopyOfTheNumbers() {
        val shared = owner.copy()
        val d = Driver()
        d.perform(shared)
        shared.wipe() // someone zeroes the frame's descriptor after the fact
        assertEquals(owner, d.detector.faceDescriptor())

        // ...and the detector's own wipe leaves the caller's descriptor alone.
        val kept = owner.copy()
        val d2 = Driver()
        d2.perform(kept)
        d2.detector.wipe()
        assertEquals(owner, kept)
    }

    @Test
    fun wipingForgetsTheFaceAlongsideTheSequence() {
        val d = Driver()
        d.perform(owner)
        assertNotNull(d.detector.faceDescriptor())
        assertNotNull(d.detector.sequenceOrNull())

        d.detector.wipe()

        assertNull(d.detector.faceDescriptor())
        assertNull(d.detector.sequenceOrNull())
    }

    @Test
    fun theReportedFaceIsACopyThatTheCallerMayWipe() {
        val d = Driver()
        d.perform(owner)
        val first = d.detector.faceDescriptor()!!
        first.wipe()
        assertNotEquals(first, d.detector.faceDescriptor())
        assertEquals(owner, d.detector.faceDescriptor())
    }

    @Test
    fun aFailedSessionHasNoFace() {
        val d = Driver()
        d.feed(700, rest(owner))
        d.feed(5_500, rest(owner)) // idle timeout
        assertEquals(DetectorPhase.FAILED, d.last.phase)
        assertNull(d.detector.faceDescriptor())
    }
}
