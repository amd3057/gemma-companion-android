package com.amit.gemmcompanion

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiteRtGemmaRuntime(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelFile: File? = null
    private var currentHistory: List<ChatTurn> = emptyList()
    private var activeBackendIndex: Int = 0

    var isMultimodalModel: Boolean = false
        private set

    suspend fun load(modelFile: File, history: List<ChatTurn>) = withContext(Dispatchers.IO) {
        close()
        currentModelFile = modelFile
        currentHistory = history

        val fileName = modelFile.name.lowercase()
        isMultimodalModel = fileName.contains("3n") ||
            fileName.contains("multimodal") ||
            fileName.contains("paligemma") ||
            fileName.contains("audio") ||
            fileName.contains("vision")

        val backends = getAvailableBackends()
        var lastError: Exception? = null
        for (index in backends.indices) {
            try {
                initEngineAndConversation(modelFile, history, backends[index])
                activeBackendIndex = index
                return@withContext
            } catch (e: Exception) {
                lastError = e
                closeEngineOnly()
            }
        }
        throw lastError ?: IllegalStateException("Could not initialize Gemma on any supported backend.")
    }

    private fun getAvailableBackends(): List<Backend> {
        val isGoogleTensor = Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            Build.HARDWARE.lowercase().contains("tensor") ||
            Build.SOC_MANUFACTURER.equals("Google", ignoreCase = true)

        return if (isGoogleTensor) {
            listOf(Backend.GOOGLE_TENSOR(), Backend.CPU())
        } else {
            listOf(Backend.GPU(), Backend.CPU())
        }
    }

    private fun initEngineAndConversation(modelFile: File, history: List<ChatTurn>, backend: Backend) {
        val isCpu = backend is Backend.CPU
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = backend,
            visionBackend = if (isMultimodalModel && !isCpu) Backend.GPU() else null,
            audioBackend = if (isMultimodalModel && !isCpu) Backend.CPU() else null,
            cacheDir = context.cacheDir.absolutePath
        )

        val nextEngine = Engine(config).apply { initialize() }
        val validHistory = history.takeLast(12).filter { it.text.isNotBlank() }
        val initialMessages = validHistory.mapNotNull { turn ->
            when (turn.role) {
                "user" -> Message.user(turn.text)
                "assistant" -> Message.model(turn.text)
                else -> null
            }
        }

        val nextConversation = runCatching {
            nextEngine.createConversation(
                ConversationConfig(
                    systemInstruction = if (isMultimodalModel) {
                        Contents.of("You are a warm, helpful, grounded AI companion.")
                    } else null,
                    initialMessages = initialMessages,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
                )
            )
        }.getOrElse {
            nextEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
                )
            )
        }

        engine = nextEngine
        conversation = nextConversation
    }

    suspend fun reply(text: String, image: Bitmap?, audioWav: ByteArray?): String = withContext(Dispatchers.IO) {
        val activeConversation = checkNotNull(conversation) { "Import or load a compatible Gemma .litertlm model first." }

        val content = buildList {
            if (image != null && isMultimodalModel) add(Content.ImageBytes(image.toPngBytes()))
            if (audioWav != null && isMultimodalModel) add(Content.AudioBytes(audioWav))
            if (text.isNotBlank()) add(Content.Text(text))
        }
        check(content.isNotEmpty()) { "Please enter a message or attach an image/audio clip." }

        try {
            executeReply(activeConversation, content)
        } catch (error: Exception) {
            val backends = getAvailableBackends()
            if (activeBackendIndex < backends.lastIndex) {
                currentModelFile?.let { modelFile ->
                    close()
                    for (index in (activeBackendIndex + 1)..backends.lastIndex) {
                        try {
                            initEngineAndConversation(modelFile, currentHistory, backends[index])
                            activeBackendIndex = index
                            val fallbackConversation = checkNotNull(conversation)
                            return@withContext executeReply(fallbackConversation, content)
                        } catch (_: Exception) {
                            closeEngineOnly()
                        }
                    }
                }
            }
            throw error
        }
    }

    private suspend fun executeReply(activeConversation: Conversation, content: List<Content>): String {
        var cumulativeText = ""
        val deltaText = StringBuilder()
        var isDeltaMode = false

        activeConversation.sendMessageAsync(Contents.of(*content.toTypedArray())).collect { message ->
            val chunk = message.extractText()
            if (chunk.isNotEmpty()) {
                if (!isDeltaMode) {
                    if (cumulativeText.isEmpty() || chunk.startsWith(cumulativeText)) {
                        cumulativeText = chunk
                    } else {
                        isDeltaMode = true
                        deltaText.append(cumulativeText).append(chunk)
                    }
                } else {
                    deltaText.append(chunk)
                }
            }
        }

        val result = if (isDeltaMode) deltaText.toString() else cumulativeText
        return result.ifBlank { "I generated an empty response. Try asking in a different way." }.trim()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        closeEngineOnly()
        currentModelFile = null
        currentHistory = emptyList()
    }

    private fun closeEngineOnly() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }

    private fun Message.extractText(): String {
        return contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}
