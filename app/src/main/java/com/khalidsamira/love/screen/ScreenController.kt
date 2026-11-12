package com.khalidsamira.love.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import com.khalidsamira.love.SnapLockAdminReceiver

class ScreenController(context: Context) {

    companion object {
        private const val TAG = "ScreenController"
        private const val WAKELOCK_TAG = "KhalidSamiraLove:SnapWake"
        private const val WAKE_TIMEOUT_MS = 3000L
    }

    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(appContext, SnapLockAdminReceiver::class.java)

    fun handleSnap() {
        if (!powerManager.isInteractive) {
            wakeScreen()
            return
        }
        tryLockScreen()
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        if (powerManager.isInteractive) return
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

    private fun tryLockScreen() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            android.util.Log.w(
                TAG,
                "Snap to Lock is enabled but Device Admin is not active."
            )
            return
        }
        try {
            devicePolicyManager.lockNow()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Could not lock screen", e)
        }
    }
}
