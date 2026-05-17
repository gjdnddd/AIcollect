package com.gjdnd.aicollector

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private lateinit var logText: TextView
    private lateinit var logger: UploadLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logger = UploadLogger(this)
        logText = findViewById(R.id.logText)

        findViewById<Button>(R.id.refreshLogButton).setOnClickListener {
            loadLogs()
        }
        loadLogs()
    }

    private fun loadLogs() {
        val logs = logger.readLatestFirst()
        logText.text = logs.ifBlank { getString(R.string.no_logs) }
    }
}
