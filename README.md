# Gemma Companion

Native Android chat with on-device Gemma models. Text, camera snapshots, and microphone clips are sent to LiteRT-LM locally. Conversation turns and notes are stored on-device; reminders are scheduled with Android WorkManager notifications. The assistant can speak responses aloud with Android TextToSpeech.

## Requirements

- Android Studio with Android SDK Platform 36 and JDK 17 or later
- Android 12 (API 31) or later; a physical device with enough free memory for the chosen model is strongly recommended
- Internet access for loading Google AI Edge Gallery's model catalog and downloading a model
- About 4 GB free storage for Gemma 3n E2B; check each catalog entry's device-memory recommendation before downloading

This app runs model inference offline after importing the model. It does not include live web search, so answers about current events or changing facts may be out of date.

## Run

1. Open this directory in Android Studio and allow Gradle to sync.
2. Connect an Android 12+ device and run the `app` configuration.
3. Tap the folder icon or **Browse and download Gemma models**. Choose a multimodal Gemma chat model from the Google AI Edge Gallery catalog and download it. Previously downloaded models can be selected again without downloading twice. You can also import a compatible `.litertlm` chat model manually.
4. Ask by text, use the camera button to attach a snapshot, or grant microphone permission and record a voice clip. Use the speaker controls to hear answers.
5. Tap the notebook icon to save notes, create reminders, or review and delete them. Grant notification permission when prompted for reminders.

The catalog also offers **EmbeddingGemma 2** as a separate memory model. Download and enable it to index saved notes locally and retrieve relevant notes by meaning in later chats. It is an embedding model, not a chat model, so it cannot replace the Gemma chat model.

The first model load can take several seconds. Model files are large; ensure the device has sufficient storage and memory. Image/audio inference requires a model that supports those modalities, such as Gemma 3n.

## Memory and privacy

Conversation turns, notes, and EmbeddingGemma2 vectors are stored locally on the device. In chat, prefix a message with “remember that”, “take a note”, or “save this” to capture a note automatically; assistant replies can also be saved with the note icon. With EmbeddingGemma2 enabled, relevant saved notes are retrieved locally for each new question. The Gallery catalog is fetched from Google AI Edge Gallery; selected model files are downloaded from their listed Hugging Face repositories to app-private storage. No hosted inference service is used. Use the trash icon to clear chat memory; use the notebook to delete notes/reminders. Android's system TextToSpeech may use its installed voice provider; text-to-speech is separate from Gemma inference.

## Implementation references

- [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
- [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)