package com.kryptex.vault.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureProgressTrackerTest {
    private fun capturing(count: Int, steps: Int = 4) = GlanceState.Capturing(
        steps = steps,
        committedCount = count,
        committed = emptyList(),
        candidate = null,
        holdProgress = 0f,
        awaitingNeutral = false,
        faceDetected = true,
        secondsLeft = 5,
        totalSeconds = 5,
    )

    private fun completed(steps: Int) = GlanceState.Completed(GestureSequence(List(steps) { Gesture.SMILE }))

    @Test
    fun nothingAcceptedYetMeansNoPulse() {
        val t = GestureProgressTracker()
        assertFalse(t.accepted(GlanceState.Idle))
        assertFalse(t.accepted(GlanceState.Starting))
        assertFalse(t.accepted(GlanceState.Calibrating(0.5f, faceDetected = true)))
        assertFalse(t.accepted(capturing(0)))
    }

    @Test
    fun everyNewlyAcceptedGestureGivesExactlyOnePulse() {
        val t = GestureProgressTracker()
        t.accepted(capturing(0))
        assertTrue(t.accepted(capturing(1)))
        // The engine publishes a state on every camera frame: the same count again is not news.
        assertFalse(t.accepted(capturing(1)))
        assertFalse(t.accepted(capturing(1)))
        assertTrue(t.accepted(capturing(2)))
    }

    @Test
    fun theFinalGestureCountsEvenThoughItArrivesAsCompleted() {
        val t = GestureProgressTracker()
        t.accepted(capturing(0, steps = 3))
        assertTrue(t.accepted(capturing(1, steps = 3)))
        assertTrue(t.accepted(capturing(2, steps = 3)))
        assertTrue("the third gesture", t.accepted(completed(3)))
        assertFalse(t.accepted(completed(3)))
    }

    @Test
    fun aNewSessionStartsCountingFromZero() {
        val t = GestureProgressTracker()
        t.accepted(capturing(0))
        assertTrue(t.accepted(capturing(1)))
        t.accepted(GlanceState.Idle) // the camera went off between sessions
        assertFalse(t.accepted(capturing(0)))
        assertTrue("first gesture of the new session", t.accepted(capturing(1)))
    }

    @Test
    fun aFailedSessionDoesNotLeakIntoTheNext() {
        val t = GestureProgressTracker()
        t.accepted(capturing(2))
        assertFalse(t.accepted(GlanceState.Failed(FailureReason.TIMEOUT)))
        assertTrue(t.accepted(capturing(1)))
    }

    @Test
    fun theKeyAndDefaultAreTheDocumentedOnes() {
        // Pinned because the key is the name of a stored preference: renaming it would silently reset users.
        assertEquals("haptic_feedback_enabled", com.kryptex.vault.ui.Haptics.KEY_ENABLED)
        assertTrue(com.kryptex.vault.ui.Haptics.DEFAULT_ENABLED)
    }
}
