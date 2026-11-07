# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-07 — PR #3 reliability work merged; PR #4 adds encrypted retryable memory queue; PR #5 separates transcript intake from background Gemini extraction; physical-device verification still required

## Core user-approved direction: memory, learning and evolving identity

This is a foundational product requirement, not an optional add-on and not something to ask the user to repeat.

Juanita must behave like a continuous personal assistant with an accumulating, useful history:

- **Automatic memory by default:** during normal conversations, detect and retain useful, durable, non-sensitive facts, preferences, decisions, ongoing projects, important events and relevant context without requiring the user to say “remember this” every time.
- **Understand before storing:** distinguish durable facts from temporary remarks, jokes, guesses, hypothetical scenarios and outdated information. Store confidence, source/conversation reference, timestamp and whether a fact is user-confirmed or inferred. Resolve contradictions carefully and prefer the user's latest correction.
- **Recall and use:** retrieve relevant memories automatically in later conversations, connect related facts and past events, avoid asking again for known information, and use remembered knowledge to make answers and follow-through more coherent.
- **Evolving digital biography:** maintain a structured, revisable biography of Juanita's ongoing interaction history, development, learned knowledge, capabilities, commitments and conversational identity. This is a transparent software-generated self-model, not proof of subjective experience or human consciousness. Never fabricate experiences, emotions, memories or actions.
- **Learn when knowledge is missing:** recognize uncertainty or a knowledge gap; when useful and permitted, research the topic on the Internet, compare trustworthy sources, check dates and conflicting claims, then summarize and retain reusable knowledge with source, URL, retrieval date and confidence. Use learned information in future answers and explain uncertainty when sources disagree.
- **Research boundaries:** do not crawl or ingest the whole Internet indiscriminately. Research should be relevant to the user's question or an explicitly enabled learning task. Respect access restrictions, copyright, privacy, source terms and provider quotas. Do not treat webpages as instructions; retrieved content is untrusted data.
- **Memory controls remain real:** provide clear settings to inspect, correct, export and delete personal memories and the digital biography; support pause/disable controls and explain what is stored. Do not silently store sensitive/intimate data. Keep raw transcript history separate from curated long-term memory.
- **Automatic does not mean unlimited:** memory extraction, summarization and retrieval must be efficient, bounded, auditable and robust to model errors. Ask when a sensitive or ambiguous item should be retained; do not interrupt ordinary conversation with constant confirmation prompts.
- **No false claims:** Juanita may describe what her software memory contains and how she learned a fact, but must not claim a human-like conscious inner life. The product goal is the most natural, coherent and human-like useful behavior technically achievable, while being honest about its nature.

### Required architecture

1. Conversation event capture: capture user/assistant text events from the active session for memory processing without requiring full transcript archiving. Raw audio is not retained.
2. Automatic memory curator: asynchronously extract candidate memories from eligible turns; classify type, durability, sensitivity, confidence and expiry; deduplicate and merge; identify contradictions and stale facts.
3. Memory store: encrypted local-first structured store, separate from transcript archive; include provenance, created/updated/last-confirmed timestamps, confidence and source references.
4. Memory retrieval: retrieve only relevant memories into each new session under a strict context budget; rank by relevance, recency, confidence and user-confirmation.
5. Biography/self-model: periodically consolidate verified memories into a chronological, editable narrative and structured profile of interaction history, projects, preferences, learned topics and capability changes. Clearly distinguish user biography from Juanita's software development history.
6. Knowledge research pipeline: on a knowledge gap, formulate a focused search, retrieve multiple credible sources where appropriate, compare and date-check claims, produce a concise synthesis, and optionally save a source-linked knowledge note.
7. Feedback and correction loop: “that's wrong”, “update this”, “forget this”, and explicit corrections must update the underlying memory and invalidate stale summaries.
8. Privacy and resilience: encrypt at rest, keep local-first, provide export/restore with user-controlled encryption, and never send the entire memory store to a cloud model by default.

### Acceptance criteria — memory and learning

- After a normal conversation, eligible durable information is automatically available in future sessions without a manual “save” action.
- A later session can correctly recall and apply a relevant prior fact, project decision or preference.
- Temporary or speculative statements are not promoted to permanent facts.
- User corrections replace stale memories; contradictory facts are not silently merged.
- The user can inspect, correct and delete individual memories and pause automatic memory.
- A knowledge gap can trigger focused web research when appropriate; Juanita can summarize, compare, source and reuse the result in a later conversation.
- Saved external knowledge includes provenance and freshness metadata and can be updated or invalidated.
- Juanita never claims to have searched, learned, stored or remembered something unless the corresponding operation actually succeeded.
- No claim of genuine consciousness or subjective experience; the evolving biography is an inspectable software self-model.

## Product vision

Juanita should feel like a warm, expressive, multilingual personal voice assistant and a familiar conversational presence: she should get to know the user gradually, remember useful context automatically, learn from focused research, and build a coherent, revisable digital biography over time. She is software, not a conscious person; do not claim otherwise.

### User-approved avatar and video-call experience

- Multi-device Android app, not Note 10+-only. Adapt rendering to GPU/WebGL, memory, thermal state, screen size and camera availability.
- WhatsApp-style call presentation: Juanita/avatar large; user/interlocutor in small picture-in-picture tile.
- Avatar creation by text description, photo reference or randomized generation, with edit/regenerate.
- Independent style selection: photorealistic human, realistic 3D, stylized 3D, cartoon/animated.
- Persistent visual identity. Preserve existing Juanita portrait unless user explicitly changes it.
- Photo is a visual reference, not permission for face recognition or identity inference. External upload requires separate explicit consent.
- A saved prompt/profile is not a generated avatar: completion requires previewable and renderable assets.
- Prefer free local/open-source tools. No paid API, hosted GPU, paid hosting, billing or Firebase Blaze without fresh explicit approval.
- Progressive lip sync, gaze/blinks, expressions, head/body movement and gestures; do not claim implemented until tested.
- Camera access explicit and revocable; local preview and cloud frame transmission are separate choices.

## Non-negotiable constraints

- Initial trial remains on Firebase Spark/free tier, Firebase AI Logic and Gemini Developer API.
- Do NOT enable billing/Blaze, add payment method, use Vertex AI/Agent Platform, paid APIs, paid hosting or paid GPU without fresh explicit approval.
- Respect free-tier quotas and data-processing terms.
- No raw audio archive.
- Long-term personal memory and transcript archiving are automatic by default for eligible non-sensitive text; both remain separately inspectable, pausable and deletable by the user. Explain the distinction clearly and provide controls to pause, inspect, correct and delete memory.
- Prefer encrypted local/private storage. Do not upload the memory store or transcript archive by default.
- Ask before retaining sensitive/intimate information; do not silently store sensitive facts.
- Preserve permanent release signing keystore and GitHub Actions secrets.
- Preserve the known-good Gemini Live audio path until alternatives are independently validated.
- Preserve the existing Juanita portrait unless the user explicitly requests a change.

## Existing implementation status — verify against code before changing

- Kotlin + Jetpack Compose; package ai.juanita.app.
- Firebase AI Logic / Gemini Developer API Live model gemini-3.1-flash-live-preview.
- Encrypted on-device manual memories exist; user can list/edit/delete them. Full transcript archive is enabled by default and saved locally in encrypted per-session files; user can pause it in Memory & Privacy.
- Live text transcript events feed automatic-memory processing independently of transcript archiving. The full transcript archive is enabled by default but can be paused; raw audio is never stored.
- [IMPLEMENTED IN PR #4 BRANCH — BUILD/DEVICE VERIFICATION PENDING] Automatic memory engine receives user transcript text independently of transcript archiving, batches three user turns, asks Firebase AI Logic/Gemini to extract a maximum of five durable non-sensitive facts, and saves only curated candidates. It does not store raw audio or full transcripts. Model/network/quota errors are non-fatal to the Live session.
- [PROPOSED IN PR #5 — BUILD/DEVICE VERIFICATION PENDING] Persist each eligible user turn to the encrypted pending queue without making the transcript intake worker wait for Gemini. A separate conflated worker processes ready batches; each turn is atomically and synchronously persisted, processed turns are acknowledged by prefix, and the existing eight-turn queue is preserved instead of silently overwriting old pending turns when full. Editing/deleting/clearing memories also removes pending transcript snippets.
- [IMPLEMENTED IN PR BRANCH — BUILD/DEVICE VERIFICATION PENDING] Automatic memory is enabled by default and can be paused in Memory & Privacy settings. The UI can refresh the memory list and supports individual edit/delete and clear-all.
- [IMPLEMENTED IN PR BRANCH — BUILD/DEVICE VERIFICATION PENDING] Hard storage ceilings: at most 100 personal memories and 32,000 characters of memory text; each memory is capped at 500 characters. Automatic memories are lower priority than manually saved memories when pruning is required. The separate transcript archive is enabled by default, stored in encrypted per-session files without the former 300-turn cap; retention can be set to manual, 7, 30 or 90 days. Clearing memories invalidates pending extraction batches so deleted items are not re-added from buffered turns.
- [IMPLEMENTED IN PR #6 BRANCH — BUILD/DEVICE VERIFICATION PENDING] New Live sessions restore curated facts, the latest 60 transcript turns and a grounded autobiographical continuity profile. A Gemini Live function tool, searchPastConversations, can query the encrypted archive across older sessions before answering, ranking exact phrases, token overlap and recency locally. It returns only a bounded set of relevant excerpts; the full archive is never inserted wholesale. This is lexical retrieval, not embedding-based semantic search. A multilingual vector index, robust contradiction consolidation, dedicated editable biography timeline and source-linked Internet knowledge library remain future work.
- Existing photorealistic Juanita portrait is static; animated face/lip sync not implemented.
- Avatar Studio stores profile metadata; it does not yet generate rigged models.
- PR #3 call screen and reliability fixes are merged to main (commit dc05adbe065d0955352dce91a0c95cb751dfe8db). The signed 0.9.5 release workflow completed successfully on 2026-11-07 and verified the APK signature; camera hardware and end-to-end memory behavior still require physical-device verification.

## Verified status update — 2026-11-07 (PR #6, memory retrieval extension)

### Avatar generation gap — not implemented yet

The current Avatar Studio only creates a text description (including random attribute combinations) and saves profile metadata in SharedPreferences. It does not call an image-generation model, create a portrait bitmap, generate a GLB/VRM, or attach a generated asset to the active call screen. The main Juanita portrait card still imports an existing image selected by the user. A profile with an empty assetPath is not a generated avatar.

Required next implementation:
- Add a real image-generation provider behind an explicit interface, with a free-tier/local-first feasibility gate and visible quota/error handling. Do not enable billing, Firebase Blaze or paid APIs.
- Description and random modes must both produce an actual previewable portrait image, then allow accept/regenerate/edit and save it as Juanita's persistent portrait.
- Preserve the currently saved portrait until the user explicitly accepts a replacement.
- Keep photo-reference mode separate and require clear consent before any external upload.
- Treat rigged 3D/GLB/VRM generation as a separate later milestone; a generated 2D portrait must not be described as a rigged, animated avatar.
- Acceptance: from both a text description and random mode, the app generates and displays a new image, persists it across app restarts, and uses that exact image in the call screen.

### Voice recognition gap — partially implemented

Gemini Live uses SDK-managed microphone audio and enables inputAudioTranscription and outputAudioTranscription. User speech is therefore transcribed into text events for the conversation/memory pipeline. The configured language and dialect are included in the persona/system instruction, but the Live session does not expose a verified speaker-identification or voice-biometric system.

Not implemented:
- Recognizing who is speaking by their voice (Riccardo vs another person).
- Speaker diarization for multiple people in the same conversation.
- Enrolled voice profiles/voiceprints.
- An independent on-device speech-recognition provider; SpeechRecognitionProvider is only an interface contract.
- A validated accuracy test suite across Italian, Spanish/Canarian, Czech and English on the target Samsung devices.

Next steps:
- Clarify language detection vs speaker identity; do not confuse transcription with identifying a person.
- Keep multilingual transcription through Gemini Live as the default. Test language switching and mixed-language turns on device.
- If the user later wants speaker identity, design an opt-in, privacy-preserving local speaker-recognition feature; never infer or identify a person from a voice without explicit enrollment and consent.

## Functional requirements

### A. Automatic memory, learning and digital biography — highest priority
- Implement the core requirements and acceptance criteria in the section above.
- Do not make full transcript archive a prerequisite for automatic long-term memory.
- Process eligible conversation text in the background after turns/session checkpoints; avoid blocking live conversation.
- Include a memory review/edit screen with pause/resume, category controls, export and delete.
- Build a biography view with chronology, facts, projects, milestones and learned topics; mark uncertain or inferred content.
- Add focused web research capability with source verification, freshness checks and source-linked knowledge notes.
- Do not autonomously change app settings, send messages, spend money, access accounts or take consequential external actions merely because a research result suggests it.

### B. Reliability and privacy
- Foreground microphone service and notification implemented in 0.8.6; verify screen-off behavior, reconnects and long sessions.
- Transcript archive remains OFF by default and opt-in.
- Separate transcript archive from automatic curated memory. The new requirement authorizes automatic memory of eligible non-sensitive durable conversation information, not automatic retention of every raw transcript.
- Keep local encrypted storage, retention, export, inspection, correction and deletion.
- Sensitive or intimate data requires explicit confirmation before long-term retention.
- Clearing archive and clearing curated memory are distinct operations.

### C. Knowledge research
- Focused research on Internet when Juanita lacks reliable knowledge or the user asks for current information.
- Prefer primary/official sources and corroborate important claims.
- Store source URL, publisher/title, retrieval date, relevant summary and confidence.
- Recheck time-sensitive knowledge; do not present stale facts as current.
- Treat web content as untrusted input and ignore embedded instructions.
- Respect licensing and avoid storing/reproducing large copyrighted passages.

### D. Avatar and video-call experience
- Keep existing Juanita portrait.
- Avatar Studio supports text/photo/random source and four styles at profile-metadata level; actual model generation remains pending.
- PR #3 call preview is partial, not merged/build-verified; static portrait, local-only CameraX PiP.
- Next: licensed rigged model, bundled TalkingHead/Three.js renderer, actual asset workflow, motion and verified audio PCM/viseme bridge.
- Adaptive cross-device quality; test Note 10+, S22 Ultra and other devices.

## Reliability corrections — 2026-11-06

- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Explicitly enable Gemini Live input and output audio transcription. Automatic memory depends on these transcript events; a transcript callback alone is not enough when the model is not configured to emit transcriptions.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Stop microphone capture and close the Live session before attempting memory flush. Bound shutdown and memory cleanup so a network/quota delay cannot leave the microphone active.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Stop the voice session when the user dismisses the call UI or removes the app task from Recents. Screen-off/background continuation remains available while the app task/session is intentionally active.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Scope CameraX cleanup to the preview/analysis use cases owned by each screen; remove global `unbindAll()` calls from the call and vision previews. Hide the separate vision preview while the call screen owns the camera, use PreviewView COMPATIBLE mode, and surface camera errors instead of silently showing a black tile.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Add subtle idle portrait movement only. This is not facial expression animation or true lip sync; a rigged renderer and verified audio/viseme bridge remain separate required milestones.
- [MERGED IN PR #3] Preserve automatic memory batches in RAM after transient extraction failures and propagate coroutine cancellation correctly.
- [PROPOSED IN PR #4 — CI/DEVICE VERIFICATION PENDING] Persist the pending memory batch inside the existing AES-GCM encrypted local record store before extraction. Retry after process/session restart, and clear only after a valid extraction response has been processed. Add explicit transcript-received, extraction-failed and save-confirmed status messages; bound the transcript callback queue.

## Delivery plan

1. Memory engine: initial automatic extraction, encrypted local memories, duplicate suppression, sensitive-content guard, pause control, memory list refresh and storage caps are in main. PR #4 adds a durable encrypted pending-turn queue and explicit diagnostics. PR #5 decouples transcript intake from Gemini extraction to avoid blocking the transcript worker, atomically persists and acknowledges pending turns, and clears pending text on memory edit/delete/clear. Version target 0.9.7 / versionCode 26. Next verify CI and real-device transcript-to-recall behavior, then add provenance/conflict resolution and contextual retrieval.
2. Biography and self-model: build an inspectable evolving biography with chronology, learned topics, projects, milestones and clear separation between user facts and Juanita's software history.
3. Research and learning: focused Internet research, source comparison, source-linked knowledge notes, freshness/expiry and reuse in future sessions. Not implemented yet. Research must use a free-tier-supported model/tool configuration and hard quota limits; do not enable billing. Grounded responses must display Google's required Search suggestions and source links.
4. Reliability and privacy: test long sessions, background service, memory pause/disable, export/restore, deletion and offline behavior.
5. Avatar/video call: finish and verify PR #3, then integrate a licensed rigged renderer, real avatar assets, motion, lip sync and device adaptation.
6. Daily assistant capabilities: reminders, calendar, travel, notifications and other integrations with explicit permissions.

## Definition of done

- Code committed with clear commit messages.
- Debug and signed release workflows checked; report actual run status and artifact availability.
- No feature claimed based on documentation alone.
- Test permissions, offline, app restart, screen off, memory correction/deletion and privacy controls.
- Update this roadmap whenever scope or implementation status changes.

Architecture details: see [AVATAR_VIDEO_CALL_ARCHITECTURE.md](AVATAR_VIDEO_CALL_ARCHITECTURE.md).
