package com.khalidsamira.love

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.InputFilter
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.khalidsamira.love.voice.VoiceWakeConfig
import com.khalidsamira.love.voice.VoiceWakeService
import com.khalidsamira.love.voice.VoskModelManager
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val MIC_PERMISSION_CODE = 3001

        private val COLOR_BG = Color.rgb(8, 7, 13)
        private val COLOR_CARD = Color.rgb(15, 12, 22)
        private val COLOR_BORDER = Color.rgb(88, 62, 140)
        private val COLOR_PURPLE = Color.rgb(167, 139, 250)
        private val COLOR_PURPLE_LIGHT = Color.rgb(196, 181, 253)
        private val COLOR_WHITE = Color.WHITE
        private val COLOR_MUTED = Color.rgb(160, 160, 176)
        private val COLOR_GREEN = Color.rgb(74, 222, 128)
        private val COLOR_GREEN_LIGHT = Color.rgb(134, 239, 172)
        private val COLOR_OFF = Color.rgb(126, 112, 145)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        AppUpdater.checkForUpdates(this, manual = false)
    }

    override fun onResume() {
        super.onResume()
        if (::actionButton.isInitialized) refreshState()
        refreshVoiceCard()
        AppUpdater.installPendingUpdate(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(52), dp(22), dp(28))
            setBackgroundColor(COLOR_BG)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "KOFAXA"
            textSize = 32f
            setTextColor(COLOR_WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "VOICE WAKE ONLY"
            textSize = 11f
            setTextColor(COLOR_PURPLE_LIGHT)
            gravity = Gravity.CENTER
        })
        addVoiceCard(root)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        AppUpdater.checkForUpdates(this, manual = false)
    }

    override fun onResume() {
        super.onResume()
        refreshVoiceCard()
        AppUpdater.installPendingUpdate(this)
    }

    private var voiceStatusView: TextView? = null
    private var voiceActionButton: Button? = null
    private var voiceStartButton: Button? = null
    private var voiceEndButton: Button? = null
    private var voiceKeywordButton: Button? = null

    private fun addVoiceCard(root: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_CARD)
                setStroke(dp(1), Color.rgb(45, 32, 70))
            }
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        card.addView(TextView(this).apply {
            text = "WAKE WITH VOICE"
            textSize = 10f
            setTextColor(COLOR_PURPLE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
        })
        space(card, 10)
        voiceStatusView = TextView(this).apply {
            textSize = 12f
            setTextColor(COLOR_MUTED)
        }
        card.addView(voiceStatusView, LinearLayout.LayoutParams(-1, -2))
        space(card, 12)
        voiceActionButton = Button(this).apply {
            textSize = 13f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PURPLE_LIGHT)
            background = voiceButtonBackground()
            stateListAnimator = null
            setOnClickListener { onVoiceActionClicked() }
        }
        card.addView(voiceActionButton, LinearLayout.LayoutParams(-1, dp(46)))
        space(card, 8)
        voiceKeywordButton = Button(this).apply {
            textSize = 12f
            isAllCaps = false
            setTextColor(COLOR_PURPLE_LIGHT)
            background = voiceButtonBackground()
            stateListAnimator = null
            setOnClickListener { editWakeKeyword() }
        }
        card.addView(voiceKeywordButton, LinearLayout.LayoutParams(-1, dp(42)))
        space(card, 8)
        val scheduleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        voiceStartButton = Button(this).apply {
            textSize = 11f
            isAllCaps = false
            setTextColor(COLOR_MUTED)
            background = voiceButtonBackground()
            stateListAnimator = null
            setOnClickListener { chooseVoiceHour(true) }
        }
        voiceEndButton = Button(this).apply {
            textSize = 11f
            isAllCaps = false
            setTextColor(COLOR_MUTED)
            background = voiceButtonBackground()
            stateListAnimator = null
            setOnClickListener { chooseVoiceHour(false) }
        }
        scheduleRow.addView(voiceStartButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        scheduleRow.addView(voiceEndButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) })
        card.addView(scheduleRow)
        space(card, 8)
        card.addView(Button(this).apply {
            text = "Disable battery optimization"
            textSize = 11f
            isAllCaps = false
            setTextColor(COLOR_MUTED)
            background = voiceButtonBackground()
            stateListAnimator = null
            setOnClickListener {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")))
                        } else {
                            Toast.makeText(this@MainActivity, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Open Settings → Battery → Unrestricted for KOFAXA", Toast.LENGTH_LONG).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        refreshVoiceCard()
    }

    private fun voiceButtonBackground() = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Color.argb(28, 167, 139, 250))
        setStroke(dp(1), COLOR_BORDER)
    }

    private fun refreshVoiceCard() {
        val status = voiceStatusView ?: return
        val button = voiceActionButton ?: return
        val manager = VoskModelManager(this)
        val ready = manager.isModelReady()
        val active = VoiceWakeService.isActive
        val keyword = getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE)
            .getString(VoiceWakeConfig.KEY_KEYWORD, VoiceWakeConfig.DEFAULT_KEYWORD)
            ?: VoiceWakeConfig.DEFAULT_KEYWORD
        status.text = when {
            !ready -> "Voice model not downloaded · about 40 MB"
            active -> "Listening only for: $keyword"
            else -> "Model ready · wake phrase: $keyword"
        }
        voiceKeywordButton?.text = "Change wake phrase · $keyword"
        status.setTextColor(if (active) COLOR_GREEN_LIGHT else COLOR_MUTED)
        button.text = when {
            !ready -> "Download voice model"
            active -> "Disable voice wake"
            else -> "Enable voice wake"
        }
        val prefs = getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE)
        voiceStartButton?.text = "Start: %02d:00".format(prefs.getInt(VoiceWakeConfig.KEY_START_HOUR, 0))
        voiceEndButton?.text = "End: %02d:00".format(prefs.getInt(VoiceWakeConfig.KEY_END_HOUR, 0))
    }

    private fun editWakeKeyword() {
        val prefs = getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE)
        val current = prefs.getString(VoiceWakeConfig.KEY_KEYWORD, VoiceWakeConfig.DEFAULT_KEYWORD)
            ?: VoiceWakeConfig.DEFAULT_KEYWORD
        val input = EditText(this).apply {
            setText(current)
            hint = "wake up"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(40))
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Wake phrase")
            .setMessage("Choose the English words KOFAXA should recognize. Other speech and background sounds will not trigger it.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val keyword = VoiceWakeConfig.normalizeKeyword(input.text.toString())
                if (keyword.isBlank()) {
                    Toast.makeText(this, "Wake phrase cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.edit().putString(VoiceWakeConfig.KEY_KEYWORD, keyword).apply()
                refreshVoiceCard()
                if (VoiceWakeService.isActive) {
                    stopService(Intent(this, VoiceWakeService::class.java).setAction(VoiceWakeService.ACTION_STOP))
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing) startVoiceService()
                    }, 800L)
                    Toast.makeText(this, "Wake phrase saved · restarting listener", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Wake phrase saved", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun chooseVoiceHour(start: Boolean) {
        val prefs = getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE)
        val key = if (start) VoiceWakeConfig.KEY_START_HOUR else VoiceWakeConfig.KEY_END_HOUR
        val current = prefs.getInt(key, if (start) 0 else 0)
        TimePickerDialog(this, { _, hour, _ ->
            prefs.edit().putInt(key, hour).apply()
            refreshVoiceCard()
        }, current, 0, true).show()
    }

    private fun onVoiceActionClicked() {
        val manager = VoskModelManager(this)
        when {
            VoiceWakeService.isActive -> {
                stopService(Intent(this, VoiceWakeService::class.java).setAction(VoiceWakeService.ACTION_STOP))
                getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(VoiceWakeConfig.KEY_ENABLED, false).apply()
                refreshVoiceCard()
            }
            !manager.isModelReady() -> downloadVoiceModel(manager)
            else -> requestMicAndStartVoice()
        }
    }

    private fun downloadVoiceModel(manager: VoskModelManager) {
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Downloading voice model")
            .setMessage("One-time download · about 40 MB")
            .setView(progress)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setCancelable(false)
        dialog.show()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            val result = manager.downloadModel { pct -> runOnUiThread { if (!isFinishing) progress.progress = pct } }
            runOnUiThread {
                if (!isFinishing) {
                    dialog.dismiss()
                    if (result.isSuccess) {
                        Toast.makeText(this, "Voice model downloaded", Toast.LENGTH_SHORT).show()
                        refreshVoiceCard()
                    } else {
                        Toast.makeText(this, "Download failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
                executor.shutdown()
            }
        }
    }

    private fun requestMicAndStartVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_CODE)
        } else {
            startVoiceService()
        }
    }

    private fun startVoiceService() {
        try {
            val intent = Intent(this, VoiceWakeService::class.java).setAction(VoiceWakeService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE).edit()
                .putBoolean(VoiceWakeConfig.KEY_ENABLED, true).apply()
            Toast.makeText(this, "Voice wake service starting", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start voice wake: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refreshVoiceCard()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoiceService()
            else Toast.makeText(this, "Microphone permission is required for voice wake", Toast.LENGTH_LONG).show()
            return
        }
    }
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
