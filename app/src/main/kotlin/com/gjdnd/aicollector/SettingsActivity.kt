package com.gjdnd.aicollector

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        securePrefs = SecurePrefs(this).also { it.migrateLegacyValues() }

        val patInput = findViewById<EditText>(R.id.patInput)
        val ownerInput = findViewById<EditText>(R.id.repoOwnerInput)
        val repoInput = findViewById<EditText>(R.id.repoNameInput)

        ownerInput.setText(securePrefs.getRepoOwner())
        repoInput.setText(securePrefs.getRepoName())

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            val pat = patInput.text.toString().trim()
            if (pat.isNotBlank()) {
                securePrefs.savePat(pat)
            }
            securePrefs.saveRepoOwner(ownerInput.text.toString().trim())
            securePrefs.saveRepoName(repoInput.text.toString().trim())
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            UploadLogger(this).append("설정 저장 완료")
            finish()
        }
    }
}
