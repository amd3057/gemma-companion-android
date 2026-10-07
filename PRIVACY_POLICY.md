# Privacy Policy: Mira - Private Agent

Effective date: October 7, 2026

## Developer and contact

Mira - Private Agent is published by `amd3057`. For privacy questions, use the [project issue tracker](https://github.com/amd3057/gemma-companion-android/issues).

## Data processed by the app

- **Chat text and conversation history:** Stored in the app's private on-device preferences and supplied to the selected local Gemma model for responses. The app does not send prompts to a developer-operated server.
- **Camera images:** Captured only after the user taps the camera control. An image may be processed on-device by the selected image-capable Gemma model and, when enabled, EmbeddingGemma 2 to find related saved notes. Images are not uploaded by this app.
- **Voice input:** The app requests microphone access after an in-app disclosure and uses Android's on-device speech recognizer. The app sends the recognized transcript, not a recorded audio file, to the local Gemma model. Recognition requires a compatible on-device language pack.
- **Notes and reminders:** Stored in app-private on-device preferences. Notes can be embedded locally with EmbeddingGemma 2. Reminder text is scheduled through Android WorkManager and displayed as a local notification.
- **Imported/downloaded models:** Stored in app-private storage. The app downloads the Gallery model catalog from Google AI Edge Gallery and model files from the listed Hugging Face repositories. Those services receive standard network request metadata such as the device's IP address; chat prompts, notes, and captured images are not sent to them by the app.
- **Text-to-speech:** Responses are read by the Android system text-to-speech engine selected on the device. The handling of text by that engine depends on the user's Android speech provider and settings.

## Sharing, retention, and deletion

The app has no account system, advertising SDK, analytics service, or developer-operated inference endpoint. Chat history, notes, vectors, reminders, and model files remain in app-private storage until deleted in the app or the app is uninstalled. Android notifications may display reminder text on the device. Android backup is disabled for app data.

## Permissions

- Microphone: used for user-initiated, on-device speech transcription.
- Notifications: used to deliver reminders the user schedules.
- Internet: used to retrieve the model catalog and download model files only.

Camera capture is initiated through Android's system camera action; the app requests no broad camera permission.

## Changes

This policy will be updated if the app's data practices change. The current version will be published in the [project repository](https://github.com/amd3057/gemma-companion-android/blob/main/PRIVACY_POLICY.md).