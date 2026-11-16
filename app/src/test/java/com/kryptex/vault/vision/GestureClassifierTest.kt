package com.kryptex.vault.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {
    private val classifier = GestureClassifier()
    private val rest = NeutralBaseline.ZERO

    private fun frame(
        blinkLeft: Float = 0f,
        blinkRight: Float = 0f,
        gazeX: Float = 0f,
        gazeY: Float = 0f,
        browRaise: Float = 0f,
        jawOpen: Float = 0f,
        smile: Float = 0f,
        headYaw: Float = 0f,
        headPitch: Float = 0f,
        faceDetected: Boolean = true,
    ) = FaceFrame(0, faceDetected, blinkLeft, blinkRight, gazeX, gazeY, browRaise, jawOpen, smile, headYaw, headPitch)

    private fun classify(f: FaceFrame, baseline: NeutralBaseline = rest, active: Gesture? = null) =
        classifier.classify(f, baseline, active)

    // ---- individual gestures ---------------------------------------------------------------

    @Test
    fun aRestingFaceIsNotAGesture() {
        assertNull(classify(frame()))
    }

    @Test
    fun noFaceIsNotAGesture() {
        assertNull(classify(frame(smile = 0.9f, faceDetected = false)))
    }

    @Test
    fun eachGestureIsRecognisedOnItsOwn() {
        assertEquals(Gesture.LONG_BLINK, classify(frame(blinkLeft = 0.9f, blinkRight = 0.9f)))
        assertEquals(Gesture.WINK_LEFT, classify(frame(blinkLeft = 0.9f, blinkRight = 0.05f)))
        assertEquals(Gesture.WINK_RIGHT, classify(frame(blinkLeft = 0.05f, blinkRight = 0.9f)))
        assertEquals(Gesture.GAZE_LEFT, classify(frame(gazeX = 0.8f)))
        assertEquals(Gesture.GAZE_RIGHT, classify(frame(gazeX = -0.8f)))
        assertEquals(Gesture.GAZE_UP, classify(frame(gazeY = 0.8f)))
        assertEquals(Gesture.GAZE_DOWN, classify(frame(gazeY = -0.8f)))
        assertEquals(Gesture.BROW_RAISE, classify(frame(browRaise = 0.8f)))
        assertEquals(Gesture.MOUTH_OPEN, classify(frame(jawOpen = 0.8f)))
        assertEquals(Gesture.SMILE, classify(frame(smile = 0.8f)))
    }

    @Test
    fun winkAndBlinkAreDistinguishedByTheOtherEye() {
        assertEquals(Gesture.LONG_BLINK, classify(frame(blinkLeft = 0.9f, blinkRight = 0.7f)))
        // One eye closed and the other half shut is neither a clean wink nor a blink.
        assertNull(classify(frame(blinkLeft = 0.9f, blinkRight = 0.45f)))
    }

    @Test
    fun gazeIsIgnoredWhileTheEyesAreClosing() {
        assertNull(classify(frame(blinkLeft = 0.5f, blinkRight = 0.5f, gazeX = 0.9f)))
    }

    @Test
    fun theDominantGazeAxisWins() {
        assertEquals(Gesture.GAZE_LEFT, classify(frame(gazeX = 0.6f, gazeY = 0.4f)))
        assertEquals(Gesture.GAZE_UP, classify(frame(gazeX = 0.3f, gazeY = 0.6f)))
    }

    // ---- head pose -------------------------------------------------------------------------

    @Test
    fun aTurnedHeadSuppressesGestures() {
        assertEquals(Gesture.SMILE, classify(frame(smile = 0.8f, headYaw = 0.05f)))
        assertNull(classify(frame(smile = 0.8f, headYaw = 0.12f)))
        assertNull(classify(frame(smile = 0.8f, headYaw = -0.12f)))
        assertNull(classify(frame(smile = 0.8f, headPitch = 0.12f)))
    }

    @Test
    fun headPoseIsMeasuredAgainstTheNeutralPoseNotAgainstZero() {
        val tiltedRest = NeutralBaseline(headYaw = 0.10f, headPitch = -0.08f)
        assertEquals(Gesture.SMILE, classify(frame(smile = 0.8f, headYaw = 0.10f, headPitch = -0.08f), tiltedRest))
        assertNull(classify(frame(smile = 0.8f, headYaw = 0.0f, headPitch = -0.08f), tiltedRest))
    }

    // ---- baseline, hysteresis, ambiguity ---------------------------------------------------

    @Test
    fun gesturesAreRelativeToTheRestingFace() {
        val baseline = NeutralBaseline(gazeX = 0.3f, smile = 0.3f)
        assertNull("resting gaze is not a gesture", classify(frame(gazeX = 0.3f, smile = 0.3f), baseline))
        assertEquals(Gesture.GAZE_LEFT, classify(frame(gazeX = 0.8f, smile = 0.3f), baseline))
        assertNull(classify(frame(smile = 0.6f), baseline)) // 0.3 above rest: below the 0.5 threshold
        assertEquals(Gesture.SMILE, classify(frame(smile = 0.85f), baseline))
    }

    @Test
    fun anActiveGestureSurvivesASlightDipButCannotStartThere() {
        val dip = frame(smile = 0.45f) // below 0.5, above 0.5 * 0.8
        assertNull(classify(dip, active = null))
        assertEquals(Gesture.SMILE, classify(dip, active = Gesture.SMILE))
    }

    @Test
    fun twoSimultaneousExpressionsAreAmbiguousAndNotCommitted() {
        // A wide grin that also opens the jaw: neither reading is trustworthy.
        assertNull(classify(frame(smile = 0.8f, jawOpen = 0.65f)))
        // But a clear winner still wins.
        assertEquals(Gesture.SMILE, classify(frame(smile = 0.9f, jawOpen = 0.45f)))
    }

    @Test
    fun baselineIsTheMedianOfTheRestingFrames() {
        val b = NeutralBaseline.median(listOf(frame(smile = 0.2f, gazeX = 0.1f), frame(smile = 0.4f, gazeX = 0.3f)))
        assertEquals(0.3f, b.smile, 1e-6f)
        assertEquals(0.2f, b.gazeX, 1e-6f)

        // One glitched frame does not move a median.
        val withGlitch = NeutralBaseline.median(
            listOf(frame(gazeY = 0.0f), frame(gazeY = 0.02f), frame(gazeY = -0.9f), frame(gazeY = 0.01f), frame(gazeY = 0.0f)),
        )
        assertEquals(0.0f, withGlitch.gazeY, 1e-6f)
    }

    // ---- resting lids, release, reliability ------------------------------------------------

    @Test
    fun eyeThresholdsFollowTheRestingLids() {
        // Looking down at a phone: the lids rest at 0.4.
        val droopy = NeutralBaseline(blinkLeft = 0.4f, blinkRight = 0.4f)
        assertNull("lids drooping a little further are not a blink", classify(frame(blinkLeft = 0.6f, blinkRight = 0.6f), droopy))
        assertEquals(Gesture.LONG_BLINK, classify(frame(blinkLeft = 0.9f, blinkRight = 0.9f), droopy))
        assertEquals(Gesture.WINK_LEFT, classify(frame(blinkLeft = 0.9f, blinkRight = 0.4f), droopy))
        assertEquals("gaze stays readable with resting lids", Gesture.GAZE_LEFT, classify(frame(blinkLeft = 0.4f, blinkRight = 0.4f, gazeX = 0.8f), droopy))

        // Wide-open eyes keep the fixed thresholds.
        assertEquals(Gesture.LONG_BLINK, classify(frame(blinkLeft = 0.6f, blinkRight = 0.6f)))
        assertNull(classify(frame(blinkLeft = 0.4f, blinkRight = 0.4f, gazeX = 0.8f)))
    }

    @Test
    fun aGestureEndsOnlyWhenItClearlyLetsGo() {
        assertFalse("still held", classifier.isReleased(Gesture.SMILE, frame(smile = 0.8f), rest))
        assertFalse("a dip is not an end", classifier.isReleased(Gesture.SMILE, frame(smile = 0.35f), rest))
        assertTrue(classifier.isReleased(Gesture.SMILE, frame(smile = 0.1f), rest))
        assertTrue("eyes open again after a blink", classifier.isReleased(Gesture.LONG_BLINK, frame(), rest))
        assertTrue(classifier.isReleased(Gesture.GAZE_LEFT, frame(gazeX = -0.1f), rest))
    }

    @Test
    fun anUnreadableFrameProvesNothing() {
        assertFalse(classifier.isReleased(Gesture.SMILE, frame(faceDetected = false), rest))
        assertFalse(classifier.isReleased(Gesture.SMILE, frame(headYaw = 0.2f), rest))
        assertFalse(
            "gaze cannot be read through closing lids",
            classifier.isReleased(Gesture.GAZE_LEFT, frame(blinkLeft = 0.9f, blinkRight = 0.9f), rest),
        )
        assertNull(classifier.level(Gesture.SMILE, frame(faceDetected = false), rest))
        assertFalse(classifier.isReliable(frame(headPitch = -0.2f), rest))
        assertTrue(classifier.isReliable(frame(), rest))
    }

    @Test
    fun levelIsZeroAtRestAndOneAtTheThreshold() {
        val droopy = NeutralBaseline(blinkLeft = 0.4f, blinkRight = 0.4f, smile = 0.2f)
        assertEquals(0f, classifier.level(Gesture.LONG_BLINK, frame(blinkLeft = 0.4f, blinkRight = 0.4f), droopy)!!, 1e-6f)
        assertEquals(0f, classifier.level(Gesture.SMILE, frame(blinkLeft = 0.4f, blinkRight = 0.4f, smile = 0.2f), droopy)!!, 1e-6f)
        assertEquals(1f, classifier.level(Gesture.SMILE, frame(smile = 0.5f), rest)!!, 1e-6f)
        assertEquals(1f, classifier.level(Gesture.LONG_BLINK, frame(blinkLeft = 0.55f, blinkRight = 0.55f), rest)!!, 1e-6f)
    }

    // ---- gaze / head geometry on synthetic landmarks ---------------------------------------

    private fun landmarks(
        rightIrisX: Float = 0.43f,
        leftIrisX: Float = 0.57f,
        noseX: Float = 0.50f,
        noseY: Float = 0.55f,
    ): FaceLandmarks {
        val x = FloatArray(478)
        val y = FloatArray(478)
        // User's right eye is on the image-left; corners 33 (outer) .. 133 (inner), width 0.06.
        x[FaceMesh.RIGHT_EYE_OUTER] = 0.40f
        x[FaceMesh.RIGHT_EYE_INNER] = 0.46f
        x[FaceMesh.LEFT_EYE_INNER] = 0.54f
        x[FaceMesh.LEFT_EYE_OUTER] = 0.60f
        x[FaceMesh.RIGHT_IRIS_CENTER] = rightIrisX
        x[FaceMesh.LEFT_IRIS_CENTER] = leftIrisX
        x[FaceMesh.CHEEK_RIGHT] = 0.30f
        x[FaceMesh.CHEEK_LEFT] = 0.70f
        y[FaceMesh.FOREHEAD] = 0.20f
        y[FaceMesh.CHIN] = 0.80f
        x[FaceMesh.NOSE_TIP] = noseX
        y[FaceMesh.NOSE_TIP] = noseY
        return FaceLandmarks(x, y)
    }

    @Test
    fun centredIrisesMeanNoHorizontalGaze() {
        assertEquals(0f, GazeEstimator.horizontalGaze(landmarks())!!, 1e-4f)
    }

    @Test
    fun irisesTowardTheNoseOnTheRightEyeAndOuterCornerOnTheLeftEyeMeanLookingLeft() {
        // Both eyes rotate toward the user's left: user's right iris -> nose, user's left iris -> outer corner.
        val gaze = GazeEstimator.horizontalGaze(landmarks(rightIrisX = 0.45f, leftIrisX = 0.59f))!!
        assertTrue("expected a clearly positive (left) gaze but was $gaze", gaze > 0.8f)
    }

    @Test
    fun theOppositeIrisMotionMeansLookingRight() {
        val gaze = GazeEstimator.horizontalGaze(landmarks(rightIrisX = 0.41f, leftIrisX = 0.55f))!!
        assertTrue("expected a clearly negative (right) gaze but was $gaze", gaze < -0.8f)
    }

    @Test
    fun gazeIsNullWithoutIrisLandmarks() {
        val noIris = FaceLandmarks(FloatArray(468), FloatArray(468))
        assertNull(GazeEstimator.horizontalGaze(noIris))
    }

    @Test
    fun noseTowardTheImageRightCheekIsAHeadTurnToTheUsersLeft() {
        assertEquals(0f, GazeEstimator.headPose(landmarks())!!.yaw, 1e-4f)
        assertEquals(0.125f, GazeEstimator.headPose(landmarks(noseX = 0.55f))!!.yaw, 1e-4f)
        assertEquals(-0.125f, GazeEstimator.headPose(landmarks(noseX = 0.45f))!!.yaw, 1e-4f)
    }

    @Test
    fun noseTowardTheForeheadIsATiltUp() {
        val frontal = GazeEstimator.headPose(landmarks(noseY = 0.55f))!!.pitch
        val up = GazeEstimator.headPose(landmarks(noseY = 0.45f))!!.pitch
        assertTrue(up > frontal)
    }

    @Test
    fun extractorBuildsAFrameFromLandmarksAndBlendshapes() {
        val blendshapes = mapOf(
            "eyeBlinkLeft" to 0.9f, "eyeBlinkRight" to 0.1f,
            "eyeLookUpLeft" to 0.6f, "eyeLookUpRight" to 0.4f,
            "eyeLookDownLeft" to 0.1f, "eyeLookDownRight" to 0.1f,
            "browInnerUp" to 0.7f, "jawOpen" to 0.2f,
            "mouthSmileLeft" to 0.8f, "mouthSmileRight" to 0.6f,
        )
        val f = FaceFeatureExtractor.extract(1234L, landmarks(), blendshapes)
        assertTrue(f.faceDetected)
        assertEquals(1234L, f.timestampMs)
        assertEquals(0.9f, f.blinkLeft, 1e-6f)
        assertEquals(0.1f, f.blinkRight, 1e-6f)
        assertEquals(0.4f, f.gazeY, 1e-6f) // up 0.5 - down 0.1
        assertEquals(0.7f, f.browRaise, 1e-6f)
        assertEquals(0.7f, f.smile, 1e-6f)
        assertEquals(0.2f, f.jawOpen, 1e-6f)
    }

    // ---- sequence encoding -----------------------------------------------------------------

    @Test
    fun sequenceSecretUsesStableGestureIds() {
        val seq = GestureSequence(listOf(Gesture.SMILE, Gesture.LONG_BLINK, Gesture.GAZE_UP))
        assertArrayEquals(byteArrayOf(1, 3, 10, 1, 6), seq.toSecret())
    }

    @Test
    fun differentOrdersGiveDifferentSecrets() {
        val a = GestureSequence(listOf(Gesture.SMILE, Gesture.LONG_BLINK, Gesture.GAZE_UP)).toSecret()
        val b = GestureSequence(listOf(Gesture.LONG_BLINK, Gesture.SMILE, Gesture.GAZE_UP)).toSecret()
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun sequenceLengthIsBounded() {
        assertThrows(IllegalArgumentException::class.java) { GestureSequence(List(2) { Gesture.SMILE }) }
        assertThrows(IllegalArgumentException::class.java) { GestureSequence(List(13) { Gesture.SMILE }) }
        assertNotNull(GestureSequence(List(12) { Gesture.SMILE }))
    }

    @Test
    fun sequenceNeverPrintsItsContent() {
        val text = GestureSequence(listOf(Gesture.SMILE, Gesture.LONG_BLINK, Gesture.GAZE_UP)).toString()
        assertFalse(text.contains("SMILE"))
        assertFalse(text.contains("BLINK"))
    }

    @Test
    fun entropyGrowsWithLength() {
        val six = GestureSequence(List(6) { Gesture.SMILE }).entropyBits
        assertEquals(19.93, six, 0.01)
        assertTrue(GestureSequence(List(8) { Gesture.SMILE }).entropyBits > six)
    }

    @Test
    fun gestureIdsAreUniqueAndStable() {
        assertEquals(Gesture.entries.size, Gesture.entries.map { it.id }.toSet().size)
        assertEquals(Gesture.LONG_BLINK, Gesture.fromId(1))
        assertEquals(Gesture.SMILE, Gesture.fromId(10))
        assertNull(Gesture.fromId(99))
    }
}
