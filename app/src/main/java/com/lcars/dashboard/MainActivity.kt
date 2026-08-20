package com.lcars.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var updateManager: UpdateManager
    private var currentUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        updateManager = UpdateManager(this)

        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Add JavaScript interface for update functionality
        webView.addJavascriptInterface(AppUpdateInterface(), "AndroidUpdate")

        currentUrl = getSharedPreferences("lcars", MODE_PRIVATE)
            .getString("dashboard_url", "http://192.168.1.49:8123/local/lcars-dashboard.html")
            ?: "http://192.168.1.49:8123/local/lcars-dashboard.html"

        webView.loadUrl(currentUrl)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()

        val newUrl = getSharedPreferences("lcars", MODE_PRIVATE)
            .getString("dashboard_url", "http://192.168.1.49:8123/local/lcars-dashboard.html")
            ?: "http://192.168.1.49:8123/local/lcars-dashboard.html"

        if (newUrl != currentUrl) {
            currentUrl = newUrl
            webView.loadUrl(currentUrl)
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // JavaScript interface for update functionality
    inner class AppUpdateInterface {

        @JavascriptInterface
        fun checkForUpdates() {
            runOnUiThread {
                if (!updateManager.canInstallPackages()) {
                    AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                        .setTitle("Permissie vereist")
                        .setMessage("Om updates te installeren moet je 'Onbekende apps' inschakelen voor LCARS Dashboard.")
                        .setPositiveButton("Instellingen") { _, _ ->
                            updateManager.openInstallSettings()
                        }
                        .setNegativeButton("Annuleer", null)
                        .show()
                    return@runOnUiThread
                }

                updateManager.checkForUpdates(object : UpdateManager.UpdateCallback {
                    override fun onUpdateAvailable(update: UpdateManager.UpdateInfo) {
                        runOnUiThread {
                            AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                                .setTitle("Update beschikbaar")
                                .setMessage("Nieuwe versie: ${update.version}\nHuidige versie: ${updateManager.getCurrentVersion()}")
                                .setPositiveButton("Download") { _, _ ->
                                    startDownload(update)
                                }
                                .setNegativeButton("Later", null)
                                .show()
                        }
                    }

                    override fun onNoUpdate() {
                        runOnUiThread {
                            AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                                .setTitle("Geen update")
                                .setMessage("Je hebt al de nieuwste versie!")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                                .setTitle("Fout")
                                .setMessage("Kon niet checken: $error")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }

                    override fun onDownloadComplete(filePath: String) {
                        runOnUiThread {
                            AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                                .setTitle("Download gereed")
                                .setMessage("Nu installeren?")
                                .setPositiveButton("Installeer") { _, _ ->
                                    updateManager.installApk(filePath)
                                }
                                .setNegativeButton("Later", null)
                                .show()
                        }
                    }

                    override fun onDownloadProgress(progress: Int) {
                        // Could update UI here if needed
                    }
                })
            }
        }

        private fun startDownload(update: UpdateManager.UpdateInfo) {
            updateManager.downloadUpdate(update, object : UpdateManager.UpdateCallback {
                override fun onUpdateAvailable(update: UpdateManager.UpdateInfo) {}
                override fun onNoUpdate() {}
                override fun onError(error: String) {
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                            .setTitle("Download fout")
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                override fun onDownloadComplete(filePath: String) {
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity, R.style.LCARS_Dialog)
                            .setTitle("Download gereed")
                            .setMessage("Nu installeren?")
                            .setPositiveButton("Installeer") { _, _ ->
                                updateManager.installApk(filePath)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                }

                override fun onDownloadProgress(progress: Int) {}
            })
        }

        @JavascriptInterface
        fun getCurrentVersion(): String {
            return updateManager.getCurrentVersion()
        }
    }
}
