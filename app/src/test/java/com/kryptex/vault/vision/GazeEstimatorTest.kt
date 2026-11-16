package com.kryptex.vault.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * MediaPipe reports landmarks in the unrotated camera buffer, normalised separately by width and
 * height. For a front camera held upright that buffer is usually sideways, and the user may tilt
 * their head. These tests show the same face in every such form and require the same readings.
 */
class GazeEstimatorTest {

    /** Landmarks in pixel units of a [width] x [height] picture. */
    private class Picture(val width: Float, val height: Float, val points: Map<Int, Pair<Float, Float>>) {
        /** What MediaPipe would report for this picture, converted the way the app converts it. */
        fun landmarks(aspect: Float = width / height): FaceLandmarks {
            val x = FloatArray(478)
            val y = FloatArray(478)
            points.forEach { (i, p) ->
                x[i] = p.first / width
                y[i] = p.second / height
            }
            return FaceLandmarks.fromNormalized(x, y, aspect)
        }

        /** The picture turned 90 degrees clockwise, as a camera buffer may deliver it. */
        fun turnedClockwise() = Picture(height, width, points.mapValues { (_, p) -> (height - p.second) to p.first })

        fun mirrored() = Picture(width, height, points.mapValues { (_, p) -> (width - p.first) to p.second })

        /** The head tilted sideways by [degrees] around the middle of the face. */
        fun rolled(degrees: Double): Picture {
            val a = Math.toRadians(degrees)
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            return Picture(width, height, points.mapValues { (_, p) ->
                val dx = p.first - CENTER_X
                val dy = p.second - CENTER_Y
                (CENTER_X + dx * c - dy * s) to (CENTER_Y + dx * s + dy * c)
            })
        }

        companion object {
            const val CENTER_X = 240f
            const val CENTER_Y = 360f
        }
    }

    /** An upright 480 x 640 selfie: eyes 40 px wide, looking left and a little down, head slightly turned and raised. */
    private val upright = Picture(
        480f,
        640f,
        mapOf(
            FaceMesh.RIGHT_EYE_OUTER to (180f to 300f),
            FaceMesh.RIGHT_EYE_INNER to (220f to 300f),
            FaceMesh.LEFT_EYE_INNER to (260f to 300f),
            FaceMesh.LEFT_EYE_OUTER to (300f to 300f),
            // 58 % of the way along each eye toward the user's left; 6 px below the corner line.
            FaceMesh.RIGHT_IRIS_CENTER to (203.2f to 306f),
            FaceMesh.LEFT_IRIS_CENTER to (283.2f to 306f),
            FaceMesh.CHEEK_RIGHT to (140f to 340f),
            FaceMesh.CHEEK_LEFT to (340f to 340f),
            FaceMesh.NOSE_TIP to (250f to 350f),
            FaceMesh.FOREHEAD to (240f to 200f),
            FaceMesh.CHIN to (240f to 520f),
        ),
    )

    private fun assertSameReadings(what: String, expected: FaceLandmarks, actual: FaceLandmarks) {
        assertEquals("$what: gaze", GazeEstimator.horizontalGaze(expected)!!, GazeEstimator.horizontalGaze(actual)!!, 1e-4f)
        val e = GazeEstimator.headPose(expected)!!
        val a = GazeEstimator.headPose(actual)!!
        assertEquals("$what: yaw", e.yaw, a.yaw, 1e-4f)
        assertEquals("$what: pitch", e.pitch, a.pitch, 1e-4f)
    }

    @Test
    fun anUprightFaceReadsAsDrawn() {
        val lm = upright.landmarks()
        assertEquals(0.4f, GazeEstimator.horizontalGaze(lm)!!, 1e-4f) // (0.58 - 0.5) * 5
        val head = GazeEstimator.headPose(lm)!!
        assertEquals(0.05f, head.yaw, 1e-4f) // nose 55 % of the way to the left cheek
        assertEquals(0.03125f, head.pitch, 1e-4f) // nose 46.9 % of the way from forehead to chin
    }

    @Test
    fun aSidewaysCameraBufferGivesTheSameReadings() {
        // Front cameras held upright usually deliver frames turned by 90 or 270 degrees.
        var picture = upright
        for (quarterTurns in 1..3) {
            picture = picture.turnedClockwise()
            assertSameReadings("$quarterTurns quarter turns", upright.landmarks(), picture.landmarks())
        }
    }

    @Test
    fun aMirroredFrameGivesTheSameReadings() {
        assertSameReadings("mirrored", upright.landmarks(), upright.mirrored().landmarks())
    }

    @Test
    fun aTiltedHeadInASidewaysBufferGivesTheSameReadings() {
        for (degrees in listOf(-20.0, 12.0, 25.0)) {
            assertSameReadings("tilted $degrees", upright.landmarks(), upright.rolled(degrees).turnedClockwise().landmarks())
        }
    }

    @Test
    fun theAspectRatioMustBeUndoneForTiltedFaces() {
        // MediaPipe's per-axis normalisation squashes a 640 x 480 buffer; read without undoing it,
        // a tilted face's gaze comes out measurably wrong.
        val sideways = upright.rolled(15.0).turnedClockwise()
        val right = GazeEstimator.horizontalGaze(sideways.landmarks())!!
        val squashed = GazeEstimator.horizontalGaze(sideways.landmarks(aspect = 1f))!!
        assertEquals(0.4f, right, 1e-4f)
        assertTrue("expected a visible error, got $squashed vs $right", abs(squashed - right) > 0.01f)
    }

    @Test
    fun positionIsMeasuredAlongTheAxis() {
        val lm = FaceLandmarks(floatArrayOf(0f, 2f, 1f, 1f), floatArrayOf(0f, 2f, 1f, 0f))
        assertEquals(0.5f, lm.position(2, 0, 1, 1e-3f)!!, 1e-6f) // on the axis, half way
        assertEquals(0.25f, lm.position(3, 0, 1, 1e-3f)!!, 1e-6f) // off the axis: only the along-axis part counts
        assertNull("coincident ends", lm.position(2, 0, 0, 1e-3f))
    }

    @Test
    fun aCollapsedMeshGivesNoReading() {
        val flat = FaceLandmarks(FloatArray(478), FloatArray(478))
        assertNull(GazeEstimator.horizontalGaze(flat))
        assertNull(GazeEstimator.headPose(flat))
    }

    @Test
    fun anInvalidAspectRatioIsRejected() {
        val ok = runCatching { FaceLandmarks.fromNormalized(FloatArray(1), FloatArray(1), aspect = 0f) }
        assertTrue(ok.exceptionOrNull() is IllegalArgumentException)
    }
}
