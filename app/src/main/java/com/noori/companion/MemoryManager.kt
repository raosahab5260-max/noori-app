package com.noori.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MemoryItem(
    val id: Long,
    val fact: String,
    val createdAt: Long = System.currentTimeMillis()
)

class MemoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("noori_memory_db", Context.MODE_PRIVATE)
    private val key = "saved_memories_v1"
    private val enabledKey = "memory_enabled_v1"

    private val sensitiveKeywords = listOf(
        "password", "otp", "pin", "cvv", "aadhaar", "pan card", "bank", "पासवर्ड", "ओटीपी", "पिन"
    )

    fun isMemoryEnabled(): Boolean = prefs.getBoolean(enabledKey, true)
    fun setMemoryEnabled(enabled: Boolean) = prefs.edit().putBoolean(enabledKey, enabled).apply()

    fun isSensitive(text: String): Boolean {
        val lower = text.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }

    fun getMemories(): List<MemoryItem> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                MemoryItem(obj.getLong("id"), obj.getString("fact"), obj.optLong("createdAt", System.currentTimeMillis()))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(list: List<MemoryItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().put("id", item.id).put("fact", item.fact).put("createdAt", item.createdAt))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun addMemory(fact: String, allowSensitive: Boolean = false): Boolean {
        if (!isMemoryEnabled()) return false
        val clean = fact.trim()
        if (clean.isEmpty()) return false
        if (!allowSensitive && isSensitive(clean)) return false
        val current = getMemories().toMutableList()
        if (current.any { it.fact.equals(clean, ignoreCase = true) }) return false
        current.add(0, MemoryItem(System.currentTimeMillis(), clean))
        saveAll(current)
        return true
    }

    fun deleteMemory(id: Long) = saveAll(getMemories().filterNot { it.id == id })

    fun clearAll() = saveAll(emptyList())
}
