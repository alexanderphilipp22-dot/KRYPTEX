package com.kryptex.vault.vision

import com.kryptex.vault.crypto.VaultKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * The face check only ever sees ten ratios, so these tests build a small 3D head, show it the way
 * MediaPipe would (any distance, sideways buffer, mirrored, turned) and require the same ratios, and
 * show clearly different heads and require clearly different ratios.
 */
class FaceGeometryTest {

    private data class P(val x: Float, val y: Float, val z: Float)

    /** A head in units of the distance between the outer eye corners. z grows away from the camera, as in MediaPipe. */
    private class Head(val points: Map<Int, P>) {
        fun rotated(yawDeg: Double, pitchDeg: Double = 0.0, rollDeg: Double = 0.0): Head {
            val yaw = Math.toRadians(yawDeg)
            val pitch = Math.toRadians(pitchDeg)
            val roll = Math.toRadians(rollDeg)
            return Head(points.mapValues { (_, p) ->
                // Turn around the middle of the face, not the camera.
                val x0 = p.x
                val y0 = p.y - 0.3f
                val z0 = p.z
                val x1 = (x0 * cos(yaw) + z0 * sin(yaw)).toFloat()
                val z1 = (-x0 * sin(yaw) + z0 * cos(yaw)).toFloat()
                val y2 = (y0 * cos(pitch) - z1 * sin(pitch)).toFloat()
                val z2 = (y0 * sin(pitch) + z1 * cos(pitch)).toFloat()
                val x3 = (x1 * cos(roll) - y2 * sin(roll)).toFloat()
                val y3 = (x1 * sin(roll) + y2 * cos(roll)).toFloat()
                P(x3, y3 + 0.3f, z2)
            })
        }

        fun shot(width: Float, height: Float, pixelsPerUnit: Float): Shot =
            Shot(width, height, points.mapValues { (_, p) ->
                P(width / 2 + p.x * pixelsPerUnit, height / 2 + p.y * pixelsPerUnit, p.z * pixelsPerUnit)
            })
    }

    /** What the camera buffer holds, in pixels, with depth in the same pixel scale as x. */
    private class Shot(val width: Float, val height: Float, val points: Map<Int, P>) {
        fun landmarks(withDepth: Boolean = true): FaceLandmarks {
            val x = FloatArray(478)
            val y = FloatArray(478)
            val z = FloatArray(478)
            points.forEach { (i, p) ->
                x[i] = p.x / width // exactly how MediaPipe normalises
                y[i] = p.y / height
                z[i] = if (withDepth) p.z / width else 0f
            }
            return FaceLandmarks.fromNormalized(x, y, aspect = width / height, z = z)
        }

        fun turnedClockwise() = Shot(height, width, points.mapValues { (_, p) -> P(height - p.y, p.x, p.z) })
        fun mirrored() = Shot(width, height, points.mapValues { (_, p) -> P(width - p.x, p.y, p.z) })
    }

    private fun head(
        innerEye: Float = 0.17f,
        tipY: Float = 0.62f,
        tipZ: Float = -0.30f,
        baseY: Float = 0.72f,
        wing: Float = 0.22f,
        wingY: Float = 0.70f,
    ) = Head(
        mapOf(
            FaceMesh.RIGHT_EYE_OUTER to P(-0.5f, 0f, 0f),
            FaceMesh.RIGHT_EYE_INNER to P(-innerEye, 0f, -0.03f),
            FaceMesh.LEFT_EYE_INNER to P(innerEye, 0f, -0.03f),
            FaceMesh.LEFT_EYE_OUTER to P(0.5f, 0f, 0f),
            FaceMesh.NOSE_BRIDGE to P(0f, 0.02f, -0.06f),
            FaceMesh.NOSE_TIP to P(0f, tipY, tipZ),
            FaceMesh.SUBNASALE to P(0f, baseY, -0.20f),
            FaceMesh.NOSTRIL_RIGHT to P(-wing, wingY, -0.12f),
            FaceMesh.NOSTRIL_LEFT to P(wing, wingY, -0.12f),
        ),
    )

    private val owner = head()

    /** A face that is clearly someone else: wider-set eyes, a longer, wider nose. */
    private val stranger = head(innerEye = 0.21f, tipY = 0.70f, tipZ = -0.34f, baseY = 0.80f, wing = 0.27f, wingY = 0.78f)

    /** Differs by only a few per cent in each ratio. */
    private val lookAlike = head(innerEye = 0.176f, tipY = 0.64f, tipZ = -0.31f, baseY = 0.735f, wing = 0.227f, wingY = 0.71f)

    private fun measure(shot: Shot, withDepth: Boolean = true): FaceDescriptor =
        FaceGeometry.measure(shot.landmarks(withDepth)) ?: error("a complete face must be measurable")

    private fun measure(head: Head) = measure(head.shot(480f, 640f, 200f))

    /** The ten numbers, read back out of the stored form. */
    private fun FaceDescriptor.numbers(): List<Float> {
        val buffer = ByteBuffer.wrap(toBytes())
        buffer.position(2)
        return List(FaceGeometry.DIMENSIONS) { buffer.getFloat() }
    }

    // ---- what is measured ------------------------------------------------------------------

    @Test
    fun aFaceYieldsTenRatiosOfAboutTheExpectedSize() {
        val numbers = measure(owner).numbers()
        assertEquals(FaceGeometry.DIMENSIONS, numbers.size)
        assertEquals("inner-eye distance", 0.34f, numbers[0], 1e-3f)
        assertEquals("eye width", 0.3314f, numbers[1], 1e-3f) // 0.33 across, 0.03 deep
        assertEquals("nose width", 0.44f, numbers[5], 1e-3f)
        assertTrue(numbers.all { it in 0.1f..1.5f })
    }

    @Test
    fun distanceAndShiftOfTheFaceChangeNothing() {
        val reference = measure(owner)
        val far = measure(owner.shot(480f, 640f, 110f))
        val near = measure(owner.shot(1080f, 1920f, 520f))
        assertTrue(reference.distanceTo(far) < 0.05f)
        assertTrue(reference.distanceTo(near) < 0.05f)
    }

    @Test
    fun aSidewaysAndMirroredCameraBufferGivesTheSameRatios() {
        val shot = owner.shot(480f, 640f, 200f)
        val reference = measure(shot)
        var turned = shot
        for (quarterTurns in 1..3) {
            turned = turned.turnedClockwise()
            assertTrue("$quarterTurns quarter turns", reference.distanceTo(measure(turned)) < 0.05f)
        }
        assertTrue("mirrored", reference.distanceTo(measure(shot.mirrored())) < 0.05f)
        assertTrue("mirrored and sideways", reference.distanceTo(measure(shot.mirrored().turnedClockwise())) < 0.05f)
    }

    @Test
    fun aHeadTurnedInThreeDimensionsGivesTheSameRatios() {
        val reference = measure(owner)
        for ((yaw, pitch, roll) in listOf(
            Triple(25.0, 0.0, 0.0), Triple(-25.0, 0.0, 0.0),
            Triple(0.0, 15.0, 0.0), Triple(0.0, -15.0, 0.0),
            Triple(0.0, 0.0, 20.0), Triple(18.0, -10.0, 12.0),
        )) {
            val turned = owner.rotated(yaw, pitch, roll).shot(480f, 640f, 200f)
            assertTrue("yaw $yaw pitch $pitch roll $roll", reference.distanceTo(measure(turned)) < 0.05f)
            assertTrue("and sideways", reference.distanceTo(measure(turned.turnedClockwise())) < 0.05f)
        }
    }

    @Test
    fun withoutDepthATiltedHeadWouldReadDifferently() {
        // The reason the depth coordinate is used at all: a flat reading squashes the nose when the
        // head is tipped up or down, so the same face would read as a different one.
        val front = owner.shot(480f, 640f, 200f)
        val tipped = owner.rotated(yawDeg = 0.0, pitchDeg = 30.0).shot(480f, 640f, 200f)
        val flatDistance = measure(front, withDepth = false).distanceTo(measure(tipped, withDepth = false))
        assertTrue("flat distance $flatDistance", flatDistance > FaceGeometry.MATCH_THRESHOLD)
        assertTrue("with depth", measure(front).distanceTo(measure(tipped)) < 0.05f)
    }

    // ---- telling people apart --------------------------------------------------------------

    @Test
    fun theSameFaceMatchesItself() {
        assertEquals(0f, measure(owner).distanceTo(measure(owner)), 1e-6f)
        assertTrue(measure(owner).matches(measure(owner)))
    }

    @Test
    fun smallMeasurementNoiseStaysWellInsideTheThreshold() {
        // A deterministic jitter of about 1.5 % of the eye distance on every coordinate, for 40 frames.
        var seed = 12345L
        fun jitter(): Float {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33).toInt() % 2001 - 1000) / 1000f * 0.015f
        }
        val frames = List(40) {
            val noisy = Head(owner.points.mapValues { (_, p) -> P(p.x + jitter(), p.y + jitter(), p.z + jitter()) })
            measure(noisy)
        }
        val median = FaceDescriptor.median(frames)!!
        assertTrue("median of noisy frames: ${median.distanceTo(measure(owner))}", median.distanceTo(measure(owner)) < 0.6f)
        assertTrue(median.matches(measure(owner)))
    }

    @Test
    fun aClearlyDifferentFaceIsFarOutsideTheThreshold() {
        val distance = measure(owner).distanceTo(measure(stranger))
        assertTrue("stranger distance $distance", distance > 1.5f * FaceGeometry.MATCH_THRESHOLD)
        assertFalse(measure(owner).matches(measure(stranger)))
        assertFalse(measure(stranger).matches(measure(owner)))
    }

    @Test
    fun aVerySimilarFaceCanPassWhichIsWhyThisIsOnlyARoughCheck() {
        // Documented limitation, not a goal: the check turns away different faces, not look-alikes.
        assertTrue(measure(owner).matches(measure(lookAlike)))
    }

    @Test
    fun theDistanceIsSymmetricAndOrderedByHowDifferentTheFacesAre() {
        val a = measure(owner)
        val b = measure(lookAlike)
        val c = measure(stranger)
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-5f)
        assertTrue(a.distanceTo(b) < a.distanceTo(c))
    }

    // ---- unusable meshes -------------------------------------------------------------------

    @Test
    fun aCollapsedOrIncompleteMeshGivesNoMeasurement() {
        assertNull("all zero", FaceGeometry.measure(FaceLandmarks(FloatArray(478), FloatArray(478))))
        assertNull("too few points", FaceGeometry.measure(FaceLandmarks(FloatArray(300), FloatArray(300))))
        // A face far away: 2 % of the frame height between the eyes.
        assertNull("too small", FaceGeometry.measure(owner.shot(480f, 640f, 12f).landmarks()))
    }

    @Test
    fun nonFiniteLandmarksGiveNoMeasurement() {
        val lm = owner.shot(480f, 640f, 200f).landmarks()
        lm.x[FaceMesh.NOSE_TIP] = Float.NaN
        assertNull(FaceGeometry.measure(lm))
        val lm2 = owner.shot(480f, 640f, 200f).landmarks()
        lm2.z[FaceMesh.SUBNASALE] = Float.POSITIVE_INFINITY
        assertNull(FaceGeometry.measure(lm2))
    }

    @Test
    fun landmarkDepthIsScaledLikeX() {
        // MediaPipe: z "roughly the same scale as x", so both are multiplied by width / height.
        val lm = FaceLandmarks.fromNormalized(floatArrayOf(0.5f), floatArrayOf(0.5f), aspect = 0.75f, z = floatArrayOf(-0.2f))
        assertEquals(0.375f, lm.x[0], 1e-6f)
        assertEquals(-0.15f, lm.z[0], 1e-6f)
        val two = FaceLandmarks(floatArrayOf(0f, 3f), floatArrayOf(0f, 0f), floatArrayOf(0f, 4f))
        assertEquals(5f, two.distance(0, 1), 1e-6f)
    }

    @Test
    fun theExtractorFillsInTheGeometryOfAFullMesh() {
        val frame = FaceFeatureExtractor.extract(0, owner.shot(480f, 640f, 200f).landmarks(), emptyMap())
        assertNotNull(frame.geometry)
        assertTrue(frame.geometry!!.matches(measure(owner)))
        assertNull(FaceFeatureExtractor.extract(0, FaceLandmarks(FloatArray(10), FloatArray(10)), emptyMap()).geometry)
    }

    // ---- storage form ----------------------------------------------------------------------

    @Test
    fun theStoredFormRoundTripsAndFitsTheKeySlot() {
        val face = measure(owner)
        val bytes = face.toBytes()
        assertEquals(FaceGeometry.ENCODED_BYTES, bytes.size)
        assertTrue(bytes.size <= FaceGeometry.MAX_TEMPLATE_BYTES)
        assertTrue(bytes.size <= VaultKeyManager.MAX_FACE_TEMPLATE_BYTES)
        assertEquals(face, FaceDescriptor.fromBytes(bytes))
    }

    @Test
    fun malformedStoredFormsAreRejected() {
        val good = measure(owner).toBytes()
        assertNull("empty", FaceDescriptor.fromBytes(ByteArray(0)))
        assertNull("too short", FaceDescriptor.fromBytes(good.copyOf(good.size - 1)))
        assertNull("too long", FaceDescriptor.fromBytes(good + 0))
        assertNull("future version", FaceDescriptor.fromBytes(good.copyOf().also { it[0] = 2 }))
        assertNull("wrong count", FaceDescriptor.fromBytes(good.copyOf().also { it[1] = 9 }))

        val nan = good.copyOf()
        ByteBuffer.wrap(nan).putFloat(2, Float.NaN)
        assertNull("NaN", FaceDescriptor.fromBytes(nan))

        val huge = good.copyOf()
        ByteBuffer.wrap(huge).putFloat(6, 1e9f)
        assertNull("absurd ratio", FaceDescriptor.fromBytes(huge))

        val zeroed = good.copyOf()
        for (i in 2 until zeroed.size) zeroed[i] = 0
        assertNull("all zero", FaceDescriptor.fromBytes(zeroed))
    }

    @Test
    fun aWipedDescriptorIsZeroAndMatchesNothing() {
        val face = measure(owner)
        val copy = face.copy()
        face.wipe()
        assertTrue(face.numbers().all { it == 0f })
        assertEquals(Float.POSITIVE_INFINITY, face.distanceTo(copy), 0f)
        assertFalse(face.matches(face))
        assertFalse(copy.matches(face))
        assertTrue("the copy is independent", copy.numbers().any { it != 0f })
    }

    @Test
    fun descriptorsCompareByContentAndNeverPrintTheirNumbers() {
        assertEquals(measure(owner), measure(owner))
        assertEquals(measure(owner).hashCode(), measure(owner).hashCode())
        assertNotEquals(measure(owner), measure(stranger))
        val text = measure(owner).toString()
        assertFalse(text, text.contains("0."))
    }

    // ---- comparing against a stored template -----------------------------------------------

    @Test
    fun aStoredTemplateIsComparedWithTheProbe() {
        val stored = measure(owner).toBytes()
        assertTrue(FaceGeometry.matchesStored(stored, measure(owner.rotated(15.0).shot(480f, 640f, 150f))))
        assertFalse(FaceGeometry.matchesStored(stored, measure(stranger)))
    }

    @Test
    fun noProbeOrAnUnreadableTemplateIsNeverAMatch() {
        val stored = measure(owner).toBytes()
        assertFalse("no probe", FaceGeometry.matchesStored(stored, null))
        assertFalse("garbage template", FaceGeometry.matchesStored(ByteArray(42) { 1 }, measure(owner)))
        assertFalse("empty template", FaceGeometry.matchesStored(ByteArray(0), measure(owner)))
    }

    @Test
    fun comparingNeitherChangesTheProbeNorTheStoredBytes() {
        val stored = measure(owner).toBytes()
        val before = stored.copyOf()
        val probe = measure(owner)
        FaceGeometry.matchesStored(stored, probe)
        assertArrayEquals(before, stored)
        assertEquals(measure(owner), probe)
    }

    // ---- enrolment, median -----------------------------------------------------------------

    @Test
    fun theEnrolmentTemplateIsTheAverageOfTwoReadingsOfTheSameFace() {
        val first = measure(owner)
        val second = measure(lookAlike)
        val template = FaceGeometry.enrolmentTemplate(first, second)!!
        val a = first.numbers()
        val b = second.numbers()
        template.numbers().forEachIndexed { i, v -> assertEquals((a[i] + b[i]) / 2f, v, 1e-6f) }
        assertEquals("inputs are left alone", measure(owner), first)
    }

    @Test
    fun noTemplateIsMadeFromAnUnreadableOrInconsistentPass() {
        assertNull(FaceGeometry.enrolmentTemplate(null, measure(owner)))
        assertNull(FaceGeometry.enrolmentTemplate(measure(owner), null))
        assertNull(FaceGeometry.enrolmentTemplate(null, null))
        assertNull("two different people", FaceGeometry.enrolmentTemplate(measure(owner), measure(stranger)))
    }

    @Test
    fun theMedianShrugsOffOutliersAndHandlesEvenCounts() {
        val a = measure(owner)
        val wild = measure(stranger)
        val median = FaceDescriptor.median(listOf(a, a.copy(), wild, a.copy(), wild.copy()))!!
        assertEquals("three of five agree", a, median)

        val even = FaceDescriptor.median(listOf(a, wild))!!
        val x = a.numbers()
        val y = wild.numbers()
        even.numbers().forEachIndexed { i, v -> assertEquals((x[i] + y[i]) / 2f, v, 1e-6f) }

        assertNull(FaceDescriptor.median(emptyList()))
    }
}
