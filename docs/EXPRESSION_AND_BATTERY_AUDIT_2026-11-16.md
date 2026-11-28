# Juanita AI — Conversational Expression and Battery Audit
Date: 2026-11-16
Base: cumulative experimental branch at `243d70b9fa36e68d6e614c92ad363b661f4fd247`

## Conversational expression audit

### Finding
The app has two prompt paths. The legacy `PersonaProfile.toSystemInstruction()` already contains detailed human-conversation and contextual-emotion guidance. The selected encrypted `DigitalIdentity.toSystemInstruction()` path, used for the user's active character, previously included the 19 personality values as percentages and a basic pacing instruction, but did not carry equivalent operational guidance for turning those values into natural conversational choices and spoken delivery.

This explains why the editor could expose expressiveness, empathy, warmth and spontaneity without reliably making the selected character sound less formulaic.

### Change
The digital-identity prompt now includes a compact active expression section:
- infer conversational intent cautiously from the current message and recent context;
- choose whether to listen, reassure, celebrate, joke, explain or answer directly;
- express personality traits through concrete language choices instead of repeating slider values;
- vary sentence openings and avoid stock phrases and automatic follow-up questions;
- guide spoken rhythm, emphasis, pauses and intonation according to the response intention;
- avoid stage directions, fake intimacy, forced emotion and claims of human consciousness;
- explicitly acknowledge that actual prosody remains bounded by the selected Gemini voice/model.

No extra model call, service, API, paid feature, additional permission or background task is introduced. The selected voice and Live audio transport remain unchanged.

## Battery and power audit

### Findings from source
- The partial CPU wake lock is acquired only when an explicitly requested Live voice session starts. It is required to keep background audio functioning when the display turns off. It is released on confirmed shutdown, startup failure and service destruction. A bounded 15-minute timeout is renewed every five minutes as a safety mechanism; this is intentionally retained to avoid breaking screen-off conversation or losing the fail-safe.
- The standard Live path uses the Firebase SDK audio conversation. The regional TTS path owns one continuous AudioRecord and streams microphone frames while the conversation is active. Continuous capture/network/audio processing is the expected dominant cost during an active voice conversation; it must not be disabled or throttled in a way that breaks speech.
- The Android device-wide active-recording diagnostic previously queried AudioManager every 5 seconds. It is advisory rather than required for audio operation and is now sampled every 30 seconds. It still stops with the voice service.
- The Live pipeline health monitor previously checked every 2.5 seconds. It now checks every 5 seconds, retaining automatic recovery while reducing periodic wakeups. A real mic regression must still be checked on the physical Note10+.
- Location is a one-shot request at conversation start (up to 15 seconds), with no continuous background location updates or location history.
- Thermal protection is event-driven through Android's thermal status listener, not a polling loop.
- No always-on microphone, continuous GPS, screen-on lock or new background worker was added by this change.

### Limits
Source inspection cannot establish actual battery drain in mAh or percentage per hour. The app cannot reliably attribute total handset battery consumption to itself from ordinary runtime APIs. A physical comparison is required.

### Required device acceptance
On the Galaxy Note10+ / Android 12, compare equal-duration sessions under similar signal, volume and brightness:
1. App closed / no voice session.
2. App open and idle, no voice session.
3. Standard Gemini Live conversation, screen on.
4. Standard Gemini Live conversation, screen off.
5. Regional TTS conversation only if that mode is later re-enabled for testing.

Record Samsung Settings > Battery usage before and after each 30-minute interval, screen-on/off state, network type/signal, volume, device temperature and whether the mic remained responsive. Repeat each case at least twice. Do not claim battery optimization is proven until these measurements are available.

## Scope and safety
- Experimental branch only; stable release remains untouched.
- No Firebase Blaze, paid API, external billing, new permission or new dependency.
- No change to microphone ownership, audio routing, wake-lock lifetime, reconnect limits or selected voice.
- Build, JVM tests, Android instrumentation and physical-device voice/battery tests are still required before acceptance.
