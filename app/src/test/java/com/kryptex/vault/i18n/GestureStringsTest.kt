package com.kryptex.vault.i18n

import com.kryptex.vault.ui.gesture.GestureFallback
import com.kryptex.vault.ui.gesture.GestureMessage
import com.kryptex.vault.vision.FailureReason
import com.kryptex.vault.vision.Gesture
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compiler already forces every language to implement every string. This guards what it cannot:
 * blank text, a number that fell out of a template, and a "translation" that is still the English.
 */
class GestureStringsTest {
    private val translated = listOf(AppLanguage.DE.strings, AppLanguage.RU.strings)
    private val all = AppLanguage.entries.map { it.strings }

    /** Every gesture-screen string in a fixed order, so two languages can be compared index by index. */
    private fun gestureTexts(s: AppStrings): List<String> = buildList {
        add(s.gestureTitleUnlock)
        add(s.gestureTitleSetup)
        add(s.gestureChooseLengthTitle)
        add(s.gestureChooseLengthBody(6))
        add(s.gestureCount(6))
        add(s.gestureEntropyTitle(20, short = true))
        add(s.gestureEntropyTitle(30, short = false))
        add(s.gestureEntropyBody(5))
        add(s.gestureContinue)
        add(s.gestureReadyTitle)
        add(s.gestureReadyBody)
        add(s.gestureDone)
        add(s.gestureCameraOff)
        add(s.gestureChecking)
        add(s.gestureStartCamera)
        add(s.gesturePrivacyNote)
        add(s.gestureHeadlineUnlock)
        add(s.gestureHeadlineSaving)
        add(s.gestureHeadlineRecord)
        add(s.gestureHeadlineConfirm)
        add(s.gestureInstructionReady(6))
        add(s.gestureInstructionRecord(6))
        add(s.gestureInstructionConfirm(6))
        add(s.gestureStarting)
        add(s.gestureHoldStill)
        add(s.gestureFindFace)
        add(s.gestureFaceLost)
        add(s.gestureRelax)
        add(s.gestureProgress(2, 6))
        add(s.gestureFinished)
        add(s.gestureFailedGeneric)
        add(s.gestureProgressDescription(2, 6))
        Gesture.entries.forEach { add(s.gestureName(it)) }
        FailureReason.entries.forEach { add(s.gestureFailure(it)) }
        add(s.gestureMessage(GestureMessage.PermissionDenied))
        add(s.gestureMessage(GestureMessage.Recorded))
        add(s.gestureMessage(GestureMessage.Mismatch))
        add(s.gestureMessage(GestureMessage.SaveFailed("x")))
        listOf(null, 0, 1, 3).forEach { add(s.gestureMessage(GestureMessage.Wrong(it))) }
        add(s.gestureMessage(GestureMessage.FaceInconsistent))
        FailureReason.entries.forEach { add(s.gestureMessage(GestureMessage.Failure(it))) }
        GestureFallback.entries.forEach { add(s.gestureFallback(it)) }
        add(s.showPassphrase)
        add(s.hidePassphrase)
        add(s.settingsHapticTitle)
        add(s.settingsHapticBody)
        add(s.settingsFaceCheckTitle)
        add(s.settingsFaceCheckBody)
        add(s.onboardingGestureBody)
        add(s.onboardingConvenienceBody)
    }

    @Test
    fun nothingIsBlank() {
        for (s in all) gestureTexts(s).forEachIndexed { i, text -> assertTrue("blank string #$i in ${s.locale}", text.isNotBlank()) }
    }

    @Test
    fun germanAndRussianAreActuallyTranslated() {
        val english = gestureTexts(EnStrings)
        for (s in translated) {
            gestureTexts(s).forEachIndexed { i, text ->
                assertNotEquals("string #$i is still English in ${s.locale}: $text", english[i], text)
            }
        }
    }

    @Test
    fun gestureNamesAreDistinctInEveryLanguage() {
        for (s in all) {
            val names = Gesture.entries.map(s::gestureName)
            assertTrue("duplicate gesture names in ${s.locale}: $names", names.toSet().size == names.size)
        }
    }

    @Test
    fun numbersSurviveTranslation() {
        for (s in all) {
            val where = s.locale.toString()
            assertTrue(where, s.gestureProgress(2, 7).let { "2" in it && "7" in it })
            assertTrue(where, s.gestureProgressDescription(3, 8).let { "3" in it && "8" in it })
            assertTrue(where, "9" in s.gestureCount(9))
            assertTrue(where, "9" in s.gestureChooseLengthBody(9))
            assertTrue(where, "9" in s.gestureInstructionReady(9))
            assertTrue(where, "9" in s.gestureInstructionRecord(9))
            assertTrue(where, "9" in s.gestureInstructionConfirm(9))
            assertTrue(where, "17" in s.gestureEntropyTitle(17, short = true))
            assertTrue(where, "17" in s.gestureEntropyTitle(17, short = false))
            assertTrue(where, "5" in s.gestureEntropyBody(5))
            assertTrue(where, "4" in s.gestureMsgWrong(4))
        }
    }

    @Test
    fun wrongAttemptMessagesDifferByRemainingAttempts() {
        for (s in all) {
            val texts = listOf(null, 0, 1, 2).map(s::gestureMsgWrong)
            assertTrue("attempt messages collide in ${s.locale}: $texts", texts.toSet().size == texts.size)
        }
    }

    @Test
    fun shortWarningDiffersFromPlainTitle() {
        for (s in all) assertNotEquals(s.gestureEntropyTitle(20, short = true), s.gestureEntropyTitle(20, short = false))
    }

    @Test
    fun theFaceReadingMessageIsNotMistakenForAnyOtherMessage() {
        for (s in all) {
            val faceMessage = s.gestureMessage(GestureMessage.FaceInconsistent)
            val others = listOf(GestureMessage.Mismatch, GestureMessage.Recorded, GestureMessage.Wrong(null)).map(s::gestureMessage)
            assertTrue("collides with another message in ${s.locale}", faceMessage !in others)
        }
    }

    @Test
    fun theFaceCheckIsDescribedHonestlyInEveryLanguage() {
        // The privacy copy must no longer claim that nothing is kept: face proportions are saved (encrypted) at setup.
        val oldClaims = listOf("nothing is recorded or stored", "nichts wird aufgezeichnet oder gespeichert", "ничего не записывается и не сохраняется")
        for (s in all) {
            for (old in oldClaims) assertTrue("${s.locale} still says '$old'", old !in s.gesturePrivacyNote)
        }
    }

    @Test
    fun saveFailureShowsItsDetail() {
        for (s in all) assertTrue("detail missing in ${s.locale}", "disk full" in s.gestureMessage(GestureMessage.SaveFailed("disk full")))
    }
}
