package com.khalidsamira.love.voice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class VoskModelManager(private val context: Context) {
    fun modelRoot() = File(context.filesDir, "vosk")
    fun modelDir() = File(modelRoot(), VoiceWakeConfig.MODEL_DIR_NAME)

    fun isModelReady(): Boolean {
        val dir = modelDir()
        return dir.isDirectory &&
            File(dir, "am/final.mdl").isFile &&
            File(dir, "conf/model.conf").isFile
    }

    fun deleteModel() { modelRoot().deleteRecursively() }

    fun downloadModel(onProgress: (Int) -> Unit): Result<Unit> = runCatching {
        val root = modelRoot()
        if (!root.exists() && !root.mkdirs()) error("Cannot create model directory")
        val zipFile = File(root, "model.zip.part")
        val connection = (URL(VoiceWakeConfig.MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 60000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        val staging = File(root, "extracting")
        staging.deleteRecursively()
        if (!staging.mkdirs()) error("Cannot create extraction directory")
        try {
            unzipSafely(zipFile, staging)
            val extracted = File(staging, VoiceWakeConfig.MODEL_DIR_NAME)
            if (!extracted.isDirectory || !File(extracted, "am/final.mdl").isFile ||
                !File(extracted, "conf/model.conf").isFile) error("Downloaded archive is not a valid Vosk model")
            val destination = modelDir()
            destination.deleteRecursively()
            if (!extracted.renameTo(destination)) {
                extracted.copyRecursively(destination, overwrite = true)
                extracted.deleteRecursively()
            }
            if (!isModelReady()) error("Model validation failed")
            onProgress(100)
        } finally {
            staging.deleteRecursively()
            zipFile.delete()
        }
    }

    private fun unzipSafely(zipFile: File, target: File) {
        val rootPath = target.canonicalPath + File.separator
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(target, entry.name)
                val canonical = output.canonicalPath
                if (canonical != target.canonicalPath && !canonical.startsWith(rootPath)) {
                    throw SecurityException("Unsafe ZIP entry")
                }
                if (entry.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) error("Cannot create directory")
                } else {
                    output.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Cannot create directory") }
                    FileOutputStream(output).use { stream ->
                        zip.copyTo(stream)
                        stream.fd.sync()
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
