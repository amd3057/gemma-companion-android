# Google Play Release Checklist

## Project release configuration

- Package ID: `com.amit.gemmcompanion`. Confirm it is available before creating the Play Console app; changing it after the first release creates a different app.
- `targetSdk` is Android 16 / API 36, as required for new mobile apps submitted after August 31, 2026.
- Publish an Android App Bundle (`.aab`) and enroll in Play App Signing.
- Generate a secure upload keystore, configure the ignored `keystore.properties` file using `keystore.properties.example`, and never commit the keystore or passwords.
- Increase `APP_VERSION_CODE` for every upload and set `APP_VERSION_NAME` for each release.
- Review native libraries from LiteRT-LM and MediaPipe for 16 KB page-size compatibility before production rollout.

## Play Console tasks requiring the developer

- Create/verify the Play developer account and complete identity/device verification.
- Create the app using the exact application ID above and opt into Play App Signing.
- Upload the signed AAB to an internal testing track first; exercise model download, image prompts, offline transcription, notes, reminders, and app upgrades on physical devices.
- Complete Store listing: app name, short/full description, 512 px icon, feature graphic, at least two phone screenshots, category, and contact details.
- Add the public privacy policy URL and complete Data safety based on the shipped SDKs and final network/data behavior. Do not submit answers until you have reviewed all integrated SDK behavior.
- Complete Ads, App access, Target audience, Content rating, and any other App content declarations accurately.
- If this is a new personal developer account created after November 13, 2023, check Play Console for the required closed-testing track and tester-duration requirement before production access.
- Review policy status and pre-launch report, then submit the production release.

## Data safety review notes

- The app sends model-catalog and model-file requests to Google AI Edge Gallery/GitHub and Hugging Face; those services receive standard network metadata, including IP address.
- Voice uses Android's on-device speech recognizer; the app forwards recognized text, not a WAV recording, to local Gemma inference.
- Camera images and chat text are processed by local model files; they are not uploaded by the app.
- Android's configured text-to-speech provider may have its own processing behavior. Verify the selected provider and disclose it in Play Console as appropriate.