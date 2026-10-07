package com.amit.gemmcompanion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechTranscriber(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onError("On-device speech recognition is unavailable. Install an offline speech language pack and try again.")
            return
        }

        val speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = speechRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking closer to the mic."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    else -> "Speech recognition error ($error)."
                }
                release(speechRecognizer)
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                release(speechRecognizer)
                if (text.isNotBlank()) {
                    onFinalResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer.startListening(intent)
        } catch (error: Exception) {
            release(speechRecognizer)
            onError(error.message ?: "Could not start on-device speech recognition.")
        }
    }

    fun finishListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun stop() {
        val activeRecognizer = recognizer
        recognizer = null
        runCatching { activeRecognizer?.cancel() }
        runCatching { activeRecognizer?.destroy() }
    }

    private fun release(activeRecognizer: SpeechRecognizer) {
        if (recognizer === activeRecognizer) recognizer = null
        runCatching { activeRecognizer.destroy() }
    }
}
