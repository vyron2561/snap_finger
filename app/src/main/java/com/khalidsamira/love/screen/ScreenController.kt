package com.khalidsamira.love.screen

import android.content.Context
import android.os.PowerManager

class ScreenController(context: Context) {

    companion object {
        private const val WAKELOCK_TAG = "KhalidSamiraLove:SnapWake"
        private const val WAKE_TIMEOUT_MS = 3000L
    }

    private val powerManager =
        context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager

    fun wakeScreen() {
        if (powerManager.isInteractive) return
        @Suppress("DEPRECATION")
        val lock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            WAKELOCK_TAG
        )
        try {
            lock.acquire(WAKE_TIMEOUT_MS)
        } catch (_: Exception) {
        }
    }

    fun isScreenOn(): Boolean = powerManager.isInteractive
}
