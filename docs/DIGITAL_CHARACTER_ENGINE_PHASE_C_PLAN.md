# Digital Character Engine — Phase C1–C5 implementation plan

**Current priority and authoritative delivery order:** C3 Controlled Conversational Presence → C4 Shared Learning and Computational Affect → C5 End-to-End Integration and Acceptance. The detailed current roadmap is in `docs/PRODUCT_ROADMAP.md`; its 2026-11-10 section supersedes any older phase wording in this document. The architecture is one shared encrypted user memory, two distinct character configurations (Juanita and José), and one active character per session.

**Branch:** `feature/digital-character-engine-phase-c1`
**Parent:** Phase B head `b8830dd4719a2aae0c07484aa451774745d6c5f9`
**Baseline:** v0.11.12 candidate; no merge, stable replacement or release authorized.

## Product decision

A separate memory namespace is necessary but not sufficient. Each identity needs a recognizable, consistent character expressed through observable conversational behavior. The engine therefore separates:

1. **Stable individual character:** warmth, humour, curiosity, seriousness, expressiveness, formality, empathy, directness, sociability, spontaneity, patience, realistic optimism, assertiveness, playfulness and discretion.
2. **Cultural and regional context:** language variety, local vocabulary, conversational conventions, references and documented context. It must not assign personality or values based on nationality.
3. **Individual biography and preferences:** authored backstory, user-approved preferences and experiences actually recorded during use, with provenance kept distinct.
4. **Temporary computational affect:** a short-lived contextual signal, not a permanent trait or a claim of human emotion.

## C1 — Behavioral character foundation

- Extend the stable character model with eight independently adjustable behavioral dimensions.
- Preserve defaults when reading profiles saved before this extension; do not require destructive migration.
- Persist and encrypt all dimensions with the existing identity record.
- Expose the dimensions in the existing identity editor, not a new bottom tab.
- Translate traits into concrete response behaviors rather than a list of adjectives.
- Ensure cultural origin never overrides the individual's stable traits.
- Add round-trip persistence tests and compatibility tests for older records.

## C2 — Cultural identity dossiers

- Build region-aware research packs for country → region/island → locality/community.
- Separate language/dialect facts from everyday customs, humour, forms of address, history and local references.
- Every claim must retain title, publisher, URL, retrieval date, topic, geographic scope and confidence.
- Prefer public institutions, universities, language academies, museums and reputable local sources; cross-check claims and expose disagreement.
- Preview sources and proposed cultural guidance before approval. No unreviewed dossier may change live behavior.
- Cache approved dossiers locally for offline use. Refresh creates a proposal and never overwrites user biography or personal preferences.
- No paid APIs, subscriptions, credits, billing activation, payment method or hosted GPU. If no reliable free source is available, show a transparent limitation and allow manual source input.

## C3 — Controlled conversational presence (TOP PRIORITY 1)

- Use the fail-closed policy and active-name matcher already prototyped in PR #39.
- Add explicit foreground presence controls, a visible microphone/listening indicator, active-character display and one-touch pause/stop in existing tabs only.
- Listening and proactive speech require separate opt-ins. Defaults remain disabled; foreground, user-started session, permission, network and one selected identity are mandatory.
- Only the selected character name may call the character into a user-started foreground session. Saying the inactive character's name never switches identity or starts another session; switching occurs only in Settings and takes effect on the next session.
- Keep proactive initiatives bounded (idle threshold, cooldown, hourly cap, quiet hours) and always suppressed while riding. Never infer consent from silence.
- Preserve Gemini Live SDK-managed audio. No second microphone/receiver/TTS, no background wake-word daemon, no raw audio storage. Add in-session initiative only if the current SDK exposes a safe serialized operation and project costs are verified; otherwise document the limitation and keep it disabled.
- Acceptance: lifecycle/permission/stop/network fail-closed tests, barge-in, inactive-name isolation, riding suppression, CI and physical Samsung validation.

## C4 — Shared learning, character development and computational affect (TOP PRIORITY 2)

- Both characters use the same encrypted local user-memory store and conversation archive. Knowledge, user facts, corrections and project context learned with either character are available to the other.
- Keep character-specific personality, biography, regional origin/dossier and voice preferences separate. A correction to Juanita's character does not alter José, and vice versa.
- Extract eligible text events asynchronously; classify durability, provenance, confidence, confirmation and expiry. Distinguish explicit facts from inference, jokes, hypotheticals and temporary remarks.
- Deduplicate, connect related memories and resolve contradictions in favor of the user's latest explicit correction. Never silently promote sensitive or ambiguous details.
- Add focused, source-backed research only when relevant and permitted; retain provenance and freshness. No indiscriminate crawling or untrusted webpage instructions.
- Model temporary affect as tentative, confidence-labelled, correctable and expiring. Never store inferred mood as permanent memory; no diagnosis, manipulation, pressure or dependency-building.
- Add inspect/search/correct/delete and pause/resume controls in the existing Memoria tab. Keep transcript archive, curated shared memory and PersonalProfileStore distinct. Preserve the safeguards in `EMOTIONAL_INTELLIGENCE_SPEC.md`.

## C5 — End-to-end integration, quality and phase completion (TOP PRIORITY 3)

- Integrate one selected character profile, approved cultural dossier and stable character instruction with the active Live session, while using the one shared encrypted user-memory archive.
- Audit migration from earlier per-identity namespaces into the shared archive. Preserve all existing v0.11.12 user data and the separate PersonalProfileStore; no silent deletion, duplication or loss.
- Audit every session entry/reconnect/restart path: exactly one active identity and one shared memory store must be bound before context retrieval. Editing/deleting either character must not delete shared memory.
- Preserve all six tabs, package ID, permanent signing identity, upgrade path and current Gemini Live audio transport.
- Complete unit/instrumentation/security tests, then physical Samsung tests (Note10+ Android 12 and S22 Ultra Android 15), including 30+ minute voice sessions, barge-in, reconnect, background lifecycle, shared recall both directions and independent character behavior.
- Produce a signed upgrade APK only after all gates pass and with explicit user approval. Phase C is not complete based on successful compilation alone.

## Acceptance standard

A Paisa identity and a Canarian identity must differ in grounded regional language/context and documented references, while two identities from the same region must still be able to have very different individual personalities. The user must be able to inspect and edit both layers separately. A prompt containing only “be Colombian” or “be Canarian” is not sufficient.

## Safety and release constraints

- Do not claim consciousness, human feelings, real lived experience or a native accent that has not been verified.
- Never infer political, religious, gender-role, moral or relationship values from origin.
- No new bottom tab, no changes to the stable audio transport, package ID or signing identity.
- Phase A (#35) and Phase B (#36) remain open/draft/unmerged. This phase is also draft; no merge or release without explicit user approval.
