# Juanita AI — Engineering Architecture

## Product principles

- Android-first, phone-only installation and cloud CI builds.
- Independent app first; deeper Android/Google replacement integrations later.
- Free local-first core; optional replaceable cloud AI, never a mandatory API key in the APK.
- Female, natural, warm assistant persona with user-adjustable character.
- Explicit Android permissions and user-controlled background behavior.
- No fake capability: unavailable providers and missing models must surface a clear status.
- Preserve package identity `ai.juanita.app`; increment `versionCode` for every installable build.
- Never rotate the permanent release signing key.

## Current baseline (candidate 0.10.3 / versionCode 32)

- Kotlin + Jetpack Compose; package/application ID `ai.juanita.app`; multi-device Android target (minimum SDK 26).
- Firebase AI Logic with Gemini Developer API Live model `gemini-3.1-flash-live-preview`; voice session runs in a microphone foreground service with explicit start/stop and Android notification.
- Configurable assistant name, wake phrase setting (stored but not yet connected to a wake-word engine), nine persistent personality traits, language, regional profile and Gemini Live voice.
- Language profiles include Italian, Spanish (Spain), Czech, English and Spanish (Colombia); experimental Sicilian, Canarian and Paisa regional instructions are separate from voice synthesis.
- Android Keystore-backed AES-256-GCM local store for curated memories and engineering reports; encrypted per-session transcript archive is separate from curated memories. Raw microphone audio is never stored.
- Automatic memory extraction uses a bounded, durable encrypted pending queue and a Gemini curator for non-sensitive user transcript text. The pipeline and archive recall still require end-to-end physical-device verification.
- New Live sessions restore bounded recent transcript context and curated memories. A local lexical search tool can retrieve relevant historical excerpts before answering; multilingual semantic/vector retrieval is future work.
- Persistent bottom navigation: Home, Avatar, Impostazioni, Memoria and Altro. Altro contains project status, debug-only App Check tools and the local Improvement Lab (Phase 1 reports are manually authored).
- CameraX preview and optional one-frame-per-second image forwarding to the active Live session are implemented; orientation, lifecycle and long-session behavior still require device verification.
- Avatar Studio currently stores profile metadata only. Portrait generation, facial expression animation and true audio lip sync are not implemented.
- GitHub Actions builds debug APKs and permanently signed release APKs. The release certificate is pinned to the independently verified SHA-256 in `.github/workflows/release-signed.yml`; preserve the keystore and never distribute a debug APK as an in-place update.

## Assistant identity and personality

Identity, wake phrase, language, regional dialect, voice output and character are separate settings.

Default starting character values (editable by the user):
- Irony 75%
- Sweetness 90%
- Sensuality 65%
- Seriousness 40%
- Friendliness 85%
- Intelligence/curiosity 100%
- Expressiveness 80%
- Formality 15%
- Empathy 95%

The assistant should remain warm, human and conversational. Personality traits must be context-sensitive: serious or sensitive situations prioritize clarity, respect and care; flirtation and humour must never be forced. The editable wake phrase is currently stored only; actual wake-word detection is a later speech milestone.

## Target modules

### Presentation
Compose screens, state holders, accessibility, settings, chat transcript.

### Speech
- `SpeechRecognitionProvider`: streaming/partial/final recognition contract.
- `SpeechOutputProvider`: speech synthesis contract.
- Future adapters: sherpa-onnx local models, Android system speech fallback, optional remote providers.
- VAD and keyword spotting are separate services; wake-word listening must run only after explicit opt-in.

### Conversation
- `ConversationProvider`: local or remote conversational engine.
- Every request should include the selected persona/system instruction, language and regional dialect.
- Context window and prompt construction remain independent from speech providers.
- API credentials are never embedded in the APK or committed to Git.

### Memory
- User-controlled structured preferences and conversation facts.
- Data minimization, export, and delete controls.
- Raw microphone audio is not persisted by default.

### Capability self-assessment and Improvement Lab
- Keep a truthful, inspectable capability catalogue separate from personal memory.
- Engineering reports are stored in the existing Android Keystore-backed encrypted local record store, with a bounded retention count.
- Reports are manually authored in Phase 1 and exported only after an explicit user action as UTF-8 text. Do not automatically include transcripts, personal memories, raw audio, contacts or credentials.
- Automatic gap detection, runtime outcome assessment and asynchronous report drafting are later milestones; never infer successful completion from model text alone.

### LIFE mode / Android integrations
The user-approved product direction is an opt-in daily copilot: wake-up greeting, reminders, incoming-call announcements, and message/email notifications; reading and replying to messages only after explicit user enablement. Build each integration separately behind Android permissions and user controls. No broad permissions are requested until the corresponding feature is implemented and explained.

## Regional dialect strategy

Treat language recognition and voice generation as separate problems. A dialect profile must define:
- Base language/locale for the ASR/TTS engine.
- Regional vocabulary and phrase preferences for the conversation layer.
- A dedicated voice model only when a licensed, suitable regional dataset/model exists.
- Native-speaker review before using labels such as "authentic Sicilian", "Canarian", or "Paisa".

Initial locale mapping:
- Sicilian (Palermo area): Italian fallback locale `it-IT`; a Sicilian-language model is a separate future requirement.
- Canarian Spanish: Spanish fallback locale `es-ES`.
- Colombian Paisa: Colombian Spanish fallback locale `es-CO`.

Current Android system TTS previews are not dialect-accurate speech synthesis.

## Offline speech evaluation

The leading candidate is sherpa-onnx. Its official documentation describes on-device Android speech recognition without Internet access and provides Android/Kotlin examples, ASR, TTS, VAD, keyword spotting, and language-identification APIs.

Integration must be model-driven, not just dependency-driven:
1. Select an ASR model that covers Italian, Spanish, Czech, and English with acceptable accuracy and latency.
2. Measure RAM, CPU, battery, APK size, and cold-start time on the target Galaxy Note 10+.
3. Select TTS voices individually and audit each model/dataset license.
4. Download model files separately from the APK when appropriate; verify hashes and keep model licenses alongside the downloads.
5. Do not claim regional accents such as Canarian, Sicilian, or Paisa are authentic unless tested with native speakers.

Official references:
- https://k2-fsa.github.io/sherpa/onnx/android/index.html
- https://k2-fsa.github.io/sherpa/onnx/

## Signing and updates

The current debug workflow is for development only. GitHub-hosted runners create a debug signing key in the runner environment; it is not a permanent update identity.

The signed release workflow expects these GitHub Actions secrets:
- `JUANITA_KEYSTORE_BASE64`
- `JUANITA_KEYSTORE_PASSWORD`
- `JUANITA_KEY_ALIAS`
- `JUANITA_KEY_PASSWORD`

A single user-owned keystore must be created, backed up securely, and used for every release. Never commit the keystore or passwords. Do not install a release APK until the permanent signing key has been configured and the APK signature has been verified.

## Next milestones

1. Verify the signed release certificate and update metadata, then test in-place update without uninstalling or clearing local data.
2. Verify archive, automatic-memory extraction, historical recall, calendar navigation and deletion on Note10+ (Android 12) and S22 Ultra.
3. Add a user-editable biography/timeline, provenance and contradiction handling; then evaluate local multilingual semantic retrieval.
4. Extend the Improvement Lab from manual reports to asynchronous capability-gap detection based on observable task outcomes, without uploading transcripts or personal memories.
5. Evaluate genuine on-device portrait generation; later investigate rigged avatar animation and a validated audio/viseme bridge.
6. Research Android Telecom screening limits and prototype only supported, opt-in call-screening behavior.
7. Evaluate sherpa-onnx offline ASR/TTS and wake-word detection on target devices; add LIFE integrations one by one behind user-granted permissions.
