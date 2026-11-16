package com.kryptex.vault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.kryptex.vault.vision.FaceFrame
import com.kryptex.vault.vision.FaceLandmarkerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Proves the bundled model and MediaPipe's native libraries load on a real Android runtime
 * (including a 16 KB-page device) and that the live-stream wrapper delivers frames.
 *
 * A synthetic image cannot contain a face, so "no face" is the expected, correct result. The
 * gesture geometry itself is covered by JVM tests; real faces need a device check by a person.
 */
@RunWith(AndroidJUnit4::class)
class FaceLandmarkerSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledModelLoadsAndReportsNoFaceOnABlankImage() {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(FaceLandmarkerSource.MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .build()

        FaceLandmarker.createFromOptions(context, options).use { landmarker ->
            val blank = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            val result = landmarker.detect(BitmapImageBuilder(blank).build())
            assertNotNull(result)
            assertTrue("a flat grey image has no face", result.faceLandmarks().isEmpty())
        }
    }

    @Test
    fun liveStreamSourceDeliversAFrameForEverySubmittedImage() {
        val frames = AtomicReference<FaceFrame?>()
        val errors = AtomicReference<Throwable?>()
        val delivered = CountDownLatch(1)

        FaceLandmarkerSource(
            context = context,
            onFrame = { frame ->
                frames.set(frame)
                delivered.countDown()
            },
            onError = { errors.set(it) },
        ).use { source ->
            val blank = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            source.submit(blank, rotationDegrees = 0, timestampMs = 1_000)
            assertTrue("no result within 15 s", delivered.await(15, TimeUnit.SECONDS))
        }

        assertEquals(null, errors.get())
        val frame = frames.get()!!
        assertFalse("blank image must not look like a face", frame.faceDetected)
    }

    @Test
    fun nonIncreasingTimestampsAreRepairedInsteadOfCrashing() {
        val results = LinkedBlockingQueue<FaceFrame>()
        val errors = LinkedBlockingQueue<Throwable>()
        FaceLandmarkerSource(
            context = context,
            onFrame = { results.add(it) },
            onError = { errors.add(it) },
        ).use { source ->
            val blank = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            // Live-stream mode requires strictly increasing timestamps. Frames are sent one at a time
            // (each result awaited) so MediaPipe's flow limiter, which drops frames that arrive while
            // another is in flight, cannot hide the behaviour under test. Equal and older timestamps
            // must be bumped, not rejected.
            for (ts in listOf(5_000L, 5_000L, 4_000L)) {
                source.submit(blank, rotationDegrees = 0, timestampMs = ts)
                assertNotNull("no result for submitted timestamp $ts", results.poll(15, TimeUnit.SECONDS))
            }
        }
        assertTrue("unexpected errors: $errors", errors.isEmpty())
    }
}
