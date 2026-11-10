# Juanita AI — Digital Identity Engine

**Status:** Phase A implemented on this feature branch; CI and physical-device verification pending.
**Baseline:** candidate 0.11.12 (`e82f61857c3d50f60cf642136831a119811bd365`).
**Constraints:** preserve the stable app, package/signing identity and local data; no paid APIs, subscriptions, credits, billing or hosted GPUs.

## Implementation status — 2026-11-10

Phase A has an initial implementation on this feature branch: domain models, a dedicated Android Keystore AES-GCM identity store, and an in-app local identity manager for create/list/edit/delete. Instrumentation tests cover persistence across store recreation, per-identity update/delete isolation, UUID namespace binding, unreadable-file preservation and ciphertext-at-rest checks. These tests are added but must not be considered passed until CI executes them. Identity selection is not yet connected to Gemini Live; cultural web research and learning/affect runtime remain later phases.

## 1. Product vision

Support multiple independently created digital identities. Juanita and José are separate identity instances, not two names or voices attached to one shared persona. Each identity owns its biography, personality, cultural origin, languages, values, interests, autobiographical memory, temporary computational affect state, voice profile and optional avatar profile.

The runtime engine may be shared, but identity data and memory namespaces must be isolated. Switching identity must never merge, overwrite or expose another identity's autobiographical memories.

The system models personality, memory and affect computationally. It must not claim consciousness, sentience, human feelings or lived experiences. Affective states are temporary computational variables used to guide tone and response selection.

## 2. User experience: create an identity

Add a **Digital Identities** entry inside an existing tab (prefer Settings or the existing identity/profile area); do not add a seventh bottom-navigation tab.

Creation flow:
1. Choose a display name and presentation: feminine (Juanita-style) or masculine (José-style). Each identity receives an immutable UUID; names are not unique identifiers.
2. Choose country and, where meaningful, region/city/community (for example Colombia → Antioquia → Medellín/Paisa; Spain → Canary Islands → Tenerife; Italy → Sicily → Palermo).
3. Choose main language and additional languages. Language switching remains conversational; do not create one identity per language.
4. Optionally choose an initial temperament preset or tune traits. Cultural origin must not dictate personality.
5. Run a cultural research preview. Show source names, links, dates, coverage gaps and confidence. The user approves the cultural dossier before it affects the identity.
6. Generate an initial biography/personality proposal. The user can edit, accept, regenerate or leave fields blank.
7. Create and save the identity. A failed search or generation must not create a half-written identity or affect existing identities.

Allow multiple identities of either presentation, including several Juanitas or Josés.

## 3. Cultural research pipeline

Research publicly documented context relevant to the selected origin: languages and dialects; regional vocabulary and pronunciation; everyday conversational conventions, forms of address, humour and politeness; geography, local history, arts, food, music, festivals and daily life; social norms only with careful qualifiers and explicit acknowledgement of internal diversity.

Do not turn nationality into a personality stereotype. Do not encode assumptions about morality, gender roles, religion, politics, sexuality, temperament or relationship preferences as facts. Culture may inform language, references and context; it must not predetermine an individual's character.

### Sources and provenance

Use a provider abstraction so search providers can change without changing identity storage. Prefer no-cost public sources: cultural institutions, universities, language academies, public museums, encyclopedic references and openly accessible local publications. Respect terms, robots, attribution and rate limits; do not scrape behind logins or paywalls.

Every retained research item records source title, canonical URL, publisher, retrieval timestamp, concise excerpt/paraphrase, topic and geographic scope, confidence, and any disagreement or freshness caveat. Prefer primary/institutional sources and cross-check important claims where practical. Distinguish verified facts, source claims and model interpretation. Never fabricate citations or claim a search happened if unavailable.

### Free-only operation

The default must not require a paid API, subscription, usage credit, billing-enabled cloud project, payment method or hosted GPU. Online research is best-effort and bounded by provider availability and fair-use limits. Cache approved dossiers locally so ordinary conversations do not repeat searches.

If no reliable free provider is available, offer manual source import or a clearly labelled offline starter dossier; never silently switch to a paid provider. A free tier that requires billing activation or can incur charges is not considered free for this project. Check current pricing, quota, terms and data handling before enabling any provider.

Research queries contain only public geography/culture. Never send private memories or transcripts to a search provider.

### Refresh

Refreshing a cultural dossier creates a proposed revision with a diff and source list. The user approves changes before they affect the identity. A refresh must never overwrite personal experiences, stable values or user-edited biography.

## 4. Identity data model

Create a new identity domain; do not repurpose `PersonalProfileStore`, which belongs to the human app user.

Suggested model:
- `identityId`: immutable UUID; `displayName`, `presentationGender`, timestamps and `schemaVersion`.
- `origin`: country, region, locality, cultural community and optional diaspora context.
- `languages`: repertoire and preferred response language, independent of origin.
- `culturalDossier`: versioned research summary, sources, retrieval date, confidence and user approval state.
- `personality`: stable trait values, interaction style, humour, warmth, curiosity, formality and boundaries.
- `valuesAndInterests`: individually chosen or explicitly approved values, interests and preferences.
- `autobiography`: constructed backstory clearly labelled as character backstory, plus identity-specific experiences actually recorded during use.
- `affectState`: temporary computational state with timestamp, expiry and evidence; never a permanent personality fact by default.
- `memoryNamespace`: identity-scoped reference to episodic and curated memory.
- `voiceProfile` and optional `avatarProfile`: references to supported voice/avatar settings.

Use schema versions and migrations. Encrypt identity metadata, dossiers and autobiographical memories at rest using Android Keystore-backed AES-GCM, atomic writes and fail-closed recovery. Do not log decrypted content.

## 5. Memory isolation and shared context

Each identity has its own episodic archive references, curated autobiographical memories, confirmed preferences, learned expressions, corrections and relationship context. The human user's own profile remains a separate namespace.

An identity receives only the minimum human-user context required for a conversation through an explicit context assembly policy. Juanita cannot access José's memories and vice versa.

When switching identity, close the previous session cleanly; load only the selected identity's instructions and memory; do not carry over the previous identity's working context; tag archived conversations with identity UUID; and scope conversation search to the selected identity unless the user explicitly requests cross-identity search.

Identity deletion is explicit and confirmed. Explain which identity data, memories, avatar references and conversation records will be deleted. Never delete shared user profile data or another identity's data.

## 6. Personality, biography and learning

Keep four layers separate:
1. Cultural context: externally researched facts and regional language guidance.
2. Stable personality: user-approved traits and values that change rarely.
3. Autobiography: character backstory and identity-specific experiences, distinguishing authored backstory from experiences actually recorded in app conversations.
4. Temporary affect: current computational conversational state, which decays and is not automatically stored as a lasting feeling.

Learning loop: detect a candidate correction/preference/experience; classify provenance (user statement, explicit correction, approved research or model inference); estimate relevance and confidence; propose an update only when durable and appropriate; request confirmation for changes to stable identity, values, biography or sensitive information; store approved updates with timestamp/source; allow inspection, correction, export and deletion.

An identity may adapt phrasing and conversational strategy, but must not silently rewrite core values, invent memories, claim human life experiences, or modify its application code/model weights. Reflection may propose changes; it cannot bypass approval controls.

## 7. Computational affect

Use a small, transparent, bounded state model rather than claiming subjective emotion. Candidate dimensions include calmness, curiosity, enthusiasm, concern, frustration and warmth/affiliation. These are computational interaction signals, not diagnoses or proof of consciousness.

Update from conversational context and explicit statements, never nationality stereotypes. Attach confidence and evidence category (explicit statement, conversational cue, inference, unknown). Decay temporary states over time and at session boundaries where appropriate. Accept user corrections. Never use affect to manipulate, guilt, isolate or increase dependence. Do not store sensitive or inferred emotional episodes as permanent biography by default. When uncertain, respond to content without announcing an emotion.

Existing `EMOTIONAL_INTELLIGENCE_SPEC.md` remains the behavioral safety baseline and must be reconciled with this model before runtime implementation.

## 8. Conversation runtime integration

The current Gemini Live SDK-managed audio path is protected. Integrate at the instruction/context assembly boundary:
- Resolve selected identity before starting a Live session.
- Compose instructions from approved cultural dossier, stable personality, biography summary and selected language.
- Add only bounded, relevant memories from that identity's namespace.
- Keep microphone, playback, interruption, foreground service, reconnect and camera paths unchanged.
- Use the selected available Gemini voice, but do not promise a native regional accent unless the actual voice has been verified.
- Keep technical, medical, legal and safety-critical communication clear even when regional style is selected.

No second microphone recorder, alternate audio transport, background emotion monitor or unsolicited camera analysis is introduced here.

## 9. Security, privacy and trust

- Public cultural research is separate from private autobiographical and conversation data.
- Do not send private memories or transcripts to web search.
- Ask separately before optional camera-derived context or new external processing.
- Show which identity is active and provide controls to inspect/edit biography, personality, sources and memories.
- Keep existing identities useful offline after creation; network loss must not damage them.
- Never represent simulated affect as human feeling or a cultural dossier as a definitive personality.

## 10. Delivery plan and acceptance gates

### Phase A — foundation (first implementation)
- Add identity domain models and a dedicated encrypted `DigitalIdentityStore`.
- Support create, list, select, edit and delete for multiple independent identities.
- Add schema migration and tests for encryption, isolation, atomic writes and deletion.
- No online research and no Gemini Live changes yet.

**Acceptance:** create Juanita and José, restart the app, verify both persist, edit one, delete one, and prove the other identity and human user profile remain unchanged.

### Phase B — identity-aware conversation
- Resolve selected identity at session start.
- Compose separate system instructions and memory namespaces.
- Preserve all existing audio lifecycle behavior.
- Add tests proving no cross-identity memory leakage.

**Acceptance:** two identities have different names, presentation, traits and separate remembered facts across restarts and sessions.

### Phase C — cultural research and dossier approval
- Add free-only provider adapters and bounded research.
- Present citations, provenance, uncertainty and preview.
- Approve/cache dossier per identity; support offline reuse and user-approved refresh.

**Acceptance:** create Paisa and Canarian dossiers, inspect sources, disconnect the network and verify both profiles remain usable. If no free provider is available, show an honest fallback without charges.

### Phase D — biography, learning and computational affect
- Add editable biography/backstory and distinguish authored backstory from recorded experiences.
- Add controlled learning proposals and user confirmation.
- Add bounded temporary affect with expiry, correction and privacy controls.

**Acceptance:** corrections update only the selected identity; inferred temporary affect is not silently stored; no false claim of consciousness or human feelings.

### Phase E — voice and optional avatar alignment
- Bind selected identity to an available gender-consistent voice and its own avatar profile.
- Evaluate regional voice quality honestly; prompt instructions alone do not guarantee native accent.
- Avatar remains a separate gated project and must not compromise voice-call stability.

## 11. Regression and release gates

- Baseline 0.11.12 remains installable and unchanged until the user approves a tested release.
- Keep package `ai.juanita.app`, signing certificate, local data compatibility and existing tabs.
- No PR merge, release, signing-key/package change or destructive migration without explicit user approval.
- CI must compile and run tests on the final commit; physical validation is required on supported Samsung devices.
- Test identity switching during/after a call, process death, restart, offline use, failed encryption reads, failed research, empty dossier, duplicate display names and concurrent saves.
- Do not describe the system as conscious or sentient. Describe it as an evolving digital identity with memory, personality and computational affect.

## 12. Existing specifications to reconcile

- `EMOTIONAL_INTELLIGENCE_SPEC.md`: contextual empathy, uncertainty, non-manipulation and temporary emotion handling.
- `MEMORY_RECALL_AND_AUTOBIOGRAPHY.md`: current human-user memory architecture; this project adds identity-scoped namespaces, not a replacement for the user's profile.
- `AVATAR_VIDEO_CALL_ARCHITECTURE.md`: avatar/audio constraints; avatar generation/animation remains separate.
- `PRODUCT_ROADMAP.md`: update priorities when the first implementation phase is approved.
