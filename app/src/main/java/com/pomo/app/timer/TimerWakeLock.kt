package com.pomo.app.timer

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Keeps the CPU alive while a timer is running.
 *
 * The web app calls `navigator.wakeLock.request('screen')` and re-requests it
 * whenever the tab becomes visible again. Android's FLAG_KEEP_SCREEN_ON (set in
 * MainActivity) only covers the foreground, so it does not help once the app is
 * backgrounded and the device would otherwise doze. A PARTIAL_WAKE_LOCK is the
 * closest equivalent that still works in the background, and it is what makes
 * the completion alarm fire on time.
 *
 * The lock is acquired with a timeout so a leaked reference cannot keep the CPU
 * awake indefinitely.
 */
class TimerWakeLock(context: Context) {

    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    @Synchronized
    fun acquire() {
        if (wakeLock?.isHeld == true) return
        try {
            wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire(MAX_HOLD_MS)
                }
        } catch (e: SecurityException) {
            // WAKE_LOCK is declared in the manifest, but a device policy or a
            // revoked grant can still refuse. The timer still works, it just
            // may be delayed while the device sleeps.
            Log.w(TAG, "could not acquire wake lock", e)
            wakeLock = null
        }
    }

    @Synchronized
    fun release() {
        val lock = wakeLock ?: return
        try {
            if (lock.isHeld) lock.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not release wake lock", e)
        } finally {
            wakeLock = null
        }
    }

    private companion object {
        const val TAG = "pomo:timer"

        /** Upper bound on a single hold, so a leak cannot pin the CPU awake. */
        const val MAX_HOLD_MS = 3 * 60 * 60 * 1000L
    }
}
