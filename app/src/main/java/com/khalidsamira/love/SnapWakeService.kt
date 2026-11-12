
package com.khalidsamira.love

import com.khalidsamira.love.analysis.SnapAnalyzer
import com.khalidsamira.love.analysis.SnapSignatureManager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlin.concurrent.thread

class SnapWakeService : Service() {

    companion object {
        const val ACTION_START = "com.khalidsamira.love.START_SNAP"
        const val ACTION_STOP = "com.khalidsamira.love.STOP_SNAP"
        const val ACTION_UPDATE_SETTINGS = "com.khalidsamira.love.UPDATE_SETTINGS"
        const val ACTION_LEARN_SIGNATURE = "com.khalidsamira.love.LEARN_SIGNATURE"
        private const val SIGNATURE_PREFS = "snap_signature"
        private const val SIGNATURE_KEY = "template"
        private const val SAMPLE_KEY = "samples"
        private const val COUNT_KEY = "learn_count"
        private const val REQUIRED_SAMPLES = 10

        @Volatile
        var isActive: Boolean = false
            private set

        private const val CHANNEL_ID = "snap_wake_channel"
        private const val NOTIFICATION_ID = 731
        private const val SAMPLE_RATE = 16000
    }

    @Volatile private var running = false
    @Volatile private var recorder: AudioRecord? = null

    @Volatile private var proximityNear = false
    @Volatile private var proximityHasReading = false
    @Volatile private var proximityAvailable = false
    private var proximityRegistered = false
    private var proximitySensorManager: SensorManager? = null

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_PROXIMITY ||
                event.values.isEmpty()
            ) return

            proximityNear = event.values[0] < event.sensor.maximumRange
            proximityHasReading = true
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> startListening()
            ACTION_LEARN_SIGNATURE -> {
                getSharedPreferences(SIGNATURE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("learning", true).putInt(COUNT_KEY, 0)
                    .remove(SAMPLE_KEY).remove(SIGNATURE_KEY).apply()
                startListening()
            }

            ACTION_UPDATE_SETTINGS -> updateProximityRegistration()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("KOFAXA")
            .setContentText("Snap wake is active")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Snap to Wake",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description = "Keeps finger snap detection active"

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun updateProximityRegistration() {
        val enabled = getSharedPreferences("wake_settings", MODE_PRIVATE)
            .getBoolean("pocket_protection_enabled", false)

        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        if (!enabled) {
            if (proximityRegistered) {
                manager.unregisterListener(proximityListener)
            }
            proximityRegistered = false
            proximityAvailable = false
            proximityHasReading = false
            proximityNear = false
            proximitySensorManager = null
            android.util.Log.i("SnapWakeService", "Pocket Protection OFF")
            return
        }

        if (proximityRegistered) return

        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            proximityAvailable = false
            proximityHasReading = false
            android.util.Log.w(
                "SnapWakeService",
                "Pocket Protection unavailable: no proximity sensor"
            )
            return
        }

        proximitySensorManager = manager
        proximityAvailable = true
        proximityHasReading = false

        // Conservatively block snaps until the sensor reports its first value.
        proximityNear = true

        proximityRegistered = manager.registerListener(
            proximityListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (!proximityRegistered) {
            proximityAvailable = false
            proximityNear = false
            android.util.Log.e(
                "SnapWakeService",
                "Could not register proximity sensor"
            )
        } else {
            android.util.Log.i(
                "SnapWakeService",
                "Pocket Protection ON; maxRange=${sensor.maximumRange}"
            )
        }
    }

    private fun startListening() {
        if (running) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) {
            isActive = false
            stopSelf()
            return
        }

        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 4096)
            )
        } catch (e: Exception) {
            android.util.Log.e("SnapWakeService", "Could not create AudioRecord", e)
            isActive = false
            stopSelf()
            return
        }

        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            isActive = false
            stopSelf()
            return
        }

        recorder = audio
        running = true
        isActive = true

        getSharedPreferences("wake_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("snap_enabled", true)
            .apply()

        updateProximityRegistration()

        thread(name = "SnapAudioDetector") {
            detectSnaps(audio)
        }
    }

    private fun detectSnaps(audio: AudioRecord) {
        val samples = ShortArray(1024)
        val analyzer = SnapAnalyzer()
        val signatureManager = SnapSignatureManager(this)

        var filled = 0
        var lastAccepted = 0L
        var armed = true
        var quietFrames = 0
        var lastDebugLog = 0L

        try {
            audio.startRecording()

            while (running) {
                val count = audio.read(
                    samples,
                    filled,
                    samples.size - filled
                )

                if (count <= 0) continue

                filled += count
                if (filled < samples.size) continue

                val analysis = analyzer.analyze(samples)
                val now = System.currentTimeMillis()

                if (analysis.peak >= 3000 && analysis.rms >= 250.0 &&
                    now - lastDebugLog >= 180L
                ) {
                    lastDebugLog = now
                    android.util.Log.i(
                        "SnapWakeService",
                        "AUDIO_FRAME candidate=${analysis.looksLikeSnap} " +
                            "peak=${analysis.peak} rms=${analysis.rms.toInt()} " +
                            "crest=${analysis.crest} lowRatio=${analysis.lowRatio} " +
                            "concentration=${analysis.concentration} " +
                            "activeChunks=${analysis.activeChunks} " +
                            "decay=${analysis.decayRatio}"
                    )
                }

                if (analysis.looksLikeSnap && armed && now - lastAccepted >= 550L) {
                    armed = false
                    lastAccepted = now

                    android.util.Log.i(
                        "SnapWakeService",
                        "Snap candidate: peak=${analysis.peak}, " +
                            "rms=${analysis.rms.toInt()}, " +
                            "lowRatio=${analysis.lowRatio}, " +
                            "decay=${analysis.decayRatio}"
                    )

                    val feature = signatureManager.createFeature(samples)

                    if (signatureManager.isLearning()) {
                        signatureManager.addLearningSample(feature)
                    } else {
                        if (signatureManager.shouldAccept(feature)) {
                            handleSnap()
                        } else {
                            android.util.Log.i(
                                "SnapWakeService",
                                "Snap rejected by personal profile"
                            )
                        }
                    }
                }

                // Require a quiet interval before detecting another snap.
                if (analysis.rms < maxOf(analyzer.noiseFloor * 1.7, 500.0)) {
                    quietFrames++
                    if (quietFrames >= 4) armed = true
                } else {
                    quietFrames = 0
                }

                // Move the last half of the window to preserve overlap.
                System.arraycopy(samples, 512, samples, 0, 512)
                filled = 512
            }
        } catch (e: Exception) {
            if (running) {
                android.util.Log.e("SnapWakeService", "Audio detection failed", e)
                running = false
                isActive = false

                getSharedPreferences("wake_settings", MODE_PRIVATE)
                    .edit()
                    .putBoolean("snap_enabled", false)
                    .apply()

                stopSelf()
            }
        } finally {
            try {
                if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audio.stop()
                }
            } catch (_: Exception) {
            }

            audio.release()
            recorder = null
        }
    }
    private fun handleSnap() {
        val pocketProtection = getSharedPreferences("wake_settings", MODE_PRIVATE)
            .getBoolean("pocket_protection_enabled", false)

        if (pocketProtection && proximityAvailable &&
            (!proximityHasReading || proximityNear)
        ) {
            android.util.Log.i(
                "SnapWakeService",
                "Snap ignored: proximity sensor is covered"
            )
            return
        }

        val power = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Screen is off: wake it.
        if (!power.isInteractive) {
            wakeScreen()
            return
        }

        // Screen is on: the next accepted snap locks the screen.
        // Android requires the user to activate KOFAXA as a Device Admin first.
        val admin = android.content.ComponentName(
            this,
            SnapLockAdminReceiver::class.java
        )
        val policy = getSystemService(Context.DEVICE_POLICY_SERVICE)
            as android.app.admin.DevicePolicyManager

        if (!policy.isAdminActive(admin)) {
            android.util.Log.w(
                "SnapWakeService",
                "Snap to Lock is enabled but Device Admin is not active."
            )
            return
        }

        try {
            policy.lockNow()
        } catch (e: Exception) {
            android.util.Log.e(
                "SnapWakeService",
                "Could not lock screen",
                e
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val power =
            getSystemService(Context.POWER_SERVICE) as PowerManager

        if (!power.isInteractive) {
            val lock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "KhalidSamiraLove:SnapWake"
            )

            try {
                lock.acquire(3000)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        running = false
        isActive = false

        proximitySensorManager?.unregisterListener(proximityListener)
        proximityRegistered = false
        proximityAvailable = false
        proximityHasReading = false
        proximityNear = false

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        getSharedPreferences("wake_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("snap_enabled", false)
            .apply()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

