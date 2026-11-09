# Juanita AI

Android voice assistant for natural multilingual, multi-turn conversations, configurable personality and regional speech profiles.

## Current development candidate: 0.11.7 (versionCode 46) — PR #31, draft

The cumulative 0.11.7 candidate retains the local multi-alarm manager, Gemini Live morning greeting, progressive personal profile, 30 gender-grouped Live voices and Juanita/José identity choice. This maintenance release fixes an alarm-owned Live session that could survive alarm dismissal and keep an indefinite partial CPU wake lock; it also closes stale full-screen alarm UI and stops periodic home, avatar and archive refresh loops while the app is backgrounded. It stops a failed Live reconnect from leaving the foreground service and wake lock running. Source changes are committed; fresh signed Release CI, instrumentation and physical Samsung tests are required before distribution. Do not install intermediate artifacts.

## Historical release: 0.10.4 (versionCode 33)

This candidate adds opt-in Android notification reading, increments versionCode, and keeps the permanent production application ID and signing certificate unchanged. Debug builds use the isolated package ai.juanita.app.debug and a separate Firebase variant configuration. Only the signed Release APK is suitable for updating the production app.

- Kotlin + Jetpack Compose Android app.
- Firebase AI Logic with Gemini Developer API and Gemini Live audio model `gemini-3.1-flash-live-preview`.
- Bidirectional microphone/audio conversation with explicit start/stop and microphone permission.
- Gemini Live session owned by an Android microphone foreground service with an ongoing notification and Stop action; screen-off reliability is pending physical-device verification.
- Configurable assistant name, wake phrase setting (wake-word engine not yet connected), and nine persistent personality controls.
- Separate language and regional profile selectors.
- Experimental regional profiles: Sicilian (Palermo area), Canarian Spanish, and Colombian Paisa (Antioquia/Medellin).
- Regional instructions are included in the Gemini Live system prompt. This guides language and vocabulary but does not guarantee a native regional voice.
- No Android system TextToSpeech playback is used. The configured Gemini Live voice is used for live conversations; standalone voice preview and notification read-aloud remain deferred until they can use that same voice path.
- Gemini Live response voice selector with seven documented female voices; selection is saved locally and applied when a new Live session starts.
- Android speech-recognition intent remains available as a separate microphone test.
- Android requests foreground location permission on first launch (precise GPS or approximate location). It reads a fresh device location automatically on app launch and again before each Gemini Live session, then includes the locality and coordinates in that session context. No weather screen/provider, background tracking, or persistent coordinate history.
- Home-screen shortcut opens the Memory & Privacy manager directly; the manager supports editing/deleting individual or selected personal memories, clearing all personal memories, deleting individual archived conversations, clearing the archive and deleting archived turns by inclusive local date range.
- Android Keystore-backed AES-256-GCM memory. Full text transcripts from both sides of every Gemini Live conversation are archived automatically by default, locally, in encrypted per-session files; archive survives app/process restarts and is independent of curated personal facts. Raw microphone audio is never archived. Archive can be paused, assigned manual/7/30/90-day retention, and individual conversations can be deleted; manual retention is the default. The app restores the latest 60 archived turns plus curated facts and a grounded autobiographical continuity profile into a new Live session (bounded by the model context budget). A Gemini Live function tool can search older archived sessions on demand and return a small relevant excerpt before answering. Automatic curated personal-memory extraction is enabled by default, separately, and uses a retryable encrypted queue; it now captures meaningful non-sensitive experiences, milestones, relevant people/context and project decisions as well as stable facts. Extraction still requires successful Gemini transcription and a successful model response; inspect the in-app memory status and memory list to verify each step. A local lexical archive-search tool now lets Gemini retrieve relevant older conversations before answering; it ranks phrase/token matches and recency. Vector/embedding-based multilingual semantic search and a dedicated editable biography screen remain future work.
- CameraX front/rear camera preview and optional 1 FPS image frames to the active Gemini Live session are implemented in 0.9.0; physical-device testing is pending.
- The Altro tab includes a local capability catalogue and Improvement Lab. User-authored engineering reports are encrypted on-device, can be previewed, exported as ordinary UTF-8 .txt files and deleted. Automatic gap detection is a later phase.
- GitHub Actions builds a debug APK and a permanently signed release APK.

## AI provider and cost constraints

The user selected Firebase Spark/free tier with Firebase AI Logic and Gemini Developer API for the initial trial. Billing, Blaze, Vertex AI and Agent Platform must remain disabled unless the user explicitly approves a change. Gemini Live is a preview feature and free-tier availability is subject to Google's current quotas and terms.

## App Check

The Android app initializes Firebase App Check. Debug builds use the Firebase App Check debug provider; release builds use Play Integrity. The debug provider requires registering its generated debug token in Firebase Console. Release App Check registration and distribution settings must be completed before distributing the signed release build. Do not publish debug tokens, signing credentials or private keys.

For a pre-release conversation test, prefer the debug build and Firebase's documented debug-provider flow. A production Play Integrity registration is still required before distributing the signed release build.

## Notification reader — deferred

- Notification reading is excluded from the 0.11.3 sideload candidate after Google Play Protect blocked the 0.11.2 candidate.
- The shipped manifest does not declare the privileged Android notification-listener service; Gemini Live notification tools, notification-reader settings and onboarding actions are removed.
- Historical implementation files may remain in the repository for a later review, but they are not part of the active app experience.
- Do not ask users to disable Play Protect. Revisit only after a compliant distribution/policy path is established; Play Developer registration alone does not guarantee approval.


## Not implemented yet

- Native regional neural voices guaranteed to sound authentically Sicilian, Canarian or Paisa.
- Local familiar-person speaker identification and diarization; Gemini Live transcription alone does not identify a speaker.
- Familiar-person visual profiles and live face matching; ordinary photo memory and biometric recognition are separate features.
- Always-on wake phrase detection (not implemented; the current foreground service is user-started and does not listen for a wake phrase).
- Offline ASR/TTS runtime and model downloads.
- Multilingual semantic/vector retrieval beyond the current on-device lexical archive search; a dedicated editable biography/timeline screen.
- Background location tracking (intentionally not requested).
- SMS, direct WhatsApp account access/replies, email, calendar and intelligent cellular call screening/AI receptionist; each requires separate permissions and platform-feasibility testing. Android notification access is now prototyped in candidate 0.10.4. Android call screening is not yet a two-way caller conversation feature.

## Product roadmap

The canonical feature checklist, privacy requirements and phased implementation plan are maintained in [`docs/PRODUCT_ROADMAP.md`](docs/PRODUCT_ROADMAP.md). This roadmap distinguishes existing functionality from planned work.

## Build

- Debug APK: `.github/workflows/android.yml`
- Permanently signed release APK: `.github/workflows/release-signed.yml`

The release workflow uses the existing permanent GitHub Actions signing secrets. Never replace or regenerate the permanent keystore for routine updates.

## Privacy

The microphone is activated only after the user grants permission and starts a Live session. Audio is streamed to Gemini for real-time conversation and is not stored by this app. Conversation text archiving is on by default and encrypted on-device with Android Keystore AES-GCM; raw audio is never stored. Users can pause or clear the archive in the privacy panel. Manual memories are also encrypted on-device.


## Current engineering work (2026-11-08)

- Smart Alarm: app-owned local multi-alarm scheduling, persistence, controls, recurrence, voice stop and optional Gemini Live morning greeting are implemented in the 0.11.5/0.11.6 candidate. Physical reliability testing on Note10+ Android 12 and S22 Ultra Android 15 is still required.
- Gemini Live audio recovery: PR #24 adds GoAway handling, websocket health checks, bounded reconnection and recent transcript context replay. It must pass CI and real-device testing before being called fixed or included in a Release APK.
- Familiar-person speaker identification remains unimplemented. Voice transcription is not speaker recognition; local enrollment requires explicit consent and privacy/legal review.
- Notification reading remains deferred; do not disable Play Protect.


## Current candidate status — 2026-11-09

- PR #31 remains draft and unmerged. Current cumulative candidate is 0.11.6 / versionCode 45. Keep the production application ID `ai.juanita.app`, permanent signing certificate and all local data unchanged.
- The first 0.11.6 signed Release artifact passed permanent-certificate verification. A final correction aligning José/Juanita identity across the morning alarm greeting, alarm tab and ringing notification was committed afterward; fresh CI must pass on the latest branch head before the corrected APK is distributed.
- Notification reading remains deferred after the earlier Play Protect block. Do not disable Play Protect or restore NotificationListenerService in this release.
- Current tabs remain Home, Avatar, Sveglia, Settaggi, Memoria and Altro. The audit confirms their current functions and distinguishes implemented features from placeholders in the Improvement Lab and roadmap.
- Next approved product area: Juanita Personal Organizer — after alarm validation, implement encrypted local reminders/tasks, then extend read-only Android calendar agenda. No paid APIs, subscriptions or billing without explicit approval.


## 0.11.4 candidate — voice-controlled alarm stop

- VersionCode 43. A user can ask Juanita to stop the currently ringing Juanita alarm immediately, after a number of seconds, after a number of complete ringtone playbacks, or whichever of the two limits is reached first.
- A plain “stop the alarm” request stops the current occurrence immediately. Timed/count-based stop affects only the currently ringing occurrence; future weekly recurrence remains scheduled.
- If multiple alarms are ringing, Juanita must ask which one. Supported limits: 1–86,400 seconds and 1–100 complete ringtone playbacks.
- The count is defined as one full playback of the configured Android alarm tone, not a short beep. Device testing is required to validate this behavior on Samsung hardware.
- Notification reading remains deferred in the sideload candidate. Do not disable Play Protect.


## 0.11.5 candidate — gender-separated Gemini Live voices

- VersionCode 44. The voice selector groups the full 30-voice Gemini Live catalogue into 14 female and 16 male voices, following Firebase AI Logic's published gender labels.
- Existing saved voice IDs remain unchanged; the selected voice continues to be used by Live conversations and the voice preview. Selection is persisted as before.
- No Android system TTS is introduced. Each option can be auditioned through the existing Gemini Live preview; actual availability must be confirmed by a successful Live session on the configured model.


## 0.11.6 candidate — assistant identity and background display lifecycle

- VersionCode 45. First-run onboarding offers Juanita (female identity) or José (male identity); the choice is saved locally and can be changed later in Settings.
- Persona instructions use matching grammatical gender and assistant name. The initial Gemini Live voice is aligned to the selected identity; a previously selected voice is retained when it already matches. The user can still choose any voice manually.
- Existing installations default to the existing Juanita identity and retain their saved assistant name, voice and wake phrase. Switching identity updates the default wake phrase only when it still matches the previous app default; a custom phrase is preserved.
- Identity switching does not clear memories, conversation archive, alarms, avatar profiles or other preferences.
- The final source audit also updates the alarm screen's spoken instruction, retry buttons, alarm-tab labels and ringing notification to the selected assistant name and grammatical gender.
- MainActivity now clears its keep-screen-on window flag while paused and restores it only when the active call screen requires it in the foreground. The alarm lock-screen activity clears show-when-locked, turn-screen-on and keep-screen-on overrides when it stops, then reapplies them only when visible.
- The biometric symptom is not yet confirmed fixed: no overlay permission, accessibility service or fingerprint-control API is declared in the manifest. Validate on Note10+ Android 12 with the user's foreground/background/force-stop test sequence before claiming resolution.
- No Android TTS, paid service, package ID or signing-key change.


## 0.11.7 candidate — background power and alarm-session cleanup

- VersionCode 46. An alarm-started Gemini Live session is now tagged to its originating alarm. Snoozing or dismissing that alarm stops only that matching voice session, releases its CPU partial wake lock and closes its foreground service; unrelated manually started voice calls are not stopped.
- Explicit timed and ring-count alarm stops also stop the matching alarm-owned voice session. The normal first spoken morning response may stop the ringtone without stopping the conversation.
- Alarm lock-screen activity listens for an alarm-closed event, clears its screen flags on stop, and refuses stale full-screen PendingIntents when the alarm is no longer active.
- Main and avatar clocks update only while their Activity is RESUMED. Conversation archive refresh polling is also suspended outside the foreground.
- If Gemini Live exhausts its reconnect attempts, the voice service now begins cleanup rather than retaining its foreground service and CPU wake lock indefinitely.
- These are source-level fixes, not proof of battery improvement. Verify battery drain, screen wake behavior, microphone indicator and wake-lock release on Note10+ Android 12 and S22 Ultra Android 15.
