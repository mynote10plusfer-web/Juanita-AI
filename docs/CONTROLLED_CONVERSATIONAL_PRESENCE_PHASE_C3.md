# Phase C3 — Controlled Conversational Presence

**Branch:** `feature/digital-character-engine-phase-c3-controlled-presence`
**Parent:** Phase C2 cultural dossiers, PR #38
**Status:** isolated policy prototype; no microphone/audio integration yet.

## Product behavior

The optional mode lets the selected identity (Juanita or José) participate in a conversation without requiring the user to tap-to-talk for every turn. It is not an always-on background assistant.

### Consent and privacy gates

- Disabled by default. The user must explicitly enable the feature and explicitly start a presence session.
- The user starts the session while the app is visibly in the foreground. Background continuation is a separate, explicit opt-in; it is never implied by enabling presence.
- In the background, microphone access is permitted only while the Android microphone foreground service is active with its persistent user-visible notification and the user-started session remains authorized.
- If the app loses foreground without background consent, the foreground service is unavailable, permission is revoked, the user pauses/stops, or another audio call takes control, listening must stop immediately.
- A clear in-app microphone/listening indicator and one-touch pause/stop control are mandatory.
- No hidden background capture, no wake-word daemon, no raw audio recording, and no new transcript retention beyond the existing user-controlled archive setting.
- Exactly one selected character is bound to a session. Juanita and José are the only two character slots; each slot has one editable regional configuration, not multiple regional copies. Switching is deliberate in Settings and applies only to the next session; never switch because another name is spoken.
- Both characters use one shared encrypted memory store for user facts, learned knowledge, corrections and conversation history. Their stable personality, presentation, voice selection and cultural configuration remain separate. The same approved regional dossier can be used by either character.
- No session may start itself after force-stop, reboot, explicit stop, or app backgrounding.

## Proactive speech policy

Listening and speaking first are separate permissions. Even while listening is allowed, unsolicited speech is blocked unless all conditions pass:

1. User separately enables proactive initiatives.
2. App is foreground and the explicitly started session is live.
3. User has been idle for at least 3 minutes by default.
4. Neither user nor assistant is currently speaking.
5. At least 30 minutes have elapsed since the previous initiative.
6. No more than 2 initiatives in the previous hour (user-adjustable, hard-bounded 0–4).
7. Quiet hours are inactive.
8. Riding mode is inactive; while riding, the assistant only responds when addressed.
9. Network and selected identity are available.

The policy is fail-closed: unknown or missing prerequisites must never authorize listening or unsolicited speech.

## Conversation style

- Short, context-aware observations or a gentle question, never repeated generic greetings.
- One initiative at a time; no monologues or repeated attempts when the user does not respond.
- Natural barge-in: user speech interrupts assistant output immediately using the existing SDK-managed path.
- Respect a declined/ignored initiative and increase the cooldown; never pressure the user to engage.
- Never infer the user's mood, attention, or desire to talk from silence alone.

## Audio architecture constraints

- Preserve Gemini Live SDK-managed microphone, playback, VAD, AEC, interruptions, reconnect and foreground-service ownership.
- Never create a second microphone reader, competing `LiveSession.receive()` collector, parallel TTS engine, or speaker-listening loop.
- The current `GeminiLiveAssistant.start()` accepts an `initialPrompt`, but it does not yet expose a dedicated, bounded proactive-turn API. Do not misuse the startup prompt or trigger a second session as a workaround.
- A later integration must add one serialized assistant-initiative method on the existing active session, with state checks before sending. It must not open a new Live session just to speak.
- Existing Gemini Live API usage may incur network/API costs depending on project billing and quotas. Do not silently enable autonomous cloud sessions or claim that model usage is free. Before implementing remote proactive turns, verify the project's no-cost allowance and require explicit opt-in if any charge is possible.
- Local-only timers and policy checks can be free, but they cannot generate genuinely contextual speech without an available inference engine.

## Prototype implemented in C3

- `ConversationalPresencePolicy` contains pure, fail-closed decisions for listening and proactive speech.
- Default is disabled; listening requires an explicit user-started session, mic permission and selected identity. Background continuation additionally requires both the active user-visible Android foreground service and separate user consent.
- Proactive speech has independent opt-in, idle threshold, cooldown, hourly cap, quiet-hours and riding-mode gates.
- Unit tests cover the principal allow/deny cases.
- Instrumentation regression test confirms the same regional dossier is injected for masculine and feminine identities without changing grammatical presentation.
- No UI toggle is exposed yet because connecting an inert toggle to the existing microphone path would be misleading and unsafe.

## Next integration sequence

1. Confirm C2 and C3 CI.
2. Add visible presence and background-continuation controls to the existing voice screen/settings, wired to the policy (no new bottom tab).
3. Add foreground-service/lifecycle enforcement, explicit session start/stop and revocation of background consent when the session ends.
4. Add a single proactive-turn call to the existing Live session only after cost/billing constraints are verified.
5. Run scenario tests: quiet user, active user, interruption, app background, mic revoked, another call, riding mode, network loss, selected identity switch.
6. Physical Samsung test with visible mic indicator and immediate stop/pause verification.

## Non-goals

- No simulated human consciousness or claim of real feelings.
- No continuous background surveillance or hidden recording.
- No unsolicited speech while riding.
- No paid API, paid service, subscription, credits, trial, hosted GPU or payment method without explicit authorization.
- No changes to stable v0.11.12, package ID, signing key or release channel.


## Shared-memory / single-active-character correction (2026-11-10)

The product decision is explicit: one shared encrypted memory store is used by both character configurations. A fact, preference, correction or experience learned while talking to Juanita is available when José is selected, and vice versa. This is not a shared personality: each character retains its own name, grammatical presentation, voice preference, cultural origin/dossier, biography and stable character traits.

The local identity archive is limited to two canonical slots, Juanita and José. Each slot can be edited in place to change its region, locality, language context or character traits. Regional variants are never separate saved characters. The active identity preference stores a single UUID; the service resolves only that selected configuration before creating the session. A Settings change affects the next session, not a live session. Wake phrase matching must receive only the currently selected name, and must never switch identity or activate the other name.

The common archive remains encrypted at rest and uses the existing shared store. Deleting or editing a character configuration must never clear or alter common memories. The human user's separate PersonalProfileStore remains separate from this shared conversational memory.
