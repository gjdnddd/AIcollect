package com.gjdnd.aicollector

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        SecurePrefs(this).migrateLegacyValues()

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.logButton).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            openAllFilesAccessSettings()
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            startCollectorIfReady()
        }

        requestNotificationPermissionIfNeeded()
        promptBatteryOptimizationOnce()
        startCollectorIfReady()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (Environment.isExternalStorageManager()) {
            startCollectorIfReady()
        }
    }

    private fun startCollectorIfReady() {
        updateStatus()
        if (!Environment.isExternalStorageManager()) {
            return
        }

        val intent = Intent(this, CollectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatus() {
        val securePrefs = SecurePrefs(this)
        val hasPat = securePrefs.getPat().isNotBlank()
        statusText.text = when {
            !Environment.isExternalStorageManager() -> getString(R.string.status_permission_required)
            !hasPat -> getString(R.string.status_pat_required)
            else -> getString(R.string.status_ready)
        }
    }

    private fun openAllFilesAccessSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun promptBatteryOptimizationOnce() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BATTERY_PROMPTED, false)) {
            return
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return
        }

        prefs.edit().putBoolean(PREF_BATTERY_PROMPTED, true).apply()
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        const val PREFS_NAME = "ai_collector_prefs"
        private const val PREF_BATTERY_PROMPTED = "pref_battery_prompted"
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
