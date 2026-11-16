package com.kryptex.vault.vision

/** Head orientation proxies derived from landmark ratios (no camera intrinsics needed). */
data class HeadPose(val yaw: Float, val pitch: Float)

/**
 * Gaze and head-pose estimation from the face mesh.
 *
 * Every number is the position of one landmark along an axis of the face itself (eye corner to
 * eye corner, cheek to cheek, forehead to chin), never along the image axes. That is what makes
 * it work on a phone: MediaPipe reports landmarks in the unrotated camera buffer, where an upright
 * face usually lies on its side, and the user may tilt their head. Projections onto the face's own
 * axes are unchanged by rotation, scale and mirroring, so none of that matters.
 *
 * Horizontal gaze is the only source for left/right, so two conventions can never cancel out.
 */
object GazeEstimator {
    /** Iris travel between the corners is small; this maps typical travel onto roughly -1..1. */
    private const val IRIS_GAIN = 5f

    /** Eye corners or face outline points closer than these are a collapsed mesh, not a face. */
    private const val MIN_EYE_WIDTH = 1e-4f
    private const val MIN_FACE_SIZE = 1e-3f

    /** @return positive when the user looks toward their own left; null without iris landmarks. */
    fun horizontalGaze(lm: FaceLandmarks): Float? {
        if (lm.count < FaceMesh.MIN_LANDMARKS_FOR_IRIS) return null

        // User's right eye, outer -> inner corner: 1 is toward the nose, i.e. toward the user's left.
        val right = lm.position(FaceMesh.RIGHT_IRIS_CENTER, FaceMesh.RIGHT_EYE_OUTER, FaceMesh.RIGHT_EYE_INNER, MIN_EYE_WIDTH)
            ?: return null
        // User's left eye, inner -> outer corner: 1 is toward the outer corner, again the user's left.
        val left = lm.position(FaceMesh.LEFT_IRIS_CENTER, FaceMesh.LEFT_EYE_INNER, FaceMesh.LEFT_EYE_OUTER, MIN_EYE_WIDTH)
            ?: return null

        return (((right + left) / 2f - 0.5f) * IRIS_GAIN).coerceIn(-1f, 1f)
    }

    /**
     * Yaw: where the nose tip sits between the two cheek outlines (0.5 when frontal).
     * Pitch: where the nose tip sits between forehead and chin (the frontal value varies by face,
     * so callers compare against a per-session neutral baseline).
     */
    fun headPose(lm: FaceLandmarks): HeadPose? {
        if (lm.count < FaceMesh.MIN_LANDMARKS_FOR_HEAD) return null
        val across = lm.position(FaceMesh.NOSE_TIP, FaceMesh.CHEEK_RIGHT, FaceMesh.CHEEK_LEFT, MIN_FACE_SIZE) ?: return null
        val down = lm.position(FaceMesh.NOSE_TIP, FaceMesh.FOREHEAD, FaceMesh.CHIN, MIN_FACE_SIZE) ?: return null

        // Nose toward the left cheek = turned toward the user's left (positive).
        // Nose toward the forehead = tilted up (positive).
        return HeadPose(yaw = across - 0.5f, pitch = 0.5f - down)
    }
}
