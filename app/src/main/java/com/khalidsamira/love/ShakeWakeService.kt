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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.khalidsamira.love.screen.ScreenController
import com.khalidsamira.love.sensor.ShakeDetector

class ShakeWakeService : Service() {

    companion object {
        private const val TAG = "ShakeWakeService"
        private const val CHANNEL_ID = "shake_wake_channel"
        private const val NOTIFICATION_ID = 812
        private const val PREFS = "wake_settings"
        private const val KEY_MODE = "shake_mode"
        private const val KEY_SENSITIVITY = "shake_sensitivity"

        const val MODE_LIFT = 0
        const val MODE_SHAKE = 1

        const val ACTION_START = "com.khalidsamira.love.SHAKE_START"
        const val ACTION_STOP = "com.khalidsamira.love.SHAKE_STOP"
        const val ACTION_UPDATE = "com.khalidsamira.love.SHAKE_UPDATE"

        @Volatile
        var isActive: Boolean = false
            private set
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var screenController: ScreenController
    private lateinit var shakeDetector: ShakeDetector

    private var accelerometer: Sensor? = null
    private var significantMotion: Sensor? = null
    private var screenReceiverRegistered = false
    private var sensorRegistered = false
    private var currentMode = MODE_LIFT

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> armSensor()
                Intent.ACTION_SCREEN_ON -> disarmSensor()
            }
        }
    }

    private val significantMotionListener =
        object : android.hardware.TriggerEventListener() {
            override fun onTrigger(event: android.hardware.TriggerEvent?) {
                sensorRegistered = false
                try {
                    screenController.wakeScreen()
                    android.util.Log.i(TAG, "WAKE_BY_SIGNIFICANT_MOTION")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "WAKE_FAILED", e)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        screenController = ScreenController(this)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        significantMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        shakeDetector = ShakeDetector {
            try {
                screenController.wakeScreen()
                android.util.Log.i(TAG, "WAKE_BY_SHAKE")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "WAKE_FAILED", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundCompat()
                registerScreenReceiver()
                loadSettings()
                isActive = true
                if (!isScreenOn()) armSensor()
            }
            ACTION_UPDATE -> {
                loadSettings()
                disarmSensor()
                if (!isScreenOn()) armSensor()
            }
        }
        return START_STICKY
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        currentMode = prefs.getInt(KEY_MODE, MODE_LIFT)
        shakeDetector.setSensitivity(prefs.getInt(KEY_SENSITIVITY, 1))
    }

    private fun isScreenOn(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isInteractive
    }

    private fun armSensor() {
        if (sensorRegistered) return
        val sensor = when (currentMode) {
            MODE_SHAKE -> accelerometer
            else -> significantMotion
        }
        if (sensor == null) {
            android.util.Log.w(TAG, "Requested sensor not available")
            return
        }
        try {
            if (currentMode == MODE_SHAKE) {
                sensorRegistered = sensorManager.registerListener(
                    shakeDetector,
                    sensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            } else {
                sensorRegistered = sensorManager.requestTriggerSensor(
                    significantMotionListener,
                    sensor
                )
            }
            android.util.Log.i(TAG, "ARMED mode=$currentMode result=$sensorRegistered")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ARM_FAILED", e)
        }
    }

    private fun disarmSensor() {
        if (!sensorRegistered) return
        try {
            if (currentMode == MODE_SHAKE) {
                sensorManager.unregisterListener(shakeDetector)
            } else {
                significantMotion?.let {
                    sensorManager.cancelTriggerSensor(significantMotionListener, it)
                }
            }
        } catch (_: Exception) {
        }
        sensorRegistered = false
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("KOFAXA")
            .setContentText("Wake on motion is active")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake on motion",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isActive = false
        disarmSensor()
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
