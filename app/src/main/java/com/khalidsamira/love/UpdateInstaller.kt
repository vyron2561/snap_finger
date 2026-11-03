package com.khalidsamira.love

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

object UpdateInstaller {
    private const val ENDPOINT = "https://ebdaeoqqrszzdcgarpzy.supabase.co/functions/v1/kofaxa-check-update"

    fun downloadAndInstall(activity: Activity) {
        val progress = AlertDialog.Builder(activity)
            .setTitle("KOFAXA update")
            .setMessage("Preparing secure download…")
            .setCancelable(false)
            .create()
        progress.show()

        thread {
            var metadataConnection: HttpURLConnection? = null
            var apkConnection: HttpURLConnection? = null
            try {
                metadataConnection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (metadataConnection.responseCode !in 200..299) error("Could not contact update service")
                val metadata = JSONObject(metadataConnection.inputStream.bufferedReader().use { it.readText() })
                if (!metadata.optBoolean("update_available", false)) error("No new version is available")
                val latestCode = metadata.getLong("version_code")
                val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                }
                if (latestCode <= installedCode) error("KOFAXA is already up to date")
                val downloadUrl = metadata.getString("download_url")
                val expectedHash = metadata.getString("sha256").lowercase()

                apkConnection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    requestMethod = "GET"
                }
                if (apkConnection.responseCode !in 200..299) error("APK download failed")
                val apkFile = File(activity.cacheDir, "kofaxa-update.apk")
                apkConnection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output -> input.copyTo(output) }
                }
                val actualHash = MessageDigest.getInstance("SHA-256")
                    .digest(apkFile.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (actualHash != expectedHash) {
                    apkFile.delete()
                    error("Downloaded file failed its security check")
                }

                activity.runOnUiThread {
                    progress.dismiss()
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !activity.packageManager.canRequestPackageInstalls()
                        ) {
                            AlertDialog.Builder(activity)
                                .setTitle("Allow KOFAXA installation")
                                .setMessage("Enable “Allow from this source” for KOFAXA, then return and tap the update notification again.")
                                .setPositiveButton("Settings") { _, _ ->
                                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${activity.packageName}")))
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                            return@runOnUiThread
                        }
                        val apkUri = FileProvider.getUriForFile(
                            activity, "${activity.packageName}.fileprovider", apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(installIntent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.dismiss()
                    AlertDialog.Builder(activity)
                        .setTitle("Update unavailable")
                        .setMessage(e.message ?: "An unexpected error occurred")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } finally {
                metadataConnection?.disconnect()
                apkConnection?.disconnect()
            }
        }
    }
}
