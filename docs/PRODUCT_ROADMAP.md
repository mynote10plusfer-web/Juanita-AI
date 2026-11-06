# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-06

## Latest implementation update — 0.8.5

- Added a local preference for the Gemini Live response voice.
- Added a selector in the app for seven documented female voices; the selected voice is passed into the Live model configuration when a new session starts.
- The existing stable SDK-managed audio path and interruption setting are unchanged.
- Next engineering priority remains the microphone foreground-service lifecycle and screen-off reliability. Voice selection has not yet been verified on the physical device; GitHub Actions build results must be checked before distributing the APK.
Repository: https://github.com/mynote10plusfer-web/Juanita-AI

This document is the canonical product checklist. Keep it updated whenever scope, implementation status, or user-approved constraints change. A requirement listed here is not a claim that the feature is already implemented.

## Product vision

Juanita should feel like a warm, expressive, multilingual personal voice assistant and a familiar conversational presence: she should get to know the user gradually, remember only what the user permits, take useful initiative without being intrusive, and help with practical daily tasks. She is software, not a conscious person; do not claim otherwise.

## Non-negotiable constraints

- Initial trial must remain on Firebase Spark/free tier, Firebase AI Logic and Gemini Developer API.
- Do NOT enable billing/Blaze, add a payment method, use Vertex AI / Agent Platform, paid APIs, paid hosting or paid GPU services without fresh, explicit user approval.
- Respect Google's free-tier quotas and data-processing terms accepted for the trial.
- No raw audio archive in the app. Live audio is streamed to Gemini for the active conversation.
- Conversation archive stays OFF by default. All storage and permissions must be transparent, opt-in and user-controllable.
- Prefer on-device/private app storage for conversations and personal memories. Do not upload them to Firebase or third-party services by default.
- Keep personal memory separate from the conversation transcript archive; each must be independently viewable, editable and deletable.
- User works from an Android phone (Samsung Galaxy Note 10+, Android 12); instructions must be phone-friendly and step-by-step.
- Preserve the permanent release signing keystore and existing GitHub Actions secrets. Never regenerate or replace the signing key for routine updates.
- Preserve the known-good Gemini Live audio path: SDK-managed audio, default server VAD, interruptions disabled unless separately tested. Avoid regressions and verify builds before claiming success.
- Do not ask the user to upload the existing Juanita portrait again; it is already available in project context. Preserve that exact face.

## Existing implementation (verify against code before changing)

- Kotlin + Jetpack Compose Android app; package `ai.juanita.app`.
- Firebase AI Logic / Gemini Developer API, Live model `gemini-3.1-flash-live-preview`.
- Stable audio path currently uses `startAudioConversation(enableInterruptions = false)`.
- Microphone permission and explicit start/stop controls.
- Language and regional profile selectors (Italian, Spanish, Czech, English, Colombian Spanish; experimental Sicilian, Canarian and Paisa profiles).
- Nine personality sliders are saved locally and included in the system instruction when a Live session starts. They do not dynamically change an already-running session; apply changes on next session/restart.
- Encrypted on-device manual memories exist.
- Conversation archive consent toggle exists, OFF by default; automatic transcript archive and retention/deletion management are not complete.
- Existing photorealistic Juanita portrait is displayed in an avatar card; animated face and lip sync are not implemented.
- Wake phrase field exists, but wake-word detection is not connected.
- Android TTS preview exists; Gemini Live voice is selectable among seven documented female voices (Kore, Aoede, Sulafat, Achernar, Vindemiatrix, Zephyr, Pulcherrima) and the choice is persisted locally. A voice change applies on the next Live session; no in-session voice switching or dedicated preview has been implemented yet.
- Debug and permanently signed release GitHub Actions workflows exist.

## Functional requirements

### A. Conversation continuity and reliability — highest priority
- Keep voice conversation stable during long turns and pauses.
- Support screen-off/background conversation through a properly owned microphone foreground service, started only from a user-visible action after permission is granted.
- Persistent notification while microphone is active, with clear status and a Stop action.
- Reconnection/error state, lifecycle handling and safe stop/release.
- Visible app states: ready/listening, user speaking (when measurable), Juanita speaking, processing, reconnecting, disconnected.
- Transcript display for user and assistant where supported.
- Do not use an indefinite wake lock; profile before adding one.
- Keep SDK-managed audio and stable VAD while first implementing lifecycle/background reliability.

### B. Privacy, conversation history and memory
- Conversation transcript archive OFF by default and opt-in.
- Store transcripts locally in private app storage, not cloud by default.
- Retention choices: never save, 7 days, 30 days, 90 days, or keep until manually deleted.
- Delete one conversation, a selected calendar day, a date range, or all conversation history.
- Automatic expiry must actually delete expired local records.
- Separate personal memory from raw conversation history.
- Personal memory controls: inspect, add, edit/correct, delete individual memory, clear all personal memory.
- Memory categories can include name, language, interests, preferences, routines, projects and important people only when voluntarily shared.
- Distinguish temporary context from facts the user explicitly wants remembered.
- No hidden memory extraction or cloud synchronization. Ask before saving sensitive or intimate information.
- Explain clearly that clearing history and clearing personal memory are separate actions.

### C. Getting to know the user and conversational initiative
- First-run onboarding should be a natural conversation, not a long questionnaire.
- Juanita may gradually ask the user's preferred name, languages, location (optional), interests, hobbies, routines, communication preferences and goals.
- Ask one natural question at a time; do not repeatedly ask for information already supplied.
- User can skip, correct, inspect or delete anything.
- Follow up naturally on previously discussed plans or events when useful.
- Offer spontaneous conversation and check-ins only when enabled; never become intrusive or pretend to have feelings/consciousness.
- Add a “Conosciamoci” guided mode and a user-controlled initiative level.

### D. Voice, language and personality
- Selectable Gemini Live voices, persisted locally, with sample/preview where technically supported.
- Keep voice identity independent from language/dialect profile.
- Adjustable warmth, pitch/style where the provider/device actually supports it; never promise unsupported pitch controls.
- Preserve multilingual automatic conversation and existing regional profiles.
- Personality sliders and presets (e.g. warm, playful, calm, professional); changes applied safely at session restart.
- Clear distinction between model-generated voice and Android system TTS preview.

### E. Location, weather and alerts
- Request foreground location only when needed and with clear consent; provide manual city/location fallback.
- Do not continuously track location by default.
- Answer current weather, forecast, rain, wind and local official weather alerts for the chosen/current permitted location.
- Optional weather notifications and morning briefing.
- Location history is not required; do not retain GPS coordinates unnecessarily.
- Background location must not be requested unless a specific feature genuinely requires it and the user separately opts in.

### F. Morning alarm, daily briefing and reminders
- User-configurable alarm time, weekdays/repeat pattern, enable/disable, snooze and alarm tone/volume behavior.
- Spoken wake-up greeting with varied wording rather than the same message every day.
- Optional personalized briefing: today's calendar appointments, reminders, weather, and user-approved notifications.
- Alarm must work offline for its basic trigger; AI-generated spoken content may require network and should have a local fallback.
- Android exact-alarm permission and notification/full-screen-intent rules must be respected; provide a reliable fallback if permission is unavailable.
- Reminders: create by voice, schedule, list, edit, complete and delete; notify at the requested time.
- No fabricated calendar events or message content.

### G. Notifications and messaging
- Optional Android NotificationListenerService access, explicitly enabled by the user in system settings.
- User-configurable allowed apps and privacy filters; do not read sensitive notification content by default.
- Announce sender/app and message availability; ask “Vuoi che te lo legga?” before reading full content.
- Voice reply: transcribe/draft, read back, ask confirmation, then send only through supported notification actions or a supported integration.
- Never send, delete, mark read or interact with messages without user intent and confirmation.
- WhatsApp/SMS/email capabilities depend on Android/app APIs and permissions; clearly disclose limitations.
- No background surveillance or cloud upload of notifications.

### H. Avatar and expression
- Keep the existing exact photorealistic brunette, green-eyed portrait; do not replace it with a generic 3D character.
- Progressive 2D animation: blinking, gaze, subtle head movement and expressions.
- Speech-synchronized mouth movement; amplitude-based mouth animation may be an initial approximation.
- Real phoneme/viseme lip sync requires access to PCM/timing data and a custom audio pipeline; do not claim true lip sync before implemented and tested.
- Facial expressions should be subtle, coherent and not childish.
- Keep the current stable audio path until an alternative PCM pipeline has been independently validated.

### I. Daily assistant capabilities
- Agenda/calendar integration and spoken appointment briefing.
- Alarms and reminders.
- Travel and motorcycle mode: weather, wind, route/places/stops and practical trip information.
- Translation and multilingual conversation assistance.
- Smart-home integration only with compatible user-selected devices and explicit connection.
- Context-aware suggestions for interests, films, recipes, travel and projects.
- Emotional sensitivity based on conversational context, without claiming certainty or diagnosis.

## Delivery plan (ordered; update status as work lands)

1. **Reliability foundation:** inspect current branch/workflows; implement foreground-service lifecycle for Live audio; visible state and transcript; verify screen-off behavior and repeated long conversations.
2. **Privacy foundation:** local transcript data model, opt-in archive, retention policy, delete by conversation/day/range/all; independently manage encrypted personal memories.
3. **Personal onboarding:** gradual “Conosciamoci”, profile editor, explicit save/forget controls, non-intrusive follow-up.
4. **Voice/personality:** selectable supported Live voices and persistent choice (voice selector implemented in 0.8.5); personality presets and safe apply/restart remain pending.
5. **Daily utilities:** local alarms/reminders first, then morning briefing and calendar; weather/location with manual fallback and opt-in notifications.
6. **Notification companion:** opt-in notification listener, per-app allowlist, read-on-request, confirmed replies.
7. **Avatar:** staged 2D animation, then evaluate PCM/viseme lip sync without sacrificing audio stability.
8. **Extended integrations:** motorcycle/travel, translation, smart home and other capabilities.

## Definition of done for every phase

- Code committed to GitHub with a clear commit message.
- Debug and signed release workflows checked; report actual run status and artifact availability.
- No claim of implementation based on documentation alone.
- Test permission denied, feature disabled, offline, app restart, screen off and deletion paths as applicable.
- Update this roadmap's implementation status and known limitations.
