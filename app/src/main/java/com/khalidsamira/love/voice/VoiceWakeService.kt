package com.khalidsamira.love.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.khalidsamira.love.screen.ScreenController
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.sqrt

class VoiceWakeService : Service() {
    companion object {
        private const val TAG = "VoiceWakeService"
        const val ACTION_START = "com.khalidsamira.love.VOICE_START"
        const val ACTION_STOP = "com.khalidsamira.love.VOICE_STOP"
        @Volatile var isActive = false
            private set
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false
    private var worker: Thread? = null
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var screenController: ScreenController

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE)
        screenController = ScreenController(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Microphone permission missing")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        isActive = true
        worker = thread(name = "KofaxaVoiceWake") { initializeAndListen() }
        return START_STICKY
    }

    private fun initializeAndListen() {
        try {
            val modelDir = VoskModelManager(this).modelDir()
            if (!VoskModelManager(this).isModelReady()) error("Vosk model is not ready")
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, VoiceWakeConfig.SAMPLE_RATE.toFloat(), VoiceWakeConfig.GRAMMAR_JSON)
            listenLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Voice wake initialization/listening failed", e)
        } finally {
            running = false
            isActive = false
            releaseAudio()
            try { recognizer?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: Exception) {}
            recognizer = null
            model = null
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun listenLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceWakeConfig.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) error("Unsupported audio configuration")
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            VoiceWakeConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, VoiceWakeConfig.CHUNK_SIZE * 4)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord initialization failed")
        }
        audioRecord = record
        val buffer = ShortArray(VoiceWakeConfig.CHUNK_SIZE)
        val preRoll = ArrayDeque<ShortArray>()
        var noiseFloor = VoiceWakeConfig.VAD_BASE_THRESHOLD
        var inSpeech = false
        var silenceChunks = 0
        var lastWakeAt = 0L
        record.startRecording()

        while (running) {
            if (!isWithinSchedule()) {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                Thread.sleep(1000)
                if (running && isWithinSchedule()) {
                    record.startRecording()
                    preRoll.clear()
                    inSpeech = false
                    silenceChunks = 0
                }
                continue
            }
            val count = record.read(buffer, 0, buffer.size)
            if (count <= 0) continue
            val rms = rms(buffer, count)
            val threshold = maxOf(VoiceWakeConfig.VAD_BASE_THRESHOLD, noiseFloor * VoiceWakeConfig.VAD_NOISE_FACTOR)
            if (!inSpeech && rms < threshold) {
                noiseFloor = noiseFloor * 0.98 + rms * 0.02
                if (preRoll.size == 3) preRoll.removeFirst()
                preRoll.addLast(buffer.copyOf(count))
                continue
            }
            if (!inSpeech) {
                inSpeech = true
                silenceChunks = 0
                while (preRoll.isNotEmpty()) feed(preRoll.removeFirst())
            }
            feed(buffer.copyOf(count))
            if (rms < threshold) silenceChunks++ else silenceChunks = 0
            if (silenceChunks >= 8) {
                val result = recognizer?.finalResult.orEmpty()
                detectKeyword(result, System.currentTimeMillis(), lastWakeAt)?.let { lastWakeAt = it }
                recognizer?.reset()
                inSpeech = false
                silenceChunks = 0
                preRoll.clear()
            }
            if (!inSpeech) {
                if (preRoll.size == 3) preRoll.removeFirst()
                preRoll.addLast(buffer.copyOf(count))
            }
        }
    }

    private fun feed(samples: ShortArray) {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val value = samples[i].toInt()
            bytes[i * 2] = (value and 0xff).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
        }
        recognizer?.acceptWaveForm(bytes, bytes.size)
    }

    private fun rms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val value = samples[i].toDouble()
            sum += value * value
        }
        return sqrt(sum / count)
    }

    private fun detectKeyword(jsonText: String, now: Long, previousWake: Long): Long? {
        return try {
            val text = JSONObject(jsonText).optString("text").lowercase()
            if (text.isBlank() || now - previousWake < 5000L) return null
            val selected = (prefs.getString(VoiceWakeConfig.KEY_KEYWORD, "kofaxa") ?: "kofaxa").lowercase()
            val matched = text.contains(selected) || text.contains("kofasha") || text.contains("kofaxa") ||
                text.contains("wake up") || text.contains("computer")
            if (!matched) return null
            Log.i(TAG, "Wake phrase detected: $text")
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!power.isInteractive) screenController.wakeScreen()
            now
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse Vosk result", e)
            null
        }
    }

    private fun isWithinSchedule(): Boolean {
        val start = prefs.getInt(VoiceWakeConfig.KEY_START_HOUR, VoiceWakeConfig.DEFAULT_START_HOUR).coerceIn(0, 23)
        val end = prefs.getInt(VoiceWakeConfig.KEY_END_HOUR, VoiceWakeConfig.DEFAULT_END_HOUR).coerceIn(0, 23)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (start == end) return true
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(VoiceWakeConfig.CHANNEL_ID, "Voice wake", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, VoiceWakeConfig.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification = builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KOFAXA")
            .setContentText("Listening for your wake phrase")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(VoiceWakeConfig.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(VoiceWakeConfig.NOTIFICATION_ID, notification)
        }
    }

    private fun releaseAudio() {
        val record = audioRecord ?: return
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        } catch (_: Exception) {}
        try { record.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        worker?.interrupt()
        isActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
