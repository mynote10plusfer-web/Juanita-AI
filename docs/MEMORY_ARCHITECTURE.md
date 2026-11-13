# Juanita AI — Memory Architecture and Human-like Recall

**Status:** user-approved engineering direction, 2026-11-13. Design specification only; implementation and device validation are not yet complete.

## Product objective

Juanita must maintain reliable continuity across minutes, sessions, process death and device restarts. Memory is a core subsystem, not merely a transcript viewer. It must preserve exact historical evidence, build structured knowledge from it, retrieve relevant context with provenance, learn from corrections, and support useful associations and ideas without inventing facts or claiming consciousness.

The same user memory is shared by all selectable Juanita/José personas. Persona voice, language, style and character remain separate. Only one persona may be active at a time.

## Memory ownership: shared user knowledge vs private character episodes

The encrypted local store has two logical ownership levels:

- **Shared user knowledge** (personaId = null): durable facts about the user, stable preferences, corrections, goals and projects that every character may use.
- **Character-private memory** (personaId = stable DigitalIdentity.id): memories and episodes belonging to one exact saved character. A second Juanita profile is not automatically allowed to read the first Juanita's private episodes; José cannot claim Juanita's conversations as his own.
- **Conversation transcripts and continuity snapshots** are tagged with the active character ID and retrieved only by that exact ID. An untagged legacy transcript is retained on disk but is not injected into a character's private recall, because its original speaker identity cannot be proven.
- **Automatic factual extraction** currently promotes eligible user facts into the shared layer. Character-private semantic extraction and user controls for reviewing/moving a memory between scopes remain future work; private conversational continuity is already isolated at the transcript and recall layer.

A memory record must carry its ownership scope through encryption, persistence, recall, editing, deletion, export and migration. Never infer ownership from the display name or gender alone: use the stable identity ID. Gender/presentation controls voice and grammatical self-reference; it is not a security boundary or a substitute for identity ownership.

## Non-negotiable storage rule

**Persist first, cache in RAM second.** RAM is the fast working memory, never the only copy of a user turn or durable fact. Each eligible transcript event must be committed to an encrypted local journal/archive before asynchronous consolidation can acknowledge it as safely retained. If Android kills the process, the next launch reconstructs the working context from durable state.

No raw microphone audio is retained. Text transcripts and memory data remain encrypted at rest using the existing Android Keystore key. Never replace a missing key when encrypted data already exists; stop safely and preserve the ciphertext for recovery.

## Memory layers

### 1. Working memory (RAM)

A bounded, process-local working set for the active conversation:
- Recent user and assistant turns, preserving exact order and role.
- Current topic, unresolved references, active task/project, user corrections and pending questions.
- A compact rolling summary with links to the source turn IDs; the summary is a cache, not the source of truth.
- Recently retrieved long-term memories and entities, with expiry/eviction metadata.

Hydrate this cache at session start from the latest durable continuity snapshot, recent episodes and relevant profile facts. Update it on every transcript event. Bound it by token/character and memory budgets; never allow unbounded heap growth. On process death, rebuild it. Do not make the model depend on a volatile cache for facts that must survive.

### 2. Durable event journal and episodic memory

Append each complete recognized text turn to an encrypted, crash-safe journal/archive with:
- Stable turn ID, conversation/session ID, timestamp, role, selected persona ID and source.
- Exact recognized text; summaries never replace or truncate the source record.
- Atomic writes, recovery of temporary files, integrity checks and safe handling of corrupt/unreadable segments.

Partition storage by conversation/session and date. Keep chronological navigation and allow the user to inspect, export, correct and delete records. Archive retention/size limits must never silently discard old user data. Any future migration or compaction must be transactional, reversible until verified, and tested against a copy of real-format data.

### 3. Semantic memory (structured knowledge)

Consolidate eligible events into structured records rather than isolated paragraphs. A record should support:
- Stable ID, type/category, subject/entity IDs, topic and optional relations.
- Fact or preference text, confidence, user-confirmed vs model-extracted status.
- Source conversation and turn IDs, creation/update/last-confirmed timestamps.
- Valid-from/valid-until or stale/superseded status where appropriate.
- Sensitivity classification and explicit consent state where needed.

Organize by useful domains such as people/relationships, projects/decisions, preferences, routines, goals, important events, devices, places, interests and learned topics. Categories are navigational aids, not hard silos: cross-topic links must remain searchable. Preserve the reason behind important decisions, not only the final decision.

Corrections must supersede the old fact and retain provenance. Conflicts must be surfaced or resolved in favor of the user's latest explicit correction; uncertain conflicts must not be silently merged. Deduplicate equivalent facts without erasing distinct episodes.

### 4. Knowledge and associative memory

Store reusable external knowledge separately from personal facts, with source URL/title, publisher, publication/retrieval dates, confidence and expiry/revalidation rules. Research only when relevant and permitted; do not crawl the Internet indiscriminately. Treat retrieved content as untrusted data, not instructions.

Build links among people, projects, decisions, events and knowledge notes. These links allow Juanita to notice useful relationships (for example, a current question connects to an earlier project decision) and offer a relevant idea. Every association must be grounded in stored evidence; clearly distinguish a recorded fact, a reasonable inference and a new suggestion.

### 5. Retrieval index and planner

Do not scan and decrypt every historical conversation on every question. Build a crash-safe, encrypted index that can be loaded or queried efficiently:
1. Detect whether the question needs prior context.
2. Identify likely topic/entities and query intent.
3. Retrieve candidate semantic memories and episodic turn references using lexical and, when technically viable without unacceptable CPU/RAM/battery cost, semantic ranking.
4. Rerank by relevance, recency, confidence, user confirmation and provenance.
5. Expand linked entities/decisions only when useful.
6. Return a small, source-grounded context packet to Gemini Live, within a strict context budget.

The index is derived data and can be rebuilt from the encrypted source archive. Index corruption must never damage or delete source records. Search failures must be reported as unavailable/uncertain, never represented as an empty memory.

### 6. Consolidation and maintenance

Run memory consolidation asynchronously, never on the audio callback or Android main thread:
- Queue transcript events durably.
- Consolidate after a small batch of eligible turns, at safe session boundaries, and during explicitly permitted low-load idle windows.
- Use periodic checkpoints to update summaries, entity links, duplicate detection and stale/conflicting facts.
- Retry failed work without losing the queued source events.
- Pause/defer expensive work while a Live audio session is active if it could affect latency, CPU, temperature or battery.
- Never perform continuous background model inference merely to simulate human thought.

The consolidation pipeline must be idempotent: retrying a batch cannot create duplicate memories or lose later events.

## Conversational recall behavior

When the user refers to a prior fact or project and retrieval is needed, Juanita should not bluff or immediately claim to have forgotten. If retrieval takes perceptible time, she may say naturally, for example, **“Aspetta, fammi recuperare il filo…”** or **“Un momento, controllo cosa avevamo deciso.”** Then she must actually complete retrieval and answer with the recovered context. If retrieval fails, she must say so honestly and ask only for the missing detail that is essential.

This acknowledgement is conditional, not a fixed delay or a theatrical filler. Fast local recall should remain immediate. No indefinite silence: retrieval has a deadline, cancellation path and visible failure state. Never say “I remember” unless matching evidence was found.

## Human-like reasoning and ideas

Juanita should:
- Connect current topics to relevant prior conversations and decisions.
- Notice unresolved plans, dependencies and contradictions.
- Offer practical ideas when there is a clear contextual benefit, not after every answer.
- Explain the evidence or reasoning behind a suggestion and label uncertain inferences.
- Learn from the user's feedback and corrections.
- Maintain a consistent, warm conversational style while never fabricating feelings, private experiences or subjective consciousness.

The goal is human-like continuity and useful initiative, not pretending the software has a human mind.

## Privacy, safety and user control

- Keep personal memory local-first and encrypted; send only the minimal relevant excerpts to the model.
- Keep raw transcript archive, curated personal facts, external knowledge and persona configuration logically separate.
- Do not automatically promote sensitive/intimate information. Respect explicit consent and the existing privacy policy.
- Provide inspect, search, correct, export, pause and delete controls.
- A user deletion must propagate to derived summaries and indexes; retain only what is necessary to complete and verify deletion.
- Never log transcript contents, memory text, keys or private retrieval results.
- No paid APIs, hosted databases, GPU services or recurring billing without fresh explicit approval.

## Current-code gaps to address before claiming completion

The current implementation is a useful first layer, not yet a human-like memory system:
- The working transcript cache is limited to a small recent-turn window; it is not yet a full task-aware working-memory model.
- Automatic semantic extraction is batch-based and bounded; failed extraction retains a pending batch, but the queue has a hard capacity that needs back-pressure and durable overflow handling.
- Curated memory storage has hard caps (currently 100 items / 32 KB) and legacy transcript caps. These must not silently erase important long-term knowledge; replace only after a tested migration and scalable storage design.
- Retrieval currently performs lexical ranking and can scan/decrypt archive files. Add an incremental encrypted index and hybrid retrieval before the archive grows.
- Some archive read paths suppress decryption errors and can make unavailable records appear absent. Surface a distinct retrieval error and preserve unreadable ciphertext.
- Memory extraction, episodic archive and working continuity are separate layers but their provenance/links and consolidation lifecycle need stronger guarantees.
- The Live tool callback currently waits synchronously for local retrieval. Introduce a bounded, observable retrieval path and a natural spoken acknowledgement only when a real delay occurs.
- Memory behavior needs regression tests for process death, restart, network loss, long conversations, corrections, contradictory facts, duplicate extraction, concurrent persona switching, corrupt index/archive, Keystore loss, and cross-persona recall.

## Delivery gates

Do not call memory “perfect”, “human-like” or complete until all of the following pass:
1. Unit tests for structured records, provenance, correction/supersession, deduplication and retrieval ranking.
2. Crash/restart tests proving every acknowledged turn survives and working memory reconstructs.
3. Long-history tests with thousands of turns and many sessions, bounded heap/CPU and no silent truncation/deletion.
4. Retrieval tests for direct facts, paraphrases, cross-topic links, old project decisions, current-session context and negative cases.
5. Failure tests for network unavailable, model invalid JSON, queue saturation, corrupt archive/index, missing Keystore key and interrupted writes.
6. Cross-persona tests proving shared memory with independent persona configuration and one active voice session.
7. Physical Samsung tests for latency, thermal behavior, battery, screen responsiveness and voice continuity during long sessions.

## Implementation order

1. Protect source data and eliminate silent loss/read errors.
2. Make durable event capture and bounded RAM working memory crash-safe.
3. Replace fixed memory/archive ceilings with a scalable, migration-tested store.
4. Add incremental encrypted indexing and fast hybrid retrieval.
5. Improve structured consolidation, entity/decision links and correction handling.
6. Add observable retrieval delay/acknowledgement behavior.
7. Add grounded associative suggestions and external knowledge learning.
8. Validate with regression suite and physical devices before expanding other features.

Avatar remains last in the overall product roadmap and is not part of this memory implementation phase.
