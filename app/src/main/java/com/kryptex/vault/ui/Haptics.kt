package com.kryptex.vault.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one short, crisp pulse that tells the user a gesture of their sequence was recognised, so
 * they know when to move on to the next. Purely functional: no sound, no animation.
 *
 * The on/off switch is a plain preference, not a row of the encrypted settings table, because it
 * is needed on the lock screen where that table cannot be read yet. It holds one boolean that
 * says nothing about the vault.
 */
class Haptics(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val vibrator: Vibrator? = runCatching { appContext.getSystemService(Vibrator::class.java) }.getOrNull()

    private val state = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))

    /** Whether the gesture pulse is on. */
    val enabled: StateFlow<Boolean> = state.asStateFlow()

    fun setEnabled(value: Boolean) {
        state.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** One system "click" pulse, if the user has it on and the phone can vibrate. Never throws. */
    fun gestureRecognised() {
        if (!state.value) return
        val v = vibrator ?: return
        try {
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (_: Exception) {
            // Vendor vibrator services can be picky; a missing pulse must never cost the user the unlock.
        }
    }

    companion object {
        const val KEY_ENABLED = "haptic_feedback_enabled"
        const val DEFAULT_ENABLED = true
        private const val PREFS = "kryptex_haptics"
    }
}
