# Juanita AI — Digital Identity Engine

**Architecture decision (2026-11-10):** one shared encrypted memory, two distinct character configurations (Juanita and José), one active character per session.
**Baseline:** stable v0.11.12; feature work remains isolated in draft pull requests.
**Constraints:** preserve package/signing identity, stable audio path and user data. No paid APIs, subscriptions, credits, billing, hosted GPUs or charges without explicit approval.

## 1. Product model

Juanita and José are two selectable digital characters, never concurrent assistants. The user deliberately selects one in Settings. Only that selected character is loaded into a Live session. Changing the selection applies to the next session; it never changes an ongoing conversation and speaking the other character's name never switches identities.

Each character has one editable configuration:
- Juanita: feminine presentation, her own stable personality, voice preference, biography and approved cultural/region configuration.
- José: masculine presentation, his own stable personality, voice preference, biography and approved cultural/region configuration.

Regional styles are configuration values, not additional characters. For example, changing Juanita from Canarian to Colombian/Paisa edits Juanita's existing profile; it does not create another Juanita. The local store is limited to two canonical slots, one Juanita and one José. It must not become a catalogue of dozens of personas.

## 2. Memory model — shared knowledge, separate personalities

There is one shared encrypted conversational memory store used by whichever character is selected. Knowledge, user facts, preferences, corrections, learned expressions and conversation history recorded while talking to Juanita must also be available to José, and vice versa. The user must not have to teach the same information twice.

The shared store is not a shared personality:
- Character name, grammatical presentation, stable traits, biography/backstory, cultural origin and approved regional dossier remain attached to that character's configuration.
- A change to Juanita's character traits must not rewrite José's traits, and vice versa.
- Memories are historical/user knowledge, not instructions. A newer correction takes precedence over an older record.
- The human user's separate `PersonalProfileStore` remains distinct from the shared conversational memory.

All runtime paths — automatic memory extraction, continuity snapshots, archive, semantic recall and memory search — must use the same `EncryptedMemoryStore(context)` without a persona UUID namespace. Deleting or editing a character configuration must never clear common memories. The common archive stays encrypted at rest with the existing Android Keystore AES-GCM mechanism.

## 3. Single-active-session invariant

- `AssistantPreferences.activeDigitalIdentityId` stores zero or one selected character ID.
- Before session start, the service resolves that exact configuration and constructs its character-specific system instruction.
- One Live session, one microphone owner, one playback owner and one active character at a time.
- The selected character ID is immutable for the lifetime of that session.
- If the selected profile is missing or unreadable, fail closed with an actionable error; do not silently fall back to the other character.
- A Settings selection change applies only after the current session has ended and a new one is explicitly started.
- A call-in/wake matcher receives only the selected character's name. It may recognize a direct call at the beginning of an utterance; it must ignore the other character's name and must never switch profiles.

## 4. Character configuration

The encrypted identity store contains at most two slots: one feminine Juanita and one masculine José. The canonical names and presentations are fixed; the user can edit regional/cultural origin, preferred language, optional biography and personality traits in place. A second profile with the same presentation is rejected. This avoids duplicate regional variants becoming separate people.

Cultural origin informs relevant language and context only. It must not determine personality, values, beliefs, gender roles or relationship preferences. Regional dossier approval is explicit, sources are visible, and the same dossier can be assigned to either character without changing grammatical presentation.

Stable character traits include warmth, humour, curiosity, seriousness, expressiveness, formality, empathy, directness, sociability, spontaneity, patience, optimism, assertiveness, playfulness and discretion. Traits must be translated into observable conversational choices rather than maximized simultaneously.

Biography/backstory is user-authored character configuration, not a claim of real lived experience. Temporary affective state is computational, bounded and transparent; the app must never claim biological consciousness or human feelings.

## 5. Cultural research and provenance

Prefer no-cost public sources such as language academies, universities, cultural institutions, public museums and openly accessible local publications. Research only public geography/culture; never send private memories or transcripts to search providers. Respect source terms, attribution and rate limits.

Each dossier records source title, URL, publisher, retrieval time, topic, confidence and concise summary. Distinguish source-backed facts from interpretation and note regional variation. The user reviews and explicitly approves a dossier before it enters the character instruction. Approved dossiers are cached locally for offline use. Refreshes create proposed revisions; they never overwrite personal biography or personality.

If a reliable free source is unavailable, use an honest offline/manual fallback. A free tier requiring billing activation or exposing the user to charges is not considered free for this project.

## 6. Conversational presence (C3)

Presence and proactive speech are separate permissions. Listening is disabled by default and may only be enabled through explicit user action while the app is visibly foregrounded, with a selected character and microphone permission. Background/hidden capture and wake-word daemons are out of scope.

Unsolicited speech requires a second opt-in and stricter gates: idle period, cooldown, hourly cap, quiet hours, no user/assistant speech, network and no riding mode. While riding, no proactive initiatives are allowed; the selected character responds only when directly addressed.

The existing Gemini Live SDK-managed audio path is protected. Do not add a second microphone reader, competing Live receive collector, parallel TTS or speaker-listening loop. A proactive turn can only be integrated through a serialized method on the existing active session, after free-tier/cost constraints are verified. Never open another Live session just to speak.

## 7. Security and data controls

- Identity configuration and common conversational memory remain encrypted at rest using Android Keystore-backed AES-GCM.
- No decrypted content in logs.
- Character deletion removes only that character configuration; it does not delete the common memory, human profile or the other character.
- Memory inspection/edit/delete and archive controls apply to the common store, regardless of which character is active.
- Raw microphone audio is never stored.
- No private data is sent to cultural research/search providers.

## 8. Delivery and acceptance

### Phase A — encrypted character configuration
- Two canonical character slots, Juanita and José.
- Each slot can be created, edited and deleted; no duplicate regional variants.
- Encrypted local storage and fail-closed recovery.

### Phase B — shared memory and selected character runtime
- Both characters use the same encrypted memory store.
- Each session binds to exactly one selected character instruction.
- Character switching never changes an active session.
- Tests prove memory written through one character is visible through the other, while their personality and cultural settings remain independent.

### Phase C — cultural dossier and controlled presence
- Source-backed Paisa/Canarian dossier review and explicit approval.
- Fail-closed foreground presence policy and selected-name-only matcher.
- No audio integration until the existing SDK exposes a safe serialized turn path and cost constraints are verified.

### Phase D — learning and computational affect
- Shared knowledge learning with provenance, confidence and user correction.
- Character-specific stable personality and temporary computational affect.
- No silent rewrite of stable traits or sensitive memories.

### Phase E — optional avatar
- Avatar remains separate from the protected audio path.
- No paid API, service, subscription, credit, trial or hosted GPU without explicit approval.

## 9. Regression and release gates

- Stable v0.11.12 remains unchanged until a tested change is explicitly approved.
- Preserve package `ai.juanita.app`, signing certificate, existing tabs and local data.
- CI must pass unit and API 31 instrumentation tests on the final commit.
- Physical Samsung verification remains required.
- Never merge a PR, publish a release or replace the installed APK without explicit approval.
