package com.gjdnd.aicollector

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var pasteFileNameInput: EditText
    private lateinit var pasteContentInput: EditText
    private lateinit var logger: UploadLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        pasteFileNameInput = findViewById(R.id.pasteFileNameInput)
        pasteContentInput = findViewById(R.id.pasteContentInput)
        logger = UploadLogger(this)
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

        findViewById<Button>(R.id.collectButton).setOnClickListener {
            runManualCollection()
        }

        findViewById<Button>(R.id.pasteUploadButton).setOnClickListener {
            uploadPastedText()
        }

        requestNotificationPermissionIfNeeded()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun runManualCollection() {
        updateStatus()
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, R.string.status_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        startCollectorService(null)
        logger.append("수동 수집 실행")
        Toast.makeText(this, R.string.collection_started, Toast.LENGTH_SHORT).show()
    }

    private fun uploadPastedText() {
        val content = pasteContentInput.text.toString()
        if (content.isBlank()) {
            Toast.makeText(this, R.string.paste_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = normalizeFileName(pasteFileNameInput.text.toString())
        val file = File(getStagingDir(), fileName)
        file.writeText(content, Charsets.UTF_8)

        startCollectorService(file.absolutePath)
        logger.append("붙여넣기 업로드 실행: $fileName")
        Toast.makeText(this, R.string.paste_upload_started, Toast.LENGTH_SHORT).show()
    }

    private fun startCollectorService(filePath: String?) {
        val intent = Intent(this, CollectorService::class.java).apply {
            if (!filePath.isNullOrBlank()) {
                putExtra(CollectorService.EXTRA_FILE_PATH, filePath)
            }
        }
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
            else -> getString(R.string.status_manual_ready)
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

    private fun getStagingDir(): File {
        return File(filesDir, "staging").also { it.mkdirs() }
    }

    private fun normalizeFileName(rawName: String): String {
        val fallback = "paste-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.md"
        val candidate = rawName.trim().ifBlank { fallback }
        val withExtension = if (candidate.endsWith(".md", ignoreCase = true)) candidate else "$candidate.md"
        return withExtension.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    companion object {
        const val PREFS_NAME = "ai_collector_prefs"
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
