package com.kryptex.vault.ui

import com.kryptex.vault.ui.onboarding.PassphrasePolicy
import com.kryptex.vault.ui.onboarding.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphrasePolicyTest {
    @Test
    fun shortPassphrasesAreRejected() {
        assertNotNull(PassphrasePolicy.check(""))
        assertNotNull(PassphrasePolicy.check("Abc123!xyz")) // 10 chars
        assertNull(PassphrasePolicy.check("Abc123!xyzQw")) // 12 chars
    }

    @Test
    fun obviousChoicesAreRejected() {
        for (bad in listOf("passwordpassword", "aaaaaaaaaaaaaaaa", "abababababab", "123456789012", "abcdefghijklmnop", "kryptex-kryptex", "Password1234")) {
            assertNotNull("'$bad' should be rejected", PassphrasePolicy.check(bad))
        }
    }

    @Test
    fun longPassphrasesWithWordsAreAccepted() {
        assertNull(PassphrasePolicy.check("correct horse battery staple"))
        assertNull(PassphrasePolicy.check("Mein-Hund heisst Bello 1987"))
    }

    @Test
    fun strengthGrowsWithLengthAndVariety() {
        val order = listOf(
            PassphrasePolicy.strength(""),
            PassphrasePolicy.strength("aaaaaaaaaaaaaaaa"),
            PassphrasePolicy.strength("correcthorsebattery"),
            PassphrasePolicy.strength("Correct-horse-battery-staple-9"),
        )
        assertEquals(Strength.TOO_WEAK, order[0])
        assertEquals(Strength.TOO_WEAK, order[1])
        assertTrue("fractions must not decrease: ${order.map { it.fraction }}", order.zipWithNext().all { (a, b) -> a.fraction <= b.fraction })
        assertTrue(order[3].fraction >= Strength.STRONG.fraction)
    }
}
