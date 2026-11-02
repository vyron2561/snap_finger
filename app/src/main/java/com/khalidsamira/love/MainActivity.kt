package com.khalidsamira.love

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var statusDot: TextView
    private lateinit var toggleButton: Button
    private lateinit var logoView: ImageView

    private var enabled = false
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private var accent = DEFAULT_ACCENT
    private var bright = DEFAULT_BRIGHT
    private var muted = DEFAULT_MUTED
    private var border = DEFAULT_BORDER

    companion object {
        private const val PREFS_NAME = "kofaxa_appearance"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_LOGO_URI = "logo_uri"
        private const val REQUEST_BACKGROUND = 3101
        private const val REQUEST_LOGO = 3102

        private val DEFAULT_ACCENT = Color.rgb(167, 139, 250)
        private val DEFAULT_BRIGHT = Color.rgb(196, 181, 253)
        private val DEFAULT_MUTED = Color.rgb(166, 157, 184)
        private val DEFAULT_BORDER = Color.rgb(73, 52, 111)
        private val DEFAULT_BACKGROUND = Color.rgb(8, 7, 13)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadAppearance()
        enabled = SnapWakeService.isActive
        buildScreen()
        refreshState()
    }

    private val updater = object : Runnable {
        override fun run() {
            val active = SnapWakeService.isActive
            if (enabled != active) {
                enabled = active
                refreshState()
            }
            handler.postDelayed(this, 400)
        }
    }

    private fun loadAppearance() {
        accent = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        bright = mix(accent, Color.WHITE, 0.24f)
        muted = Color.rgb(166, 157, 184)
        border = mix(accent, Color.BLACK, 0.58f)
    }

    private fun buildScreen() {
        val screen = FrameLayout(this)

        // Background layer: default KOFAXA artwork, a chosen gallery image, or a custom solid color.
        val backgroundImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val imageUri = prefs.getString(KEY_BACKGROUND_URI, null)
            when {
                imageUri != null -> {
                    try {
                        setImageURI(Uri.parse(imageUri))
                        visibility = View.VISIBLE
                    } catch (_: Exception) {
                        prefs.edit().remove(KEY_BACKGROUND_URI).apply()
                        setImageResource(R.drawable.kofaxa_background)
                    }
                }
                prefs.contains(KEY_BACKGROUND_COLOR) -> {
                    visibility = View.GONE
                    screen.setBackgroundColor(prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND))
                }
                else -> setImageResource(R.drawable.kofaxa_background)
            }
        }
        screen.addView(backgroundImage, FrameLayout.LayoutParams(-1, -1))

        val imageOverlay = View(this).apply {
            val hasPhoto = prefs.getString(KEY_BACKGROUND_URI, null) != null
            setBackgroundColor(Color.BLACK)
            alpha = if (hasPhoto) 0.42f else 0f
        }
        screen.addView(imageOverlay, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        screen.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(screen)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
        }
        // FrameLayout.LayoutParams is accepted by ScrollView (which extends FrameLayout).
        scroll.addView(root, FrameLayout.LayoutParams(-1, -1))

        space(root, 22)

        logoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "KOFAXA profile picture"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 20, 36))
                setStroke(dp(3), accent)
            }
            // Clip the selected artwork itself to a true circle; keep the ring as a foreground.
            setPadding(0, 0, 0, 0)
            clipToOutline = true
            foreground = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), accent)
            }
            elevation = dp(7).toFloat()

            val logoUri = prefs.getString(KEY_LOGO_URI, null)
            if (logoUri == null) {
                setImageResource(R.drawable.kofaxa_logo)
            } else {
                try {
                    setImageURI(Uri.parse(logoUri))
                } catch (_: Exception) {
                    prefs.edit().remove(KEY_LOGO_URI).apply()
                    setImageResource(R.drawable.kofaxa_logo)
                }
            }
        }
        root.addView(logoView, LinearLayout.LayoutParams(dp(116), dp(116)))
        space(root, 11)

        val name = label("KOFAXA", 30f, Color.WHITE, true, Gravity.CENTER)
        name.letterSpacing = 0.18f
        root.addView(name, full())

        val subtitle = label("SNAP TO WAKE", 11f, bright, true, Gravity.CENTER)
        subtitle.letterSpacing = 0.28f
        root.addView(subtitle, full())

        space(root, 27)

        // Keep the original centered WAKE YOUR PHONE card.
        val main = card()
        root.addView(main, full())
        main.addView(label("WAKE YOUR PHONE", 20f, Color.WHITE, true), full())
        space(main, 8)
        main.addView(
            label("Wake your screen with a simple finger snap.", 14f, muted),
            full()
        )
        space(main, 16)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = label("●", 17f, Color.rgb(130, 110, 150), true)
        statusText = label("Snap detection is off", 14f, muted, true)
        row.addView(statusDot)
        row.addView(statusText, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            leftMargin = dp(10)
        })
        main.addView(row, full())
        space(main, 19)

        toggleButton = Button(this).apply {
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(3).toFloat()
            setOnClickListener {
                if (SnapWakeService.isActive) disableSnapWake() else enableSnapWake()
            }
        }
        main.addView(toggleButton, LinearLayout.LayoutParams(-1, dp(54)))

        // Small appearance shortcut, preserving the original screen layout.
        space(root, 14)
        val customizeButton = Button(this).apply {
            text = "Customize"
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bright)
            background = rounded(Color.argb(28, 255, 255, 255), 24, border, 1)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { showCustomizeDialog() }
        }
        root.addView(customizeButton, LinearLayout.LayoutParams(-1, dp(48)))

        // The weighted spacer keeps the signature near the bottom on tall screens.
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        space(root, 14)
        val footer = label("BY YOUSSEF", 10f, muted, true, Gravity.CENTER)
        footer.letterSpacing = 0.20f
        root.addView(footer, full())

        val barColor = if (prefs.contains(KEY_BACKGROUND_COLOR)) {
            darken(prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND), 0.48f)
        } else {
            Color.rgb(8, 7, 13)
        }
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun showCustomizeDialog() {
        val wakePrefs = getSharedPreferences("wake_settings", MODE_PRIVATE)
        val pocketEnabled = wakePrefs.getBoolean("pocket_protection_enabled", false)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(15, 15, 18))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        }

        val heading = label("PERSONALIZE KOFAXA", 20f, Color.WHITE, true)
        heading.letterSpacing = 0.06f
        page.addView(heading, full())
        space(page, 4)
        page.addView(
            label("A cleaner look, your colors, and smarter snap controls.", 12f, Color.rgb(170, 170, 178), false),
            full()
        )
        space(page, 18)

        fun addSection(text: String) {
            val section = label(text.uppercase(), 10f, accent, true)
            section.letterSpacing = 0.14f
            page.addView(section, full())
            space(page, 8)
        }

        lateinit var dialog: AlertDialog

        fun addAction(title: String, detail: String, symbol: String, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(11), dp(13), dp(11))
                background = rounded(
                    Color.rgb(29, 29, 34),
                    17,
                    Color.rgb(58, 58, 66),
                    1
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

            val icon = TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                textSize = 21f
                setTextColor(accent)
                background = rounded(Color.rgb(43, 43, 50), 13, Color.TRANSPARENT, 0)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(43), dp(43)))
            row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            textColumn.addView(label(title, 14f, Color.WHITE, true), full())
            space(textColumn, 3)
            textColumn.addView(label(detail, 11f, muted, false), full())
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

            val arrow = label("›", 25f, muted, false, Gravity.CENTER)
            row.addView(arrow, LinearLayout.LayoutParams(dp(24), dp(40)))
            page.addView(row, full())
            space(page, 8)
        }

        addSection("Appearance")
        addAction(
            "Accent color",
            "Choose any color for buttons and highlights",
            "◉"
        ) {
            dialog.dismiss()
            showColorPicker("Accent color", KEY_ACCENT, prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT))
        }
        addAction(
            "Background color",
            "Set a custom solid background color",
            "▧"
        ) {
            dialog.dismiss()
            showColorPicker(
                "Background color",
                KEY_BACKGROUND_COLOR,
                prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND)
            )
        }

        addSection("Images")
        addAction(
            "Background photo",
            "Choose an image from your gallery",
            "▣"
        ) {
            dialog.dismiss()
            openImagePicker(REQUEST_BACKGROUND)
        }
        addAction(
            "Profile logo",
            "Choose the circular KOFAXA profile picture",
            "◎"
        ) {
            dialog.dismiss()
            openImagePicker(REQUEST_LOGO)
        }

        addSection("Smart controls")
        val pocketRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(29, 29, 34), 17, Color.rgb(58, 58, 66), 1)
        }
        val pocketText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pocketText.addView(label("Pocket Protection", 14f, Color.WHITE, true), full())
        space(pocketText, 3)
        val pocketStatus = label(
            if (pocketEnabled) "ON · prevents accidental snaps" else "OFF · prevents accidental snaps",
            11f, if (pocketEnabled) bright else muted
        )
        pocketText.addView(pocketStatus, full())
        pocketRow.addView(pocketText, LinearLayout.LayoutParams(0, -2, 1f))
        val pocketSwitch = android.widget.Switch(this).apply {
            isChecked = pocketEnabled
            showText = false
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.rgb(175, 175, 185))
            )
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, Color.rgb(75, 75, 84))
            )
            setOnCheckedChangeListener { _, checked ->
                wakePrefs.edit().putBoolean("pocket_protection_enabled", checked).apply()
                pocketStatus.text = if (checked) "ON · prevents accidental snaps" else "OFF · prevents accidental snaps"
                pocketStatus.setTextColor(if (checked) bright else muted)
                notifySnapServiceSettingsChanged()
                Toast.makeText(
                    this@MainActivity,
                    if (checked) "Pocket Protection enabled" else "Pocket Protection disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pocketRow.addView(pocketSwitch)
        page.addView(pocketRow, full())
        space(page, 8)

        space(page, 5)
        val restore = TextView(this).apply {
            text = "Restore default appearance"
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = rounded(Color.rgb(22, 19, 29), 13, Color.rgb(52, 45, 65), 1)
            setOnClickListener {
                dialog.dismiss()
                restoreDefaultAppearance()
            }
        }
        page.addView(restore, full())

        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Done", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width = minOf(resources.displayMetrics.widthPixels - dp(28), dp(420))
            dialog.window?.setLayout(width, minOf(dp(650), resources.displayMetrics.heightPixels - dp(70)))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                text = "CLOSE"
                setTextColor(Color.rgb(215, 215, 225))
                background = rounded(Color.rgb(38, 38, 46), 22, Color.rgb(70, 70, 82), 1)
                setPadding(dp(18), dp(7), dp(18), dp(7))
            }
        }
        dialog.show()
    }

    private fun notifySnapServiceSettingsChanged() {
        if (!SnapWakeService.isActive) return

        val intent = Intent(this, SnapWakeService::class.java).apply {
            action = SnapWakeService.ACTION_UPDATE_SETTINGS
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not update snap settings: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showColorPicker(title: String, key: String, initialColor: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var selectedHue = hsv[0]
        var selectedSaturation = hsv[1]
        var selectedValue = hsv[2]
        var selectedName = "Custom color"
        var updatingHex = false

        val namedColors = listOf(
            "Purple" to "#8B5CF6", "Violet" to "#7C3AED", "Lavender" to "#C4B5FD", "Lilac" to "#A78BFA",
            "Indigo" to "#4F46E5", "Blue" to "#2563EB", "Sky" to "#0EA5E9", "Cyan" to "#06B6D4",
            "Teal" to "#0D9488", "Mint" to "#34D399", "Green" to "#16A34A", "Lime" to "#84CC16",
            "Yellow" to "#EAB308", "Amber" to "#F59E0B", "Orange" to "#F97316", "Coral" to "#FB7185",
            "Red" to "#EF4444", "Rose" to "#F43F5E", "Pink" to "#EC4899", "Magenta" to "#D946EF",
            "White" to "#FFFFFF", "Silver" to "#CBD5E1", "Slate" to "#64748B", "Charcoal" to "#27272A"
        )

        fun selectedColor(): Int = Color.HSVToColor(
            floatArrayOf(selectedHue, selectedSaturation, selectedValue)
        )

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(22))
            setBackgroundColor(Color.rgb(13, 13, 17))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        val preview = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val hexInput = android.widget.EditText(this).apply {
            isSingleLine = true
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            hint = "Enter HEX, e.g. #8B5CF6"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(Color.rgb(27, 27, 34), 15, Color.rgb(65, 65, 77), 1)
        }

        fun updatePreview(updateHex: Boolean = true) {
            val color = selectedColor()
            val hex = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color)
            preview.text = "$selectedName     $hex"
            preview.setTextColor(contrastColor(color))
            preview.background = rounded(color, 13, Color.WHITE, 1)
            if (updateHex && hexInput.text.toString() != hex) {
                updatingHex = true
                hexInput.setText(hex)
                hexInput.setSelection(hexInput.text.length)
                updatingHex = false
            }
        }

        val saturationView = object : View(this@MainActivity) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val hueColor = Color.HSVToColor(floatArrayOf(selectedHue, 1f, 1f))
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, Color.BLACK), null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = dp(3).toFloat()
                paint.color = Color.WHITE
                val x = selectedSaturation * width
                val y = (1f - selectedValue) * height
                canvas.drawCircle(x, y, dp(9).toFloat(), paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = Color.BLACK
                canvas.drawCircle(x, y, dp(5).toFloat(), paint)
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        selectedSaturation = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedValue = (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedName = "Custom color"
                        updatePreview()
                        invalidate()
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        performClick()
                        return true
                    }
                }
                return true
            }
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }

        val hueView = object : View(this@MainActivity) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val spectrum = intArrayOf(
                Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
                Color.GREEN, Color.YELLOW, Color.RED
            )
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val centerY = height / 2f
                val radius = height * 0.28f
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    spectrum, null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(0f, centerY - radius, width.toFloat(), centerY + radius,
                    radius, radius, paint)
                paint.shader = null
                val markerX = (selectedHue / 360f) * width
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(markerX, centerY, dp(9).toFloat(), paint)
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = dp(2).toFloat()
                paint.color = Color.BLACK
                canvas.drawCircle(markerX, centerY, dp(9).toFloat(), paint)
                paint.style = android.graphics.Paint.Style.FILL
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        selectedHue = ((event.x / width.coerceAtLeast(1)) * 360f).coerceIn(0f, 360f)
                        selectedName = "Custom color"
                        saturationView.invalidate()
                        invalidate()
                        updatePreview()
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        performClick()
                        return true
                    }
                }
                return true
            }
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }

        page.addView(label("COLOR STUDIO", 19f, Color.WHITE, true), full())
        space(page, 7)
        page.addView(preview, LinearLayout.LayoutParams(-1, dp(50)))
        space(page, 12)
        page.addView(label("FINE TUNE · SATURATION & BRIGHTNESS", 11f, Color.rgb(190, 190, 202), true), full())
        space(page, 7)
        saturationView.background = rounded(Color.DKGRAY, 16, Color.rgb(75, 75, 84), 1)
        saturationView.clipToOutline = true
        page.addView(saturationView, LinearLayout.LayoutParams(-1, dp(175)))
        space(page, 9)
        page.addView(label("HUE", 11f, Color.rgb(190, 190, 202), true), full())
        page.addView(hueView, LinearLayout.LayoutParams(-1, dp(34)))
        space(page, 8)
        page.addView(label("HEX VALUE", 11f, Color.rgb(190, 190, 202), true), full())
        space(page, 6)
        page.addView(hexInput, LinearLayout.LayoutParams(-1, dp(46)))
        space(page, 15)
        page.addView(label("CURATED PALETTE", 11f, Color.rgb(190, 190, 202), true), full())
        space(page, 7)

        val grid = android.widget.GridLayout(this).apply {
            columnCount = 4
            useDefaultMargins = false
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
        }
        namedColors.forEach { (name, hex) ->
            val color = Color.parseColor(hex)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(7), dp(4), dp(7))
                background = rounded(Color.rgb(25, 25, 31), 13, Color.rgb(48, 48, 58), 1)
                setOnClickListener {
                    Color.colorToHSV(color, hsv)
                    selectedHue = hsv[0]
                    selectedSaturation = hsv[1]
                    selectedValue = hsv[2]
                    selectedName = name
                    saturationView.invalidate()
                    hueView.invalidate()
                    updatePreview()
                }
            }
            val chip = TextView(this).apply {
                background = rounded(color, 9, Color.argb(210, 255, 255, 255), 1)
                elevation = dp(2).toFloat()
            }
            item.addView(chip, LinearLayout.LayoutParams(dp(43), dp(25)))
            space(item, 5)
            item.addView(label(name, 10f, Color.WHITE, true, Gravity.CENTER), full())
            item.addView(label(hex, 8f, Color.rgb(160, 160, 174), false, Gravity.CENTER), full())
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(78)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(1), dp(2), dp(1))
            }
            grid.addView(item, params)
        }
        page.addView(grid, LinearLayout.LayoutParams(-1, -2))

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (updatingHex) return
                val raw = s?.toString()?.trim().orEmpty()
                if (raw.isBlank()) return
                try {
                    val parsed = Color.parseColor(if (raw.startsWith("#")) raw else "#$raw")
                    Color.colorToHSV(parsed, hsv)
                    selectedHue = hsv[0]
                    selectedSaturation = hsv[1]
                    selectedValue = hsv[2]
                    selectedName = namedColors.firstOrNull {
                        Color.parseColor(it.second) == parsed
                    }?.first ?: "Custom color"
                    saturationView.invalidate()
                    hueView.invalidate()
                    updatePreview(false)
                } catch (_: IllegalArgumentException) {
                    // Keep the user's in-progress HEX entry until it becomes valid.
                }
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Color Studio · $title")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply color", null)
            .create()

        updatePreview()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width = minOf(resources.displayMetrics.widthPixels - dp(28), dp(420))
            dialog.window?.setLayout(width, minOf(dp(690), resources.displayMetrics.heightPixels - dp(70)))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                text = "APPLY COLOR"
                setTextColor(Color.BLACK)
                background = rounded(accent, 22, accent, 1)
                setPadding(dp(18), dp(7), dp(18), dp(7))
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                text = "CANCEL"
                setTextColor(Color.rgb(215, 215, 225))
                background = rounded(Color.rgb(38, 38, 46), 22, Color.rgb(70, 70, 82), 1)
                setPadding(dp(18), dp(7), dp(18), dp(7))
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = selectedColor()
                val editor = prefs.edit()
                if (key == KEY_BACKGROUND_COLOR) {
                    editor.putInt(KEY_BACKGROUND_COLOR, selected)
                    editor.remove(KEY_BACKGROUND_URI)
                } else {
                    editor.putInt(KEY_ACCENT, selected)
                }
                editor.apply()
                dialog.dismiss()
                rebuildAppearance()
            }
        }
        dialog.show()
    }

    private fun openImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open gallery: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses ACTION_OPEN_DOCUMENT for compatibility with Android 9 and newer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!
        try {
            val grantedFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (grantedFlags != 0) {
                contentResolver.takePersistableUriPermission(uri, grantedFlags)
            }
            when (requestCode) {
                REQUEST_BACKGROUND -> {
                    prefs.edit()
                        .putString(KEY_BACKGROUND_URI, uri.toString())
                        .remove(KEY_BACKGROUND_COLOR)
                        .apply()
                    rebuildAppearance()
                    Toast.makeText(this, "Background saved", Toast.LENGTH_SHORT).show()
                }
                REQUEST_LOGO -> {
                    prefs.edit().putString(KEY_LOGO_URI, uri.toString()).apply()
                    rebuildAppearance()
                    Toast.makeText(this, "Logo saved", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save this image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreDefaultAppearance() {
        prefs.edit()
            .remove(KEY_ACCENT)
            .remove(KEY_BACKGROUND_COLOR)
            .remove(KEY_BACKGROUND_URI)
            .remove(KEY_LOGO_URI)
            .apply()
        rebuildAppearance()
        Toast.makeText(this, "Default appearance restored", Toast.LENGTH_SHORT).show()
    }

    private fun rebuildAppearance() {
        loadAppearance()
        buildScreen()
        refreshState()
    }

    private fun enableSnapWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        try {
            val intent = Intent(this, SnapWakeService::class.java).apply {
                action = SnapWakeService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            Toast.makeText(this, "KOFAXA is starting...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableSnapWake() {
        val intent = Intent(this, SnapWakeService::class.java).apply {
            action = SnapWakeService.ACTION_STOP
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (_: Exception) {
        }
        enabled = false
        refreshState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableSnapWake()
            } else {
                Toast.makeText(this, "Audio access is needed for snap detection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enabled = SnapWakeService.isActive
        if (::toggleButton.isInitialized) refreshState()
        handler.removeCallbacks(updater)
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }

    private fun refreshState() {
        if (!::toggleButton.isInitialized) return
        enabled = SnapWakeService.isActive
        statusDot.setTextColor(
            if (enabled) Color.rgb(74, 222, 128) else Color.rgb(126, 112, 145)
        )
        statusText.text = if (enabled) "Snap detection is active" else "Snap detection is off"
        statusText.setTextColor(if (enabled) Color.rgb(134, 239, 172) else muted)
        toggleButton.text = if (enabled) "Disable Snap to Wake" else "Enable Snap to Wake"
        toggleButton.setTextColor(Color.rgb(18, 12, 30))
        toggleButton.background = rounded(
            if (enabled) bright else accent,
            28, bright, 1
        )
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                mix(accent, Color.rgb(20, 16, 31), 0.68f),
                Color.rgb(19, 15, 29),
                mix(accent, Color.rgb(19, 15, 29), 0.78f)
            )
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), border)
        }
        elevation = dp(4).toFloat()
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.CENTER_VERTICAL
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.gravity = gravity
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(strokeWidth), strokeColor)
        }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun full() = LinearLayout.LayoutParams(-1, -2)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun mix(first: Int, second: Int, amount: Float): Int {
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * amount).toInt().coerceIn(0, 255),
            (Color.green(first) * inverse + Color.green(second) * amount).toInt().coerceIn(0, 255),
            (Color.blue(first) * inverse + Color.blue(second) * amount).toInt().coerceIn(0, 255)
        )
    }

    private fun darken(color: Int, amount: Float): Int = mix(color, Color.BLACK, amount)

    private fun contrastColor(color: Int): Int {
        val brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return if (brightness > 150) Color.BLACK else Color.WHITE
    }
}
