package com.kryptex.vault.ui.onboarding

/** How strong a passphrase looks. The wording lives in the language files; [fraction] drives the meter. */
enum class Strength(val fraction: Float) {
    TOO_WEAK(0.1f),
    WEAK(0.35f),
    FAIR(0.6f),
    STRONG(0.85f),
    EXCELLENT(1f),
}

/** Why a passphrase was refused. The wording lives in the language files. */
enum class PassphraseProblem { TOO_SHORT, TOO_FEW_CHARACTERS, TOO_COMMON, SEQUENTIAL }

/**
 * Minimal, honest passphrase rules. Length is what matters against offline guessing; the estimate
 * is deliberately conservative and does not try to out-guess a dictionary attacker.
 */
object PassphrasePolicy {
    const val MIN_LENGTH = 12
    private const val HUMAN_DISCOUNT = 0.4

    private val COMMON = listOf(
        "password", "passwort", "qwerty", "123456", "letmein", "iloveyou", "admin", "welcome", "kryptex",
        "abcdef", "111111", "000000",
    )

    /** @return why [passphrase] is not acceptable, or null if it is. */
    fun check(passphrase: String): PassphraseProblem? {
        if (passphrase.length < MIN_LENGTH) return PassphraseProblem.TOO_SHORT
        if (passphrase.toSet().size < 5) return PassphraseProblem.TOO_FEW_CHARACTERS
        val lowered = passphrase.lowercase()
        // A common word, repeated or padded with a few digits or symbols, is still a common word.
        if (COMMON.any { word -> lowered.contains(word) && lowered.replace(word, "").length <= 4 }) {
            return PassphraseProblem.TOO_COMMON
        }
        if (isSequential(lowered)) return PassphraseProblem.SEQUENTIAL
        return null
    }

    fun strength(passphrase: String): Strength {
        if (passphrase.isEmpty()) return Strength.TOO_WEAK
        if (check(passphrase) != null && passphrase.length >= MIN_LENGTH) return Strength.TOO_WEAK

        var classes = 0
        if (passphrase.any { it.isLowerCase() }) classes++
        if (passphrase.any { it.isUpperCase() }) classes++
        if (passphrase.any { it.isDigit() }) classes++
        if (passphrase.any { !it.isLetterOrDigit() }) classes++

        val unique = passphrase.toSet().size
        // Rough bits: log2(pool) per character, discounted for repeated characters. Human-chosen
        // text is far more predictable than random characters, so the result is cut to 40 %:
        // only a long passphrase can reach "Strong".
        val pool = when (classes) { 1 -> 26; 2 -> 52; 3 -> 62; else -> 94 }
        val effectiveLength = minOf(passphrase.length.toDouble(), unique * 2.0)
        val bits = effectiveLength * (Math.log(pool.toDouble()) / Math.log(2.0)) * HUMAN_DISCOUNT
        return when {
            bits < 45 -> Strength.WEAK
            bits < 62 -> Strength.FAIR
            bits < 80 -> Strength.STRONG
            else -> Strength.EXCELLENT
        }
    }

    private fun isSequential(s: String): Boolean {
        if (s.length < 8) return false
        var run = 1
        for (i in 1 until s.length) {
            val step = s[i].code - s[i - 1].code
            run = if (step == 1 || step == -1) run + 1 else 1
            if (run >= 8) return true
        }
        return false
    }
}
