package com.khalidsamira.love

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

object KofaxaDialog {
    private const val BG = 0xFF0B0912.toInt()
    private const val PANEL = 0xFF15111F.toInt()
    private const val PURPLE = 0xFFA78BFA.toInt()
    private const val PURPLE_DARK = 0xFF7C3AED.toInt()
    private const val WHITE = 0xFFF7F5FF.toInt()
    private const val MUTED = 0xFFA7A1B5.toInt()

    data class ProgressUi(
        val dialog: AlertDialog,
        val bar: ProgressBar,
        val detail: TextView,
        val percent: TextView
    ) {
        fun update(value: Int, detailText: String? = null) {
            bar.isIndeterminate = false
            bar.progress = value.coerceIn(0, 100)
            percent.text = "${value.coerceIn(0, 100)}%"
            if (detailText != null) detail.text = detailText
        }
    }

    fun showProgress(activity: Activity, title: String, subtitle: String): ProgressUi {
        val content = panel(activity)
        val mark = TextView(activity).apply {
            text = "K"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x332A174D)
                setStroke(dp(activity, 1), PURPLE_DARK)
            }
        }
        content.addView(mark, LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(activity, 18)
        })
        content.addView(label(activity, title, 20f, WHITE, true).apply {
            gravity = Gravity.CENTER
        })
        val detail = label(activity, subtitle, 12f, MUTED, false).apply {
            gravity = Gravity.CENTER
        }
        content.addView(detail, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(activity, 8)
        })
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF30283D.toInt())
        }
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(activity, 7)).apply {
            topMargin = dp(activity, 24)
        })
        val percent = label(activity, "0%", 12f, PURPLE, true).apply {
            gravity = Gravity.END
        }
        content.addView(percent, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(activity, 9)
        })
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.72f)
        dialog.window?.setLayout(dp(activity, 320), -2)
        return ProgressUi(dialog, bar, detail, percent)
    }

    fun showUpdate(activity: Activity, version: String, onUpdate: () -> Unit) {
        val content = panel(activity)
        val badge = TextView(activity).apply {
            text = "NEW VERSION  •  $version"
            textSize = 10f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(PURPLE)
            background = rounded(0x332A174D, 0x665B3B91, dp(activity, 18).toFloat())
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8))
        }
        content.addView(badge, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(activity, 18)
        })
        content.addView(label(activity, "KOFAXA is ready to evolve", 19f, WHITE, true).apply {
            gravity = Gravity.CENTER
        })
        content.addView(label(activity, "A new version is available with improvements and fixes. Download it now and keep your app up to date.", 13f, MUTED, false).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(activity, 3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(activity, 10)
            bottomMargin = dp(activity, 22)
        })
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val later = actionButton(activity, "Later", false)
        val update = actionButton(activity, "Update now", true)
        actions.addView(later, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        actions.addView(View(activity), LinearLayout.LayoutParams(dp(activity, 10), 1))
        actions.addView(update, LinearLayout.LayoutParams(0, dp(activity, 46), 1.25f))
        content.addView(actions)
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setDimAmount(0.72f)
            dialog.window?.setLayout(dp(activity, 340), -2)
        }
        later.setOnClickListener { dialog.dismiss() }
        update.setOnClickListener { dialog.dismiss(); onUpdate() }
        dialog.show()
    }

    private fun panel(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(activity, 24), dp(activity, 26), dp(activity, 24), dp(activity, 22))
        background = rounded(PANEL, 0xFF342746.toInt(), dp(activity, 24).toFloat())
    }

    private fun label(activity: Activity, value: String, size: Float, color: Int, bold: Boolean) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun actionButton(activity: Activity, value: String, primary: Boolean) =
        TextView(activity).apply {
            text = value
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) WHITE else PURPLE)
            background = rounded(
                if (primary) PURPLE_DARK else 0x332A174D,
                if (primary) PURPLE_DARK else 0x665B3B91,
                dp(activity, 14).toFloat()
            )
            isClickable = true
            isFocusable = true
        }

    private fun rounded(color: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(1, stroke)
    }

    private fun dp(activity: Activity, value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()
}
