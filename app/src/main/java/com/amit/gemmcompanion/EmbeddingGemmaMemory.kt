package com.amit.gemmcompanion

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EmbeddingGemmaMemory(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("embedding_memory", Context.MODE_PRIVATE)
    private var embedder: UniversalEmbedder? = null

    fun modelPath(): String? = preferences.getString(KEY_MODEL_PATH, null)

    suspend fun initialize(modelFile: File, notes: List<CompanionNote>) = withContext(Dispatchers.IO) {
        close()
        val options = UniversalEmbedderOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setL2Normalize(true)
            .build()
        embedder = UniversalEmbedder.createFromOptions(appContext, options)
        preferences.edit().putString(KEY_MODEL_PATH, modelFile.absolutePath).apply()
        notes.forEach(::indexNoteBlocking)
    }

    suspend fun indexNote(note: CompanionNote) = withContext(Dispatchers.IO) {
        indexNoteBlocking(note)
    }

    suspend fun removeNote(id: String) = withContext(Dispatchers.IO) {
        val records = readRecords()
        records.remove(id)
        writeRecords(records)
    }

    suspend fun relevantNotes(query: String, notes: List<CompanionNote>, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            val activeEmbedder = embedder
            if (activeEmbedder == null || query.isBlank()) {
                return@withContext notes.takeLast(limit).map { it.text }
            }

            val queryVector = runCatching { embeddingFor(activeEmbedder, query) }.getOrNull()
                ?: return@withContext notes.takeLast(limit).map { it.text }
            val noteById = notes.associateBy { it.id }
            readRecords().mapNotNull { (id, vector) ->
                val note = noteById[id] ?: return@mapNotNull null
                note.text to cosineSimilarity(queryVector, vector)
            }.sortedByDescending { it.second }.take(limit).map { it.first }
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { embedder?.close() }
        embedder = null
    }

    private fun indexNoteBlocking(note: CompanionNote) {
        val activeEmbedder = embedder ?: return
        val vector = runCatching { embeddingFor(activeEmbedder, note.text) }.getOrNull() ?: return
        val records = readRecords()
        records[note.id] = vector
        writeRecords(records)
    }

    private fun embeddingFor(activeEmbedder: UniversalEmbedder, text: String): FloatArray {
        val result = activeEmbedder.embedText(text)
        return checkNotNull(result.embeddings().firstOrNull()?.floatEmbedding()) {
            "EmbeddingGemma2 did not return a text vector."
        }
    }

    private fun readRecords(): MutableMap<String, FloatArray> = runCatching {
        val json = JSONObject(preferences.getString(KEY_VECTORS, "{}") ?: "{}")
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val values = json.getJSONArray(id)
                put(id, FloatArray(values.length()) { values.getDouble(it).toFloat() })
            }
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun writeRecords(records: Map<String, FloatArray>) {
        val json = JSONObject()
        records.forEach { (id, vector) ->
            json.put(id, JSONArray().apply { vector.forEach { put(it.toDouble()) } })
        }
        preferences.edit().putString(KEY_VECTORS, json.toString()).apply()
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        val denominator = sqrt(leftNorm) * sqrt(rightNorm)
        return if (denominator == 0.0) 0f else (dot / denominator).toFloat()
    }

    private companion object {
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_VECTORS = "note_vectors"
    }
}