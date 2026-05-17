package com.gjdnd.aicollector

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefs(private val context: Context) {
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }
    private val legacyPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun migrateLegacyValues() {
        val legacyPat = legacyPrefs.getString(PREF_PAT, "").orEmpty()
        if (legacyPat.isNotBlank() && getPat().isBlank()) {
            securePrefs.edit().putString(PREF_PAT, legacyPat).apply()
            legacyPrefs.edit().remove(PREF_PAT).apply()
        }
    }

    fun getPat(): String {
        return securePrefs.getString(PREF_PAT, "").orEmpty()
    }

    fun savePat(value: String) {
        securePrefs.edit().putString(PREF_PAT, value).apply()
    }

    fun getRepoOwner(): String {
        return securePrefs.getString(PREF_REPO_OWNER, DEFAULT_REPO_OWNER).orEmpty().ifBlank {
            DEFAULT_REPO_OWNER
        }
    }

    fun saveRepoOwner(value: String) {
        securePrefs.edit().putString(PREF_REPO_OWNER, value.ifBlank { DEFAULT_REPO_OWNER }).apply()
    }

    fun getRepoName(): String {
        return securePrefs.getString(PREF_REPO_NAME, DEFAULT_REPO_NAME).orEmpty().ifBlank {
            DEFAULT_REPO_NAME
        }
    }

    fun saveRepoName(value: String) {
        securePrefs.edit().putString(PREF_REPO_NAME, value.ifBlank { DEFAULT_REPO_NAME }).apply()
    }

    private fun createSecurePrefs(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        const val PREF_PAT = "pref_pat"
        const val PREF_REPO_OWNER = "pref_repo_owner"
        const val PREF_REPO_NAME = "pref_repo_name"
        private const val SECURE_PREFS_NAME = "ai_collector_secure_prefs"
        private const val DEFAULT_REPO_OWNER = "gjdnddd"
        private const val DEFAULT_REPO_NAME = "AIkonw"
    }
}
