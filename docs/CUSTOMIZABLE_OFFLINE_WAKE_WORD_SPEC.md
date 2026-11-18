# Customizable offline wake phrase — engineering plan

Status: isolated KWS detector and lifecycle policy ported; model provisioning, opt-in UI, service orchestration and physical-device validation remain pending.
Target baseline: unified cumulative integration branch based on the battery/thermal-safe 0.11.12 line.
Development branch: `integration/unified-rollup-2026-11-18`.
Stable `0.11.12` and all release signing/data remain untouched.

## User requirement

The user must be able to choose and edit the wake phrase in Settings (the existing field is already persisted as `AssistantPreferences.wakePhrase`). `Hola Juanita` is NOT a required phrase and must not be hard-coded as the activation. The selected phrase is a true acoustic trigger, not merely a match against Gemini Live's transcript. New setup must let the user choose a supported phrase; any suggested example such as `Ehi Juanita` remains editable and is not treated as acoustically validated.

No paid APIs, subscriptions, cloud audio streaming, or mandatory hosted services.

## What exists today

- Settings already exposes and persists a free-text “Parola/frase di attivazione” field.
- Its own supporting text explicitly says automatic recognition is not implemented.
- `WakePhraseMatcher` only matches a direct call in text already transcribed by Gemini Live.
- `ConversationalPresencePolicy` is a pure fail-closed policy; it does not own or open the microphone.
- `JuanitaVoiceService` owns Gemini Live and its microphone lifecycle. A wake-word engine must never capture concurrently with an active Live session.
- Android manifest declares microphone foreground-service permission/type.

## Platform constraints

Samsung Bixby Voice wake-up on supported Galaxy models offers Samsung's own supported phrases (“Hi, Bixby” / “Bixby”), voice enrollment and sensitivity options. It does not expose a public API that lets Juanita register an arbitrary phrase with Bixby's private detector.

On Android 12, `AlwaysOnHotwordDetector` is a system API intended for privileged/system assistant integration. A normal sideloaded third-party app cannot assume access to Samsung's low-power DSP hotword path. Do not use hidden APIs, privileged permissions, Accessibility workarounds, or attempt to intercept Bixby.

Therefore build an app-owned offline KWS engine as the practical fallback. It must run only under explicit user opt-in and a visible microphone foreground-service notification. Do not claim DSP-level power use.

## Candidate engine

Evaluate sherpa-onnx open-vocabulary keyword spotting on Android. Candidate Spanish model:
`jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08` (Apache-2.0 model card; ~3.3M parameter streaming Zipformer transducer; runtime keyword list can be regenerated without retraining; 16 kHz mono input). The model card reports about 320 ms latency, but its evaluation is based on five short safety phrases and one 536.7-second clip; int8 parity is not yet evaluated. It explicitly warns that spontaneous, far-field, noisy and strongly accented speech may behave differently. This is a candidate, not a verified production model.

### Critical integration defect found in the current prototype

`SherpaOfflineWakeWordDetector` currently configures `keywordsFile = ""` and calls `keywordSpotter.createStream(phrase)` with the user's raw phrase. For this BPE model, sherpa-onnx expects BPE pieces separated by spaces (and multiple keywords separated by `/`), not ordinary raw text. The official Kotlin API passes the supplied string directly to the native `CreateStream` call; it does not tokenize the phrase for the app. Therefore the existing isolated detector must not be described as functional even after simply connecting it to the service.

Before activation, implement and test a real SentencePiece BPE encoder against the exact downloaded `bpe.model`, or generate and verify a local `keywords.txt` for a deliberately bounded set of supported phrases. Do not fake tokenization by splitting on spaces. If custom arbitrary phrases remain editable, the app must encode them correctly on-device or clearly reject unsupported phrases. The Spanish model's training/evaluation does not establish recognition quality for “Hola Juanita”, “Hola José”, Canarian Spanish, Colombian Paisa or Italian; collect consented local test samples and measure false accepts/misses on both phones.

Validate provenance, exact model files, per-file SHA-256 checksums, licensing/attributions (including training voice obligations), APK/storage size, ARM64 performance and battery/thermal behavior before adoption.

A phrase is only “supported” after checking both BPE tokenization and acoustic performance for the target language/accents. Initially evaluate user-selected phrases in the supported language set; do not privilege “Hola Juanita” or promise every arbitrary language/phrase until a compatible model exists and is tested. Keep the wake phrase configurable and persist it locally.

## Runtime state machine

1. DISABLED: no microphone, no worker, no wake lock.
2. ARMED: app-owned offline KWS only, explicit opt-in, foreground service + visible notification, bounded audio buffers, no network.
3. TRIGGERED: debounce and confidence threshold; stop/release KWS AudioRecord fully.
4. STARTING_LIVE: only after the trigger, start the existing service-owned Gemini Live session using the selected digital identity, language and voice.
5. LIVE_ACTIVE: KWS is stopped; Gemini Live is the sole microphone owner.
6. STOPPING: close Live and confirm recorder/session shutdown before returning to ARMED or DISABLED.
7. ERROR: fail closed; no second recorder is opened; visible diagnostic and manual recovery.

No detached microphone coroutine. Serialize KWS/Live transitions through the existing service lifecycle supervisor. If microphone release cannot be confirmed, do not start another capture path.

## Power and privacy requirements

- No network access in ARMED.
- No stored raw audio; ring buffer only, promptly overwritten.
- No partial transcript or personal content collected by the wake detector.
- No manual partial WakeLock by default; measure actual screen-off behavior and use only if platform lifecycle demonstrably requires it.
- Pause/stop on permission revocation, competing calls/audio capture, user disable, service stop, force-stop, and critical thermal conditions.
- Show persistent, truthful status and a one-tap stop action while listening.
- Never keep Gemini Live open just to detect the phrase.
- If Android/Samsung denies microphone foreground service or background start, report the limitation and require an explicit foreground start.

## User-facing settings

Reuse the existing Settaggi field; do not add a seventh tab.
- Editable phrase with validation (non-empty, bounded length, no control characters).
- Toggle “Attivazione vocale offline” default OFF until user explicitly enables it.
- “Verifica frase” test flow while app is foregrounded; no cloud upload.
- Sensitivity/false-trigger setting only if supported by engine and backed by tests.
- Status: unsupported language/phrase, model missing, permission missing, armed, paused, or error.
- Preserve current phrase setting and legacy defaults on upgrade.

## Test gates

Unit:
- phrase validation and token generation;
- phrase update while armed reloads keyword list safely;
- duplicate/empty/too-long phrase rejection;
- trigger debounce and false-trigger suppression;
- state-machine serialization and no dual microphone owner;
- disable/permission revocation/thermal/error fail-closed behavior.

Instrumentation:
- Android 12/API 31 and current target API;
- foreground-service notification and mic privacy indicator;
- screen-off/lock, background restrictions and battery saver;
- KWS stop before Live start; Live stop before KWS restart;
- repeated wake/stop cycles and app exit.

Physical:
- Galaxy Note10+ Android 12 first, then S22 Ultra Android 15.
- Quiet room, TV/music, conversation distractors, different distances/orientations, Spanish Canarian and Colombian pronunciation, Italian alternatives.
- Measure 30-minute battery delta, CPU, RAM, temperature, false activations and missed activations against idle baseline.
- No “low battery” claim until measured on-device.

## PR history and dependencies to preserve

- PR #58 is the sole cumulative lane. It is based on PR #49 head and has exactly 35 commits beyond that baseline (the 710 count against `main` is full historical ancestry, not 710 newly added changes).
- The PR #49 baseline already carries the relevant advanced voice/audio, memory/archive, reflection, vision, alarms, thermal and background-idle work. Do not copy those older sibling branches over it again.
- PR #50 grouped memory scopes and reliable identity switching, PR #57's reviewed memory-recall/continuity/reflection slices, PR #34 Czech/Moravian profiles and PR #56's isolated KWS foundation have been ported into #58.
- PR #41 explicitly records the decision to pause separate Gemini Flash TTS and restore native SDK-managed Gemini Live voice. Preserve that decision: do not re-enable cloud TTS or spend its separate quota.
- PRs #42–#43 contribute photo understanding and anonymous Commons/Openverse image search. These do not provide general text-web research.
- Temporary CI-only PRs #51–#55 are not integration bases and must never be merged.

## Non-goals

- Do not take over Bixby or Google Assistant's private wake-word detector.
- Do not add hidden Android APIs or privileged permissions.
- Do not stream ambient audio to Gemini or any server.
- Do not run two microphone capture engines simultaneously.
- Do not change stable release, package ID, signing identity, encrypted data formats, or existing six-tab navigation.
- Do not merge any draft PR or publish a release without explicit user approval.


## Phrase calibration — speaker identity explicitly excluded

Wake activation is based only on detecting the user-selected phrase. **It must not identify, authenticate, enroll, or distinguish the person speaking.** No speaker embeddings, voiceprints, speaker-verification model, biometric template, or speaker-specific gate belongs in this feature. Any person whose voice produces a valid detection of the selected phrase may trigger Juanita.

### Phrase setup and calibration

- In Settings, the user can edit the activation phrase within the languages and phrase lengths genuinely supported by the installed acoustic model and tokenizer.
- A foreground-only setup flow may ask for 5–8 repetitions of the selected phrase, spoken naturally with modest variations in intonation, speed and volume. These are phrase samples, not speaker enrollment.
- Process samples locally in bounded RAM buffers. Do not upload, persist, log, or retain raw audio; discard it promptly after the setup/test step.
- Do not claim that repetitions train or adapt the acoustic model unless the selected engine explicitly implements a documented adaptation mechanism and tests prove it. If the engine cannot adapt its acoustic model, samples may only be used to test phrase suitability and calibrate a supported detection threshold; explain that limitation honestly.
- The detector must match the phrase acoustically, not merely search a transcript after Gemini Live has already started.
- Phrase recognition should tolerate ordinary changes in intonation, pace, volume and distance, but accent/language coverage must be measured rather than promised. Reject or label unsupported phrases clearly.
- Changing the phrase must regenerate and validate its BPE/token representation before arming. Never silently fall back to a different phrase.

### Acceptance tests

- Setup, retake, cancel and phrase change work without storing raw audio.
- The same phrase is detected across ordinary variations in pace, intonation, volume and distance.
- Different people can say the phrase and trigger activation; no identity check is performed.
- Similar-sounding phrases, TV/radio and ordinary conversation are measured as distractors.
- No audio, speaker embedding, voiceprint or biometric template is written to disk or logs.
- False-accept and missed-activation rates, CPU/RAM, battery delta and temperature are measured on Note10+ first, then S22 Ultra.
- Phrase change invalidates/rebuilds the KWS keyword encoding; unsupported language/phrase or model errors fail closed.
