package com.gjdnd.aicollector

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadLogger(private val context: Context) {
    private val logFile: File
        get() = File(context.filesDir, LOG_FILE_NAME)

    @Synchronized
    fun append(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        logFile.appendText("[$timestamp] $message\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    @Synchronized
    fun readLatestFirst(): String {
        if (!logFile.exists()) {
            return ""
        }

        return logFile.readLines(Charsets.UTF_8)
            .asReversed()
            .joinToString("\n")
    }

    private fun trimIfNeeded() {
        if (!logFile.exists() || logFile.length() <= MAX_BYTES) {
            return
        }

        val lines = logFile.readLines(Charsets.UTF_8)
        val trimmed = lines.takeLast(MAX_LINES)
        logFile.writeText(trimmed.joinToString("\n") + "\n", Charsets.UTF_8)
    }

    companion object {
        private const val LOG_FILE_NAME = "upload_log.txt"
        private const val MAX_BYTES = 1024 * 1024
        private const val MAX_LINES = 500
    }
}
