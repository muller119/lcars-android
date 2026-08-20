package com.lcars.dashboard

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        urlInput = findViewById(R.id.urlInput)
        saveButton = findViewById(R.id.saveButton)

        // Load current URL
        val prefs = getSharedPreferences("lcars", MODE_PRIVATE)
        val currentUrl = prefs.getString("dashboard_url", "http://homeassistant.local:8123/lcars-dashboard.html")
            ?: "http://homeassistant.local:8123/lcars-dashboard.html"
        urlInput.setText(currentUrl)

        saveButton.setOnClickListener {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                prefs.edit().putString("dashboard_url", newUrl).apply()
                finish()
            }
        }
    }
}
