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
        private const val PREFS = "wake_settings"
        private const val KEY_ENABLED = "shake_enabled"
        private const val KEY_MODE = "shake_mode"
        private const val KEY_SENSITIVITY = "shake_sensitivity"
        private const val NOTIF_PERMISSION_CODE = 2001
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

    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var modeLabel: TextView
    private lateinit var sensitivityLabel: TextView

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

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(52), dp(22), dp(28))
        }
        scroll.addView(root, LinearLayout.LayoutParams(-1, -1))
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "KOFAXA"
            textSize = 32f
            setTextColor(COLOR_WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "WAKE ON MOTION"
            textSize = 11f
            setTextColor(COLOR_PURPLE_LIGHT)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.28f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        space(root, 36)

        // Status card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(COLOR_CARD)
                setStroke(dp(1), COLOR_BORDER)
            }
            elevation = dp(4).toFloat()
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2))

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        statusDot = TextView(this).apply {
            text = "●"
            textSize = 22f
            setTextColor(COLOR_OFF)
        }
        statusRow.addView(statusDot)
        statusText = TextView(this).apply {
            text = "Wake on motion is off"
            textSize = 16f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(10)
        })
        card.addView(statusRow, LinearLayout.LayoutParams(-1, -2))

        space(card, 16)

        actionButton = Button(this).apply {
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(18, 12, 30))
            stateListAnimator = null
            setOnClickListener { onActionClicked() }
        }
        card.addView(actionButton, LinearLayout.LayoutParams(-1, dp(54)))

        space(root, 20)

        // Settings card
        val settingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_CARD)
                setStroke(dp(1), Color.rgb(45, 32, 70))
            }
        }
        root.addView(settingsCard, LinearLayout.LayoutParams(-1, -2))

        settingsCard.addView(TextView(this).apply {
            text = "MODE"
            textSize = 10f
            setTextColor(COLOR_PURPLE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
        }, LinearLayout.LayoutParams(-1, -2))

        space(settingsCard, 10)

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(COLOR_WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        modeRow.addView(modeLabel, LinearLayout.LayoutParams(0, -2, 1f))
        val modeButton = Button(this).apply {
            text = "Switch"
            textSize = 12f
            isAllCaps = false
            setTextColor(COLOR_PURPLE_LIGHT)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(40, 167, 139, 250))
                setStroke(dp(1), COLOR_BORDER)
            }
            stateListAnimator = null
            setOnClickListener { toggleMode() }
        }
        modeRow.addView(modeButton, LinearLayout.LayoutParams(dp(110), dp(38)))
        settingsCard.addView(modeRow, LinearLayout.LayoutParams(-1, -2))

        space(settingsCard, 16)

        sensitivityLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(COLOR_MUTED)
        }
        settingsCard.addView(sensitivityLabel, LinearLayout.LayoutParams(-1, -2))

        space(settingsCard, 8)

        val seekBar = SeekBar(this).apply {
            max = 2
            progress = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_SENSITIVITY, 1)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(KEY_SENSITIVITY, value).apply()
                    updateSensitivityLabel(value)
                    if (ShakeWakeService.isActive) {
                        startForegroundUpdate()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        settingsCard.addView(seekBar, LinearLayout.LayoutParams(-1, dp(40)))

        addVoiceCard(root)

        space(root, 24)

        root.addView(TextView(this).apply {
            text = "BY YOUSSEF"
            textSize = 10f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
        }, LinearLayout.LayoutParams(-1, -2))


        root.addView(Button(this).apply {
            text = "Check for updates"
            textSize = 13f
            isAllCaps = false
            setTextColor(COLOR_PURPLE_LIGHT)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(28, 167, 139, 250))
                setStroke(dp(1), COLOR_BORDER)
            }
            stateListAnimator = null
            setOnClickListener { AppUpdater.checkForUpdates(this@MainActivity, manual = true) }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(14) })

        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG

        refreshState()
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

    private fun updateSensitivityLabel(value: Int) {
        val label = when (value) {
            0 -> "Sensitivity: Easy (may trigger often)"
            2 -> "Sensitivity: Hard (needs strong shake)"
            else -> "Sensitivity: Medium (recommended)"
        }
        sensitivityLabel.text = label
    }

    private fun toggleMode() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(KEY_MODE, ShakeWakeService.MODE_LIFT)
        val newMode = if (current == ShakeWakeService.MODE_LIFT)
            ShakeWakeService.MODE_SHAKE else ShakeWakeService.MODE_LIFT
        prefs.edit().putInt(KEY_MODE, newMode).apply()
        refreshState()
        if (ShakeWakeService.isActive) startForegroundUpdate()
    }

    private fun onActionClicked() {
        if (ShakeWakeService.isActive) {
            stopService(Intent(this, ShakeWakeService::class.java))
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false).apply()
            refreshState()
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_CODE
                )
                return
            }
            startWakeService()
        }
    }

    private fun startWakeService() {
        try {
            val intent = Intent(this, ShakeWakeService::class.java).apply {
                action = ShakeWakeService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true).apply()
            refreshState()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startForegroundUpdate() {
        try {
            val intent = Intent(this, ShakeWakeService::class.java).apply {
                action = ShakeWakeService.ACTION_UPDATE
            }
            startService(intent)
        } catch (_: Exception) {}
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
        if (requestCode == NOTIF_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        }
    }

    private fun refreshState() {
        val active = ShakeWakeService.isActive
        statusDot.setTextColor(if (active) COLOR_GREEN else COLOR_OFF)
        statusText.text = if (active) "Wake on motion is active" else "Wake on motion is off"
        statusText.setTextColor(if (active) COLOR_GREEN_LIGHT else COLOR_MUTED)
        actionButton.text = if (active) "Disable" else "Enable"
        actionButton.background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(COLOR_PURPLE)
            setStroke(dp(1), COLOR_PURPLE_LIGHT)
        }

        val mode = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getInt(KEY_MODE, ShakeWakeService.MODE_LIFT)
        modeLabel.text = if (mode == ShakeWakeService.MODE_LIFT)
            "Lift to wake (battery saver)" else "Shake to wake"
        updateSensitivityLabel(
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_SENSITIVITY, 1)
        )
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
