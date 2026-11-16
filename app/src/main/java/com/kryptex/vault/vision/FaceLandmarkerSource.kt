package com.kryptex.vault.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Wraps MediaPipe's Face Landmarker (478 landmarks including iris, 52 blendshapes) in live-stream
 * mode and converts each result to a [FaceFrame]. The model is read from the app's own assets;
 * nothing is downloaded at runtime.
 *
 * Frames are analysed and dropped: no image is stored or logged. That includes the copy of every
 * frame MediaPipe hands back with each result, which is freed at once (see [deliver]).
 */
class FaceLandmarkerSource(
    context: Context,
    private val onFrame: (FaceFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) : AutoCloseable {

    private val landmarker: FaceLandmarker
    private var lastTimestampMs = -1L

    /** Width / height of the submitted frames; used if a result ever arrives without its image. */
    @Volatile
    private var submittedAspect = 4f / 3f

    init {
        val base = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            // Head pose is derived from landmark ratios instead (see GazeEstimator).
            .setOutputFacialTransformationMatrixes(false)
            .setResultListener { result, input -> deliver(result, input) }
            .setErrorListener { error -> onError(error) }
            .build()
        landmarker = FaceLandmarker.createFromOptions(context.applicationContext, options)
    }

    /**
     * Queues one frame. [rotationDegrees] is CameraX's `imageInfo.rotationDegrees`. It lets the
     * face detector see an upright face; the landmarks still come back in the coordinates of the
     * unrotated [bitmap], which [GazeEstimator] handles. Timestamps must strictly increase for
     * live-stream mode.
     */
    @Synchronized
    fun submit(bitmap: Bitmap, rotationDegrees: Int, timestampMs: Long) {
        val ts = if (timestampMs <= lastTimestampMs) lastTimestampMs + 1 else timestampMs
        lastTimestampMs = ts
        if (bitmap.width > 0 && bitmap.height > 0) submittedAspect = bitmap.width.toFloat() / bitmap.height
        val options = ImageProcessingOptions.builder().setRotationDegrees(rotationDegrees).build()
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), options, ts)
    }

    @Synchronized
    override fun close() {
        landmarker.close()
    }

    /** MediaPipe's result thread. Nothing may be thrown back into it. */
    private fun deliver(result: FaceLandmarkerResult, input: MPImage?) {
        val aspect = try {
            input?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height } ?: submittedAspect
        } finally {
            // MediaPipe gives every result its own full-size copy of the camera frame. Nothing here
            // needs the pixels: free them now instead of leaving a picture of the user's face in
            // memory until the next garbage collection.
            input?.close()
        }
        try {
            onFrame(toFrame(result, aspect))
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun toFrame(result: FaceLandmarkerResult, aspect: Float): FaceFrame {
        val timestamp = result.timestampMs()
        val face = result.faceLandmarks().firstOrNull() ?: return FaceFrame.noFace(timestamp)

        val landmarks = FaceLandmarks.fromNormalized(
            x = FloatArray(face.size) { face[it].x() },
            y = FloatArray(face.size) { face[it].y() },
            aspect = aspect,
            z = FloatArray(face.size) { face[it].z() },
        )
        val blendshapes: Map<String, Float> = result.faceBlendshapes()
            .map { all -> all.firstOrNull()?.associate { it.categoryName() to it.score() } ?: emptyMap() }
            .orElse(emptyMap())
        return FaceFeatureExtractor.extract(timestamp, landmarks, blendshapes)
    }

    companion object {
        const val MODEL_ASSET = "face_landmarker.task"
    }
}
