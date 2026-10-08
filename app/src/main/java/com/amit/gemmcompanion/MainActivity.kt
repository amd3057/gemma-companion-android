package com.amit.gemmcompanion

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionApp() }
    }
}

private enum class MediaDisclosure { CAMERA, MICROPHONE }

private const val PRIVACY_POLICY_URL =
    "https://github.com/amd3057/gemma-companion-android/blob/main/PRIVACY_POLICY.md"

@SuppressLint("MissingPermission")
@Composable
private fun CompanionApp(viewModel: CompanionViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var activeUtteranceId by remember { mutableStateOf<String?>(null) }
    var showOrganizer by remember { mutableStateOf(false) }
    var showModelCatalog by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var mediaDisclosure by remember { mutableStateOf<MediaDisclosure?>(null) }
    var pendingReminder by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val speechTranscriber = remember { SpeechTranscriber(context) }
    val textToSpeech = remember {
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts.setLanguage(Locale.getDefault())
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
                ttsReady = true
            }
        }
        tts
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        viewModel.setImage(bitmap)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isRecording = true
            draft = ""
            viewModel.reportStatus("Listening and transcribing on this device…")
            speechTranscriber.startListening(
                onPartialResult = { partial -> draft = partial },
                onFinalResult = { final ->
                    isRecording = false
                    speechTranscriber.stop()
                    if (final.isNotBlank()) {
                        draft = ""
                        viewModel.send(final)
                    } else {
                        viewModel.reportStatus("No speech was recognized. Try again or type your message.")
                    }
                },
                onError = { message ->
                    viewModel.reportStatus(message)
                    isRecording = false
                    speechTranscriber.stop()
                }
            )
        } else {
            viewModel.reportStatus("Microphone permission is needed for on-device transcription.")
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val reminder = pendingReminder
        if (granted && reminder != null) {
            viewModel.addReminder(reminder.first, reminder.second)
        } else if (!granted) {
            viewModel.reportStatus("Allow notifications to schedule reminders.")
        }
        pendingReminder = null
    }

    DisposableEffect(Unit) {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null) context.mainExecutor.execute { activeUtteranceId = utteranceId }
            }

            override fun onDone(utteranceId: String?) {
                context.mainExecutor.execute {
                    if (activeUtteranceId == utteranceId) activeUtteranceId = null
                }
            }

            override fun onError(utteranceId: String?) {
                context.mainExecutor.execute {
                    if (activeUtteranceId == utteranceId) activeUtteranceId = null
                }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                context.mainExecutor.execute {
                    if (activeUtteranceId == utteranceId) activeUtteranceId = null
                }
            }
        })
        onDispose {
            speechTranscriber.stop()
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    LaunchedEffect(viewModel.turns.size) {
        if (viewModel.turns.isNotEmpty()) listState.animateScrollToItem(viewModel.turns.lastIndex)
    }

    fun speakText(text: String) {
        if (!ttsReady || text.isBlank()) return
        textToSpeech.setLanguage(Locale.getDefault())
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            activeUtteranceId = null
            viewModel.reportStatus("Text-to-speech could not start.")
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF146B5A),
            onPrimary = Color.White,
            secondary = Color(0xFFB45138),
            background = Color(0xFFF4F7F5),
            surface = Color.White,
            onSurface = Color(0xFF1B2824),
            surfaceVariant = Color(0xFFE8EFEB),
            onSurfaceVariant = Color(0xFF51615B)
        )
    ) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .imePadding()
            ) {
                Header(
                    modelLabel = viewModel.modelLabel.value,
                    memoryCount = viewModel.turns.count { it.role == "user" },
                    isSpeaking = activeUtteranceId != null,
                    onManageModels = { showModelCatalog = true },
                    onOpenOrganizer = { showOrganizer = true },
                    onClearMemory = viewModel::clearMemory,
                    onSpeakLatest = {
                        if (activeUtteranceId != null || textToSpeech.isSpeaking) {
                            textToSpeech.stop()
                            activeUtteranceId = null
                        } else {
                            val lastAnswer = viewModel.turns.lastOrNull { it.role == "assistant" }?.text
                            if (ttsReady && !lastAnswer.isNullOrBlank()) speakText(lastAnswer)
                        }
                    }
                )

                if (showModelCatalog) {
                    ModelCatalogDialog(
                        viewModel = viewModel,
                        onDismiss = { showModelCatalog = false },
                        onSelect = { model ->
                            viewModel.selectDownloadedModel(model)
                            showModelCatalog = false
                        }
                    )
                }

                if (showOrganizer) {
                    OrganizerDialog(
                        notes = viewModel.notes,
                        reminders = viewModel.reminders,
                        onDismiss = { showOrganizer = false },
                        onSaveNote = viewModel::addNote,
                        onDeleteNote = viewModel::deleteNote,
                        onDeleteReminder = viewModel::deleteReminder,
                        onScheduleReminder = { text, remindAt ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingReminder = text to remindAt
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.addReminder(text, remindAt)
                            }
                        }
                    )
                }

                StatusLine(text = viewModel.status.value)

                if (!viewModel.modelReady.value) {
                    ModelSetup(
                        loading = viewModel.isLoadingModel.value,
                        onImport = { modelPicker.launch(arrayOf("*/*")) },
                        onBrowse = { showModelCatalog = true }
                    )
                } else if (viewModel.turns.isEmpty()) {
                    WelcomePanel(onPrompt = { draft = it })
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(viewModel.turns) { _, turn ->
                        MessageBubble(
                            turn = turn,
                            onSaveNote = viewModel::addNote,
                            onSpeak = { text ->
                                if (activeUtteranceId != null || textToSpeech.isSpeaking) {
                                    textToSpeech.stop()
                                    activeUtteranceId = null
                                } else if (ttsReady) {
                                    speakText(text)
                                }
                            },
                            isSpeaking = activeUtteranceId != null
                        )
                    }
                    if (viewModel.isSending.value) {
                        item { Text("Thinking locally…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
                    }
                }

                Composer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    image = viewModel.attachedImage.value,
                    recording = isRecording,
                    enabled = viewModel.modelReady.value && !viewModel.isSending.value,
                    imageSupported = viewModel.modelSupportsImage.value,
                    onImageUnsupported = {
                        viewModel.reportStatus("This model is text-only. Tap Model and select an image-capable Gemma model.")
                    },
                    onCamera = { mediaDisclosure = MediaDisclosure.CAMERA },
                    onMicrophone = {
                        if (isRecording) {
                            isRecording = false
                            speechTranscriber.finishListening()
                        } else {
                            mediaDisclosure = MediaDisclosure.MICROPHONE
                        }
                    },
                    onPrivacy = { showPrivacyPolicy = true },
                    onRemoveImage = { viewModel.setImage(null) },
                    onSend = {
                        viewModel.send(draft)
                        draft = ""
                    }
                )

                if (showPrivacyPolicy) {
                    PrivacyPolicyDialog(
                        onDismiss = { showPrivacyPolicy = false },
                        onOpenPolicy = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                        }
                    )
                }

                mediaDisclosure?.let { disclosure ->
                    MediaDisclosureDialog(
                        disclosure = disclosure,
                        onDismiss = { mediaDisclosure = null },
                        onContinue = {
                            mediaDisclosure = null
                            when (disclosure) {
                                MediaDisclosure.CAMERA -> camera.launch(null)
                                MediaDisclosure.MICROPHONE -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun Header(
    modelLabel: String?,
    memoryCount: Int,
    isSpeaking: Boolean,
    onManageModels: () -> Unit,
    onOpenOrganizer: () -> Unit,
    onClearMemory: () -> Unit,
    onSpeakLatest: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompanionAvatar(size = 48.dp, speaking = false)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Mira", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(modelLabel ?: "Private Agent · on-device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            TextButton(onClick = onManageModels) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = "Select or download a model")
                Text("Model")
            }
            IconButton(onClick = onOpenOrganizer) {
                Icon(Icons.Rounded.EditNote, contentDescription = "Open notes and reminders")
            }
            IconButton(onClick = onSpeakLatest) {
                Icon(
                    if (isSpeaking) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop spoken answer" else "Speak latest answer"
                )
            }
            IconButton(onClick = onClearMemory, enabled = memoryCount > 0) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear conversation memory")
            }
        }
    }
}

@Composable
private fun CompanionAvatar(size: androidx.compose.ui.unit.Dp, speaking: Boolean) {
    val scale by animateFloatAsState(if (speaking) 1.06f else 1f, label = "avatar-pulse")
    Box(
        modifier = Modifier.size(size * scale).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF0D6B5A), Color(0xFF8AC7A8), Color(0xFFF1B27C))), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.64f)) {
            val eyeRadius = this.size.minDimension * 0.055f
            drawCircle(Color(0xFF173A32), eyeRadius, center = androidx.compose.ui.geometry.Offset(this.size.width * 0.34f, this.size.height * 0.42f))
            drawCircle(Color(0xFF173A32), eyeRadius, center = androidx.compose.ui.geometry.Offset(this.size.width * 0.66f, this.size.height * 0.42f))
            drawArc(
                color = Color(0xFF173A32),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(this.size.width * 0.32f, this.size.height * 0.46f),
                size = androidx.compose.ui.geometry.Size(this.size.width * 0.36f, this.size.height * 0.28f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = this.size.minDimension * 0.045f)
            )
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun ModelSetup(loading: Boolean, onImport: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Choose a local Gemma chat model", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Download from the Gallery catalog or import a compatible .litertlm file. Chat and memory models are separate.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onImport, enabled = !loading) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Preparing model…" else "Import local model")
        }
        OutlinedButton(onClick = onBrowse, enabled = !loading) {
            Text("Browse and download Gemma models")
        }
    }
}

@Composable
private fun ModelCatalogDialog(
    viewModel: CompanionViewModel,
    onDismiss: () -> Unit,
    onSelect: (GalleryModel) -> Unit
) {
    val progress = viewModel.downloadProgress.value
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemma models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Chat and semantic memory models from Google AI Edge Gallery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = viewModel::refreshCatalog, enabled = !viewModel.isCatalogLoading.value) { Text("Refresh") }
                }
                if (progress != null) {
                    val fraction = if (progress.totalBytes > 0) progress.receivedBytes.toFloat() / progress.totalBytes else 0f
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        TextButton(onClick = viewModel::cancelModelDownload) { Text("Cancel") }
                    }
                    Text(
                        "Downloading ${progress.fileName}: ${formatBytes(progress.receivedBytes)} / ${formatBytes(progress.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (viewModel.isCatalogLoading.value) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading model catalog…", modifier = Modifier.padding(start = 10.dp))
                    }
                }
                viewModel.catalogError.value?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        viewModel.catalog.sortedBy { if (it.purpose == GalleryModelPurpose.EMBEDDING) 0 else 1 },
                        key = { it.modelId + it.modelFile }
                    ) { model ->
                        val downloaded = viewModel.isDownloaded(model)
                        ModelCatalogCard(
                            model = model,
                            downloaded = downloaded,
                            selected = viewModel.isActive(model),
                            busy = viewModel.isLoadingModel.value,
                            onDownload = { viewModel.downloadAndSelectModel(model) },
                            onSelect = { onSelect(model) }
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ModelCatalogCard(
    model: GalleryModel,
    downloaded: Boolean,
    selected: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(formatBytes(model.sizeInBytes), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                if (model.purpose == GalleryModelPurpose.EMBEDDING) {
                    "Memory model · finds related saved notes; does not generate chat replies"
                } else {
                    "Chat model · generates replies with image and audio input"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (model.minDeviceMemoryInGb > 0) {
                Text("Recommended device memory: ${model.minDeviceMemoryInGb} GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                compactModelDescription(model.description),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when {
                    selected -> OutlinedButton(onClick = {}, enabled = false) {
                        Text(if (model.purpose == GalleryModelPurpose.EMBEDDING) "Memory enabled" else "Chat selected")
                    }
                    downloaded -> Button(onClick = onSelect, enabled = !busy) {
                        Text(if (model.purpose == GalleryModelPurpose.EMBEDDING) "Enable memory" else "Select chat model")
                    }
                    else -> Button(onClick = onDownload, enabled = !busy) {
                        Text(if (model.purpose == GalleryModelPurpose.EMBEDDING) "Download memory model" else "Download chat model")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Size unavailable"
    val gigabytes = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f GB", gigabytes)
}

private fun compactModelDescription(description: String): String =
    description.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("\\s+"), " ")
        .take(200)

@Composable
private fun OrganizerDialog(
    notes: List<CompanionNote>,
    reminders: List<CompanionReminder>,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onScheduleReminder: (String, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showingReminders by remember { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var reminderDraft by rememberSaveable { mutableStateOf("") }
    val firstReminderTime = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis }
    var remindAt by remember { mutableStateOf(firstReminderTime) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Your notebook", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showingReminders = false }) {
                        Text("Notes (${notes.size})")
                    }
                    OutlinedButton(onClick = { showingReminders = true }) {
                        Text("Reminders (${reminders.size})")
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (!showingReminders) {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New note") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            onSaveNote(noteDraft)
                            noteDraft = ""
                        },
                        enabled = noteDraft.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Save note") }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                                    Text(
                                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(note.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note")
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = reminderDraft,
                        onValueChange = { reminderDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What should Mira remind you about?") },
                        maxLines = 2,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = {
                            val selected = Calendar.getInstance().apply { timeInMillis = remindAt }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = remindAt
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                    }
                                    remindAt = updated.timeInMillis
                                },
                                selected.get(Calendar.YEAR),
                                selected.get(Calendar.MONTH),
                                selected.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) { Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(remindAt))) }
                        OutlinedButton(onClick = {
                            val selected = Calendar.getInstance().apply { timeInMillis = remindAt }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = remindAt
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                    }
                                    remindAt = updated.timeInMillis
                                },
                                selected.get(Calendar.HOUR_OF_DAY),
                                selected.get(Calendar.MINUTE),
                                false
                            ).show()
                        }) { Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(remindAt))) }
                    }
                    Text(
                        "Reminders use Android notifications. Allow notifications when prompted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Button(
                        onClick = {
                            onScheduleReminder(reminderDraft, remindAt)
                            reminderDraft = ""
                        },
                        enabled = reminderDraft.isNotBlank() && remindAt > System.currentTimeMillis(),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Set reminder") }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(reminders.sortedBy { it.remindAt }, key = { it.id }) { reminder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(reminder.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(reminder.remindAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteReminder(reminder.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Cancel reminder")
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun WelcomePanel(onPrompt: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("What should we explore?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("What am I looking at?", "Help me think this through").forEach { prompt ->
                OutlinedButton(onClick = { onPrompt(prompt) }) { Text(prompt) }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    turn: ChatTurn,
    onSpeak: (String) -> Unit,
    onSaveNote: (String) -> Unit,
    isSpeaking: Boolean
) {
    val isAssistant = turn.role == "assistant"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isAssistant) {
            CompanionAvatar(size = 30.dp, speaking = false)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isAssistant) MaterialTheme.colorScheme.surface else Color(0xFFDCECE4),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = if (isAssistant) 1.dp else 0.dp,
            modifier = Modifier.fillMaxWidth(0.84f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = formatChatMarkdown(turn.text),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isAssistant) {
                    Row(modifier = Modifier.align(Alignment.End)) {
                        IconButton(onClick = { onSaveNote(turn.text) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.EditNote, contentDescription = "Save answer as note", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onSpeak(turn.text) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isSpeaking) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop spoken answer" else "Speak this answer",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatChatMarkdown(rawText: String): AnnotatedString {
    val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")
    if (normalized.isBlank()) return AnnotatedString("")

    val builder = AnnotatedString.Builder()
    val lines = normalized.split('\n')
    var inCodeBlock = false

    lines.forEachIndexed { index, line ->
        if (line.trimStart().startsWith("```")) {
            val textSoFar = builder.toAnnotatedString().text
            if (index > 0 && textSoFar.isNotEmpty() && textSoFar.last() != '\n') {
                builder.append('\n')
            }
            inCodeBlock = !inCodeBlock
            return@forEachIndexed
        }

        val textSoFar = builder.toAnnotatedString().text
        if (textSoFar.isNotEmpty() && textSoFar.last() != '\n') {
            builder.append('\n')
        }

        if (line.isBlank()) {
            if (inCodeBlock) {
                builder.append(" ")
            }
            return@forEachIndexed
        }

        if (inCodeBlock) {
            builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFE9EEF3)))
            builder.append(line)
            builder.pop()
            return@forEachIndexed
        }

        val trimmed = line.trimStart()
        val text = when {
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("- ").removePrefix("* ")}".trimEnd()
            trimmed.startsWith("#") -> trimmed
            else -> line.trimEnd()
        }

        if (text.isBlank()) {
            return@forEachIndexed
        }

        appendInlineMarkdown(builder, text)
    }

    return builder.toAnnotatedString()
}

private fun appendInlineMarkdown(builder: AnnotatedString.Builder, text: String) {
    var cursor = 0

    while (cursor < text.length) {
        val boldMarker = text.indexOf("**", cursor)
        val italicMarker = text.indexOf("*", cursor)
        val codeMarker = text.indexOf("`", cursor)
        val linkMarker = text.indexOf("[", cursor)

        val nextToken = listOf(
            boldMarker to "bold",
            italicMarker to "italic",
            codeMarker to "code",
            linkMarker to "link"
        )
            .filter { it.first >= 0 }
            .minByOrNull { it.first }
            ?: break

        val (start, tokenType) = nextToken
        if (start > cursor) {
            builder.append(text.substring(cursor, start))
        }

        when (tokenType) {
            "bold" -> {
                val end = text.indexOf("**", start + 2)
                if (end > start + 2) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendInlineMarkdown(builder, text.substring(start + 2, end))
                    builder.pop()
                    cursor = end + 2
                    continue
                }
            }
            "italic" -> {
                val end = text.indexOf("*", start + 1)
                if (end > start + 1) {
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInlineMarkdown(builder, text.substring(start + 1, end))
                    builder.pop()
                    cursor = end + 1
                    continue
                }
            }
            "code" -> {
                val end = text.indexOf("`", start + 1)
                if (end > start + 1) {
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF1F4F7)))
                    builder.append(text.substring(start + 1, end))
                    builder.pop()
                    cursor = end + 1
                    continue
                }
            }
            "link" -> {
                val closingBracket = text.indexOf("]", start + 1)
                val openingParen = text.indexOf("(", closingBracket + 1)
                val closingParen = text.indexOf(")", openingParen + 1)
                if (closingBracket > start && openingParen > closingBracket && closingParen > openingParen) {
                    val label = text.substring(start + 1, closingBracket)
                    builder.pushStyle(SpanStyle(color = Color(0xFF4F6EEB)))
                    builder.append(label)
                    builder.pop()
                    cursor = closingParen + 1
                    continue
                }
            }
        }

        builder.append(text.substring(start, start + 1))
        cursor = start + 1
    }

    if (cursor < text.length) {
        builder.append(text.substring(cursor))
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    image: Bitmap?,
    recording: Boolean,
    enabled: Boolean,
    imageSupported: Boolean,
    onImageUnsupported: () -> Unit,
    onCamera: () -> Unit,
    onMicrophone: () -> Unit,
    onPrivacy: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (image != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    androidx.compose.foundation.Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Camera attachment",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Text("Photo attached", modifier = Modifier.padding(start = 8.dp).weight(1f))
                    IconButton(onClick = onRemoveImage) { Text("×") }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { if (imageSupported) onCamera() else onImageUnsupported() }, enabled = enabled) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Take a camera photo")
                }
                IconButton(onClick = onMicrophone, enabled = enabled) {
                    Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, contentDescription = if (recording) "Stop recording" else "Record a voice message")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    placeholder = { Text(if (recording) "Recording… tap stop when done" else "Ask Mira anything") },
                    maxLines = 4,
                    shape = RoundedCornerShape(8.dp)
                )
                IconButton(
                    onClick = onSend,
                    enabled = enabled && (draft.isNotBlank() || image != null)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Local inference · your content stays on device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp)
                )
                TextButton(onClick = onPrivacy) { Text("Privacy") }
            }
        }
    }
}

@Composable
private fun MediaDisclosureDialog(
    disclosure: MediaDisclosure,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val isCamera = disclosure == MediaDisclosure.CAMERA
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCamera) "Use a camera image" else "Use voice input") },
        text = {
            Text(
                if (isCamera) {
                    "The photo you take is processed by your selected on-device Gemma model. If EmbeddingGemma 2 is enabled, it also creates a local image vector to find related saved notes. This app does not upload the photo."
                } else {
                    "Android's on-device speech recognizer processes your voice and returns a transcript. Only the transcript is sent to your on-device Gemma model; this app does not save or upload a voice recording."
                }
            )
        },
        confirmButton = {
            Button(onClick = onContinue) { Text(if (isCamera) "Continue to camera" else "Continue to microphone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit, onOpenPolicy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy") },
        text = {
            Text(
                "Chat, notes, photos, transcripts, reminders, and model files are stored or processed on this device. " +
                    "Internet access is used to load the model catalog and download models. The configured Android text-to-speech provider may process text it reads. " +
                    "You can clear chat history from the header and delete notes or reminders in the notebook."
            )
        },
        confirmButton = { TextButton(onClick = onOpenPolicy) { Text("Full privacy policy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}