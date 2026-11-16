package com.kryptex.vault.ui

import android.app.Activity
import android.content.Context
import android.view.View
import com.kryptex.vault.BuildConfig

/**
 * Safety net for broken GPU rendering (a driver that drops its context, or rendering that takes
 * the process down before anything is shown).
 *
 * Every launch counts as pending until a frame has actually been handed to the system compositor.
 * After [STRIKES] launches in a row that never got that far, this app version renders the window
 * in software from then on: slower, but it shows. Healthy devices reset the count on their first
 * frame and never notice; a new app version tries the GPU again.
 *
 * It also opts the window out of system "force dark", which some vendor dark modes apply to apps
 * on their own and which would repaint a UI that is already dark by design.
 *
 * Stores two numbers in a private preferences file, nothing else.
 */
object RenderGuard {
    private const val PREFS = "kryptex_render"
    private const val KEY_PENDING = "pending_launches"
    private const val KEY_SOFTWARE_VERSION = "software_for_version"
    private const val STRIKES = 2

    /** Starts reading the preferences file in the background, well before an activity needs it. */
    fun prefetch(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Call from `onCreate`, before `setContent`. */
    fun install(activity: Activity) {
        val decor = activity.window.decorView
        decor.isForceDarkAllowed = false

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY_PENDING, 0)
        var software = prefs.getInt(KEY_SOFTWARE_VERSION, -1) == BuildConfig.VERSION_CODE
        if (!software && pending >= STRIKES) {
            software = true
            prefs.edit().putInt(KEY_SOFTWARE_VERSION, BuildConfig.VERSION_CODE).apply()
        }
        if (software) decor.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        prefs.edit().putInt(KEY_PENDING, pending + 1).apply()
        // Registered once the view is attached, so it lands on the window's own observer. The
        // splash animates continuously, so a next frame always follows.
        decor.post {
            decor.viewTreeObserver.registerFrameCommitCallback {
                prefs.edit().putInt(KEY_PENDING, 0).apply()
            }
        }
    }
}
