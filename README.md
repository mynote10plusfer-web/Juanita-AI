# Juanita AI

Android voice assistant for natural multilingual, multi-turn conversations, configurable personality and regional speech profiles.

## Current version: 0.8.8 (versionCode 17)

- Kotlin + Jetpack Compose Android app.
- Firebase AI Logic with Gemini Developer API and Gemini Live audio model `gemini-3.1-flash-live-preview`.
- Bidirectional microphone/audio conversation with explicit start/stop and microphone permission.
- Gemini Live session owned by an Android microphone foreground service with an ongoing notification and Stop action; screen-off reliability is pending physical-device verification.
- Configurable assistant name, wake phrase setting (wake-word engine not yet connected), and nine persistent personality controls.
- Separate language and regional profile selectors.
- Experimental regional profiles: Sicilian (Palermo area), Canarian Spanish, and Colombian Paisa (Antioquia/Medellin).
- Regional instructions are included in the Gemini Live system prompt. This guides language and vocabulary but does not guarantee a native regional voice.
- Android system TTS sample playback; the device's installed voice is used and regional authenticity is not guaranteed.
- Gemini Live response voice selector with seven documented female voices; selection is saved locally and applied when a new Live session starts.
- Android speech-recognition intent remains available as a separate microphone test.
- Android Keystore-backed encrypted manual memory with a dedicated privacy screen: list/edit/delete individual memories, clear all personal memories, enable/disable transcript archiving, choose retention (manual/7/30/90 days), delete individual archived conversations or clear the archive independently. Gemini Live transcript capture is not yet wired into the archive; no automatic conversation transcript is currently stored.
- GitHub Actions builds a debug APK and a permanently signed release APK.

## AI provider and cost constraints

The user selected Firebase Spark/free tier with Firebase AI Logic and Gemini Developer API for the initial trial. Billing, Blaze, Vertex AI and Agent Platform must remain disabled unless the user explicitly approves a change. Gemini Live is a preview feature and free-tier availability is subject to Google's current quotas and terms.

## App Check

The Android app initializes Firebase App Check. Debug builds use the Firebase App Check debug provider; release builds use Play Integrity. The debug provider requires registering its generated debug token in Firebase Console. Release App Check registration and distribution settings must be completed before distributing the signed release build. Do not publish debug tokens, signing credentials or private keys.

For a pre-release conversation test, prefer the debug build and Firebase's documented debug-provider flow. A production Play Integrity registration is still required before distributing the signed release build.

## Not implemented yet

- Native regional neural voices guaranteed to sound authentically Sicilian, Canarian or Paisa.
- Always-on wake phrase detection (not implemented; the current foreground service is user-started and does not listen for a wake phrase).
- Automatic extraction, summarization and curation of conversation memories.
- Offline ASR/TTS runtime and model downloads.
- LIFE integrations (notifications, SMS, WhatsApp, email, calls, calendar), each requiring explicit permissions.

## Product roadmap

The canonical feature checklist, privacy requirements and phased implementation plan are maintained in [`docs/PRODUCT_ROADMAP.md`](docs/PRODUCT_ROADMAP.md). This roadmap distinguishes existing functionality from planned work.

## Build

- Debug APK: `.github/workflows/android.yml`
- Permanently signed release APK: `.github/workflows/release-signed.yml`

The release workflow uses the existing permanent GitHub Actions signing secrets. Never replace or regenerate the permanent keystore for routine updates.

## Privacy

The microphone is activated only after the user grants permission and starts a Live session. Audio is streamed to Gemini for real-time conversation and is not stored by this app. Conversation archiving remains off by default. Manual memories are stored encrypted on-device.
