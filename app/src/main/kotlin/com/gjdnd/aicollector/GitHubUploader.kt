package com.gjdnd.aicollector

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitHubUploader(private val context: Context) {
    private val securePrefs = SecurePrefs(context).also { it.migrateLegacyValues() }
    private val logger = UploadLogger(context)
    private val queue = UploadQueue(context)

    fun uploadOrQueue(file: File) {
        if (!file.exists() || !file.extension.equals("md", ignoreCase = true)) {
            return
        }

        val pat = securePrefs.getPat().trim()
        if (pat.isEmpty()) {
            logger.append("PAT 미설정 - 설정 화면에서 입력 필요: ${file.name}")
            queue.enqueue(file.absolutePath)
            return
        }

        val result = runCatching { upload(file, pat) }
        if (result.getOrDefault(false)) {
            if (file.delete()) {
                logger.append("업로드 성공 후 원본 삭제: ${file.name}")
            } else {
                logger.append("업로드 성공, 원본 삭제 실패: ${file.name}")
            }
            queue.remove(file.absolutePath)
        } else {
            val reason = result.exceptionOrNull()?.javaClass?.simpleName ?: "응답 오류"
            logger.append("업로드 실패 - 큐에 저장: ${file.name} ($reason)")
            queue.enqueue(file.absolutePath)
        }
    }

    fun retryQueue() {
        queue.getAll().forEach { path ->
            val file = File(path)
            if (file.exists()) {
                uploadOrQueue(file)
            } else {
                queue.remove(path)
            }
        }
    }

    private fun upload(file: File, pat: String): Boolean {
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val uploadName = findAvailableName(month, file.name, pat) ?: return false
        val remotePath = "$month/$uploadName"
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("message", "add: $remotePath")
            .put("content", encoded)
            .toString()

        val connection = openConnection(remotePath, "PUT", pat)
        return try {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            connection.responseCode == HttpURLConnection.HTTP_CREATED
        } finally {
            connection.disconnect()
        }
    }

    private fun findAvailableName(month: String, originalName: String, pat: String): String? {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ".md"

        for (index in 0..999) {
            val candidate = if (index == 0) originalName else "$baseName($index)$extension"
            val remotePath = "$month/$candidate"
            val connection = openConnection(remotePath, "GET", pat)
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> return candidate
                    HttpURLConnection.HTTP_OK -> Unit
                    else -> return null
                }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun openConnection(remotePath: String, method: String, pat: String): HttpURLConnection {
        val encodedPath = remotePath.split("/").joinToString("/") { encodePathSegment(it) }
        val owner = securePrefs.getRepoOwner()
        val repo = securePrefs.getRepoName()
        val url = URL("https://api.github.com/repos/$owner/$repo/contents/$encodedPath")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $pat")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
    }

    private fun encodePathSegment(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
