package com.amit.gemmcompanion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

data class CompanionNote(val id: String, val text: String, val createdAt: Long)

data class CompanionReminder(val id: String, val text: String, val remindAt: Long)

class OrganizerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("companion_organizer", Context.MODE_PRIVATE)

    fun loadNotes(): List<CompanionNote> = runCatching {
        val values = JSONArray(preferences.getString(KEY_NOTES, "[]"))
        List(values.length()) { index ->
            val item = values.getJSONObject(index)
            CompanionNote(item.getString("id"), item.getString("text"), item.getLong("createdAt"))
        }
    }.getOrDefault(emptyList())

    fun addNote(text: String): CompanionNote {
        val note = CompanionNote(UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis())
        saveNotes(loadNotes() + note)
        return note
    }

    fun deleteNote(id: String) {
        saveNotes(loadNotes().filterNot { it.id == id })
    }

    fun loadReminders(): List<CompanionReminder> = runCatching {
        val values = JSONArray(preferences.getString(KEY_REMINDERS, "[]"))
        List(values.length()) { index ->
            val item = values.getJSONObject(index)
            CompanionReminder(item.getString("id"), item.getString("text"), item.getLong("remindAt"))
        }
    }.getOrDefault(emptyList())

    fun addReminder(text: String, remindAt: Long): CompanionReminder {
        val reminder = CompanionReminder(UUID.randomUUID().toString(), text.trim(), remindAt)
        saveReminders(loadReminders() + reminder)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_ID, reminder.id)
                    .putString(ReminderWorker.KEY_TEXT, reminder.text)
                    .build()
            )
            .setInitialDelay((remindAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "companion-reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return reminder
    }

    fun deleteReminder(id: String) {
        preferences.edit().putString(
            KEY_REMINDERS,
            JSONArray().apply {
                loadReminders().filterNot { it.id == id }.forEach { reminder ->
                    put(JSONObject().put("id", reminder.id).put("text", reminder.text).put("remindAt", reminder.remindAt))
                }
            }.toString()
        ).apply()
        WorkManager.getInstance(appContext).cancelUniqueWork("companion-reminder-$id")
    }

    private fun saveNotes(notes: List<CompanionNote>) {
        val json = JSONArray()
        notes.forEach { note ->
            json.put(JSONObject().put("id", note.id).put("text", note.text).put("createdAt", note.createdAt))
        }
        preferences.edit().putString(KEY_NOTES, json.toString()).apply()
    }

    private fun saveReminders(reminders: List<CompanionReminder>) {
        val json = JSONArray()
        reminders.forEach { reminder ->
            json.put(JSONObject().put("id", reminder.id).put("text", reminder.text).put("remindAt", reminder.remindAt))
        }
        preferences.edit().putString(KEY_REMINDERS, json.toString()).apply()
    }

    private companion object {
        const val KEY_NOTES = "notes"
        const val KEY_REMINDERS = "reminders"
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        val id = inputData.getString(KEY_ID)?.hashCode() ?: text.hashCode()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Companion reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Mira reminder")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
            Result.success()
        }.getOrElse { Result.failure() }
    }

    companion object {
        const val KEY_ID = "reminder_id"
        const val KEY_TEXT = "reminder_text"
        private const val CHANNEL_ID = "companion_reminders"
    }
}