package com.lcars.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var saveButton: Button
    private lateinit var checkUpdateButton: Button
    private lateinit var versionText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateManager = UpdateManager(this)

        urlInput = findViewById(R.id.urlInput)
        saveButton = findViewById(R.id.saveButton)
        checkUpdateButton = findViewById(R.id.checkUpdateButton)
        versionText = findViewById(R.id.versionText)
        updateStatusText = findViewById(R.id.updateStatusText)
        progressBar = findViewById(R.id.progressBar)

        // Show current version
        versionText.text = "Huidige versie: ${updateManager.getCurrentVersion()}"

        // Load current URL
        val prefs = getSharedPreferences("lcars", MODE_PRIVATE)
        val currentUrl = prefs.getString("dashboard_url", "http://192.168.1.49:8123/local/lcars-dashboard.html")
            ?: "http://192.168.1.49:8123/local/lcars-dashboard.html"
        urlInput.setText(currentUrl)

        saveButton.setOnClickListener {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                prefs.edit().putString("dashboard_url", newUrl).apply()
                finish()
            }
        }

        checkUpdateButton.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        // Check if we can install packages
        if (!updateManager.canInstallPackages()) {
            AlertDialog.Builder(this, R.style.LCARS_Dialog)
                .setTitle("Permissie vereist")
                .setMessage("Om updates te installeren moet je 'Onbekende apps' inschakelen voor LCARS Dashboard.")
                .setPositiveButton("Instellingen") { _, _ ->
                    updateManager.openInstallSettings()
                }
                .setNegativeButton("Annuleer", null)
                .show()
            return
        }

        checkUpdateButton.isEnabled = false
        checkUpdateButton.text = "Checking..."
        updateStatusText.text = "Zoek naar updates..."
        progressBar.visibility = android.view.View.VISIBLE

        updateManager.checkForUpdates(object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(update: UpdateManager.UpdateInfo) {
                progressBar.visibility = android.view.View.GONE
                checkUpdateButton.isEnabled = true
                checkUpdateButton.text = "CHECK OP UPDATES"
                updateStatusText.text = "Nieuwe versie: ${update.version}"

                showUpdateDialog(update)
            }

            override fun onNoUpdate() {
                progressBar.visibility = android.view.View.GONE
                checkUpdateButton.isEnabled = true
                checkUpdateButton.text = "CHECK OP UPDATES"
                updateStatusText.text = "Je hebt de nieuwste versie!"
            }

            override fun onError(error: String) {
                progressBar.visibility = android.view.View.GONE
                checkUpdateButton.isEnabled = true
                checkUpdateButton.text = "CHECK OP UPDATES"
                updateStatusText.text = "Fout: $error"
            }

            override fun onDownloadComplete(filePath: String) {
                progressBar.visibility = android.view.View.GONE
                updateStatusText.text = "Download gereed!"
                showInstallDialog(filePath)
            }

            override fun onDownloadProgress(progress: Int) {
                progressBar.progress = progress
                updateStatusText.text = "Downloaden: $progress%"
            }
        })
    }

    private fun showUpdateDialog(update: UpdateManager.UpdateInfo) {
        val message = buildString {
            append("Nieuwe versie: ${update.version}\n")
            append("Huidige versie: ${updateManager.getCurrentVersion()}\n")
            if (update.releaseNotes.isNotBlank()) {
                append("\nWijzigingen:\n${update.releaseNotes.take(200)}")
            }
        }

        AlertDialog.Builder(this, R.style.LCARS_Dialog)
            .setTitle("Update beschikbaar")
            .setMessage(message)
            .setPositiveButton("Download") { _, _ ->
                startDownload(update)
            }
            .setNegativeButton("Annuleer", null)
            .show()
    }

    private fun startDownload(update: UpdateManager.UpdateInfo) {
        updateStatusText.text = "Downloaden..."
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.progress = 0

        updateManager.downloadUpdate(update, object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(update: UpdateManager.UpdateInfo) {}
            override fun onNoUpdate() {}
            override fun onError(error: String) {
                progressBar.visibility = android.view.View.GONE
                updateStatusText.text = "Download mislukt: $error"
            }

            override fun onDownloadComplete(filePath: String) {
                progressBar.visibility = android.view.View.GONE
                updateStatusText.text = "Download gereed!"
                showInstallDialog(filePath)
            }

            override fun onDownloadProgress(progress: Int) {
                progressBar.progress = progress
                updateStatusText.text = "Downloaden: $progress%"
            }
        })
    }

    private fun showInstallDialog(filePath: String) {
        AlertDialog.Builder(this, R.style.LCARS_Dialog)
            .setTitle("Installatie")
            .setMessage("Update gedownload. Nu installeren?")
            .setPositiveButton("Installeer") { _, _ ->
                updateManager.installApk(filePath)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
