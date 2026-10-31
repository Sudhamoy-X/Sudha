package com.sudha.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class UpdateActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var checkButton: Button
    private var downloadedApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 32)
            setBackgroundColor(android.graphics.Color.rgb(16, 16, 20))
        }
        root.addView(TextView(this).apply {
            text = if (BuildConfig.DEBUG) "SUDHA Debug Updater" else "SUDHA Stable Updater"
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
        })
        status = TextView(this).apply {
            text = "Check for the latest SUDHA update."
            textSize = 15f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 24, 0, 24)
        }
        checkButton = Button(this).apply {
            text = "Check for updates"
            setOnClickListener { checkForUpdate() }
        }
        root.addView(status)
        root.addView(checkButton)
        setContentView(root)
        if (intent.getBooleanExtra("AUTO_UPDATE", false)) checkForUpdate()
    }

    private fun checkForUpdate() {
        checkButton.isEnabled = false
        status.text = "Checking update channel…"
        executor.execute {
            try {
                val debug = BuildConfig.DEBUG
                val endpoint = if (debug) "https://api.github.com/repos/Sudhamoy-X/Sudha/releases/tags/debug-latest"
                    else "https://api.github.com/repos/Sudhamoy-X/Sudha/releases/latest"
                val json = getJson(endpoint)
                val assets = json.getJSONArray("assets")
                val apkName = if (debug) "SUDHA-debug.apk" else "SUDHA-release.apk"
                val checksumName = "$apkName.sha256"
                var apkUrl: String? = null
                var checksumUrl: String? = null
                var versionUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    when (asset.getString("name")) {
                        apkName -> apkUrl = asset.getString("browser_download_url")
                        checksumName -> checksumUrl = asset.getString("browser_download_url")
                        "version.txt" -> versionUrl = asset.getString("browser_download_url")
                    }
                }
                if (apkUrl == null || checksumUrl == null) throw IllegalStateException("APK or checksum asset is missing.")
                val remoteVersion = if (debug) {
                    if (versionUrl == null) throw IllegalStateException("Debug version metadata is missing.")
                    URL(versionUrl).openStream().bufferedReader().use { it.readText() }.trim()
                } else json.optString("tag_name").removePrefix("v").substringBefore("-")
                val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                if (compareVersions(remoteVersion, currentVersion) <= 0) {
                    runOnUiThread {
                        status.text = "SUDHA is up to date (v$currentVersion)."
                        checkButton.isEnabled = true
                    }
                    return@execute
                }
                runOnUiThread { status.text = "Version $remoteVersion found. Downloading and verifying…" }
                val dir = File(filesDir, "updates").apply { mkdirs() }
                val apk = File(dir, apkName)
                download(apkUrl!!, apk)
                val expected = URL(checksumUrl!!).openStream().bufferedReader().use { it.readText() }
                    .trim().substringBefore(" ").lowercase()
                if (!expected.matches(Regex("[a-f0-9]{64}")) || expected != sha256(apk)) {
                    apk.delete()
                    throw SecurityException("APK checksum verification failed. Installation blocked.")
                }
                downloadedApk = apk
                runOnUiThread {
                    status.text = "Verified v$remoteVersion. Tap Install update to continue."
                    checkButton.text = "Install update"
                    checkButton.isEnabled = true
                    checkButton.setOnClickListener { installUpdate() }
                    if (intent.getBooleanExtra("AUTO_UPDATE", false) && packageManager.canRequestPackageInstalls()) installUpdate()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Update check failed: ${e.message ?: "Unknown error"}"
                    checkButton.isEnabled = true
                }
            }
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SUDHA-Android-Updater")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Update server returned HTTP ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun installUpdate() {
        val apk = downloadedApk ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            status.text = "Allow SUDHA to install updates, then return and tap Install update."
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        })
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("User-Agent", "SUDHA-Android-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("APK download failed: HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split(".").map { it.toIntOrNull() ?: 0 }
        val right = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0).compareTo(right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
