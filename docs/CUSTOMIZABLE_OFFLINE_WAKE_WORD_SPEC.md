# Customizable offline wake phrase — engineering plan

Status: design/audit complete; implementation and device validation pending.
Target baseline: `6098c0c2dea0d958573577dda25ab5eddb4be6a1` (identity/memory/exit fixes).
Development branch: `feat/offline-wakeword-prototype`.
Stable `0.11.12` and all release signing/data remain untouched.

## User requirement

The user must be able to choose and edit the wake phrase in Settings (the existing field is already persisted as `AssistantPreferences.wakePhrase`). The default remains `Hola Juanita` (or the matching active assistant name). The phrase is a true acoustic trigger, not merely a match against Gemini Live's transcript.

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
`jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08` (Apache-2.0 model card; ~3.3M parameter streaming Zipformer transducer; runtime keyword list can be regenerated without retraining; 16 kHz mono input).

The model card reports ~320 ms latency but its reported evaluation is limited and int8 parity is not yet evaluated. Treat it as an experiment, not as a verified production model. Validate provenance, exact model files, checksums, licensing, APK size, ARM64 performance, and false-accept/false-reject rates before adoption.

A phrase is only “supported” after checking tokenization and the acoustic model's language coverage. Initially evaluate Spanish phrases such as “Hola Juanita”, “Juanita”, and user-entered Spanish alternatives. Do not promise every arbitrary language/phrase until a compatible model exists and is tested. Keep the wake phrase configurable and persist it locally.

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

- PR #31: persistent Gemini Live session / native audio routing and explicit Stop notification. It remains draft/open; do not silently replace its transport behavior.
- PR #39: C3 fail-closed conversational-presence policy prototype. It deliberately does not open the microphone.
- PR #40: character collection, one-active-identity lifecycle supervisor, orderly microphone close and safe exit; six tabs preserved.
- PR #50: later grouped memory scopes and reliable identity switching, built on #40. Exact-head CI passed; physical Samsung validation still required.
- PRs #44–#49: separate cumulative audio/battery/memory chain (natural voice/diagnostics, automatic-memory recall, continuity storage, reflection cues, thermal/photo timeout, background Live idle timeout and power diagnostics). These are drafts and not all are present in the #50 baseline. Integrate deliberately by dependency and compare, not by merging unrelated branches wholesale.
- PRs #41–#43: native Gemini Live voice restored, visual understanding/image search and free image research; preserve native Live microphone ownership and no paid TTS.
- PR #55: temporary CI-only PR, closed unmerged.

## Non-goals

- Do not take over Bixby or Google Assistant's private wake-word detector.
- Do not add hidden Android APIs or privileged permissions.
- Do not stream ambient audio to Gemini or any server.
- Do not run two microphone capture engines simultaneously.
- Do not change stable release, package ID, signing identity, encrypted data formats, or existing six-tab navigation.
- Do not merge any draft PR or publish a release without explicit user approval.
