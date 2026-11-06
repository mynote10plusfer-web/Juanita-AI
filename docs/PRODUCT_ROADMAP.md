# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-06 — 0.9.2 automatic foreground GPS context

## Latest implementation update — 0.9.2 (automatic foreground GPS context)

- Removed the weather-only screen and Open-Meteo request from the GPS feature.
- Android asks for foreground location permission on the first app launch, using the standard Android runtime permission prompt (precise or approximate).
- When permission is granted, the app reads the current device location automatically while in use and refreshes it before each Gemini Live session.
- The current locality, coordinates, estimated accuracy and age of the fix are passed to Gemini Live as contextual information, so Juanita can use location for local information, nearby places, distances and travel questions.
- Location is not continuously tracked in the background and no location history or persistent coordinates are stored by the app.
- If permission is denied or a fix is unavailable, Juanita must not pretend to know the user's location.
- Physical-device GPS testing is required; GPS quality depends on Android permission choice, device settings, satellite reception and network conditions.

## Previous implementation update — 0.9.0 (camera integration)\n\n- CameraX preview supports front and rear cameras, one-shot image sending and optional low-rate live frames to the active Gemini Live session.\n- Camera is not activated until the user opens/enables it. Cloud frames are sent only while requested; the app does not archive raw camera frames.\n- Physical-device camera tests remain pending.\n\n## Previous implementation update — 0.8.8 (memory and privacy controls)\n\n- Added a dedicated “Memoria e privacy” screen section in the Android app.\n- Personal memories can be listed, edited, deleted individually, or cleared as a group after confirmation.\n- Conversation archive controls are separate: opt-in toggle, retention setting (manual/7/30/90 days), per-conversation deletion and clear-archive action, each with confirmation for destructive operations.\n- Clearing personal memories does not clear conversation history; clearing the archive does not clear personal memories.\n- Gemini Live transcript capture is connected in 0.8.9: user and assistant text can be stored in the local archive only when the user explicitly enables it. Raw audio is never archived. Automatic personal-memory extraction remains pending.\n\n## Previous implementation update — 0.8.6 (foreground audio service)

- Added `JuanitaVoiceService`, an Android microphone foreground service that owns the Gemini Live assistant lifecycle independently of MainActivity.
- The user starts the service explicitly from the visible app after granting RECORD_AUDIO permission.
- The service promotes itself to foreground immediately, declares the microphone service type, and shows an ongoing notification with a Stop action.
- MainActivity now observes service state through StateFlow and no longer owns/stops the Live session when the Activity is destroyed.
- The known-good Firebase SDK-managed audio path and `enableInterruptions = false` remain unchanged.
- The user confirmed on the physical Samsung Galaxy Note 10+ that Live conversation continues with the display off. Long pauses, incoming calls, network loss and repeated long sessions remain to be tested.
- Foreground service improves Android background survival but cannot guarantee uninterrupted operation under every OEM battery policy or network condition.
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
- Encrypted on-device manual memories exist. The 0.8.8 UI supports listing, editing, deleting individual memories and clearing all personal memories. Separate archive controls expose opt-in, retention, per-conversation deletion and clear-archive. Live transcript capture remains pending; archive entries will remain empty until that connection is implemented.
- Conversation archive consent toggle exists, OFF by default; Live text transcript capture is wired in 0.8.9, with retention/deletion management available. Automatic personal-memory extraction remains pending.
- Existing photorealistic Juanita portrait is displayed in an avatar card; animated face and lip sync are not implemented.
- Wake phrase field exists, but wake-word detection is not connected.
- Android TTS preview exists; Gemini Live voice is selectable among seven documented female voices (Kore, Aoede, Sulafat, Achernar, Vindemiatrix, Zephyr, Pulcherrima) and the choice is persisted locally. A voice change applies on the next Live session; no in-session voice switching or dedicated preview has been implemented yet.
- Debug and permanently signed release GitHub Actions workflows exist.

## Functional requirements

### A. Conversation continuity and reliability — highest priority
- Keep voice conversation stable during long turns and pauses.
- [IMPLEMENTED IN 0.8.6 — DEVICE TEST PENDING] Support screen-off/background conversation through a properly owned microphone foreground service, started only from a user-visible action after permission is granted.
- [IMPLEMENTED IN 0.8.6 — DEVICE TEST PENDING] Persistent notification while microphone is active, with a Stop action.
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
- [IMPLEMENTED IN 0.9.2 — DEVICE TEST PENDING] Request foreground location permission on first app launch; automatically read current location while app is in use and before each Gemini Live session.
- [IMPLEMENTED IN 0.9.2 — DEVICE TEST PENDING] Pass current locality and coordinates into the active Gemini Live session for location-aware answers about nearby places, distances and travel.
- Do not continuously track location by default; do not store location history or persistent coordinates.
- Current weather, forecasts, rain, wind and local official weather alerts require a separate live-data integration and are not part of the GPS permission feature.
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
- [FOUNDATION ADDED IN avatar-call-foundation BRANCH] Portable avatar profile model and private local metadata store support description/photo-reference/random sources, photorealistic/realistic 3D/stylized 3D/cartoon styles, motion presets and an active profile selection. This stores profile metadata only; it does not generate or animate models yet.
- Build a WhatsApp-style call screen: large Juanita avatar, user/interlocutor camera preview in a small picture-in-picture tile, camera/mic controls and clear end-call action.
- Avatar Studio UI: description editor, Android Photo Picker, randomize controls, style selection, preview, save/select/delete.
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

1. **Reliability foundation:** foreground-service lifecycle and Stop notification implemented in 0.8.6; next add detailed live status/transcript and verify screen-off behavior, reconnects and repeated long conversations on the physical device.
2. **Privacy foundation:** local archive APIs and memory/privacy UI implemented in 0.8.8. Personal memories can be edited/deleted independently; archive consent, retention and deletion controls are exposed. Actual Gemini Live transcript capture remains pending; archive remains opt-in and no transcript is currently captured automatically.
3. **Personal onboarding:** gradual “Conosciamoci”, profile editor, explicit save/forget controls, non-intrusive follow-up.
4. **Voice/personality:** selectable supported Live voices and persistent choice (voice selector implemented in 0.8.5); personality presets and safe apply/restart remain pending.
5. **Daily utilities:** foreground GPS context implemented in 0.9.2 (physical-device test pending); next build separate live-data integrations for weather/local alerts, then local alarms/reminders, morning briefing and calendar.
6. **Notification companion:** opt-in notification listener, per-app allowlist, read-on-request, confirmed replies.
7. **Avatar:** profile model/local store foundation added on `feature/avatar-call-foundation`; next Avatar Studio UI and bundled, openly licensed model assets, then adaptive WebView renderer + CameraX picture-in-picture call screen; finally PCM/viseme lip sync after a supported Gemini audio-output bridge is verified.
8. **Extended integrations:** motorcycle/travel, translation, smart home and other capabilities.

## Definition of done for every phase

- Code committed to GitHub with a clear commit message.
- Debug and signed release workflows checked; report actual run status and artifact availability.
- No claim of implementation based on documentation alone.
- Test permission denied, feature disabled, offline, app restart, screen off and deletion paths as applicable.
- Update this roadmap's implementation status and known limitations.


Avatar architecture details: see [AVATAR_VIDEO_CALL_ARCHITECTURE.md](AVATAR_VIDEO_CALL_ARCHITECTURE.md). The profile foundation is not a finished avatar generator or video-call feature; keep implementation status explicit.
