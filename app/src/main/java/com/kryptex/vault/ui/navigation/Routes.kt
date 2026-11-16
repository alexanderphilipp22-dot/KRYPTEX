package com.kryptex.vault.ui.navigation

/** Screens of the unlocked app. Onboarding and the lock screen are handled by the root gate, not routes. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val VAULT = "vault"
    const val ITEM = "item/{id}"
    const val EMBED = "embed"
    const val EXTRACT = "extract"
    const val SETTINGS = "settings"
    const val GESTURE_ENROLL = "gesture/enroll"

    const val ARG_ID = "id"

    fun item(id: Long) = "item/$id"

    /** Destinations that show the bottom navigation bar. */
    val topLevel = setOf(DASHBOARD, VAULT, SETTINGS)
}
