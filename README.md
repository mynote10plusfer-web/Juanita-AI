# Juanita AI

Android-first personal voice assistant focused on multilingual interaction, configurable identity and personality, offline-capable speech, privacy, and replaceable AI providers.

## Current version: 0.6.0

- Kotlin + Jetpack Compose Android application.
- Editable assistant display name and configurable wake phrase (saved locally; wake-word engine not yet connected).
- Nine persistent personality controls: irony, sweetness, sensuality, seriousness, friendliness, curiosity, expressiveness, formality, and empathy.
- Persona prompt model prepared for the future conversation provider.
- Persistent language and regional-profile selection.
- Experimental Sicilian (Palermo-area), Canarian Spanish, and Colombian Paisa phrase profiles.
- Android system TTS sample playback; regional accent authenticity is not guaranteed.
- Android speech-recognition intent as a temporary input adapter.
- Provider-neutral interfaces for speech recognition, speech output, and conversational AI.
- Firebase Android configuration and Firebase AI Logic SDK integrated; Gemini Live runtime connection and App Check setup are the next implementation steps.
- GitHub Actions debug APK and permanently signed release APK builds; permanent signing secrets are configured and signature verification passed for v0.5.1.

The personality settings are stored on-device and are ready to be passed to the conversation provider. Firebase AI Logic SDK is now included, but the Live session, persona prompt wiring, microphone/audio streaming, and App Check initialization are not yet connected.

## AI provider decision (November 5, 2026)

- Selected first live conversational provider: Firebase AI Logic with the Gemini Developer API, using the free-tier Gemini Live model `gemini-3.1-flash-live-preview` for the initial trial.
- The user explicitly chose the Firebase Spark/free setup and declined enabling billing. Do not enable billing or switch to Agent Platform/Vertex AI without a new explicit approval.
- Firebase project and Android registration are complete. `google-services.json`, Google Services Gradle plugin, and Firebase AI Logic Android SDK are integrated in v0.6.0. App Check initialization and the Live audio session are still pending, so the provider is not yet connected in the Android runtime.
- Gemini Live through Firebase AI Logic is currently documented by Google as Preview; model availability and limits can change.

## Not implemented yet

- Actual conversational AI provider and multi-turn chat.
- sherpa-onnx offline ASR/TTS runtime and model downloads.
- Authentic regional Sicilian, Canarian, or Paisa neural voices.
- Wake-word detection and always-listening foreground service.
- LIFE integrations (notifications, SMS, WhatsApp, email, calls, calendar); each will require explicit permissions.


## JUANITA CORE

The planned persistent identity, autonomous memory curation, reflection journal, privacy controls and adaptive-style architecture are specified in [JUANITA_CORE.md](JUANITA_CORE.md). This is a design specification; these runtime capabilities are not implemented yet.

## Product direction

The first release is an independent Android app. Core functions should remain free and local wherever practical; optional cloud intelligence may be added without making cloud access mandatory for basic device functions. A later LIFE mode is intended to act as an opt-in daily copilot for alarms, messages, email, reminders, and incoming-call announcements.

## Build

- Debug APK: `.github/workflows/android.yml`
- Permanently signed release: `.github/workflows/release-signed.yml`

The release workflow requires the four `JUANITA_*` GitHub Actions secrets documented in [ARCHITECTURE.md](ARCHITECTURE.md). Do not install a release build until a single permanent keystore has been configured and backed up.

## Security

Never commit API keys, passwords, access tokens, signing keys, or private personal data. Keep secrets in GitHub Actions Secrets and never package them into the APK.


## Version 0.6.0 — Firebase foundation

Adds the user's Firebase Android configuration, Google Services Gradle plugin, Firebase AI Logic SDK, and network/microphone permissions. This is integration groundwork only: no live microphone session is started until App Check, session lifecycle, runtime permission handling, and audio UI are implemented and tested.

## Version 0.5.0 — encrypted memory foundation

This milestone adds an Android Keystore-backed AES-256-GCM local memory store, explicit conversation-archive consent (off by default), manual memory save/list/delete controls, and a deterministic candidate-ranking policy. Raw microphone audio is not stored.

**Important implementation boundary:** the conversational provider is not connected yet, so live conversation turns are not currently being archived and automatic extraction/curation is not yet active. The new curator ranks candidates supplied by a future extraction component; it does not invent memories. The memory centre currently supports explicit manual saving and deletion. Verify encryption and lifecycle behaviour on the Samsung Galaxy Note 10+ / Android 12 before treating this as production-ready.

The v0.5.1 signed-release workflow completed successfully: all four permanent-signing secrets were present, the APK was built, its signature was verified, and the signed artifact was uploaded.
