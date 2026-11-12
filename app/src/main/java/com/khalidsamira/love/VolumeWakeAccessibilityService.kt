package com.khalidsamira.love

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.khalidsamira.love.screen.ScreenController

class VolumeWakeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeWakeService"
        private const val PREFS = "wake_settings"
        private const val KEY_ENABLED = "volume_wake_enabled"

        @Volatile
        var isConnected: Boolean = false
            private set
    }

    private lateinit var screenController: ScreenController
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        screenController = ScreenController(this)
        android.util.Log.i(TAG, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            return false
        }

        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return false
        }

        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        if (!enabled) return false

        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive) return false

        try {
            screenController.handleSnap()
            android.util.Log.i(TAG, "WAKE_BY_VOLUME_UP")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "WAKE_FAILED", e)
        }

        // Consume the event so the volume doesn't change.
        return true
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }
}
