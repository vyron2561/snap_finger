package com.khalidsamira.love

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeWakeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeWakeTest"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val powerManager =
            getSystemService(Context.POWER_SERVICE) as PowerManager

        Log.i(
            TAG,
            "KEY_RECEIVED key=${event.keyCode} action=${event.action} interactive=${powerManager.isInteractive}"
        )

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            return false
        }

        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) return false

        if (powerManager.isInteractive) return false

        try {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "KhalidSamiraLove:VolumeWake"
            )

            wakeLock?.acquire(3000L)
            Log.i(TAG, "WAKELOCK_ACQUIRED")
        } catch (e: Exception) {
            Log.e(TAG, "WAKELOCK_FAILED", e)
        }

        return true
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }

        wakeLock = null
        super.onDestroy()
    }
}
