package com.khalidsamira.love.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.khalidsamira.love.screen.ScreenController

/**
 * Optional accessibility hook for waking the display with the hardware Volume Up key.
 *
 * Returning false is intentional: Android still receives the key and changes the volume.
 * The service only requests a short wake lock when the display is currently asleep.
 */
class VolumeWakeAccessibilityService : AccessibilityService() {

    private lateinit var screenController: ScreenController
    private var lastWakeRequestAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        screenController = ScreenController(this)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        Log.i(TAG, "Volume-key wake accessibility service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP ||
            event.action != KeyEvent.ACTION_DOWN ||
            event.repeatCount != 0
        ) {
            return false
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            val now = System.currentTimeMillis()
            if (now - lastWakeRequestAt >= WAKE_DEBOUNCE_MS) {
                lastWakeRequestAt = now
                try {
                    screenController.wakeScreen()
                    Log.i(TAG, "Volume Up requested screen wake")
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to wake screen from Volume Up", error)
                }
            }
        }

        // Do not consume the key; keep the phone's normal volume behavior.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "KofaxaVolumeWake"
        private const val WAKE_DEBOUNCE_MS = 1200L
    }
}
