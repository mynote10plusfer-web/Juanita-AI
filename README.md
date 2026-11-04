# Juanita AI

Juanita AI is an Android-first personal voice assistant, designed around multilingual interaction, configurable personality, privacy, and replaceable voice/AI providers.

## Current implementation

Version **0.1.0** is the first technical foundation:

- Kotlin + Jetpack Compose Android application.
- Dark, high-contrast starter interface.
- Language selection for Spanish (Spain), Italian, Czech, English, and Spanish (Colombia).
- Speech input smoke test using the Android speech-recognition intent.
- Speech output smoke test using the Android TextToSpeech engine.
- GitHub Actions workflow intended to build and publish a debug APK artifact.

These speech tests use services available on the Android device. They are **not yet** the final offline speech stack and may depend on installed Android speech services and network availability.

## Not implemented yet

- Conversational AI engine and persistent memory.
- sherpa-onnx offline ASR/TTS integration and verified voice models.
- Wake-word detection and always-listening foreground service.
- Notification, SMS, WhatsApp, email, call, and calendar integrations.
- Full personality controls, privacy dashboard, and data export/deletion.
- Production signing, release build, and automated device tests.

## Architecture direction

Keep the application modular so that the following can be replaced independently:

1. Android presentation and lifecycle layer.
2. Speech recognition (ASR), voice activity detection (VAD), wake-word detection, and speech synthesis (TTS).
3. Conversation/LLM provider (local or optional cloud).
4. Memory and user-controlled personal data.
5. Android integrations, each behind explicit permissions and user controls.

## Build

The GitHub Actions workflow is `.github/workflows/android.yml`. It uses JDK 17 and Gradle 8.9 to build the debug APK.

## Security

Never commit API keys, passwords, access tokens, signing keys, or private personal data. Keep secrets out of source control and use protected GitHub Actions secrets only when a future cloud provider is deliberately enabled.
