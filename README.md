# Juanita AI

Android-first personal voice assistant focused on multilingual interaction, configurable identity and personality, offline-capable speech, privacy, and replaceable AI providers.

## Current version: 0.4.0

- Kotlin + Jetpack Compose Android application.
- Editable assistant display name and configurable wake phrase (saved locally; wake-word engine not yet connected).
- Nine persistent personality controls: irony, sweetness, sensuality, seriousness, friendliness, curiosity, expressiveness, formality, and empathy.
- Persona prompt model prepared for the future conversation provider.
- Persistent language and regional-profile selection.
- Experimental Sicilian (Palermo-area), Canarian Spanish, and Colombian Paisa phrase profiles.
- Android system TTS sample playback; regional accent authenticity is not guaranteed.
- Android speech-recognition intent as a temporary input adapter.
- Provider-neutral interfaces for speech recognition, speech output, and conversational AI.
- GitHub Actions debug APK build; optional protected release-signing workflow.

The personality settings are stored on-device and define the system instructions that a future AI conversation provider will receive. They do not yet alter live AI replies because a conversation provider has not been connected.

## Not implemented yet

- Actual conversational AI provider and multi-turn chat.
- sherpa-onnx offline ASR/TTS runtime and model downloads.
- Authentic regional Sicilian, Canarian, or Paisa neural voices.
- Wake-word detection and always-listening foreground service.
- LIFE integrations (notifications, SMS, WhatsApp, email, calls, calendar); each will require explicit permissions.
- Production release signing key configured in GitHub Actions.

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


## Version 0.5.0 — encrypted memory foundation

This milestone adds an Android Keystore-backed AES-256-GCM local memory store, explicit conversation-archive consent (off by default), manual memory save/list/delete controls, and a deterministic candidate-ranking policy. Raw microphone audio is not stored.

**Important implementation boundary:** the conversational provider is not connected yet, so live conversation turns are not currently being archived and automatic extraction/curation is not yet active. The new curator ranks candidates supplied by a future extraction component; it does not invent memories. The memory centre currently supports explicit manual saving and deletion. Verify encryption and lifecycle behaviour on the Samsung Galaxy Note 10+ / Android 12 before treating this as production-ready.

The signed-release workflow now skips signing cleanly when repository signing secrets are absent, with a visible warning instead of a failed run. A signed APK is still produced only when all four permanent-signing secrets are configured.
