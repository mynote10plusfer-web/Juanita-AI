# Juanita AI — Memory Architecture and Human-like Recall

**Memory recall update — 2026-11-22 (PR #58, exact-head validation pending):** the live `searchPastConversations` response now includes up to eight newest saved turns from the active encrypted continuity snapshot before keyword-ranked long-term memories and older episodes. This recent-turn section is independent of query keyword matching, so vague references can still recover the just-discussed subject. Each excerpt is capped at 360 characters to reserve context budget for the newest turns and older relevant memories. The active session ID is passed from `JuanitaVoiceService`; the source snapshot remains identity-scoped and encrypted. A regression instrumentation test covers a vague query with no matching content keywords. This is a targeted reliability improvement, not a claim of complete semantic recall; it depends on Gemini invoking the retrieval tool and on transcript turns having reached the continuity writer. Exact-head CI and physical Live testing remain required.

**Status update — 2026-11-19:** the cumulative implementation is maintained on PR #58, branch `integration/unified-rollup-2026-11-18`. Provisional-memory recall, a conservative associative graph, multilingual concept normalization and rebuildable encrypted indexes are present. Recent session context now selects bounded identity-scoped journal frames before decrypting them; packed legacy archives remain a compatibility fallback and may still require a full per-session decode. Device validation remains incomplete. Durable personal memories already use independently encrypted per-record files; conversation transcripts already use encrypted per-session archives and append-only AES-GCM journal frames. The old 100-record/32 KB cap no longer applies to durable memory records. Do not restart that migration. The remaining major gaps are incremental index maintenance (the current index is invalidated and rebuilt lazily after source changes), semantic/paraphrase retrieval beyond a small deterministic synonym map, non-blocking observable retrieval, queue overflow handling, and scale/crash/device validation. Automatic extracted memories were previously stored as provisional records but excluded from both session-start context and live recall; the current branch fixes that visibility while explicitly labelling them provisional.

**Status:** user-approved engineering direction, 2026-11-13. Design specification and implementation are in progress; device validation is not complete.

## Product objective

Juanita must maintain reliable continuity across minutes, sessions, process death and device restarts. Memory is a core subsystem, not merely a transcript viewer. It must preserve exact historical evidence, build structured knowledge from it, retrieve relevant context with provenance, learn from corrections, and support useful associations and ideas without inventing facts or claiming consciousness.

Shared durable user knowledge is available to selectable Juanita/José personas, while transcript episodes and private memories remain scoped to the exact stable DigitalIdentity ID. Persona voice, language, style and character remain separate. Only one identity may be active at a time.

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

The current implementation is a useful first layer, not yet a complete memory system:
- Durable curated memory records are already independently encrypted per record; the former 100-item / 32 KB cap does not apply to these records. Legacy non-memory metadata and pending queues still use the encrypted SharedPreferences envelope and need independent review.
- The automatic extraction queue is encrypted and survives process recreation, but is bounded to eight turns. Add explicit back-pressure/overflow handling that never drops the only copy of an unprocessed turn.
- Episodic transcript retrieval remains lexical (not general semantic understanding), but it now uses a separate AES-GCM encrypted derived index containing normalized terms and routing metadata, never transcript plaintext. The index is built lazily from the authoritative encrypted archives, updated when a new journal turn is appended to an already-indexed archive, invalidated before transcript deletion/retention or metadata mutations, and atomically rewritten. This append path does not rescan or decrypt existing transcripts, although it serializes the current metadata index again. If the index is missing or corrupt it is rebuilt from source archives. Search ranks indexed metadata and decrypts only the selected journal frames; legacy packed/session and metadata-envelope records retain compatibility fallback paths. This avoids rescanning and decrypting every retained session on ordinary indexed searches. For very large archives, session-sharded indexes or a posting-list structure should be benchmarked before further scaling claims. Curated-memory recall has its own encrypted concept index; unlike the episodic index, it is still invalidated and fully rebuilt after source-memory mutations. Curated-memory recall also has a conservative query-scoped association graph: direct matches anchor one-hop links only when records share at least two meaningful concepts (or one distinctive rare concept), while the existing topic fallback remains lower priority and is labelled separately. A small deterministic Italian/Spanish/English concept map bridges high-confidence equivalents. The graph exposes bridge terms so the model can distinguish direct evidence from association. Neither index is a semantic model or a durable knowledge graph. Next: broader paraphrase handling, provenance-backed entity/decision relations, and benchmarked retrieval.
- Lexical matching can miss paraphrases. Curated-memory lookup now normalizes a small, explicitly curated set of high-confidence Italian/Spanish/English equivalents (for example moto/motocicletta/scooter, voice/voce/voz, and memory/memoria/recuerdo). This is deterministic and local, but is not a general multilingual semantic model. Expand only with regression tests and keep keyword fallback.
- Automatically extracted memories are deliberately unconfirmed in the UI. They must nevertheless be available to recall as clearly labelled provisional evidence; excluding them made learned information invisible. This is being corrected on the 2026-11-16 memory-recall branch.
- The Live tool callback currently waits synchronously for local retrieval. The model is now instructed to reason transparently over multiple retrieved records, compare dates and corrections, distinguish evidence from inference, and avoid assuming causation. Introduce a bounded, observable retrieval path and a natural spoken acknowledgement only when a real delay occurs; do not block transcript intake or audio callbacks.
- Memory extraction, episodic archive and working continuity are separate layers but their provenance/links and consolidation lifecycle need stronger guarantees.
- Some archive read paths suppress decryption errors and can make unavailable records appear absent. Surface a distinct retrieval error and preserve unreadable ciphertext.
- Memory behavior needs regression tests for process death, restart, network loss, long conversations, corrections, contradictory facts, duplicate extraction, concurrent persona switching, corrupt index/archive, Keystore loss, and cross-persona recall..

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
