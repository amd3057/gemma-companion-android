# Google Play Store Listing Draft

## App name

Mira - Private Agent

## Short description (80 characters max)

A private on-device Gemma agent for chat, camera, notes, and reminders.

## Full description

Meet Mira, your private AI agent powered by Google AI Edge models running on your Android device.

Chat with local Gemma models, ask questions about camera images, and dictate messages with Android's on-device speech recognition. Save notes from conversations, find relevant notes later with EmbeddingGemma 2, and schedule local reminders.

Chat, camera images, notes, reminders, and model files are processed or stored locally by Mira. Internet access is used to retrieve the Google AI Edge Gallery model catalog and download selected models from Hugging Face. Voice is transcribed on-device; the Android text-to-speech provider may handle spoken replies according to its own settings. Model files are large and require a compatible device with sufficient storage and memory.

Features:
- On-device Gemma chat with supported text and image inputs
- On-device voice-to-text; recognized text is sent to the chat model
- Optional EmbeddingGemma 2 semantic retrieval for saved notes
- Local notes and scheduled Android notifications
- Text-to-speech through the Android speech engine
- Import compatible LiteRT-LM models or download listed models in-app

AI-generated answers can be inaccurate or out of date. The app does not provide live web search or professional advice.

## Suggested category

Productivity or Tools. Choose the category that most accurately matches the final product in Play Console.

## Store assets

- 512 × 512 px PNG app icon: `store-listing/app-icon-512.png` (ready)
- 1024 × 500 px feature graphic: `store-listing/feature-graphic-1024x500.png` (ready)
- Phone screenshots: `store-listing/screenshots/phone-home-play.png` and `store-listing/screenshots/phone-model-catalog-play.png` (ready; capture additional states if desired)
- Privacy policy URL: `https://github.com/amd3057/gemma-companion-android/blob/main/PRIVACY_POLICY.md`