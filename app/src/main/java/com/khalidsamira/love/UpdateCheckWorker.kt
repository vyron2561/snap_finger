package com.khalidsamira.love

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(UPDATE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) return Result.retry()

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("update_available", false)) return Result.success()

            val latestCode = json.optLong("version_code", 0L)
            val latestName = json.optString("version_name", "")
            val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                applicationContext.packageManager
                    .getPackageInfo(applicationContext.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                applicationContext.packageManager
                    .getPackageInfo(applicationContext.packageName, 0).versionCode.toLong()
            }

            if (latestCode > currentCode) {
                showUpdateNotification(latestName)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun showUpdateNotification(versionName: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "KOFAXA updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications when a new KOFAXA version is available" }
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_update", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            7102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("KOFAXA update available")
            .setContentText(if (versionName.isNotBlank()) "Version $versionName is ready to install." else "A new version is ready to install.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("A new KOFAXA version is available. Open KOFAXA to continue."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UPDATE_ENDPOINT = "https://ebdaeoqqrszzdcgarpzy.supabase.co/functions/v1/kofaxa-check-update"
        private const val CHANNEL_ID = "kofaxa_updates"
        private const val NOTIFICATION_ID = 7102
    }
}
