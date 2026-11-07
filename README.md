# Juanita AI

Android voice assistant for natural multilingual, multi-turn conversations, configurable personality and regional speech profiles.

## Current version: 0.9.9 (versionCode 28)

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
- Android requests foreground location permission on first launch (precise GPS or approximate location). It reads a fresh device location automatically on app launch and again before each Gemini Live session, then includes the locality and coordinates in that session context. No weather screen/provider, background tracking, or persistent coordinate history.
- Android Keystore-backed AES-256-GCM memory. Full text transcripts from both sides of every Gemini Live conversation are archived automatically by default, locally, in encrypted per-session files; archive survives app/process restarts and is independent of curated personal facts. Raw microphone audio is never archived. Archive can be paused, assigned manual/7/30/90-day retention, and individual conversations can be deleted; manual retention is the default. The app restores the latest 60 archived turns plus curated facts into a new Live session (bounded by the model context budget); older turns remain stored on-device but are not all injected into every prompt. Automatic curated personal-memory extraction is enabled by default, separately, and uses a retryable encrypted queue. Extraction still requires successful Gemini transcription and a successful model response; inspect the in-app memory status and memory list to verify each step. Full semantic search/associative retrieval across the entire archive and a generated biography are not yet implemented.
- CameraX front/rear camera preview and optional 1 FPS image frames to the active Gemini Live session are implemented in 0.9.0; physical-device testing is pending.
- GitHub Actions builds a debug APK and a permanently signed release APK.

## AI provider and cost constraints

The user selected Firebase Spark/free tier with Firebase AI Logic and Gemini Developer API for the initial trial. Billing, Blaze, Vertex AI and Agent Platform must remain disabled unless the user explicitly approves a change. Gemini Live is a preview feature and free-tier availability is subject to Google's current quotas and terms.

## App Check

The Android app initializes Firebase App Check. Debug builds use the Firebase App Check debug provider; release builds use Play Integrity. The debug provider requires registering its generated debug token in Firebase Console. Release App Check registration and distribution settings must be completed before distributing the signed release build. Do not publish debug tokens, signing credentials or private keys.

For a pre-release conversation test, prefer the debug build and Firebase's documented debug-provider flow. A production Play Integrity registration is still required before distributing the signed release build.

## Not implemented yet

- Native regional neural voices guaranteed to sound authentically Sicilian, Canarian or Paisa.
- Always-on wake phrase detection (not implemented; the current foreground service is user-started and does not listen for a wake phrase).
- Offline ASR/TTS runtime and model downloads.
- Full semantic/associative retrieval across the complete transcript archive and an automatically generated evolving biography.
- Background location tracking (intentionally not requested).
- LIFE integrations (notifications, SMS, WhatsApp, email, calls, calendar), each requiring explicit permissions.

## Product roadmap

The canonical feature checklist, privacy requirements and phased implementation plan are maintained in [`docs/PRODUCT_ROADMAP.md`](docs/PRODUCT_ROADMAP.md). This roadmap distinguishes existing functionality from planned work.

## Build

- Debug APK: `.github/workflows/android.yml`
- Permanently signed release APK: `.github/workflows/release-signed.yml`

The release workflow uses the existing permanent GitHub Actions signing secrets. Never replace or regenerate the permanent keystore for routine updates.

## Privacy

The microphone is activated only after the user grants permission and starts a Live session. Audio is streamed to Gemini for real-time conversation and is not stored by this app. Conversation text archiving is on by default and encrypted on-device with Android Keystore AES-GCM; raw audio is never stored. Users can pause or clear the archive in the privacy panel. Manual memories are also encrypted on-device.
