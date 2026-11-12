
package com.khalidsamira.love

import com.khalidsamira.love.analysis.SnapAnalyzer
import com.khalidsamira.love.analysis.SnapSignatureManager
import com.khalidsamira.love.sensor.ProximityMonitor
import com.khalidsamira.love.screen.ScreenController

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import kotlin.concurrent.thread

class SnapWakeService : Service() {

    companion object {
        const val ACTION_START = "com.khalidsamira.love.START_SNAP"
        const val ACTION_STOP = "com.khalidsamira.love.STOP_SNAP"
        const val ACTION_UPDATE_SETTINGS = "com.khalidsamira.love.UPDATE_SETTINGS"
        const val ACTION_LEARN_SIGNATURE = "com.khalidsamira.love.LEARN_SIGNATURE"

        @Volatile
        var isActive: Boolean = false
            private set

        private const val CHANNEL_ID = "snap_wake_channel"
        private const val NOTIFICATION_ID = 731
        private const val SAMPLE_RATE = 16000
    }

    @Volatile private var running = false
    @Volatile private var recorder: AudioRecord? = null

    private lateinit var proximityMonitor: ProximityMonitor
    private lateinit var screenController: ScreenController

    override fun onCreate() {
        super.onCreate()
        proximityMonitor = ProximityMonitor(this)
        screenController = ScreenController(this)
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
                SnapSignatureManager(this).startLearning()
                startListening()
            }

            ACTION_UPDATE_SETTINGS -> proximityMonitor.refresh()
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

        proximityMonitor.refresh()

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

                if (analysis.peak >= 3000) {
                    android.util.Log.i(
                        "SnapWakeService",
                        "SPECTRAL zcr=${analysis.spectral.zeroCrossingRate} " +
                            "centroid=${analysis.spectral.spectralCentroid} " +
                            "highRatio=${analysis.spectral.highFreqRatio} " +
                            "clapLike=${analysis.isClapLike}"
                    )
                }

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

                    val feature = signatureManager.createRichFeature(samples, analysis)

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

        if (pocketProtection && proximityMonitor.isBlocking()) {
            android.util.Log.i(
                "SnapWakeService",
                "Snap ignored: proximity sensor is covered"
            )
            return
        }

        screenController.handleSnap()
    }

    override fun onDestroy() {
        running = false
        isActive = false

        proximityMonitor.disable()

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

