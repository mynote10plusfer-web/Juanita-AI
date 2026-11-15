# Juanita AI — Voice Engine Audit (2026-11-15)

## 2026-11-15 — Android microphone recording-state diagnostics

**STATUS: PRIVACY-SAFE DIAGNOSTIC PROBE ADDED; EXACT-HEAD CI AND PHYSICAL NOTE10+ TEST REQUIRED. THIS IS NOT A MICROPHONE FIX.**

- While the foreground voice service is active, the app polls Android's `AudioManager.activeRecordingConfigurations` every five seconds. It logs only changes and a 15-second heartbeat.
- The diagnostic records the number of active Android recording configurations, audio source, sample rate and (on Android 10+) whether Android reports the client as silenced.
- The same entry includes Gemini Live session presence/closed state, `isAudioConversationActive()`, and elapsed time since the last input/output transcription callback.
- The probe does not create a second `AudioRecord`, capture audio samples, record transcript text, alter routing, or trigger recovery. It is stopped as soon as voice shutdown begins.
- Interpretation gate: an active SDK session with zero Android recording configurations points to a local capture/lifecycle problem; a present but silenced configuration points to Android audio policy; a present, unsilenced configuration with no input transcription leaves VAD/network/server delivery as possible causes. None of these signals alone proves that intelligible speech reached Gemini.
- Required test: on the physical Galaxy Note10+ / Android 12, start a normal native-Live conversation, let Juanita finish the opening question, speak several replies, interrupt her while speaking, and export the technical voice log. Do not call the microphone issue fixed until the log and repeated device test support that conclusion.

## Executive decision

Use Gemini Live native audio as the only active voice path for interactive conversations. Do not run a second text-to-speech generation after every Live turn. Keep the cloud Gemini TTS laboratory paused while its free-tier quota is too small for normal use. Evaluate on-device neural TTS separately, behind a capability/benchmark gate; do not make it a mandatory dependency or replace the stable Live path before physical-device acceptance.

Constraints: Firebase Spark/free only; no Blaze, paid APIs, hosted inference, external paid accounts, or stable-release changes without explicit approval.

## Findings in the previous experimental path

1. The app supplied a Gemini TTS voice to every selected character, including a default voice when no separate TTS voice had been selected. This made the regional TTS branch the normal conversation path rather than an optional experiment.
2. In that branch the app bypassed the SDK-managed audio conversation helper, created a direct Live receiver and an app-owned AudioRecord sender, buffered Live output transcription and Live PCM, waited for the transcript-settle delay, then made a separate Gemini TTS generateContent request.
3. The full TTS WAV was buffered before playback. This added a serial model request and delayed first audible response. The Live PCM was only a fallback after TTS failed; it was not the immediate primary playback.
4. A free-tier RESOURCE_EXHAUSTED response from gemini-3.8-flash-tts prevents further calls until the quota reset. The TTS laboratory uses the same model family and can consume the same project/model allowance.

## Current experimental correction

- Normal voice conversations no longer pass a TTS voice to JuanitaVoiceService.
- Character voice selection uses the existing Gemini Live voice ID.
- GeminiLiveAssistant therefore selects its existing SDK-managed startAudioConversation path for ordinary conversations; the direct receiver/manual microphone/TTS buffering branch is not activated by the normal Home conversation or character voice preview.
- A saved Paisa/Canarian delivery preset is added to the session instruction as a linguistic/pronunciation preference. It does not guarantee a native regional accent; this must be judged by listening.
- The separate cloud TTS audition button is disabled to avoid wasting the daily free quota. Saved experimental TTS voice IDs are retained as dormant profile data and are not presented as the active conversation voice.
- Stable v0.11.12, its package identity and signing configuration are not changed. This remains an unmerged experiment.

## On-device neural TTS candidates

### Candidate A — LiteRT / MediaPipe Kokoro 82M

The current LiteRT Community Kokoro bundle advertises Spanish and Italian among its supported languages, INT8 packaging around 295 MB, and an Apache-2.0 model license. Its published speed and memory measurements are from much newer hardware (Galaxy S26) and must not be projected onto the Note10+ or S22 Ultra. It is a promising quality candidate, not yet an approved runtime choice.

### Candidate B — sherpa-onnx with VITS/Piper

sherpa-onnx supports offline TTS on Android and VITS/Piper model families. Model packages are smaller and can be selected per language/voice, but quality, accent, model licence and Spanish/Italian voice availability must be checked individually. The sherpa-onnx multilingual Kokoro package documented upstream is not currently the Spanish/Italian option; do not assume all Kokoro packages have the same language coverage.

## Device-aware strategy

Do not choose a model solely from the marketing name or RAM size. At runtime, probe supported ABI, available memory, runtime delegate availability and thermal state. Attempt hardware acceleration only when the runtime actually exposes a compatible delegate; keep a CPU fallback. Benchmark on each physical device and cache the result.

Expected test devices:
- Galaxy Note10+ / Android 12: older CPU/GPU generation; treat CPU-only inference as the baseline until a delegate is positively detected.
- Galaxy S22 Ultra: materially newer hardware, but exact regional chipset and RAM variant must be identified before making a performance claim.

For each candidate, measure:
- model download and installed size; SHA-256 and licence;
- cold-start/load time and first-audio latency;
- real-time factor (inference duration divided by generated-audio duration);
- peak process memory, CPU/GPU use and thermal rise over repeated turns;
- intelligibility and naturalness in Italian, Spanish (Spain/Canary) and Spanish (Colombia/Medellín);
- interruption, cancellation, audio-route changes, screen-off operation and resource release.

Initial acceptance gates (to be validated, not yet achieved): first audio within 1.5 s after text is ready; real-time factor below 1.0 on the target device; no out-of-memory, ANR, severe thermal escalation or microphone/session regression during a 15-minute repeated-turn test. If a device fails, do not load the large model there; retain Gemini Live as the voice path.

## Architecture recommendation

1. **Interactive online conversation:** Gemini Live native streamed audio. It avoids the second TTS request and keeps the SDK-managed microphone/playback, VAD and interruption path.
2. **Optional offline speech synthesis:** a locally installed neural model, only after device benchmark and explicit model download. It can read local content or speak text generated locally; it cannot replace Gemini's online reasoning or keep a cloud conversation alive without network.
3. **Fallback:** if local TTS is too slow or memory-heavy, use the native Live voice while online. Do not silently switch to low-quality Android system voices unless the user explicitly enables that fallback.
4. **Cloud TTS lab:** remain paused until a sustainable quota or a separate user-approved provider is established. No automatic retries on quota errors.

## Verification still required

- Exact-head CI build, JVM tests and Android instrumentation tests after the native-Live correction.
- Physical Note10+ and S22 Ultra tests for microphone start/stop, first response latency, interruption, tool calls, reconnection, background audio routing and thermal/battery behavior.
- Side-by-side listening for the Live voice and any on-device candidate. Text instructions alone do not establish acoustic accent quality.
- No APK is considered device-accepted until these checks are performed.
