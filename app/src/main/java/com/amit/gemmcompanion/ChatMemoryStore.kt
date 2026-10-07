package com.amit.gemmcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatTurn(val role: String, val text: String)

class ChatMemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("conversation_memory", Context.MODE_PRIVATE)

    fun load(): List<ChatTurn> {
        val stored = preferences.getString(KEY_TURNS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(stored)
            List(json.length()) { index ->
                val item = json.getJSONObject(index)
                ChatTurn(item.getString("role"), item.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    fun save(turns: List<ChatTurn>) {
        val json = JSONArray()
        turns.takeLast(MAX_TURNS).forEach { turn ->
            json.put(JSONObject().put("role", turn.role).put("text", turn.text))
        }
        preferences.edit().putString(KEY_TURNS, json.toString()).apply()
    }

    fun modelPath(): String? = preferences.getString(KEY_MODEL_PATH, null)

    fun setModelPath(path: String) {
        preferences.edit().putString(KEY_MODEL_PATH, path).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_TURNS).apply()
    }

    private companion object {
        const val KEY_TURNS = "turns"
        const val KEY_MODEL_PATH = "model_path"
        const val MAX_TURNS = 40
    }
}