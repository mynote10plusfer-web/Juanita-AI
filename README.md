# Juanita AI

Android-first personal voice assistant focused on multilingual interaction, offline-capable speech, configurable personality, privacy, and replaceable AI providers.

## Current version: 0.2.0

- Kotlin + Jetpack Compose Android application.
- Persistent voice-language selection.
- Android speech-recognition intent as a temporary input adapter.
- Android TextToSpeech with language availability and error handling.
- Provider-neutral interfaces for speech recognition, speech output, and conversational AI.
- GitHub Actions debug APK build.
- Protected workflow and Gradle configuration prepared for permanent release signing.

The current Android speech adapters are a development baseline, not the final offline speech system. System recognition may depend on services installed on the device and network availability.

## Not implemented yet

- sherpa-onnx offline ASR/TTS runtime and downloaded model assets.
- Wake-word detection and always-listening foreground service.
- Conversational AI provider and persistent conversational memory.
- Notification, SMS, WhatsApp, email, call, and calendar integrations.
- Production release signing key configured in GitHub Actions.

## Build

- Debug APK: `.github/workflows/android.yml`
- Permanently signed release: `.github/workflows/release-signed.yml`

The release workflow requires the four `JUANITA_*` GitHub Actions secrets documented in [ARCHITECTURE.md](ARCHITECTURE.md). Do not install a release build until a single permanent keystore has been configured and backed up.

## Security

Never commit API keys, passwords, access tokens, signing keys, or private personal data. Keep secrets in GitHub Actions Secrets and never package them into the APK.
