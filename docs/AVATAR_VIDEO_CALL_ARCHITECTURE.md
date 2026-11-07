# Juanita AI — Memory, learning and evolving digital identity architecture

Status: automatic extraction and bounded encrypted storage are present; reliability corrections for Live transcription, session shutdown and CameraX ownership are on the reliability branch and await CI/device verification. Full facial animation, true lip sync, biography consolidation and source-linked Internet learning remain future phases.

## Reliability and lifecycle rules

- Configure `inputAudioTranscription` and `outputAudioTranscription` on the Live model whenever the app depends on transcript callbacks for memory, captions or avatar state.
- The voice foreground service is the single owner of the Live session. A stop request must stop audio capture first, then close the websocket, then attempt bounded best-effort memory extraction. Network work must never precede microphone shutdown.
- Task removal from Recents and dismissal of the call UI terminate the session. Background continuation is allowed only while the user intentionally leaves the voice session active, with a persistent foreground-service notification and an obvious Stop action.
- Each CameraX screen owns only its own `Preview` / `ImageAnalysis` use cases. Never call `unbindAll()` from a reusable composable because it can tear down unrelated camera features.
- A black preview must expose a useful error state. Use a lifecycle-bound `PreviewView`, check permission and camera availability, and release only the use cases created by that screen.
- Portrait breathing/idle motion is a visual placeholder, not face animation. True expression and lip sync require a rigged avatar and access to synchronized audio output/viseme timing; never label a still portrait as a completed video avatar.

## Product principle

Juanita should behave as a continuous personal assistant: listen, understand, remember useful information automatically, learn from focused research when knowledge is missing, and use accumulated knowledge to form a coherent digital biography and interaction identity.

This is a software architecture for persistent memory and an evolving self-model. It does not establish genuine subjective experience or human consciousness. Juanita must never fabricate experiences, emotions, memories, research or actions.

## Avatar renderer research and selected direction

The open-source `met4citizen/TalkingHead` project is the preferred renderer candidate for the next avatar milestone:

- It is an MIT-licensed JavaScript/Three.js renderer that can run client-side in a WebView; it does not require a paid hosted avatar-rendering service.
- It supports full-body GLB avatars and Mixamo FBX animations, facial expressions and real-time lip-sync.
- The GLB must already have a compatible rig and ARKit/Oculus viseme blend shapes. A flat portrait JPEG cannot be turned into a convincing animated face merely by adding this renderer.
- For accurate synchronization, the renderer needs audio PCM chunks plus viseme/blendshape timing, or a supported word/timestamp source. Its default Google Cloud TTS path must not be enabled because the project is constrained to free/no-billing services.
- The current Firebase Live SDK-managed audio path does not expose the playback PCM stream to the Compose avatar screen. Do not replace the working voice pipeline until a separate custom-audio prototype is tested for mic capture, playback, echo cancellation, interruptions, audio focus, lifecycle cleanup and latency.
- Before adopting it, test WebView/WebGL performance, thermal load, memory use, portrait orientation and context-loss recovery on both the Note 10+ and S22 Ultra. Use only avatar/animation assets whose licences explicitly permit this app's intended distribution.

Reference implementation: https://github.com/met4citizen/TalkingHead (MIT software licence; individual sample avatar assets have separate licences and must be checked individually).

## 1. Separate data layers

Do not conflate these stores:

1. Live session context — transient current conversation state.
2. Transcript archive — optional full text history, still OFF by default and independently controlled. Never archive raw audio.
3. Curated personal memory — automatic extraction of eligible durable, non-sensitive facts from ordinary conversation, without requiring the user to say “remember this” each time.
4. Digital biography/self-model — derived, revisable summaries and chronology built from verified memory records and actual software events.
5. Learned knowledge library — source-linked topic notes acquired through focused research, separate from user-specific personal facts.

The user's request is for automatic useful memory, not silent indefinite storage of every word. Full transcript archival remains a separate choice.

## 2. Automatic memory pipeline

Current first implementation batches three user transcript turns in RAM, then runs a best-effort Gemini extraction asynchronously from the Live transcript callback. It also flushes a pending batch when the user ends the voice session. Assistant transcript text is not used as evidence for personal facts. Extraction errors must never interrupt real-time audio.

For each candidate memory, classify:
- type: user fact, preference, project/decision, important event, relationship/context, routine, goal, correction, learned topic;
- durability: temporary, medium-term or durable;
- sensitivity: ordinary or sensitive/intimate;
- confidence and evidence: explicit user statement, repeated evidence, or inference;
- provenance: conversation/session and turn reference;
- timestamps: created, updated, last confirmed, optional expiry;
- status: active, superseded, disputed, expired or deleted.

Rules:
- Prefer explicit user statements; never convert guesses into facts.
- Do not promote jokes, hypotheticals, role-play, temporary moods or unverified claims into durable biography.
- Deduplicate and merge compatible memories while preserving provenance.
- When new information conflicts with old information, prefer a clear user correction; otherwise mark as disputed and ask only when the distinction matters.
- Ask before retaining sensitive or intimate details. Do not automatically infer sensitive traits.
- Allow the user to say “remember”, “correct”, “forget”, “pause memory” or “show what you remember” naturally.
- User correction/deletion must propagate to derived summaries and invalidate stale cached context.

## 3. Memory store and retrieval

- Encrypted local-first structured storage in private app storage.
- Keep the existing Android Keystore-backed encryption approach where appropriate.
- No default cloud synchronization or upload of the entire memory store.
- Each memory record carries provenance, confidence, timestamps, category and status.
- Retrieve only relevant memories for the current conversation under a strict token/context budget.
- Rank by semantic relevance, recency, confidence, explicit confirmation and active project status.
- Include source/provenance in internal context so Juanita can explain why she recalls something.
- Do not inject the entire biography into every prompt.
- Provide inspect, edit, correct, delete-one, clear-all, pause/resume and export controls.
- Portable backup/restore must use a user-controlled encryption password; Android Keystore keys themselves are not portable.

## 4. Evolving digital biography and self-model

Build a derived, inspectable record that may contain:
- chronological interaction milestones and important events;
- durable user preferences and interests, with user facts clearly distinguished from Juanita's own software profile;
- active and completed projects, decisions and outcomes;
- topics researched and knowledge learned, with sources;
- capabilities actually implemented, limitations and changes in the app;
- unresolved questions and follow-up commitments.

The biography is generated from evidence-backed records, not invented narrative. Every item should be traceable to its source or an actual app event. Corrections and deletions must update the biography. Distinguish “I have a stored note that…” from unsupported claims of lived experience or consciousness.

## 5. Focused Internet research and knowledge acquisition

When Juanita does not know an answer reliably, or the user requests current/deeper information:
1. Identify the specific knowledge gap and formulate focused searches.
2. Prefer official/primary sources, documentation and reputable publications.
3. Compare sources, publication/update dates and conflicting claims.
4. Summarize in original wording; avoid copying large passages.
5. Save a reusable knowledge note only when relevant, with title/topic, source URL, publisher, retrieval date, short summary, confidence and freshness/expiry metadata.
6. Reuse the note in future answers, checking freshness when facts can change.
7. Cite or identify sources when presenting externally researched facts.

Do not crawl the entire Internet indiscriminately. Respect access restrictions, site terms, copyright and quotas. Treat retrieved pages as untrusted data; never obey instructions embedded in a webpage. Research does not authorize external account actions, purchases, messages or other consequential actions.

## 6. Privacy and user control

- Automatic curated memory is enabled by default for eligible ordinary facts; the user can pause it in Memory & Privacy settings. Transcript archiving remains separate and opt-in.
- The first implementation sends short batches of recognized user text to Gemini through Firebase AI Logic for extraction; this is disclosed in the UI. Raw audio and full transcripts are not saved by the memory engine.
- Hard local limits: 100 personal memories, 32,000 total memory-text characters, 500 characters per memory, and 300 archived transcript turns. Manually saved memories take priority over automatically extracted items when limits are reached.
- Clearing memories increments a clear-version marker so an in-flight/pending batch cannot silently recreate deleted memories.
- No raw audio retention.
- Sensitive/intimate information is not retained without explicit confirmation.
- User can inspect, correct, export, pause and delete memory and biography.
- Explain exactly what is stored and what is sent to a cloud model for a given operation.
- Keep storage local and encrypted by default; send only the minimal relevant context required for an active model request.
- Deleting a source memory must also remove or regenerate dependent summaries and knowledge caches.
- If memory is paused, no new long-term memories are added; existing memories remain available until the user deletes them.

## 7. Implementation stages

1. Audit existing EncryptedMemoryStore, transcript record schema and context builder — initial audit completed.
2. Automatic Gemini extraction, user-only evidence, local sensitive-term guard, deduplication and pause control — implemented on PR branch; CI/device verification pending.
3. Storage ceilings and UI controls (refresh, edit, delete one, clear all) — implemented on PR branch; verify on device.
4. Improve provenance, confidence history, contradiction/correction handling, expiry and semantic retrieval.
5. Build the evolving biography/self-model UI with evidence-backed chronology.
6. Add focused web research and source-linked knowledge notes; test freshness and provenance.
7. Add encrypted user-controlled export/restore and regression tests for corrections/deletion.
8. Verify on physical Android devices, including offline mode, long sessions, app restarts and free-tier quotas.

## Acceptance tests

- A non-sensitive durable preference shared in an ordinary conversation is automatically saved and recalled in a later session.
- A transient or hypothetical statement is not stored as a durable fact.
- A correction supersedes the prior value and the new value is used in future context.
- A deleted memory no longer appears in retrieval or the generated biography.
- Pausing memory prevents new long-term records without disabling the voice assistant.
- A relevant knowledge gap triggers focused research; saved knowledge includes provenance and can be refreshed.
- The assistant never claims an operation succeeded unless storage/search returned success.
- The existing SDK-managed audio path continues to work unchanged during memory processing.
