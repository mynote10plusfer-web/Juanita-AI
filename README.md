# Juanita AI

Juanita AI is a modular Android personal voice assistant.

## Project goals

- Multilingual voice interaction with configurable language and voice.
- Offline-first speech recognition and text-to-speech where supported.
- Configurable assistant personality and wake phrase.
- Privacy-conscious design with explicit permissions and optional cloud AI providers.
- Android-first implementation, with cloud builds through GitHub Actions.

## Development status

Repository initialized. The Android application and voice components are not yet implemented in this repository.

## Planned architecture

- Android app: Kotlin and Jetpack Compose.
- Speech layer: interchangeable offline/online ASR and TTS providers.
- Conversation layer: interchangeable local or remote AI providers.
- Lifecycle services: foreground audio service and user-controlled notification features.

## Security

Never commit API keys, passwords, tokens, or private personal data. Keep secrets in secure local configuration or GitHub Actions secrets.
