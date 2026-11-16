package com.kryptex.vault

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.GlanceEngine
import com.kryptex.vault.vision.GlanceState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy contract of the camera, checked on a real camera stack (the emulator's virtual
 * camera shows no face, which is exactly what is needed to exercise the failure paths):
 *  - nothing opens until start(), and
 *  - the camera is released again on stop, on screen pause and when a session fails.
 */
@RunWith(AndroidJUnit4::class)
class GlanceEngineLifecycleTest {
    @get:Rule
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private lateinit var engine: GlanceEngine
    private lateinit var owner: TestOwner

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            engine = GlanceEngine(context)
            owner = TestOwner().also { it.registry.currentState = Lifecycle.State.RESUMED }
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { engine.reset() }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitUntil(timeoutMs: Long = 20_000, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting for: $what (state=${engine.state.value}, cameraActive=${engine.isCameraActive})")
    }

    @Test
    fun nothingRunsUntilStartIsCalled() {
        assertEquals(GlanceState.Idle, engine.state.value)
        assertFalse(engine.isCameraActive)
        Thread.sleep(1_500)
        assertEquals(GlanceState.Idle, engine.state.value)
        assertFalse(engine.isCameraActive)
    }

    @Test
    fun framesWithoutAFaceStillReachTheDetector() {
        // The emulator's virtual camera shows no face. Calibrating(faceDetected = false) is only
        // published from a delivered frame, so reaching it proves the whole live pipeline
        // (camera -> bitmap -> MediaPipe -> result listener -> detector) runs, and that an empty
        // scene is reported as "no face" and not mistaken for a stalled camera.
        onMain { engine.start(owner, steps = 3, reveal = false) }
        waitUntil(timeoutMs = 25_000, what = "a frame to reach the detector") {
            val s = engine.state.value
            s is GlanceState.Calibrating || s is GlanceState.Capturing
        }
        val state = engine.state.value
        assertTrue("state was $state", state is GlanceState.Calibrating)
        assertFalse("virtual camera has no face", (state as GlanceState.Calibrating).faceDetected)
    }

    @Test
    fun startOpensTheCameraAndStopReleasesItImmediately() {
        onMain { engine.start(owner, steps = 3, reveal = false) }
        waitUntil(what = "camera bound") { engine.isCameraActive }
        assertTrue(engine.state.value is GlanceState.Starting || engine.state.value is GlanceState.Calibrating)

        onMain { engine.stop() }
        assertFalse("unbindAll() must have run by the time stop() returns", engine.isCameraActive)
        assertEquals(GlanceState.Idle, engine.state.value)
    }

    @Test
    fun pausingTheScreenReleasesTheCameraEvenIfTheUiForgetsToStop() {
        onMain { engine.start(owner, steps = 3, reveal = false) }
        waitUntil(what = "camera bound") { engine.isCameraActive }

        onMain { owner.registry.currentState = Lifecycle.State.STARTED } // ON_PAUSE

        waitUntil(what = "camera released after ON_PAUSE") { !engine.isCameraActive }
        assertEquals(GlanceState.Idle, engine.state.value)
    }

    @Test
    fun withNoFaceInViewTheSessionTimesOutAndTheCameraIsReleased() {
        // Short budget so the test does not take half a minute.
        val quick = com.kryptex.vault.vision.DetectorConfig(
            totalTimeoutBaseMillis = 1_500,
            totalTimeoutPerStepMillis = 0,
        )
        onMain { engine = GlanceEngine(context, detectorConfig = quick) }
        onMain { engine.start(owner, steps = 3, reveal = false) }

        waitUntil(timeoutMs = 30_000, what = "a terminal state") {
            engine.state.value is GlanceState.Failed || engine.state.value is GlanceState.Completed
        }
        val state = engine.state.value
        assertTrue("expected a failure, got $state", state is GlanceState.Failed)
        val reason = (state as GlanceState.Failed).reason
        assertTrue("unexpected reason $reason", reason == FailureReason.TIMEOUT || reason == FailureReason.NO_FRAMES)

        waitUntil(what = "camera released after failure") { !engine.isCameraActive }
    }

    @Test
    fun startingTwiceDoesNotOpenASecondSession() {
        onMain { engine.start(owner, steps = 3, reveal = false) }
        waitUntil(what = "camera bound") { engine.isCameraActive }
        onMain { engine.start(owner, steps = 3, reveal = false) } // ignored
        onMain { engine.stop() }
        assertFalse(engine.isCameraActive)
    }
}
