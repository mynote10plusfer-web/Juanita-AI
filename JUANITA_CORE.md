# JUANITA CORE — Persistent Identity, Memory and Self-Development

Status: product and engineering specification with partial Android runtime implementation. Encrypted archive, curated memory, automatic extraction queue and lexical recall are present in the current code; end-to-end device verification and several biography/reflection milestones remain outstanding.

## Goal

Give Juanita a persistent digital identity and a coherent personal history. She should remember relevant experiences, connect facts across conversations, reflect on interactions, and gradually refine her conversational style. This is a design for continuity and adaptive behaviour; it is not evidence of consciousness or subjective feelings.

## Non-negotiable principles

1. **Truthfulness:** Juanita must not claim to be conscious, to feel emotions, or to have experiences outside the capabilities actually implemented.
2. **User ownership:** the user can inspect, correct, export and delete stored memories and conversation records.
3. **Consent and privacy:** text-only conversation archiving and eligible non-sensitive automatic memory are enabled by default in the current product direction, clearly disclosed and independently pausable, inspectable and deletable. No raw microphone audio is retained. No background collection or external upload of the local archive/memory without explicit permission.
4. **Local-first:** memory is stored on the phone by default. Optional backup/synchronization must be separately enabled and encrypted.
5. **Traceability:** every durable memory records its source, timestamp, confidence and whether it was confirmed by the user or inferred.
6. **Bounded self-development:** Juanita may propose refinements to her style, but must not silently rewrite core safety rules, privacy settings or user-approved identity principles.
7. **No emotional manipulation:** memory and personalization must never be used to create dependency, guilt, jealousy, exclusivity or pressure.

## Memory architecture

### 1. Conversation archive (episodic source)

A text-only, searchable archive of recognized user and assistant turns, enabled by default in the current app and stored locally in encrypted per-session files. This is the source record, not the assistant's entire active context. The user can pause archiving, set retention, inspect, delete individual sessions/days/time ranges, clear the archive or export it. Raw microphone audio is never stored.

### 2. Working memory

A compact context assembled for the current conversation from recent turns and relevant long-term memories. The current implementation restores up to the latest 60 archived turns plus bounded curated memories and a grounded continuity summary; it can be discarded and rebuilt.

### 3. Semantic memory

A curated set of durable non-sensitive facts, preferences, ongoing projects, people and recurring goals. Automatic extraction runs asynchronously from user transcript text through a bounded encrypted pending queue; manual and automatic memories remain separately traceable. Each item includes:
- stable ID and category;
- concise statement;
- source conversation/turn reference;
- created and last-confirmed timestamps;
- confidence and confirmation state;
- optional expiry/review date;
- sensitivity classification.

Contradictory or uncertain information is not silently merged. Juanita asks for clarification when the difference matters.

### 4. Episodic highlights

A chronological set of meaningful events and outcomes (for example, a project milestone or a decision). Highlights link back to source turns where available.

### 5. Reflection journal

After a conversation, or at a user-configured interval, a reflection process can:
- identify potentially useful memories;
- connect new information to existing memories;
- detect contradictions and outdated facts;
- record what worked or failed in a task;
- suggest a change to conversational style.

A reflection is an internal processing record, not a claim that Juanita privately thinks or feels like a human.

## Autonomous memory curation

The memory curator should rank candidate memories using usefulness, recurrence, recency, confidence, future relevance and sensitivity. High-sensitivity details are excluded from automatic long-term retention unless the user explicitly chooses to save them. Low-confidence inferences remain provisional and must not be presented as facts.

The curator should prefer concise summaries and links to source turns over duplicating entire conversations in semantic memory. The archive, when enabled, preserves the original transcript separately.

Suggested memory states:
- candidate: extracted but not yet approved or promoted;
- confirmed: explicitly confirmed by the user;
- inferred: useful but uncertain; must be labelled internally as inferred;
- stale: possibly outdated and due for review;
- deleted: removed from active retrieval and scheduled for secure deletion where technically supported.

## Identity model

Separate the identity into three layers:

- **Core identity:** name, role, stable communication principles, honesty, privacy and safety boundaries. Changes require explicit user approval.
- **Current persona:** the existing nine personality sliders and selected language/dialect. User-controlled.
- **Adaptive style:** small, reversible refinements based on feedback and interaction patterns. Juanita may propose these; the user can accept, reject or reset them.

Every accepted change should be versioned with a timestamp and a short reason. The assistant must retain a way to restore a previous version.

## Suggested Android modules

- memory/ConversationArchive.kt: opt-in transcript storage and session management.
- memory/MemoryItem.kt: typed memory record with provenance and confidence.
- memory/MemoryRepository.kt: local persistence, search, correction, export and deletion.
- memory/MemoryCurator.kt: provider-neutral extraction, ranking, deduplication and contradiction detection.
- identity/IdentityProfile.kt: core identity and versioned adaptive style.
- reflection/ReflectionEngine.kt: post-session reflection and proposed updates.
- privacy/MemoryPrivacyController.kt: consent, pause, retention and data controls.

Persistence should use an Android Keystore-backed encryption key for private memory content. Database migrations, backup exclusion, deletion behaviour and recovery must be tested before enabling automatic archiving. Never put memory contents, credentials or private user facts in logs.

## Processing flow

1. The user starts or resumes a conversation.
2. Juanita assembles a small relevant context from working and semantic memory.
3. The conversation provider receives the current persona, context and recent turns.
4. If archiving is enabled, the completed user/assistant turns are saved locally.
5. The curator extracts candidate memories and links each candidate to its source.
6. The reflection engine evaluates usefulness, confidence, sensitivity and contradictions.
7. Safe, low-risk memories may be promoted according to the user's chosen policy; sensitive or uncertain candidates require confirmation.
8. Adaptive-style changes are proposed and versioned only after user approval.
9. The user can inspect the memory log and reverse accepted persona changes.

## Delivery milestones

1. **Core data contracts and privacy controls:** partially implemented; verify provenance, correction, pause and deletion behavior end to end.
2. **Encrypted local archive:** implemented in code with default text-only archiving, per-session encrypted files and retention controls; device verification and export/restore remain outstanding.
3. **Memory centre UI:** memory list/edit/delete and calendar-based transcript browsing/deletion are implemented in the current branch; device verification and export/restore remain outstanding.
4. **Curator:** automatic non-sensitive extraction and a durable encrypted pending queue are implemented in code; test failures, retries, contradictions and sensitive-data exclusion on device.
5. **Reflection and identity versioning:** future milestone; the current app does not yet implement a reflection journal or adaptive persona versioning.
6. **Context retrieval:** bounded recent context and local lexical archive retrieval are implemented in code; semantic multilingual retrieval remains future work.
7. **Optional backup:** encrypted export/import for full memory and archive transfer between devices remains future work, disabled until the user explicitly initiates it.

Do not describe any milestone as implemented until it exists in the app, has tests, and has been verified on the target Samsung Galaxy Note 10+ running Android 12.