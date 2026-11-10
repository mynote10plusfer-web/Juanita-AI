# Digital Character Engine — Phase C1 implementation plan

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

## C3 — Character expression and consistency

- Create a behavioral policy layer that maps traits to conversational decisions: greetings, turn-taking, humour timing, directness, disagreement, reassurance, curiosity, boundaries and social initiative.
- Add examples and counterexamples per selected regional style, with source attribution for cultural details.
- Keep the same character across text and voice sessions; voice/accent selection is a separate capability and must be verified, not promised by prompt alone.
- Avoid catchphrase spam, exaggerated slang and caricature.
- Evaluate with scenario suites: casual chat, disagreement, practical help, relationship advice, vulnerability, humour, formal topics and multilingual code-switching.

## C4 — Individual learning and computational affect

- Detect candidate preferences/corrections, classify provenance and propose durable updates.
- Require confirmation before changing stable traits, values, biography or sensitive details.
- Keep temporary affect bounded, evidence-labelled, correctable and expiring; never silently store an inferred feeling as a permanent memory.
- Preserve the non-manipulation and emotional-safety requirements in `EMOTIONAL_INTELLIGENCE_SPEC.md`.

## C5 — Integration and acceptance

- Bind the approved character and cultural dossier to the selected identity at Live session start.
- Keep memory namespace isolation and the existing SDK-managed audio transport unchanged.
- Test persona switching, app restart, offline dossier use, no cross-identity memory, prompt construction, storage compatibility and failed research.
- Run CI on the final commit, then perform physical Samsung testing before declaring the feature ready.

## Acceptance standard

A Paisa identity and a Canarian identity must differ in grounded regional language/context and documented references, while two identities from the same region must still be able to have very different individual personalities. The user must be able to inspect and edit both layers separately. A prompt containing only “be Colombian” or “be Canarian” is not sufficient.

## Safety and release constraints

- Do not claim consciousness, human feelings, real lived experience or a native accent that has not been verified.
- Never infer political, religious, gender-role, moral or relationship values from origin.
- No new bottom tab, no changes to the stable audio transport, package ID or signing identity.
- Phase A (#35) and Phase B (#36) remain open/draft/unmerged. This phase is also draft; no merge or release without explicit user approval.
