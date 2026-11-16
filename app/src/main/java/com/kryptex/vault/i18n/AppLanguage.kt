package com.kryptex.vault.i18n

import java.util.Locale

/**
 * Languages the UI is available in. [code] is what is stored in the encrypted settings table, so it
 * must never change for an existing entry.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    DE("de", "Deutsch"),
    RU("ru", "Русский"),
    ;

    val strings: AppStrings
        get() = when (this) {
            EN -> EnStrings
            DE -> DeStrings
            RU -> RuStrings
        }

    companion object {
        /** @return the language stored under [code], or null for null or an unknown code. */
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** The closest supported language for [locale]; English when there is none. */
        fun fromLocale(locale: Locale): AppLanguage =
            entries.firstOrNull { it.code == locale.language } ?: EN

        /** What the phone is set to. Used until the vault is unlocked and the saved choice can be read. */
        fun systemDefault(): AppLanguage = fromLocale(Locale.getDefault())
    }
}
