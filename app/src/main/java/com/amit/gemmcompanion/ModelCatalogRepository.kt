package com.amit.gemmcompanion

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class GalleryModelPurpose { CHAT, EMBEDDING }

data class GalleryModel(
    val purpose: GalleryModelPurpose,
    val name: String,
    val modelId: String,
    val modelFile: String,
    val commitHash: String,
    val description: String,
    val sizeInBytes: Long,
    val minDeviceMemoryInGb: Int,
    val downloadUrl: String
) {
    val modelPageUrl: String
        get() = "https://huggingface.co/$modelId"
}

object ModelCatalogRepository {
    private const val GALLERY_ALLOWLIST_URL =
        "https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_20.json"

    suspend fun loadMultimodalModels(): List<GalleryModel> = withContext(Dispatchers.IO) {
        val connection = URL(GALLERY_ALLOWLIST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        try {
            check(connection.responseCode in 200..299) {
                "Gallery model catalog returned HTTP ${connection.responseCode}."
            }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val models = root.getJSONArray("models")
            buildList {
                for (index in 0 until models.length()) {
                    val item = models.getJSONObject(index)
                    val modelFile = item.optString("modelFile")
                    val modelId = item.optString("modelId")
                    val commitHash = item.optString("commitHash")
                    val taskTypes = item.optJSONArray("taskTypes")
                    val modelName = item.optString("name", modelId.substringAfterLast('/'))
                    val isEmbeddingModel = modelName.contains("EmbeddingGemma", ignoreCase = true) ||
                        modelId.contains("embeddinggemma", ignoreCase = true)
                    val supportsChat = item.optBoolean("llmSupportImage") &&
                        item.optBoolean("llmSupportAudio") && taskTypes != null &&
                        (0 until taskTypes.length()).any { taskTypes.optString(it) == "llm_chat" }
                    if (item.optBoolean("disabled") ||
                        (!supportsChat && !isEmbeddingModel) ||
                        !modelFile.endsWith(".litertlm") || modelId.isBlank() || commitHash.isBlank()
                    ) continue

                    val customUrl = item.optString("url")
                    val downloadUrl = customUrl.takeIf { it.isNotBlank() }
                        ?: "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"
                    add(
                        GalleryModel(
                            purpose = if (isEmbeddingModel) GalleryModelPurpose.EMBEDDING else GalleryModelPurpose.CHAT,
                            name = modelName,
                            modelId = modelId,
                            modelFile = modelFile.substringAfterLast('/'),
                            commitHash = commitHash,
                            description = item.optString("description"),
                            sizeInBytes = item.optLong("sizeInBytes"),
                            minDeviceMemoryInGb = item.optInt("minDeviceMemoryInGb", 0),
                            downloadUrl = downloadUrl
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadModel(
        model: GalleryModel,
        targetDirectory: File,
        onProgress: suspend (received: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        targetDirectory.mkdirs()
        val destination = File(targetDirectory, model.modelFile)
        val partial = File(targetDirectory, "${model.modelFile}.partial")
        val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                responseCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                error("This Gemma download requires access approval. Accept its terms at ${model.modelPageUrl}, then retry.")
            }
            check(responseCode in 200..299) { "Model download failed with HTTP $responseCode." }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeInBytes
            var received = 0L
            var lastReportedAt = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        val now = System.currentTimeMillis()
                        if (now - lastReportedAt >= 400) {
                            onProgress(received, total)
                            lastReportedAt = now
                        }
                    }
                    output.fd.sync()
                }
            }
            if (total > 0 && received < total) {
                error("Download ended early ($received of $total bytes). Retry the download.")
            }
            check(partial.renameTo(destination)) { "Could not finalize the downloaded model file." }
            onProgress(received, total)
            destination
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}