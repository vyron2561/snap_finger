package com.khalidsamira.love

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val PREFS = "wake_settings"
        private const val KEY_ENABLED = "shake_enabled"
        private const val KEY_MODE = "shake_mode"
        private const val KEY_SENSITIVITY = "shake_sensitivity"
        private const val NOTIF_PERMISSION_CODE = 2001

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
    }

    override fun onResume() {
        super.onResume()
        if (::actionButton.isInitialized) refreshState()
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

        space(root, 24)

        root.addView(TextView(this).apply {
            text = "BY YOUSSEF"
            textSize = 10f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
        }, LinearLayout.LayoutParams(-1, -2))

        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG

        refreshState()
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
