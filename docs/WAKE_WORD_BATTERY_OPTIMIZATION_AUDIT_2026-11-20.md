# Wake-word battery optimization audit — 2026-11-20

## Scope and safety boundary

This audit compares Juanita's current offline wake-word path with public Android implementations. The goal is to reduce standby CPU/battery use without changing the supported phrases (Hola Juanita / Hola José), Gemini Live, identity selection, encrypted memory, or the six-tab product structure.

No runtime behavior is changed by this audit. No hidden Android API, privileged permission, root access, assistant-role replacement, paid SDK, cloud recognition, or network audio capture is permitted.

## Current Juanita implementation (PR #58, HEAD audited: f69f1c8afe12d13250daf61837ce55860b7a29da)

Source: `app/src/main/java/ai/juanita/app/presence/SherpaOfflineWakeWordDetector.kt`.

- One `AudioRecord` owns the microphone at 16 kHz, mono, PCM16.
- Blocking reads are accumulated into reusable 1,600-sample / 100 ms frames.
- One Sherpa-ONNX Zipformer2 KWS model thread is configured (`numThreads=1`, CPU provider).
- Every completed 100 ms frame is fed to the streaming `OnlineStream`; while Sherpa reports `isReady`, `decode()` and `getResult()` run.
- No speech/noise VAD gate currently prevents KWS decode while the room is silent.
- Buffers are reused; zero/negative AudioRecord reads have bounded/error handling; the audio worker has audio scheduling priority.
- A 60-second diagnostic sample records worker thread CPU time and decode wall time. This is useful, but it does not yet report the ratio of speech-active time to standby time or battery discharge.
- The service serializes Sherpa and Gemini Live: Sherpa releases AudioRecord before Live starts, and is only re-armed after Live teardown and explicit opt-in.

This is a sound correctness baseline, but the model still receives continuous audio and performs inference whenever its streaming feature pipeline is ready.

## Open-source implementations inspected

### Home Assistant Android Companion

Repository: https://github.com/home-assistant/android

Relevant implementation:
- `VoiceAudioRecorder.kt` shares one AudioRecord stream between collectors, uses 16 kHz mono where supported, bounded DROP_OLDEST buffering, and releases capture when the last collector leaves.
- `MicroWakeWordEngine.cpp` uses TensorFlow Lite Micro with a compact operator resolver, preallocated tensor arenas, streaming feature accumulation, and inference only after the model stride has filled.
- Its public Android documentation explicitly warns that continuous wake-word listening noticeably affects battery and consumes more than Google's built-in assistant because third-party apps do not get the same low-power hotword DSP path.

Takeaway: shared capture, bounded buffering, native lightweight inference, and strict lifecycle matter. microWakeWord's published stock phrases are not a drop-in replacement for Juanita's two custom phrases, and the CPU-based implementation does not eliminate standby drain.

### Suxsem / AndroidWakeWord

Repository: https://github.com/Suxsem/AndroidWakeWord (Apache-2.0)

Relevant implementation inspected:
- Native Speex preprocessing/VAD is evaluated on small audio frames.
- A state machine runs Silero VAD only after Speex reports possible speech.
- The heavier wake-word feature/embedding/classifier pipeline is batched and only advanced while speech is active.
- It preallocates audio/tensor/ring buffers and can request NNAPI/XNNPACK/QNN providers.

Important caveats:
- Its current implementation contains a process-global Speex state pointer; that exact wrapper must not be copied into Juanita unchanged because it is not safe for multiple detector instances and does not provide per-instance lifecycle ownership.
- Its model pipeline is openWakeWord-compatible and uses several ONNX sessions. It is not a drop-in wrapper around Juanita's Sherpa BPE keyword model.
- Hardware AGC/noise suppression may improve audio quality but must not be counted as a battery optimization without measurement.
- The README's “minimal battery impact” wording is not a reproducible benchmark for Juanita's devices.

### VoxRT Silero Android

Repository: https://github.com/VoxRT/voxrt-silero-android (Apache-2.0)

The repository offers a compact native runtime for streaming Silero VAD and reports low relative inference time on a Snapdragon 662. It is a candidate for a later controlled VAD experiment, not evidence of power savings on the user's Exynos/Samsung device. Its native binary, model packaging, ABI coverage, lifecycle, and license/NOTICE requirements need separate review before adoption.

## Proposed staged architecture

1. Keep the current Sherpa detector as the guaranteed fallback and keep both wake phrases unchanged.
2. Introduce a per-instance, native Speex VAD front-end (no process-global singleton). It must be independently startable/stoppable, allocation-bounded, and covered by native lifecycle tests.
3. Add a small circular PCM pre-roll buffer (initial experiment: 0.8–1.2 seconds). It preserves the beginning of a spoken phrase while the VAD transitions from silence to speech.
4. Run the heavier VAD confirmation (Silero or a validated equivalent) only after Speex reports possible speech. Use onset/offset hysteresis and a short trailing-speech window to avoid chattering.
5. Feed the pre-roll plus live speech to a fresh or safely reset Sherpa OnlineStream; do not starve its feature pipeline or discard the first syllable. After speech ends, keep KWS active for a measured trailing window, then return to VAD-only standby.
6. Keep one microphone owner and one audio worker. Never run Sherpa, Gemini Live, or a second VAD recorder concurrently.
7. Keep this mode behind an internal experiment flag until it passes phrase-accuracy and false-trigger tests. If the gate fails, automatically fall back to today's continuous Sherpa path and record the failure reason.
8. Preserve the current 60-second worker CPU diagnostic and add speech-active ratio, KWS inference count/time, VAD-trigger count, detector uptime, and fallback count. Do not log raw audio or transcripts.

## Why a simple amplitude/RMS gate is rejected

An energy threshold can classify quiet speech, whispers, distant speech, wind, road noise, music, and engine noise incorrectly. Juanita must hear both phrases at realistic levels and on the user's scooter/road environment. A naive RMS gate can save CPU by silently losing the wake phrase. It is therefore not acceptable as a production optimization.

## Required validation before enabling by default

### Functional / acoustic
- Both phrases: `Hola Juanita` and `Hola José`, across the active identity language/voice configurations.
- Quiet speech, normal speech, loud speech, different distances, television/music, wind and scooter engine noise.
- At least 100 positive phrase trials per device/environment set; record missed detections and false activations, not just successful demos.
- Verify first-syllable preservation across the VAD onset boundary and verify no duplicate callback.
- Verify mic release before Gemini Live and orderly re-arm after Live stops.

### Energy / thermal
- Baseline current Sherpa vs VAD-gated candidate on the same physical device, same battery state, brightness, network, and environment.
- Screen off: 1 h, 4 h, and 8 h idle; report battery percentage, Android battery attribution where available, temperature, worker CPU%, speech-active ratio, and false-trigger count.
- Test separately: wake-word standby, Gemini Live active, and wake-word disabled.
- Test Samsung Note10+ Android 12 (Exynos variant) and S22 Ultra Android 15; add Pixel/Qualcomm and MediaTek when available.
- Do not infer DSP use from Android version, chipset, or a low CPU sample. A real public API and runtime capability check are required.

## Decision

**Proceed with a VAD-gated Sherpa experiment, not an immediate default replacement.** First implement an isolated per-instance Speex/VAD adapter and PCM pre-roll behind an experiment flag, then compare it against the current path. Do not import AndroidWakeWord's full ONNX wake-word stack or replace Sherpa until tests demonstrate that both custom phrases remain reliable and measured standby drain improves.

The Android DSP / default-assistant interop track remains separate: no public universal third-party DSP route has been established by this audit. Keep the current direct wake-word fallback for Android versions/devices without a verified low-power backend.

## References

- Home Assistant Android source: https://github.com/home-assistant/android
- Home Assistant Android wake-word battery caveat: https://www.home-assistant.io/voice_control/android/
- Home Assistant microWakeWord implementation: https://github.com/home-assistant/android/tree/master/microwakeword
- AndroidWakeWord: https://github.com/Suxsem/AndroidWakeWord
- VoxRT Silero Android: https://github.com/VoxRT/voxrt-silero-android
- Android wake-word architecture and DSP limitation: https://www.home-assistant.io/blog/2026/07/31/a-big-win-for-android-interoperability/


## Implementation update — 2026-11-20

The first runtime prototype is now present on the cumulative integration branch:

- It uses Sherpa ONNX's own Silero VAD API and a separately downloaded, size- and SHA-256-pinned `silero_vad.onnx` model. This avoids introducing a second native runtime at this stage.
- Silero evaluates 512-sample windows at 16 kHz. When speech is detected, the gate replays a bounded 800 ms pre-roll to the existing BPE KWS stream, then forwards live audio through a 600 ms trailing-silence window.
- The optional gate is enabled only in DEBUG builds. RELEASE builds retain the current continuous Sherpa path.
- Download, native initialization, and runtime inference failures fall back to continuous Sherpa; they must not turn wake listening off.
- JVM tests cover silence suppression, chronological pre-roll, bounded buffering, trailing-silence closure, reset, and VAD counters.
- The 60-second diagnostic now includes total worker CPU, KWS decode wall time, VAD window count, speech-window count, and speech-window ratio. It records no audio or transcript.

This is an implementation prototype, **not evidence of battery savings**. Silero itself runs continuously, so its inference cost must be compared against the KWS work it suppresses. CI and physical-device acoustic/energy validation remain mandatory. The earlier Speex-first proposal is deferred: do not add a second native VAD until this simpler Sherpa-native experiment is measured and shown insufficient.
