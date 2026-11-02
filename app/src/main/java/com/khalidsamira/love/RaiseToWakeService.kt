package com.khalidsamira.love

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class RaiseToWakeService : Service() {

    companion object {
        private const val TAG = "RaiseToWake"
        private const val CHANNEL_ID = "raise_to_wake"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var triggerRegistered = false
    private var receiverRegistered = false

    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            triggerRegistered = false

            val power = getSystemService(POWER_SERVICE) as PowerManager
            if (!power.isInteractive) {
                try {
                    @Suppress("DEPRECATION")
                    val lock = power.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                        "KhalidSamiraLove:RaiseWake"
                    )
                    lock.acquire(2500L)
                    Log.i(TAG, "WAKE_TRIGGERED")
                } catch (e: Exception) {
                    Log.e(TAG, "WAKE_FAILED", e)
                }
            }

            if (!power.isInteractive) armSensor()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> armSensor()
                Intent.ACTION_SCREEN_ON -> disarmSensor()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (getSharedPreferences("wake_settings", MODE_PRIVATE)
                .getBoolean("raise_enabled", false).not()) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (!power.isInteractive) armSensor()

        return START_STICKY
    }

    private fun armSensor() {
        if (triggerRegistered) return
        val sensor = motionSensor ?: return

        try {
            triggerRegistered = sensorManager.requestTriggerSensor(triggerListener, sensor)
            Log.i(TAG, "SENSOR_ARMED=$triggerRegistered")
        } catch (e: Exception) {
            Log.e(TAG, "SENSOR_REGISTER_FAILED", e)
        }
    }

    private fun disarmSensor() {
        if (!triggerRegistered) return
        try {
            motionSensor?.let {
                sensorManager.cancelTriggerSensor(triggerListener, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SENSOR_CANCEL_FAILED", e)
        }
        triggerRegistered = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الإيقاظ عند الحركة",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("KHALID + SAMIRA = LOVE")
                .setContentText("ميزة الإيقاظ عند الحركة تعمل")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build().apply { flags = flags or Notification.FLAG_ONGOING_EVENT }
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("KHALID + SAMIRA = LOVE")
                .setContentText("ميزة الإيقاظ عند الحركة تعمل")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build().apply { flags = flags or Notification.FLAG_ONGOING_EVENT }
        }
    }

    override fun onDestroy() {
        disarmSensor()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
