package com.khalidsamira.love

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val PREFS = "wake_settings"
        private const val KEY_ENABLED = "volume_wake_enabled"

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

        // ─── Header ───
        val title = TextView(this).apply {
            text = "KOFAXA"
            textSize = 32f
            setTextColor(COLOR_WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "WAKE WITH VOLUME UP"
            textSize = 11f
            setTextColor(COLOR_PURPLE_LIGHT)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.28f
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        })

        space(root, 44)

        // ─── Status card ───
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(26))
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
            text = "Volume wake is off"
            textSize = 16f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(10)
        })
        card.addView(statusRow, LinearLayout.LayoutParams(-1, -2))

        space(card, 14)

        val description = TextView(this).apply {
            text = "Press the volume up button while the screen is off " +
                "to instantly wake your phone.\n\n" +
                "Requires Accessibility permission. The service runs " +
                "locally and sends no data anywhere."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        card.addView(description, LinearLayout.LayoutParams(-1, -2))

        space(card, 20)

        actionButton = Button(this).apply {
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(18, 12, 30))
            stateListAnimator = null
            setOnClickListener { onActionClicked() }
        }
        card.addView(actionButton, LinearLayout.LayoutParams(-1, dp(54)))

        space(root, 28)

        // ─── Updates card ───
        val updatesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(COLOR_CARD)
                setStroke(dp(1), Color.rgb(45, 32, 70))
            }
        }
        root.addView(updatesCard, LinearLayout.LayoutParams(-1, -2))

        val updatesTitle = TextView(this).apply {
            text = "UPDATES"
            textSize = 10f
            setTextColor(COLOR_PURPLE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
        }
        updatesCard.addView(updatesTitle, LinearLayout.LayoutParams(-1, -2))

        space(updatesCard, 10)

        val versionLabel = TextView(this).apply {
            text = "Current version: ${getVersionName()}"
            textSize = 12f
            setTextColor(COLOR_MUTED)
        }
        updatesCard.addView(versionLabel, LinearLayout.LayoutParams(-1, -2))

        space(updatesCard, 12)

        val checkUpdatesButton = Button(this).apply {
            text = "Check for updates"
            textSize = 13f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PURPLE_LIGHT)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(28, 167, 139, 250))
                setStroke(dp(1), COLOR_BORDER)
            }
            stateListAnimator = null
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Auto-updater coming in the next update",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        updatesCard.addView(
            checkUpdatesButton,
            LinearLayout.LayoutParams(-1, dp(46))
        )

        space(root, 24)

        val footer = TextView(this).apply {
            text = "BY YOUSSEF"
            textSize = 10f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        // System bars
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG

        refreshState()
    }

    private fun onActionClicked() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!isServiceEnabled()) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            openAccessibilitySettings()
        } else {
            openAccessibilitySettings()
        }
    }

    private fun refreshState() {
        val active = isServiceEnabled()
        statusDot.setTextColor(if (active) COLOR_GREEN else COLOR_OFF)
        statusText.text = if (active) "Volume wake is active" else "Volume wake is off"
        statusText.setTextColor(if (active) COLOR_GREEN_LIGHT else COLOR_MUTED)

        actionButton.text = if (active) "Manage in Settings" else "Enable now"
        actionButton.background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(COLOR_PURPLE)
            setStroke(dp(1), COLOR_PURPLE_LIGHT)
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(
            this,
            VolumeWakeAccessibilityService::class.java
        ).flattenToString()
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(
                this,
                "Find 'KOFAXA Volume Wake' and enable it",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not open Settings: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
