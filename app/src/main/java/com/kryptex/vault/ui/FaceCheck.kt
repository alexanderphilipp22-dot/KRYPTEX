package com.kryptex.vault.ui

import android.content.Context
import com.kryptex.vault.vision.FaceDescriptor
import com.kryptex.vault.vision.FaceGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The switch for the face check at gesture unlock: when on, the proportions of the face seen
 * during unlock must fit the ones saved when the gesture was set up (see [FaceGeometry]).
 *
 * Like [Haptics], the switch is a plain preference and not a row of the encrypted settings table,
 * because the lock screen needs it before that table can be read. It holds one boolean and says
 * nothing about the vault or the face. The face data itself never lives here: it is sealed inside
 * the gesture key slot.
 *
 * Turning it off does not delete the saved face data; it only stops the comparison. Setting the
 * gesture up again replaces it.
 */
class FaceCheck(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))

    /** Whether unlock compares the face. */
    val enabled: StateFlow<Boolean> = state.asStateFlow()

    fun setEnabled(value: Boolean) {
        state.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * The check to hand to the key manager for an unlock that saw [probe], or null when the switch
     * is off. With the switch on, a missing probe is a mismatch: if the face could not be read, the
     * check cannot be skipped by simply not looking.
     */
    fun verifier(probe: FaceDescriptor?): ((ByteArray) -> Boolean)? {
        if (!state.value) return null
        return { stored -> FaceGeometry.matchesStored(stored, probe) }
    }

    companion object {
        const val KEY_ENABLED = "face_check_enabled"
        const val DEFAULT_ENABLED = true
        private const val PREFS = "kryptex_face_check"
    }
}
