package com.kryptex.vault.i18n

import com.kryptex.vault.stego.PayloadType
import com.kryptex.vault.ui.onboarding.PassphraseProblem
import com.kryptex.vault.ui.onboarding.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun codesRoundTripAndUnknownCodesAreNull() {
        for (language in AppLanguage.entries) assertEquals(language, AppLanguage.fromCode(language.code))
        assertNull(AppLanguage.fromCode(null))
        assertNull(AppLanguage.fromCode("fr"))
    }

    @Test
    fun phoneLocalesMapToTheClosestLanguage() {
        assertEquals(AppLanguage.DE, AppLanguage.fromLocale(Locale.GERMANY))
        assertEquals(AppLanguage.RU, AppLanguage.fromLocale(Locale.forLanguageTag("ru-RU")))
        assertEquals(AppLanguage.EN, AppLanguage.fromLocale(Locale.JAPAN))
    }

    @Test
    fun everyLanguageTranslatesTheEnumeratedLabels() {
        for (language in AppLanguage.entries) {
            val s = language.strings
            for (type in PayloadType.entries) assertTrue(s.payloadType(type).isNotBlank())
            for (strength in Strength.entries) assertTrue(s.strength(strength).isNotBlank())
            for (problem in PassphraseProblem.entries) assertTrue(s.passphraseProblem(problem, 12).isNotBlank())
            for (seconds in listOf(0, 15, 30, 60, 120, 300, 600, 45)) assertTrue(s.autoLockLabel(seconds).isNotBlank())
        }
    }

    @Test
    fun germanAndRussianAreActuallyTranslated() {
        val en = AppLanguage.EN.strings
        for (language in listOf(AppLanguage.DE, AppLanguage.RU)) {
            val s = language.strings
            assertNotEquals(en.lockUnlock, s.lockUnlock)
            assertNotEquals(en.embedHideButton, s.embedHideButton)
            assertNotEquals(en.extractChoose, s.extractChoose)
            assertNotEquals(en.settingsHelpTitle, s.settingsHelpTitle)
            assertNotEquals(en.helpEmbedPicture, s.helpEmbedPicture)
        }
    }

    @Test
    fun byteSizesUseTheLanguagesUnits() {
        assertEquals("512 B", AppLanguage.EN.strings.bytes(512))
        assertEquals("1,0 КБ", AppLanguage.RU.strings.bytes(1024))
        assertTrue(AppLanguage.DE.strings.bytes(5L * 1024 * 1024).endsWith("MB"))
    }
}
