package com.gjdnd.aicollector

import android.content.Context
import org.json.JSONArray
import java.io.File

class UploadQueue(context: Context) {
    private val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val logger = UploadLogger(context)

    @Synchronized
    fun enqueue(path: String) {
        val items = getAll().toMutableList()
        if (!items.contains(path)) {
            items.add(path)
            save(items)
            logger.append("큐 저장: ${File(path).name}")
        }
    }

    @Synchronized
    fun dequeue(): String? {
        val items = getAll().toMutableList()
        val first = items.firstOrNull() ?: return null
        items.removeAt(0)
        save(items)
        return first
    }

    @Synchronized
    fun getAll(): List<String> {
        val raw = prefs.getString(KEY_QUEUE, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val existing = mutableListOf<String>()

        for (index in 0 until array.length()) {
            val path = array.optString(index)
            if (path.isNotBlank() && File(path).exists()) {
                existing.add(path)
            }
        }

        if (existing.size != array.length()) {
            save(existing)
            logger.append("존재하지 않는 큐 항목 정리")
        }
        return existing
    }

    @Synchronized
    fun remove(path: String) {
        save(getAll().filterNot { it == path })
    }

    private fun save(items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    companion object {
        private const val KEY_QUEUE = "pref_upload_queue"
    }
}
