package com.gjdnd.aicollector

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Parcelable
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val sharedUri = getSharedUri(intent)
        val stagedFile = when {
            sharedUri != null -> importStream(sharedUri)
            intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank().not() -> {
                importText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            }
            else -> null
        }

        if (stagedFile == null) {
            reject()
            return
        }

        startCollectorForFile(stagedFile)
        UploadLogger(this).append("공유 파일 수신: ${stagedFile.name}")
        Toast.makeText(this, R.string.share_received, Toast.LENGTH_SHORT).show()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun getSharedUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        }
    }

    private fun importStream(uri: Uri): File? {
        val fileName = queryDisplayName(uri).takeIf { it.endsWith(".md", ignoreCase = true) }
            ?: return null
        val outputFile = File(getStagingDir(), sanitizeFileName(fileName))

        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            outputFile
        }.getOrNull()
    }

    private fun importText(text: String): File? {
        if (text.isBlank()) {
            return null
        }

        val outputFile = File(getStagingDir(), "shared-${System.currentTimeMillis()}.md")
        outputFile.writeText(text, Charsets.UTF_8)
        return outputFile
    }

    private fun queryDisplayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else fallbackFileName()
            } else {
                fallbackFileName()
            }
        } finally {
            cursor?.close()
        }
    }

    private fun startCollectorForFile(file: File) {
        val serviceIntent = Intent(this, CollectorService::class.java).apply {
            putExtra(CollectorService.EXTRA_FILE_PATH, file.absolutePath)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun getStagingDir(): File {
        return File(filesDir, "staging").also { it.mkdirs() }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun fallbackFileName(): String {
        return "shared-${System.currentTimeMillis()}.md"
    }

    private fun reject() {
        UploadLogger(this).append("공유 파일 거부 - .md 파일만 처리")
        Toast.makeText(this, R.string.share_rejected, Toast.LENGTH_SHORT).show()
        finish()
    }
}
