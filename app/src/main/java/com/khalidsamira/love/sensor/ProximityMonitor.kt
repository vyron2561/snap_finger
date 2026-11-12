package com.khalidsamira.love.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class ProximityMonitor(context: Context) {

    companion object {
        private const val TAG = "ProximityMonitor"
        private const val PREFS = "wake_settings"
        private const val KEY_ENABLED = "pocket_protection_enabled"
    }

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Volatile private var near = false
    @Volatile private var hasReading = false
    @Volatile private var available = false
    private var registered = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_PROXIMITY ||
                event.values.isEmpty()
            ) return
            near = event.values[0] < event.sensor.maximumRange
            hasReading = true
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun refresh() {
        val enabled = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        if (enabled) enable() else disable()
    }

    fun enable() {
        if (registered) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            available = false
            hasReading = false
            android.util.Log.w(TAG, "Pocket Protection unavailable: no proximity sensor")
            return
        }
        available = true
        hasReading = false
        near = true
        registered = sensorManager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_NORMAL
        )
        if (!registered) {
            available = false
            near = false
            android.util.Log.e(TAG, "Could not register proximity sensor")
        } else {
            android.util.Log.i(TAG, "Pocket Protection ON; maxRange=${sensor.maximumRange}")
        }
    }

    fun disable() {
        if (registered) {
            sensorManager.unregisterListener(listener)
        }
        registered = false
        available = false
        hasReading = false
        near = false
        android.util.Log.i(TAG, "Pocket Protection OFF")
    }

    fun isBlocking(): Boolean = available && (!hasReading || near)
}
