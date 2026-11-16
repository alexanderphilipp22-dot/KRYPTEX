package com.kryptex.vault.ui

import android.os.SystemClock
import com.kryptex.vault.crypto.SessionManager

/**
 * Decides when the vault locks itself:
 *  - the moment the app leaves the foreground (screen off, home, app switcher), and
 *  - after [timeoutSeconds] without any touch, if the user configured one.
 *
 * Screens that hand control to the system on purpose (photo picker, permission dialog, save-as)
 * call [expectExternalLaunch] first so that the round trip does not lock the user out mid-task.
 * That exemption is bounded: if the user wanders off to another app from there, the vault still
 * locks after [EXTERNAL_GRACE_MILLIS].
 */
class AutoLock(
    private val sessions: SessionManager,
    private val timeoutSeconds: () -> Int,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var externalLaunchDeadline = NONE
    private var lastInteraction = clock()
    private var inBackground = false

    @Synchronized
    fun onUserInteraction() {
        lastInteraction = clock()
    }

    /** Call right before launching a system picker, dialog or other activity on the user's behalf. */
    @Synchronized
    fun expectExternalLaunch() {
        externalLaunchDeadline = clock() + EXTERNAL_GRACE_MILLIS
    }

    @Synchronized
    fun onActivityStopped() {
        inBackground = true
        if (externalLaunchDeadline == NONE) sessions.lock()
    }

    @Synchronized
    fun onActivityStarted() {
        inBackground = false
        externalLaunchDeadline = NONE
        lastInteraction = clock()
    }

    /** Called about once a second. */
    @Synchronized
    fun tick() {
        if (!sessions.isUnlocked) return
        val now = clock()

        if (inBackground && externalLaunchDeadline != NONE && now > externalLaunchDeadline) {
            sessions.lock()
            return
        }
        val timeout = timeoutSeconds()
        if (timeout > 0 && now - lastInteraction >= timeout * 1000L) sessions.lock()
    }

    companion object {
        const val EXTERNAL_GRACE_MILLIS = 120_000L
        private const val NONE = -1L
    }
}
