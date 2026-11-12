package com.khalidsamira.love

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import com.khalidsamira.love.voice.VoiceWakeConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal startup screen showing only the Royal Purple K Crest icon. */
class SplashActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val opened = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.systemUiVisibility = 0

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.royal_purple_k_crest)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }
        root.addView(
            icon,
            FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER)
        )

        setContentView(root)
        startInitialization()
    }

    private fun startInitialization() {
        val startedAt = SystemClock.elapsedRealtime()
        executor.execute {
            try {
                getSharedPreferences(VoiceWakeConfig.PREFS, MODE_PRIVATE).all
                VoiceWakeConfig.DEFAULT_KEYWORD
            } catch (_: Exception) {
                // Continue to MainActivity; it has its own recovery path.
            }

            val remaining = (650L - (SystemClock.elapsedRealtime() - startedAt))
                .coerceAtLeast(0L)
            mainHandler.postDelayed({ openMain() }, remaining)
        }
    }

    private fun openMain() {
        if (opened.getAndSet(true) || isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
