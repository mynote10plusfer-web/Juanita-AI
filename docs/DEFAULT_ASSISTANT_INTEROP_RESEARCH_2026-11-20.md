# Default Android assistant interoperability — research and decision (2026-11-20)

## User requirement

Preserve Juanita AI as it is: Gemini Live conversation, the selected digital identity, language/voice/personality, encrypted memory, alarms and existing six tabs. Reduce standby battery use by cooperating with the assistant already selected by the Android user, without silently replacing it or making Juanita's normal features depend on a single OEM.

## Executive conclusion

There are three distinct integration models. They must not be conflated.

### 1. Let the existing default assistant wake Juanita (non-invasive handoff)

Android/Google App Actions allow Google Assistant to match a spoken request and launch an activity or deep link in the target app. Juanita can then start its existing Gemini Live session and keep ownership of its own identity, memory and conversation.

- The default assistant performs the wake-word detection and initial intent recognition; Juanita does not need to keep Sherpa/AudioRecord armed while waiting in this mode.
- The integration is an invocation/handoff, not a shared microphone or a callback exposing the assistant's hotword event/audio.
- App Actions are specific to Google Assistant, require capability declarations and Play registration, and are not a universal Android/OEM protocol.
- Custom App Actions currently have important limits: custom intents are en-US only and query patterns expect an explicit invocation such as "open [app] and ...". They do not let an app register an arbitrary always-on hotword like "Hola Juanita".
- Bixby/OEM assistants may offer user-configured routines or app-launch shortcuts, but there is no common third-party API that Juanita can rely on across vendors.

This is a useful optional low-battery entry mode where the user accepts the assistant's own invocation phrase (for example, "Hey Google, open Juanita"). It does not preserve direct "Hola Juanita" as a wake phrase unless the assistant vendor independently supports that exact custom routine/phrase, which must be validated per device and language.

### 2. Make Juanita the Android default assistant (not the requested collaboration)

Android's ASSISTANT role can be requested by an app that handles assist actions or implements a VoiceInteractionService. The selected VoiceInteractionService is kept running by Android and is the system extension point for voice interaction and hotwording.

However:
- Android selects one current global assistant. Choosing Juanita would replace the user's current Google/Bixby/other assistant, not collaborate with it.
- Being the selected VoiceInteractionService does not itself grant every privileged hotword/DSP API.
- The standard low-power AlwaysOnHotwordDetector path depends on keyphrase enrollment, SoundTrigger HAL and hardware support. AOSP documents this architecture, but also says these capabilities may be absent and that the software fallback is a separate audio-capture path.
- Current framework sources mark newer hotword detector creation APIs @SystemApi/@hide and require privileged/system permissions such as MANAGE_HOTWORD_DETECTION. Android 12's platform manifest marks related permissions as signature/internal/preinstalled. The public SDK API diff also shows the old createAlwaysOnHotwordDetector method removed at API 31.
- Therefore we must not add hidden API/reflection calls, privileged permissions, root requirements or assume that selecting Juanita as assistant grants DSP access.

This model is not approved for implementation: it would change the user's default-assistant behavior and still does not provide a dependable public DSP route for a Play-distributed third-party app.

### 3. Future public third-party DSP interoperability

The existing cross-vendor DSP architecture remains the long-term target. Implement only against a public, documented SDK/API and verified runtime capability once the promised interoperability support is actually shipped and available on the device. Do not infer availability from Android version or chipset alone.

## Recommended product architecture

Keep one shared Juanita conversation runtime and multiple invocation providers:

1. **System-assistant handoff (optional):** App Actions/deep-link or documented OEM shortcut launches Juanita into the existing conversation flow. The default assistant owns its own wake phrase and microphone before handoff.
2. **Juanita direct wake-word (current fallback):** explicit opt-in Sherpa-ONNX listener for "Hola Juanita" / "Hola José"; only one microphone owner; stop detector before Gemini Live; re-arm only after confirmed Live shutdown and only while consent remains enabled.
3. **Public DSP backend (future):** use official public interoperability APIs when present; capability-check and fall back without losing the conversation feature.
4. **Manual/app invocation:** existing launcher and in-app controls remain available.

The selector must expose which invocation path is active and why. Do not run the default assistant handoff and Sherpa hotword listener concurrently merely to imitate always-on behavior; that would duplicate audio work and defeat the battery objective.

## Compatibility and user experience

- Android 12–17: optional assistant handoff can be explored where the installed assistant supports it; otherwise the current explicit Sherpa path remains available. No universal cross-OEM App Actions promise.
- Google Assistant: App Actions can invoke an in-app feature after assistant recognition, subject to Play registration, language and availability restrictions.
- Samsung Bixby and other OEM assistants: treat as optional per-vendor/user-configured launch integrations, never a required dependency.
- Future Android versions: public DSP integration only after actual API and device support are verified.
- Do not silently change the system default assistant, request the ASSISTANT role, or alter the user's wake phrase.
- Keep the current six tabs, package IDs, release signing, encrypted data and free-only constraints unchanged.

## Implementation sequence

A. Keep the current Sherpa/Live runtime intact; do not replace it with an unverified API.
B. Add a small invocation contract/deep-link entry point that routes an external assistant invocation into the existing explicit Live start path, with identity snapshot and microphone/session safeguards. It must not start a background microphone merely because an external intent was received.
C. Evaluate Google App Actions only for the Play-distributed app and supported locales; prototype the simplest "open Juanita" handoff first. Do not promise custom "Hola Juanita" hotword registration.
D. Consider OEM routines only after per-device documentation and hands-on validation; no hard dependency.
E. Add a separate future DSP backend only when a public API is released. Maintain Sherpa fallback for older Android versions and devices without DSP.
F. Measure battery in three separate conditions: default-assistant handoff standby (Juanita idle/off), Sherpa standby, and Gemini Live active. Compare screen-off 1/4/8-hour tests on real devices; report battery/temperature and wake accuracy separately.

## Acceptance criteria

- Existing assistant remains the user's default unless the user independently chooses otherwise.
- In handoff mode Juanita does not keep a parallel Sherpa AudioRecord open while idle.
- An external invocation starts only the requested Juanita UI/session; no silent background mic start and no data loss.
- Identity, language, voice and conversation continuity follow the existing source of truth.
- When the handoff provider is unavailable, the user can still use Juanita's current direct wake-word or manual launch.
- No hidden APIs, privileged permissions, reflection, root, paid service, billing or new Firebase tier.
- Tests cover duplicate intents, cold/warm app launch, stale identity, permission denial, session already active, microphone ownership and orderly teardown.
- Physical tests document actual default-assistant provider, OS version, language, battery drain and whether the invocation phrase is supported.

## Official references

- Android VoiceInteractionService lifecycle and role: https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- AOSP Android roles and ASSISTANT requirements: https://source.android.com/docs/core/permissions/android-roles
- AOSP DSP hotword implementation and enrollment requirements: https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development
- AOSP Android 12 manifest restrictions on hotword permissions: https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/res/AndroidManifest.xml
- Android 12/API 31 VoiceInteractionService API diff: https://developer.android.com/sdk/api_diff/31/changes/android.service.voice.VoiceInteractionService
- Google App Actions overview: https://developer.android.com/develop/devices/assistant/overview
- Google App Actions custom intent restrictions: https://developer.android.com/develop/devices/assistant/custom-intents
- Google App Actions schema: https://developer.android.com/develop/devices/assistant/action-schema

## Status

RESEARCH COMPLETE; DESIGN DECISION RECORDED; NO RUNTIME CODE CHANGED BY THIS DOCUMENT. The existing Sherpa listener remains the current direct wake-word implementation. No DSP saving or cross-assistant wake-word sharing is claimed.
