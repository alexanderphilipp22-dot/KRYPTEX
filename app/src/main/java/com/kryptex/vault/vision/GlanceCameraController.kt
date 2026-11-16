package com.kryptex.vault.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NoFrontCameraException : Exception("this device has no front camera")

/**
 * The only place that touches the camera. It binds when [start] is called and releases it with
 * `unbindAll()` in [stop]; nothing here runs on its own. Main-thread only, as CameraX requires.
 */
class GlanceCameraController(context: Context) {
    private val appContext = context.applicationContext
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var executor: ExecutorService? = null
    private var generation = 0

    /** True while any camera use case is bound. */
    val isBound: Boolean
        get() = provider?.let { p -> analysis?.let(p::isBound) == true || preview?.let(p::isBound) == true } == true

    /**
     * Opens the front camera and delivers RGBA frames to [analyzer] on a private thread.
     * @param surfaceProvider optional: shows a small live preview so the user can frame their face.
     */
    fun start(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        analyzer: ImageAnalysis.Analyzer,
        onError: (Throwable) -> Unit,
    ) {
        stop() // never run two sessions
        val mine = ++generation
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                if (mine != generation) return@addListener // stopped while the provider was loading
                try {
                    bind(future.get(), owner, surfaceProvider, analyzer)
                } catch (e: Exception) {
                    stop()
                    onError(e)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun bind(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        analyzer: ImageAnalysis.Analyzer,
    ) {
        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        if (!cameraProvider.hasCamera(selector)) throw NoFrontCameraException()

        val analysisExecutor = Executors.newSingleThreadExecutor()
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        val previewUseCase = surfaceProvider?.let { sp ->
            Preview.Builder().build().also { it.setSurfaceProvider(sp) }
        }

        provider = cameraProvider
        executor = analysisExecutor
        analysis = imageAnalysis
        preview = previewUseCase

        cameraProvider.unbindAll()
        val useCases = listOfNotNull(previewUseCase, imageAnalysis).toTypedArray()
        cameraProvider.bindToLifecycle(owner, selector, *useCases)
    }

    /** Releases the camera. Idempotent, and never throws: some camera stacks object to being torn down. */
    fun stop() {
        generation++ // invalidates a start() whose provider future is still pending
        runCatching { analysis?.clearAnalyzer() }
        runCatching { provider?.unbindAll() }
        executor?.shutdownNow()
        analysis = null
        preview = null
        executor = null
        provider = null
    }

    private companion object {
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480
    }
}
