package com.kryptex.vault.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-demand multi-modal gesture capture.
 *
 *  - **Nothing runs until [start] is called**, and [start] must only be called from an explicit
 *    user tap. Constructing the engine opens no camera and loads no model.
 *  - **The camera is released immediately** (`unbindAll()`) on completion, failure, timeout,
 *    [stop], and when the hosting screen pauses.
 *  - Frames are analysed in memory and dropped.
 *
 * Main-thread API; frame processing happens on private threads.
 */
class GlanceEngine(
    context: Context,
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
    private val detectorConfig: DetectorConfig = DetectorConfig(),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val camera = GlanceCameraController(appContext)

    private val mutableState = MutableStateFlow<GlanceState>(GlanceState.Idle)
    val state: StateFlow<GlanceState> = mutableState.asStateFlow()

    /** True while the camera is bound. Exposed so tests and the UI can verify it is really off. */
    val isCameraActive: Boolean get() = camera.isBound

    private val lock = Any()
    private var session: Session? = null
    private var nextSessionId = 0

    /** The countdown shown while capturing restarts with every accepted gesture. */
    private val gestureWindowSeconds = ((detectorConfig.idleTimeoutMillis + 999) / 1000).toInt().coerceAtLeast(1)

    private inner class Session(
        val id: Int,
        val steps: Int,
        val reveal: Boolean,
        val detector: GestureSequenceDetector,
        val startedAt: Long,
    ) {
        @Volatile var source: FaceLandmarkerSource? = null
        @Volatile var lastFrameAt: Long = startedAt
        var job: Job? = null
        var lifecycle: Lifecycle? = null
        var observer: LifecycleEventObserver? = null
    }

    /**
     * Opens the front camera and starts listening for [steps] gestures.
     *
     * @param reveal true during enrolment: the recognised gestures are shown back to the user.
     *   During unlock leave it false so the state only ever exposes anonymous progress.
     * @param surfaceProvider optional live preview so the user can frame their face.
     */
    @MainThread
    fun start(
        owner: LifecycleOwner,
        steps: Int,
        reveal: Boolean,
        surfaceProvider: Preview.SurfaceProvider? = null,
    ) {
        require(steps in GestureSequence.MIN_STEPS..GestureSequence.MAX_STEPS) { "steps out of range" }
        if (session != null) return // already running

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = GlanceState.Failed(FailureReason.PERMISSION_DENIED)
            return
        }

        val s = Session(
            id = ++nextSessionId,
            steps = steps,
            reveal = reveal,
            detector = GestureSequenceDetector(steps, config = detectorConfig),
            startedAt = uptimeMillis(),
        )
        synchronized(lock) { session = s }
        mutableState.value = GlanceState.Starting

        // Leaving the screen ends the session, whatever the UI layer does.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        s.lifecycle = owner.lifecycle
        s.observer = observer
        owner.lifecycle.addObserver(observer)

        s.job = scope.launch {
            try {
                // NonCancellable: if the session is cancelled while the model loads, the source
                // must still come back to us so it can be closed instead of leaked.
                val source = withContext(Dispatchers.Default + NonCancellable) {
                    FaceLandmarkerSource(
                        context = appContext,
                        onFrame = { frame -> onFrame(s, frame) },
                        onError = { fail(s, FailureReason.CAMERA_ERROR) },
                    )
                }
                if (!isCurrent(s)) {
                    withContext(Dispatchers.Default + NonCancellable) { source.close() }
                    return@launch
                }
                s.source = source
                s.lastFrameAt = uptimeMillis()

                camera.start(
                    owner = owner,
                    surfaceProvider = surfaceProvider,
                    analyzer = ImageAnalysis.Analyzer { image -> analyze(s, image) },
                    onError = { e ->
                        fail(s, if (e is NoFrontCameraException) FailureReason.NO_FRONT_CAMERA else FailureReason.CAMERA_ERROR)
                    },
                )
                watchdog(s)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(s, FailureReason.MODEL_ERROR)
            } catch (_: LinkageError) {
                // MediaPipe's native code would not load on this device: report it, never crash.
                fail(s, FailureReason.MODEL_ERROR)
            } catch (_: OutOfMemoryError) {
                fail(s, FailureReason.MODEL_ERROR)
            }
        }
    }

    /** Cancels a running session and turns the camera off. State returns to [GlanceState.Idle]. */
    @MainThread
    fun stop() {
        val s = synchronized(lock) { session.also { session = null } }
        if (s == null) {
            camera.stop() // belt and braces: unbind even if we think nothing is running
            return
        }
        release(s)
        mutableState.value = GlanceState.Idle
    }

    /** Clears a terminal state (and with it any recognised sequence) after the caller consumed it. */
    @MainThread
    fun reset() {
        stop()
        mutableState.value = GlanceState.Idle
    }

    // ---- frame path ------------------------------------------------------------------------

    /** Camera analysis thread. */
    private fun analyze(s: Session, image: ImageProxy) {
        try {
            val source = s.source
            if (source == null || !isCurrent(s)) return
            val bitmap = image.toBitmap()
            try {
                source.submit(bitmap, image.imageInfo.rotationDegrees, uptimeMillis())
            } finally {
                bitmap.recycle() // nothing is kept of the camera image
            }
        } catch (_: Exception) {
            fail(s, FailureReason.CAMERA_ERROR)
        } catch (_: OutOfMemoryError) {
            // A frame that cannot even be converted ends the session; the camera thread survives.
            fail(s, FailureReason.CAMERA_ERROR)
        } finally {
            image.close()
        }
    }

    /** MediaPipe result thread. */
    private fun onFrame(s: Session, frame: FaceFrame) {
        val snapshot: DetectorSnapshot
        val sequence: GestureSequence?
        synchronized(lock) {
            if (session !== s) return
            s.lastFrameAt = uptimeMillis()
            snapshot = s.detector.onFrame(frame)
            sequence = s.detector.sequenceOrNull()
        }

        when (snapshot.phase) {
            DetectorPhase.CALIBRATING ->
                publish(s, GlanceState.Calibrating(snapshot.calibrationProgress, snapshot.faceDetected))

            DetectorPhase.CAPTURING ->
                publish(
                    s,
                    GlanceState.Capturing(
                        steps = s.steps,
                        committedCount = snapshot.committed.size,
                        committed = if (s.reveal) snapshot.committed else emptyList(),
                        candidate = if (s.reveal) snapshot.candidate else null,
                        holdProgress = snapshot.holdProgress,
                        awaitingNeutral = snapshot.awaitingNeutral,
                        faceDetected = snapshot.faceDetected,
                        secondsLeft = ((snapshot.millisRemaining + 999) / 1000).toInt(),
                        totalSeconds = gestureWindowSeconds,
                    ),
                )

            DetectorPhase.COMPLETE -> finish(s, GlanceState.Completed(checkNotNull(sequence)))
            DetectorPhase.FAILED -> finish(s, GlanceState.Failed(snapshot.failure ?: FailureReason.TIMEOUT))
        }
    }

    /** Ends the session even if no frame ever arrives (stalled camera, nobody in view). */
    private suspend fun watchdog(s: Session) {
        val total = detectorConfig.totalTimeoutBaseMillis +
            detectorConfig.totalTimeoutPerStepMillis * maxOf(0, s.steps - 3) +
            FIND_FACE_ALLOWANCE_MILLIS
        while (true) {
            delay(WATCHDOG_INTERVAL_MILLIS)
            if (!isCurrent(s)) return
            val now = uptimeMillis()
            when {
                now - s.startedAt > total -> return fail(s, FailureReason.TIMEOUT)
                now - s.lastFrameAt > NO_FRAME_TIMEOUT_MILLIS -> return fail(s, FailureReason.NO_FRAMES)
            }
        }
    }

    // ---- session end -----------------------------------------------------------------------

    private fun isCurrent(s: Session): Boolean = synchronized(lock) { session === s }

    private fun publish(s: Session, state: GlanceState) {
        if (isCurrent(s)) mutableState.value = state
    }

    private fun fail(s: Session, reason: FailureReason) = finish(s, GlanceState.Failed(reason))

    /** Terminal transition: release the camera first, then publish the outcome. */
    private fun finish(s: Session, terminal: GlanceState) {
        synchronized(lock) {
            if (session !== s) return
            session = null
        }
        release(s)
        mutableState.value = terminal
    }

    /** Safe from any thread: camera and lifecycle work is hopped onto the main thread. */
    private fun release(s: Session) {
        // The recognised gestures are the secret. A completed sequence has already been copied
        // into the published state; the detector's own record goes now, not at garbage collection.
        synchronized(lock) { s.detector.wipe() }
        s.job?.cancel()
        scope.launch {
            camera.stop() // unbindAll()
            s.lifecycle?.let { lc -> s.observer?.let(lc::removeObserver) }
            s.lifecycle = null
            s.observer = null
            val source = s.source
            s.source = null
            if (source != null) withContext(Dispatchers.Default + NonCancellable) { source.close() }
        }
    }

    private companion object {
        const val WATCHDOG_INTERVAL_MILLIS = 250L
        const val NO_FRAME_TIMEOUT_MILLIS = 5_000L

        /** Extra budget for the user to find the camera and for the half-second calibration. */
        const val FIND_FACE_ALLOWANCE_MILLIS = 8_000L
    }
}
