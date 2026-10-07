package com.amit.gemmcompanion

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorder {
    @Volatile
    private var recording = false
    private var job: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope, onFinished: (ByteArray) -> Unit, onError: (String) -> Unit) {
        if (recording) return
        recording = true
        job = scope.launch(Dispatchers.IO) {
            val minimumBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minimumBuffer <= 0) {
                recording = false
                onError("This device cannot start microphone recording.")
                return@launch
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minimumBuffer
            )
            val output = ByteArrayOutputStream()
            try {
                recorder.startRecording()
                val buffer = ByteArray(minimumBuffer)
                val startTime = System.currentTimeMillis()
                while (isActive && recording && System.currentTimeMillis() - startTime < MAX_DURATION_MS) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count > 0) output.write(buffer, 0, count)
                }
                recording = false
                if (output.size() > 0) onFinished(output.toByteArray().toWav())
            } catch (error: Exception) {
                recording = false
                onError(error.message ?: "Microphone recording failed.")
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    fun stop() {
        recording = false
    }

    fun cancel() {
        recording = false
        job?.cancel()
        job = null
    }

    private fun ByteArray.toWav(): ByteArray {
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val dataSize = size
        header.put("RIFF".toByteArray())
        header.putInt(dataSize + 36)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)
        return header.array() + this
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val WAV_HEADER_SIZE = 44
        const val MAX_DURATION_MS = 15_000L
    }
}