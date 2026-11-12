package com.khalidsamira.love

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
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
import java.util.concurrent.Executors

object AppUpdater {
    private const val API_URL = "https://api.github.com/repos/vyron2561/snap_finger/releases/latest"
    private const val PREFS = "kofaxa_updater"
    private const val KEY_PENDING_INSTALL = "pending_install"
    private const val APK_NAME = "kofaxa-update.apk"
    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdates(activity: Activity, manual: Boolean) {
        executor.execute {
            try {
                val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "KOFAXA-Android")
                }
                val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                connection.disconnect()

                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    throw IllegalStateException("No stable release found")
                }

                val tag = release.optString("tag_name")
                val releaseNumber = tag.substringAfterLast('.').toIntOrNull()
                    ?: throw IllegalStateException("Invalid release version")
                val remoteVersionCode = 1000L + releaseNumber
                val installedVersionCode = if (Build.VERSION.SDK_INT >= 28) {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                }

                if (remoteVersionCode <= installedVersionCode) {
                    if (manual) activity.runOnUiThread {
                        Toast.makeText(activity, "You are using the latest version", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val assets = release.optJSONArray("assets") ?: throw IllegalStateException("APK asset missing")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val url = downloadUrl ?: throw IllegalStateException("APK asset missing")
                val body = release.optString("body")
                val expectedHash = Regex("SHA256: *`([a-fA-F0-9]{64})`")
                    .find(body)?.groupValues?.get(1)
                    ?: throw IllegalStateException("Release checksum missing")

                activity.runOnUiThread {
                    if (!activity.isFinishing) {
                        AlertDialog.Builder(activity)
                            .setTitle("KOFAXA update available")
                            .setMessage("Version $tag is available. Would you like to download and install it now?")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Update now") { _, _ ->
                                downloadAndInstall(activity, url, tag, expectedHash)
                            }
                            .show()
                    }
                }
            } catch (error: Exception) {
                if (manual) activity.runOnUiThread {
                    if (!activity.isFinishing) Toast.makeText(
                        activity, "Update check failed: ${error.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun downloadAndInstall(activity: Activity, downloadUrl: String, tag: String, expectedHash: String) {
        val progress = ProgressDialog(activity).apply {
            setTitle("Downloading KOFAXA")
            setMessage("Preparing update…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            isIndeterminate = true
            show()
        }

        executor.execute {
            try {
                val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "KOFAXA-Android")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
                }

                val total = connection.contentLengthLong
                val apk = File(activity.cacheDir, APK_NAME)
                connection.inputStream.use { input ->
                    FileOutputStream(apk).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (total > 0) activity.runOnUiThread {
                                if (progress.isShowing) {
                                    progress.isIndeterminate = false
                                    progress.max = 100
                                    progress.progress = ((downloaded * 100) / total).toInt()
                                    progress.setMessage("Downloading $tag…")
                                }
                            }
                        }
                    }
                }
                connection.disconnect()

                val actualHash = MessageDigest.getInstance("SHA-256")
                    .digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    apk.delete()
                    throw SecurityException("APK checksum verification failed")
                }

                activity.runOnUiThread {
                    if (progress.isShowing) progress.dismiss()
                    Toast.makeText(activity, "Update downloaded and verified", Toast.LENGTH_SHORT).show()
                    continueToInstaller(activity, apk)
                }
            } catch (error: Exception) {
                activity.runOnUiThread {
                    if (progress.isShowing) progress.dismiss()
                    Toast.makeText(activity, "Update failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun continueToInstaller(activity: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit()
                .putBoolean(KEY_PENDING_INSTALL, true).apply()
            AlertDialog.Builder(activity)
                .setTitle("Allow KOFAXA installation")
                .setMessage("Allow installs from this app in the next screen, then return to KOFAXA.")
                .setPositiveButton("Open settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .setNegativeButton("Later", null)
                .show()
            return
        }
        launchInstaller(activity, apk)
    }

    fun installPendingUpdate(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING_INSTALL, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) return

        prefs.edit().putBoolean(KEY_PENDING_INSTALL, false).apply()
        val apk = File(activity.cacheDir, APK_NAME)
        if (apk.isFile) launchInstaller(activity, apk)
    }

    private fun launchInstaller(activity: Activity, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (error: Exception) {
            Toast.makeText(activity, "Could not open installer: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }
}
