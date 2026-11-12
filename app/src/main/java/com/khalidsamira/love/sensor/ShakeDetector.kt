package com.khalidsamira.love.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val SHAKE_THRESHOLD_LOW = 1.5f
        private const val SHAKE_THRESHOLD_MEDIUM = 2.5f
        private const val SHAKE_THRESHOLD_HIGH = 3.8f
        private const val CROSSINGS_REQUIRED = 3
        private const val WINDOW_MS = 700L
        private const val COOLDOWN_MS = 2000L
    }

    private var threshold = SHAKE_THRESHOLD_MEDIUM
    private var lastCrossingTime = 0L
    private var crossingCount = 0
    private var lastShakeTime = 0L
    private var wasAbove = false

    fun setSensitivity(level: Int) {
        threshold = when (level) {
            0 -> SHAKE_THRESHOLD_LOW
            2 -> SHAKE_THRESHOLD_HIGH
            else -> SHAKE_THRESHOLD_MEDIUM
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (event.values.size < 3) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()
        val isAbove = magnitude > threshold

        if (isAbove && !wasAbove) {
            if (now - lastCrossingTime > WINDOW_MS) {
                crossingCount = 0
            }
            crossingCount++
            lastCrossingTime = now

            if (crossingCount >= CROSSINGS_REQUIRED &&
                now - lastShakeTime >= COOLDOWN_MS) {
                lastShakeTime = now
                crossingCount = 0
                onShakeDetected()
            }
        }
        wasAbove = isAbove
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
