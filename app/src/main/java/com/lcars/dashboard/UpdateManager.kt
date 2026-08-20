package com.lcars.dashboard

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("lcars", Context.MODE_PRIVATE)

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/muller119/lcars-android/releases/latest"
        private const val VERSION_KEY = "last_known_version"
    }

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    interface UpdateCallback {
        fun onUpdateAvailable(update: UpdateInfo)
        fun onNoUpdate()
        fun onError(error: String)
        fun onDownloadComplete(filePath: String)
        fun onDownloadProgress(progress: Int)
    }

    fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun checkForUpdates(callback: UpdateCallback) {
        Thread {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val version = extractJsonValue(response, "tag_name")?.removePrefix("v") ?: ""
                    val body = extractJsonValue(response, "body") ?: ""
                    val apkUrl = extractApkUrl(response) ?: ""

                    if (version.isNotEmpty() && apkUrl.isNotEmpty()) {
                        val currentVersion = getCurrentVersion()

                        if (isNewerVersion(version, currentVersion)) {
                            handler.post {
                                callback.onUpdateAvailable(
                                    UpdateInfo(version, apkUrl, body)
                                )
                            }
                        } else {
                            handler.post { callback.onNoUpdate() }
                        }
                    } else {
                        handler.post { callback.onError("Ongeldige response van GitHub") }
                    }
                } else {
                    handler.post { callback.onError("GitHub API fout: ${connection.responseCode}") }
                }
                connection.disconnect()
            } catch (e: Exception) {
                handler.post { callback.onError("Fout: ${e.message}") }
            }
        }.start()
    }

    fun downloadUpdate(update: UpdateInfo, callback: UpdateCallback) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("LCARS Dashboard Update")
            .setDescription("Downloaden versie ${update.version}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "lcars-dashboard-v${update.version}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        // Store download ID
        prefs.edit().putLong("download_id", downloadId).apply()

        // Monitor download progress
        Thread {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            handler.post { callback.onDownloadComplete(localUri) }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            handler.post { callback.onError("Download mislukt") }
                        }
                        else -> {
                            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (totalBytes > 0) {
                                val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                                handler.post { callback.onDownloadProgress(progress) }
                            }
                        }
                    }
                }
                cursor.close()
                if (downloading) Thread.sleep(500)
            }
        }.start()
    }

    fun installApk(filePath: String) {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            Uri.parse(filePath)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractApkUrl(json: String): String? {
        val pattern = "\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.apk[^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }
}
