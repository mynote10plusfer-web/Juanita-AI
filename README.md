# Juanita AI

Android voice assistant for natural multilingual, multi-turn conversations, configurable personality and regional speech profiles.

## Current development candidate: 0.11.3 (versionCode 42) — not yet released

Signed Release 0.10.8 adds progressive personal knowledge: a voice-led first meeting, optional name and birth date gathered naturally, a dedicated encrypted profile store connected to Gemini Live, and gradual learning from ordinary conversation. PR #23 has merged the timezone-safe Smart Alarm schedule model (not yet a working alarm). PR #24 is under CI review for automatic recovery after Gemini Live disconnects; it is not yet released or physically tested. Intermediate builds are not for installation.

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

- Smart Alarm schedule model: merged in PR #23. This is only the recurrence/time-zone calculation core; Android alarm delivery, persistence, controls and spoken greeting are still planned.
- Gemini Live audio recovery: PR #24 adds GoAway handling, websocket health checks, bounded reconnection and recent transcript context replay. It must pass CI and real-device testing before being called fixed or included in a Release APK.
- Familiar-person speaker identification remains unimplemented. Voice transcription is not speaker recognition; local enrollment requires explicit consent and privacy/legal review.
- Notification reading remains deferred; do not disable Play Protect.


## Current candidate status — 2026-11-08

- PR #31 remains draft and unmerged. Candidate 0.11.2 (versionCode 41) completed signed Release CI and permanent certificate verification but was blocked by Google Play Protect during sideload installation; it is not physically validated.
- Candidate 0.11.3 (versionCode 42) removes the notification reader from the shipped app while preserving the permanent package/signing identity and existing local data. It must pass a fresh signed Release workflow before distribution.
- Current tabs: Home, Avatar, Sveglia, Settaggi, Memoria, Altro. Do not remove or redesign these tabs as part of the notification deferral.
- Next approved product area: Juanita Personal Organizer — validate multi-alarm and spoken morning greeting, then local reminders/tasks and calendar integration. No paid APIs, subscriptions or billing without explicit approval.
