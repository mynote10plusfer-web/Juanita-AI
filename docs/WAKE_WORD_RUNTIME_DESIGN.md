# Offline wake-word runtime design

Status: implementation decision for PR #58; not yet an enabled runtime feature.

## User-visible contract

- The selected phrase, not the speaker, triggers activation.
- No speaker identification, enrollment, voiceprint, speaker embedding, or biometric template is part of wake-word activation.
- The wake detector runs locally. Gemini Live is not connected while idle.
- Microphone ownership is serialized: the detector must release AudioRecord before Gemini Live starts. After Live has fully stopped, re-arm only while the user opt-in remains enabled.
- The user can disable listening at any time. No indefinite partial WakeLock is permitted.
- Raw PCM from phrase setup is processed in memory and discarded. No audio samples are uploaded, persisted, or logged.
- Repetition samples may measure suitability and help select a supported threshold only. Do not claim model adaptation unless a real, validated adaptation mechanism is implemented.

## Model and tokenizer decision

Candidate model: `jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08` (Apache-2.0 model card). It is a Spanish streaming open-vocabulary KWS model. The model card describes runtime-customizable keywords by generating `keywords.txt` from raw phrases with sherpa-onnx `text2token`, using the model's own `tokens.txt` and `bpe.model`.

The app MUST NOT pass the raw user phrase to `KeywordSpotter.createStream(phrase)`. For BPE models, sherpa-onnx expects BPE pieces separated by spaces, and multiple keywords in the documented format. A whitespace split, character split, or hand-written approximate tokenizer is not acceptable.

Use the canonical SentencePiece implementation compatible with the exact downloaded `bpe.model` to encode the selected phrase. The output must be validated against the model vocabulary and serialized into a private app-internal `keywords.txt` file. Then configure Sherpa with that file and create its stream using the no-argument/default keyword path. Write the file atomically; bind it to a normalized phrase plus model revision/hash so a phrase or model change invalidates stale tokenization.

If the canonical tokenizer cannot be built and tested for supported Android ABIs, fail closed and keep wake activation disabled. Do not ship a detector that silently falls back to raw text.

## Provisioning

- Model files are not bundled in the APK. Download only after the user explicitly enables setup, preferably on Wi-Fi with a clear size estimate and progress.
- Pin a repository revision and verify SHA-256 for every model/tokenizer file before atomic installation in app-private storage.
- Check available storage, free space after download, and integrity before enabling.
- A failed or interrupted download must leave the previous valid model untouched and must not arm the microphone.
- Offline operation begins only after successful provisioning; no cloud recognition or paid API is involved.

## Service and audio ownership

Keep a single explicit state machine for the opt-in wake listener and Gemini Live. The transition to Live is allowed only after the detector's worker has stopped, AudioRecord has been stopped/released, and the KWS stream is released. Do not start a second recorder on a timeout; remain fail-closed and report the error. When Live ends, wait for confirmed SDK microphone teardown before re-arming the offline detector. An explicit user stop, permission revocation, service destruction, thermal critical state, or wake-listener opt-out cancels re-arming.

The idle listener requires a visible foreground-service notification and runtime RECORD_AUDIO permission. Do not silently restart after reboot. Do not use an indefinite WakeLock. Android background-start restrictions must be checked against the exact start path and OS version.

## Phrase setup and acceptance

- Validate phrase length, language, and vocabulary coverage before arming.
- Let the user record 5–8 natural repetitions to test recognition and threshold suitability; do not retain the raw samples.
- Test the selected phrase spoken by multiple people, at different speeds and intonations, with realistic room noise, plus distractor phrases.
- Report unsupported phrases honestly rather than silently substituting another phrase.
- Test false accepts and misses over a documented corpus, and on the user's Note10+ (Android 12) before making battery or reliability claims.
- Measure idle CPU, RAM/GC, battery drain, device temperature, microphone restarts, and wake latency. Repeat on S22 Ultra after Note10+.
- Add regression tests for phrase changes, stale token files, corrupted model assets, permission loss, rapid start/stop, detector-to-Live handoff, Live-to-detector re-arm, and explicit opt-out.


## Wake feedback on a sleeping display

Desired interaction after a confirmed acoustic hit:

1. Release the KWS stream and microphone first; only then request visual feedback and hand off to Gemini Live.
2. Show a minimal dark confirmation screen using the configured assistant name, e.g. `JUANITA ON` and `Ti ascolto…`. The phrase detector must not open Gemini Live until microphone ownership is confirmed.
3. Keep the visual confirmation brief (target about 2 seconds) and do not acquire a CPU WakeLock merely to keep the display visible.
4. If a Live conversation begins, hand over to the normal call/conversation UI and its existing screen policy. If no conversation starts, dismiss the confirmation and let Android's keyguard/display timeout resume.

Platform constraints:
- A normal app cannot reliably launch an Activity from the background on every Android version. Android 10+ background-activity-launch restrictions apply even to many foreground-service scenarios. Do not assume that running a foreground service grants permission to pop an Activity over the lock screen.
- Do not use a full-screen notification intent for wake-word feedback. Android 14+ reserves automatic full-screen-intent access for qualifying alarm/calling core use cases; wake-word feedback is not to be misrepresented as an alarm or call.
- `Activity.setTurnScreenOn(true)` and `setShowWhenLocked(true)` are the preferred Activity-window mechanisms once an Activity launch is legitimately allowed. They do not grant a background-launch exemption.
- An ordinary app cannot force the physical display to turn off after an arbitrary two-second delay. Closing the temporary Activity returns to the keyguard/previous task; the actual display-off moment remains governed by system/OEM policy and the user's screen timeout. Never claim otherwise.
- The deprecated `ACQUIRE_CAUSES_WAKEUP` flag is not the default implementation path. Any device-specific experiment must be isolated, time-bounded, opt-in, and tested against Android 12 and 15 before consideration.

Fallback when the OS refuses a background Activity launch: update the already-visible foreground-service notification with “Juanita attiva”; do not retry-launch repeatedly, acquire a long WakeLock, or bypass Android policy. Record whether the screen was off, whether the activity/notification appeared, and the device/OS version in local diagnostics without recording audio.

Acceptance tests:
- Screen off + valid phrase on Note10+ Android 12: determine whether the permitted wake-feedback path can light the display and show the confirmation.
- Screen off + valid phrase on S22 Ultra Android 15: confirm graceful fallback if background launch is blocked.
- Invalid phrase/distractor: no display wake, no Live connection.
- Confirmation dismissal: no persistent screen-on flag or WakeLock; keyguard and normal timeout behavior resume.
- Screen already on: avoid stealing focus unnecessarily; use a lightweight in-app status update instead.

## Current blockers

1. The existing detector is not started by the app.
2. The current detector supplies raw phrase text to a BPE keyword spotter; this is not a valid BPE encoding path.
3. Model files and checksum-pinned provisioning are absent.
4. There is no opt-in settings control, foreground idle-listener state, or serialized Live handoff yet.
5. No physical-device battery/thermal/false-trigger validation has been performed.\n6. The wake-feedback Activity/notification path is not implemented or device-validated; immediate forced display-off is not an app capability.

This document is a design gate, not a claim that wake activation is implemented.
