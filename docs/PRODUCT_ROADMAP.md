# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-06 — automatic memory, continuous learning and evolving digital biography requirements added

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
- Long-term personal memory should be automatic for eligible non-sensitive facts, while transcript archive remains a separate user-controlled feature. Explain the distinction clearly and provide controls to pause, inspect, correct and delete memory.
- Prefer encrypted local/private storage. Do not upload the memory store or transcript archive by default.
- Ask before retaining sensitive/intimate information; do not silently store sensitive facts.
- Preserve permanent release signing keystore and GitHub Actions secrets.
- Preserve the known-good Gemini Live audio path until alternatives are independently validated.
- Preserve the existing Juanita portrait unless the user explicitly requests a change.

## Existing implementation status — verify against code before changing

- Kotlin + Jetpack Compose; package ai.juanita.app.
- Firebase AI Logic / Gemini Developer API Live model gemini-3.1-flash-live-preview.
- Encrypted on-device manual memories exist; user can list/edit/delete them. Transcript archive is separately opt-in and OFF by default.
- Live text transcript capture is wired only when archive consent is enabled; raw audio is never stored.
- Current memory curator does not yet automatically extract durable memories from every eligible conversation. Automatic extraction, consolidation, retrieval quality and biography generation remain implementation work.
- Existing photorealistic Juanita portrait is static; animated face/lip sync not implemented.
- Avatar Studio stores profile metadata; it does not yet generate rigged models.
- PR #3 call screen is a partial preview and remains unmerged/build validation pending.

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

## Delivery plan

1. Memory engine: implement automatic durable-memory extraction, sensitivity filtering, deduplication, provenance, correction/forgetting, relevance retrieval and encrypted local persistence.
2. Biography and self-model: build an inspectable evolving biography with chronology, learned topics, projects, milestones and clear separation between user facts and Juanita's software history.
3. Research and learning: focused Internet research, source comparison, source-linked knowledge notes, freshness/expiry and reuse in future sessions.
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
