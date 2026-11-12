package com.khalidsamira.love

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.khalidsamira.love.voice.VoiceWakeConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Royal JK startup screen. The original GIF is loaded from app/src/main/assets.
 * No image-loading or coroutine dependency is required.
 */
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

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            RoyalJkGifView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val progress = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.rgb(160, 0, 255))
            progressTintList = ColorStateList.valueOf(Color.rgb(160, 0, 255))
            progressBackgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
        }
        root.addView(
            progress,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.68f).toInt(),
                dp(4)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(56)
            }
        )

        val label = TextView(this).apply {
            text = "ROYAL JK"
            setTextColor(Color.rgb(190, 190, 200))
            textSize = 11f
            letterSpacing = 0.28f
            gravity = Gravity.CENTER
        }
        root.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(28)
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(18)
            }
        )
        setContentView(root)

        startInitialization()
    }

    private fun startInitialization() {
        val startedAt = SystemClock.elapsedRealtime()
        executor.execute {
            try {
                // Read the saved voice-wake preferences used by MainActivity.
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

/** Lightweight animated GIF renderer using Android's built-in Movie decoder. */
private class RoyalJkGifView(activity: Activity) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val movie: Movie? = try {
        activity.assets.open("royal_jk_purple_wings.gif").use { Movie.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
    private val startedAt = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val gif = movie ?: return
        val duration = gif.duration().takeIf { it > 0 } ?: 1000
        gif.setTime(((SystemClock.uptimeMillis() - startedAt) % duration).toInt())

        val scale = minOf(width.toFloat() / gif.width(), height.toFloat() / gif.height())
        val left = (width - gif.width() * scale) / 2f
        val top = (height - gif.height() * scale) / 2f
        val save = canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        gif.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(save)
        postInvalidateOnAnimation()
    }
}
