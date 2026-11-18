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
3. Keep the visual confirmation brief (target 4 seconds by default, adjustable within 3–5 seconds) and do not acquire a CPU WakeLock merely to keep the display visible.
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


## Display and screen-off conversation policy

The display is feedback only; it must never be required for the Live audio session.

- Wake confirmation target: show the localized assistant label and a short listening confirmation for 4 seconds by default (user-adjustable later, sensible range 3–5 seconds). Use the selected app language, not a hard-coded Italian or Spanish string.
- When the confirmation expires, dismiss only the temporary visual UI and return to the prior keyguard/display state. Do not stop Gemini Live and do not keep the display awake.
- During the conversation, do not set `FLAG_KEEP_SCREEN_ON`. The user may press the physical power button or allow the normal system timeout to turn the display off; audio continues only under the existing explicit background-continuation consent and foreground microphone-service policy.
- A screen-off event hides the call/avatar UI but must not be interpreted as a conversation-end event. Do not let UI dismissal invoke the Live stop path.
- A touch on the sleeping display is handled by Android/Samsung: it wakes the lock screen. It must not restart Live or re-open the microphone. After unlock, tapping Juanita's ongoing-conversation notification or the app icon returns to the active conversation UI without reconnecting or interrupting the audio stream.
- When the conversation actually ends, stop Live and release its audio resources. Re-arm offline KWS only if the user has explicitly left wake listening enabled and all microphone teardown has been confirmed.
- No WakeLock is acquired to illuminate or hold the display. The existing bounded/consent-governed partial CPU WakeLock for background audio is a separate concern and must be measured; it must never be a screen-brightness/display WakeLock.

Acceptance tests:
- Wake confirmation appears in each supported UI language and disappears after the configured short interval without ending Live.
- Screen turns off during Live: microphone/audio session remains healthy under explicit consent, display remains off, and no screen-on flag is held.
- Tap/raise-to-wake: Android lock screen appears; the conversation continues. Unlock and open the ongoing Juanita notification/app: the same session UI returns, with no second AudioRecord and no Live reconnect.
- End Live while screen is off: audio tears down, then KWS re-arms only if opted in.
- Repeat on Note10+ Android 12 and S22 Ultra Android 15; record display behavior, CPU WakeLock duration, battery, temperature, and audio continuity.

## Implementation status — 2026-11-18 22:40

**Runtime integration is present in PR #58 source; it is not yet release-accepted.**

- `JuanitaVoiceService` now handles explicit enable/disable, starts the microphone foreground service, provisions the pinned model, creates the detector, and owns the handoff to Gemini Live.
- Sherpa closes its stream and AudioRecord before invoking the detection callback. The service closes the detector before starting Live. When Live shutdown confirms microphone capture and session teardown, the service re-arms the detector only if opt-in remains enabled.
- The Settings control is default OFF, requires RECORD_AUDIO, displays the supported phrases, and stops the current listener if the phrase is edited or the selected identity changes.
- Supported phrases are currently limited to `Hola Juanita` and `Hola José`. Canonical SentencePiece output is validated in CI and pinned by SHA-256; arbitrary user-entered phrases are not supported.
- The model is downloaded only after explicit opt-in. Five model/tokenizer assets are pinned by revision, size and SHA-256. The installer checks free storage, atomically installs the model and writes the selected keyword file. The foreground notification reports download progress.
- The current wake feedback is a notification update. Do not assume Android will launch an Activity over the lock screen or illuminate the display from the background. The 4-second visual confirmation remains a target for a permitted foreground UI path, not a guaranteed display behavior.
- The current exact head must pass CI. A prior head passed APK build, JVM tests and model/tokenizer validation, but its API 31 instrumentation job was cancelled when newer commits superseded it.
- No physical acceptance has been completed on Note10+ Android 12 or S22 Ultra Android 15.

## Current blockers

1. **Exact-head CI:** run and pass debug build, JVM tests, instrumentation compilation, API 31 instrumentation, package identity checks and both tokenizer validations on the latest PR #58 head.
2. **Physical microphone ownership:** test repeated enable/disable, rapid stop, permission revocation, wake detection, KWS-to-Live handoff and Live-to-KWS re-arm on both Samsung devices. Confirm no simultaneous AudioRecord owners and no stuck microphone.
3. **Background and screen-off:** test Home/background and display-off operation, notification visibility, Android 12/15 restrictions, removal from recents, return from notification, and the existing explicit background-continuation consent. Task removal is not equivalent to pressing Home.
4. **Acoustic quality:** measure false accepts and misses for both supported phrases across speakers, distances, speeds, room noise and distractor phrases. The model card's published benchmark does not validate these two phrases.
5. **Power and thermal acceptance:** measure idle CPU, RAM/GC, wake latency, battery percentage/temperature and overnight drain with wake OFF versus ON. Do not claim low-power DSP behavior or improved battery life before measurements.
6. **Phrase enrollment:** the optional 5–8 repetition suitability check, sample discard verification and threshold guidance are not yet implemented. No raw recording may be retained or uploaded.
7. **Visual confirmation:** a localized, short-lived foreground confirmation screen is not yet accepted. When Android blocks background Activity launch, update the existing notification and do not retry-launch, use a full-screen intent, or hold a screen WakeLock.
8. **General custom phrases:** only `Hola Juanita` and `Hola José` are supported in this build. Extending to arbitrary phrases requires canonical SentencePiece generation, vocabulary checks and new acoustic validation before exposing them as selectable.

These are acceptance gates, not a claim that the feature is already reliable on physical hardware.
