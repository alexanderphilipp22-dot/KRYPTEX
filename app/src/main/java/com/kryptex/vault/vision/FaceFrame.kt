package com.kryptex.vault.vision

import kotlin.math.max

/**
 * One analysed camera frame, reduced to the few numbers the gesture logic needs. Everything
 * MediaPipe-specific is converted away in [FaceFeatureExtractor], so the gesture logic is plain
 * Kotlin and testable on the JVM. No image data is kept.
 *
 * All directions are from the user's point of view.
 */
data class FaceFrame(
    val timestampMs: Long,
    val faceDetected: Boolean,
    /** Closure of the user's left / right eye, 0 (open) .. 1 (closed). */
    val blinkLeft: Float = 0f,
    val blinkRight: Float = 0f,
    /** Gaze offset: positive = toward the user's left. Roughly -1..1. */
    val gazeX: Float = 0f,
    /** Gaze offset: positive = up. Roughly -1..1. */
    val gazeY: Float = 0f,
    val browRaise: Float = 0f,
    val jawOpen: Float = 0f,
    val smile: Float = 0f,
    /** Head yaw proxy from landmark ratios; 0 = frontal, positive = turned toward the user's left. */
    val headYaw: Float = 0f,
    /** Head pitch proxy; 0 = frontal, positive = tilted up. */
    val headPitch: Float = 0f,
) {
    companion object {
        fun noFace(timestampMs: Long) = FaceFrame(timestampMs, faceDetected = false)
    }
}

/**
 * Landmark coordinates of one face in *isotropic* units: a unit is the same distance along both
 * axes, so projections onto slanted axes are meaningful. MediaPipe normalises x by the image width
 * and y by the image height; [fromNormalized] undoes that.
 *
 * The frame may have any orientation and may be mirrored. MediaPipe reports landmarks in the
 * unrotated camera buffer, which for a front camera held upright is usually turned by 90 or 270
 * degrees; [GazeEstimator] only measures along the face's own axes, so that does not matter.
 */
class FaceLandmarks(val x: FloatArray, val y: FloatArray) {
    init {
        require(x.size == y.size) { "x/y size mismatch" }
    }

    val count: Int get() = x.size

    /**
     * Where landmark [p] lies along the axis from landmark [from] to landmark [to]: 0 at [from],
     * 1 at [to], linear in between and beyond. Unaffected by rotating, scaling or mirroring the
     * frame. Null when the two ends are closer than [minLength] (a collapsed mesh).
     */
    fun position(p: Int, from: Int, to: Int, minLength: Float): Float? {
        val ax = x[to] - x[from]
        val ay = y[to] - y[from]
        val lengthSquared = ax * ax + ay * ay
        if (lengthSquared < minLength * minLength) return null
        return ((x[p] - x[from]) * ax + (y[p] - y[from]) * ay) / lengthSquared
    }

    companion object {
        /**
         * @param x landmark x normalised by the image width, as MediaPipe reports it
         * @param y landmark y normalised by the image height
         * @param aspect width / height of the image the landmarks were measured in
         */
        fun fromNormalized(x: FloatArray, y: FloatArray, aspect: Float): FaceLandmarks {
            require(aspect > 0f && aspect.isFinite()) { "invalid aspect ratio $aspect" }
            return FaceLandmarks(FloatArray(x.size) { x[it] * aspect }, y)
        }
    }
}

/**
 * Indices into MediaPipe's 478-point face mesh (468 face points + 10 iris points). The mesh is
 * anatomical: "left" and "right" are the user's own, whatever the image orientation.
 */
object FaceMesh {
    const val NOSE_TIP = 1
    const val FOREHEAD = 10
    const val CHIN = 152

    /** Face-silhouette points on the user's right and left cheek. */
    const val CHEEK_RIGHT = 234
    const val CHEEK_LEFT = 454

    const val RIGHT_EYE_OUTER = 33
    const val RIGHT_EYE_INNER = 133
    const val LEFT_EYE_INNER = 362
    const val LEFT_EYE_OUTER = 263

    const val RIGHT_IRIS_CENTER = 468
    const val LEFT_IRIS_CENTER = 473

    const val MIN_LANDMARKS_FOR_HEAD = CHEEK_LEFT + 1
    const val MIN_LANDMARKS_FOR_IRIS = LEFT_IRIS_CENTER + 1
}

object FaceFeatureExtractor {
    /**
     * MediaPipe's blendshapes follow the ARKit convention, where "Left" is the subject's own left.
     * If on-device verification shows the labels reversed (wink your left eye and the UI says
     * "wink right"), flip this single switch.
     */
    const val BLENDSHAPE_LEFT_IS_USER_LEFT = true

    fun extract(timestampMs: Long, landmarks: FaceLandmarks, blendshapes: Map<String, Float>): FaceFrame {
        fun bs(name: String): Float = blendshapes[name] ?: 0f

        val rawLeft = bs("eyeBlinkLeft")
        val rawRight = bs("eyeBlinkRight")
        val blinkLeft = if (BLENDSHAPE_LEFT_IS_USER_LEFT) rawLeft else rawRight
        val blinkRight = if (BLENDSHAPE_LEFT_IS_USER_LEFT) rawRight else rawLeft

        // Up/down needs no left/right convention: both eyes are averaged.
        val up = (bs("eyeLookUpLeft") + bs("eyeLookUpRight")) / 2f
        val down = (bs("eyeLookDownLeft") + bs("eyeLookDownRight")) / 2f

        val browRaise = max(bs("browInnerUp"), (bs("browOuterUpLeft") + bs("browOuterUpRight")) / 2f)
        val smile = (bs("mouthSmileLeft") + bs("mouthSmileRight")) / 2f

        val head = GazeEstimator.headPose(landmarks)
        return FaceFrame(
            timestampMs = timestampMs,
            faceDetected = true,
            blinkLeft = blinkLeft,
            blinkRight = blinkRight,
            gazeX = GazeEstimator.horizontalGaze(landmarks) ?: 0f,
            gazeY = up - down,
            browRaise = browRaise,
            jawOpen = bs("jawOpen"),
            smile = smile,
            headYaw = head?.yaw ?: 0f,
            headPitch = head?.pitch ?: 0f,
        )
    }
}
