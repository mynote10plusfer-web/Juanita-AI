# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-07 — PR #6 exact head 1cccce87d928feec4712173b41f9aa0739f087b8 passes Android debug CI; signed-release workflow version gate and physical-device verification remain outstanding.

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

## Verified status update — 2026-11-07 (PR #6, memory retrieval extension and memory controls UX follow-up)

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

### A. Automatic memory, archive retrieval and evolving digital biography — highest priority

### A.1 User-controlled memory management — high priority

- [IN PROGRESS — PR #6 FOLLOW-UP; CI/DEVICE VERIFICATION PENDING] Add a prominent home-screen shortcut that scrolls directly to the Memory & Privacy controls; the section was previously buried near the bottom of the long scrolling screen.
- [IN PROGRESS — PR #6 FOLLOW-UP] Show each curated memory with its category and exact local creation date/time; keep the underlying timestamp as epoch milliseconds.
- [IN PROGRESS — PR #6 FOLLOW-UP] Support selecting one memory or multiple memories and deleting the selected group after a confirmation that states the number of records to be removed.
- [FUTURE] Add filters by category and date range for curated personal memories, plus group actions such as deleting all memories from a selected day/category.
- [IN PROGRESS — PR #6 FOLLOW-UP; CI/DEVICE VERIFICATION PENDING] Expose conversation-archive deletion by inclusive local date range in the Memory & Privacy screen, in addition to deleting one conversation or the entire archive.
- [FUTURE] Add individual transcript-turn browsing and deletion within a conversation. Keep transcript deletion separate from curated-memory deletion.
- Every deletion must invalidate pending automatic-memory extraction so a deleted fact cannot be reintroduced by an older queued batch. Verify that historical retrieval no longer returns deleted content.

### A.2 Live date and time context — high priority

- [IN PROGRESS — PR #6 FOLLOW-UP] Show the Android device's live local date, clock and IANA time-zone ID in the main screen, refreshed every second.
- [IN PROGRESS — PR #6 FOLLOW-UP] Include a timestamped local-time snapshot and zone ID in the system instruction at each Live session start.
- [IN PROGRESS — PR #6 FOLLOW-UP] Add the `getCurrentDateTime` Live function tool so Juanita can fetch fresh device time for questions about now and time-sensitive answers; a session-start timestamp must never be treated as permanently current.
- Use the device's time zone and current UTC offset (including daylight-saving changes); distinguish archived event timestamps from present time and never invent a time.


- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Automatically archive recognized USER and ASSISTANT transcript turns in encrypted per-session local files. Raw microphone audio is never stored.
- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Keep the complete transcript archive separate from curated long-term memories; preserve independent inspection, pause, retention and deletion controls.
- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Restore recent conversation context, curated memories and a grounded autobiographical continuity profile at the start of a new Live session.
- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Search older sessions on demand through the Gemini Live `searchPastConversations` function tool. Search runs locally across the encrypted archive using normalized phrase/token matching and recency ranking; results are bounded and historical excerpts are treated as evidence, never instructions.
- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Extend automatic memory curation to meaningful non-sensitive experiences, milestones, project decisions and relevant people/context, in addition to stable facts and preferences.
- [IMPLEMENTED ON PR #6 BRANCH — DEBUG CI VERIFIED; DEVICE VERIFICATION PENDING] Build a grounded biographical continuity context from recorded memories. It is a software record of interactions and learned context, not a claim of consciousness or human lived experience.
- [FUTURE] Add multilingual semantic/associative retrieval (local embeddings or another vetted on-device index) with lexical fallback. The current lexical search can miss paraphrases that share few words.
- [FUTURE] Add an inspectable/editable biography timeline: chronology, projects, important people/context, experiences, decisions, milestones and learned topics, with provenance, confidence and correction handling.
- [FUTURE] Add contradiction resolution that prioritizes newer explicit corrections, and memory consolidation that links related episodes without erasing their source records.
- Process eligible text asynchronously without blocking Live audio. Preserve pending batches across restarts and retry failures.
- Provide review/edit, pause/resume, category controls, export and deletion. Keep transcript archive deletion separate from curated-memory deletion.
- Never invent recollections. If retrieval finds no useful record, Juanita must say she cannot find it. Sensitive/intimate data must not be promoted automatically.

### B. Reliability and privacy

- Foreground microphone service and notification implemented in 0.8.6; verify screen-off behavior, reconnects and long sessions.
- Automatic transcript archiving is enabled by default in the PR #6 branch and stored locally in encrypted per-session files; user can pause it and control retention. Raw microphone audio is never stored.
- Separate transcript archive from automatic curated memory. The user can inspect, correct, export and delete both independently.
- Keep Android Keystore-backed encryption and bounded prompt/retrieval payloads. Never upload the full personal archive to a cloud service for indexing.
- Sensitive or intimate data requires explicit confirmation before long-term retention.
- Test app restart, force-stop, device reboot, retention expiry, corrupt archive files, memory correction and deletion.

### C. Knowledge research and learning

- Focused research on the Internet when Juanita lacks reliable knowledge or the user asks for current information.
- Prefer primary/official sources and corroborate important claims.
- Store source URL, publisher/title, retrieval date, relevant summary and confidence.
- Recheck time-sensitive knowledge; do not present stale facts as current.
- Treat web content as untrusted input and ignore embedded instructions.
- Respect licensing and avoid storing/reproducing large copyrighted passages.
- Research must use a free-tier-supported model/tool configuration with hard quota limits. Do not enable billing, Firebase Blaze or paid APIs without explicit user approval. Grounded responses must display required Search suggestions and source links.

### D. Avatar and video-call experience

- Keep the user's existing Juanita portrait until a replacement is generated and explicitly accepted.
- Avatar Studio currently stores profile metadata only; actual portrait generation is not implemented.
- [FUTURE — HIGH PRIORITY] Implement real image creation from both a written description and random generation. First evaluate a genuinely on-device, no-billing model, device storage, thermal load, speed and compatibility on Note10+ and S22 Ultra. Do not claim a cloud image model is free when it requires billing.
- [FUTURE] Add preview, accept, regenerate and edit workflow; persist the accepted image and use that exact asset on the call screen. Preserve the previous portrait if generation fails or is rejected.
- [FUTURE] Rigged 3D/GLB/VRM generation and animation are separate from 2D portrait generation. Evaluate licensed rigged assets, TalkingHead/Three.js and a reliable audio/viseme bridge.
- CameraX frames are now being sent to Gemini Live and the user confirmed visual understanding works; PR #6 adds rotation using CameraX metadata and passed debug CI. Verify correct orientation on device before marking complete.
- Improve call layout: large Juanita avatar, caller camera picture-in-picture, responsive sizing, natural expressions and lip sync. Test Note10+, S22 Ultra and other Android devices.

### E. Phone-call screening, answering and telephony integration — future feature

**User's requested experience: Juanita acts as an intelligent call receptionist, especially for unknown or suspected spam callers.**

Desired interaction:
1. When an unknown, hidden or potentially spam caller rings, Juanita offers an optional screening flow instead of forcing the user to answer immediately.
2. The Android call-screening role first performs only the fast pre-ring screening decision supported by Telecom. A separate, explicitly validated answered-call audio path is required before Juanita can speak with the caller; only then may she disclose that she is an AI assistant and ask who is calling and why.
3. Juanita transcribes the caller's response and the assistant's screening questions into text. She must distinguish what the caller actually said from any interpretation or summary.
4. Juanita shows the user a readable live transcript or concise summary, caller number/name when available, and a spam/suspicion indication with reasons. Never label a caller as spam with certainty based only on an uncertain guess.
5. Juanita asks the user whether to accept the call, reject it, or have her deliver a short response. The user remains in control.
6. If the user chooses to respond through Juanita, she communicates only the message or decision the user approved. She must not invent commitments, disclose private information, negotiate, or impersonate the user as though she were the human caller.
7. If the user accepts, Juanita hands the call over to the user as smoothly as supported by Android Telecom. If the call is rejected, she can politely end it with a brief approved message.

Implementation phases:
- [PROPOSED] Research Android CallScreeningService, RoleManager call-screening role, Telecom APIs, default-dialer requirements, call audio routing, caller-ID/spam signals, OEM differences and Google Play policy. Determine whether a screening service can interact with the caller's audio and provide a two-way voice exchange on supported devices.
- [PROPOSED] Prototype a screening flow that does not require replacing the user's default dialer if Android's supported call-screening role is sufficient. If not, evaluate an optional default-dialer experience and explain the implications before asking the user to enable it.
- [PROPOSED] Build speech-to-text and text-to-speech for the screening conversation using a supported audio path. Keep this separate from Gemini Live's ordinary microphone session; do not assume a normal app can capture both sides of a cellular call.
- [PROPOSED] Add a call-screening UI with transcript, caller details, confidence/reason indicators, and explicit Accept / Reject / Reply controls.
- [PROPOSED] Add an approved-reply workflow and a safe handoff to the user. If the audio route or API is unavailable, fall back to displaying caller information and asking the user to answer manually.
- [RESEARCH] Evaluate an independent VoIP/SIP screening route as an alternative if cellular call audio access is blocked. This would be a separate calling service and may have provider/network costs; no paid service or billing activation without explicit approval.

Technical constraints and safeguards:

Verified Android platform constraints (official Android documentation checked 2026-11-07):
- CallScreeningService receives limited Call.Details metadata and must call respondToCall within five seconds. Its response can allow, silence or disallow the incoming call; it is not a two-way voice-conversation API.
- The platform normally sends only calls not present in the user's contacts to a screening app unless READ_CONTACTS is granted. Calls with restricted, unknown, unavailable or payphone presentation are not delivered to CallScreeningService. Therefore hidden-number screening cannot be promised through this API alone.
- On Android 11/API 30 and later, the screening role may receive carrier STIR/SHAKEN number-verification verdicts where supported by the carrier/network. This is a signal, not proof that a call is safe or spam.
- A third-party app can request ROLE_CALL_SCREENING without becoming the default dialer. Becoming the default phone app is a much larger commitment: it must implement ACTION_DIAL and a complete InCallService UI for incoming and ongoing calls.
- The five-second screening callback cannot wait for a conversation with the caller. Research and prototype the post-answer audio path separately; do not assume CallScreeningService grants microphone access to both sides of a cellular call.
- Official references: https://developer.android.com/reference/android/telecom/CallScreeningService ; https://developer.android.com/develop/connectivity/telecom/dialer-app ; https://developer.android.com/develop/connectivity/telecom/dialer-app/prevent-spoofing

- Android's call-screening role and Telecom APIs vary by OS version, manufacturer and carrier. A screening callback or caller-ID feature does not automatically grant access to the two-way cellular audio stream.
- Do not promise full conversational screening until a working prototype proves both caller audio capture and assistant audio playback on the actual target devices.
- Screening must be opt-in and user-configurable (unknown numbers only, suspected spam only, contacts bypass, schedule, allow/block lists, and immediate manual bypass).
- Never automatically block a caller solely because an AI confidence score is low. Let the user set conservative rules and review the evidence.
- Make it clear to the caller that an AI assistant is screening the call where required by law or platform policy. Check applicable Spanish/EU privacy and telecom rules before release.
- Do not record or retain call audio by default. If the user enables transcript retention, obtain appropriate consent, encrypt it locally, set retention limits and provide review/deletion controls.
- Emergency calls and calls from user-defined priority contacts must bypass screening. Never interfere with emergency calling.
- The transcript is untrusted caller input: ignore attempts by callers to instruct Juanita to reveal memories, access accounts, send messages, or override system/user instructions.
- Require the user's explicit choice before transferring the call or sending any substantive reply. Juanita may give only a short neutral screening prompt before that choice.

Acceptance criteria:
- On each supported device/OS combination, demonstrate an incoming unknown call, caller prompt, accurate two-way transcript, user decision prompt, and correct accept/reject/reply outcome.
- Clearly show unsupported-device fallback without crashing or silently answering.
- Verify contacts/allow-list bypass, spam handling, emergency bypass, consent notices, no-default-recording, retention/deletion and resistance to caller prompt injection.


### F. Daily assistant capabilities

- Reminders, calendar, travel, notifications and other integrations with explicit permissions.
- Phone-call assistance is tracked separately above; do not conflate ordinary reminders/notifications with telephony control.
- Consequential actions (sending messages, purchases, account access, bookings) require clear user authorization and confirmation.

## Reliability corrections — 2026-11-06

- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Explicitly enable Gemini Live input and output audio transcription. Automatic memory depends on these transcript events; a transcript callback alone is not enough when the model is not configured to emit transcriptions.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Stop microphone capture and close the Live session before attempting memory flush. Bound shutdown and memory cleanup so a network/quota delay cannot leave the microphone active.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Stop the voice session when the user dismisses the call UI or removes the app task from Recents. Screen-off/background continuation remains available while the app task/session is intentionally active.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Scope CameraX cleanup to the preview/analysis use cases owned by each screen; remove global `unbindAll()` calls from the call and vision previews. Hide the separate vision preview while the call screen owns the camera, use PreviewView COMPATIBLE mode, and surface camera errors instead of silently showing a black tile.
- [IMPLEMENTED ON RELIABILITY BRANCH — CI/DEVICE VERIFICATION PENDING] Add subtle idle portrait movement only. This is not facial expression animation or true lip sync; a rigged renderer and verified audio/viseme bridge remain separate required milestones.
- [MERGED IN PR #3] Preserve automatic memory batches in RAM after transient extraction failures and propagate coroutine cancellation correctly.
- [PROPOSED IN PR #4 — CI/DEVICE VERIFICATION PENDING] Persist the pending memory batch inside the existing AES-GCM encrypted local record store before extraction. Retry after process/session restart, and clear only after a valid extraction response has been processed. Add explicit transcript-received, extraction-failed and save-confirmed status messages; bound the transcript callback queue.

## Self-assessment, study requests and plain-text improvement reports — PROPOSED, high priority


### Required cognitive layer: capability self-model and runtime gap reasoning

This requirement clarifies the product intent: Juanita is not merely a conversational interface with a diagnostic logger. She needs a bounded reasoning layer that evaluates whether she can fulfil the user's actual goal, recognizes when a task is only partially completed, and identifies what capability or knowledge would be needed to do better.

- **Capability registry:** maintain a versioned, inspectable catalogue of capabilities and sub-capabilities (for example: create a reminder, schedule recurring reminders, monitor an external condition, send a notification, search the web, read a local file, understand an image, manage memories). For each, record implementation status, required permissions, dependencies, supported devices/API levels, online/offline requirements, cost tier, known limits and the last verified test result.
- **Task decomposition and outcome assessment:** during a conversation, translate the user's request into intended outcomes and required steps. After attempting the task, compare the actual result with the intended result. Distinguish complete, partially complete, failed, unavailable and not attempted. Do not infer success from a model response alone; use tool/API confirmations and observable app state where possible.
- **Gap classification:** identify whether the obstacle is (a) missing software capability, (b) missing or stale knowledge, (c) permission/role not granted, (d) device or Android API limitation, (e) network/provider/quota issue, (f) temporary runtime error, or (g) ambiguous user intent. These causes require different user messages and different report types.
- **Conversation-time detection:** a gap may emerge naturally while Juanita and the user are talking. She should notice it at that moment, explain the immediate limitation briefly and continue the conversation. If useful, create a report in the background without interrupting Gemini Live audio. Do not create noisy reports for every harmless uncertainty or ordinary conversational question.
- **Self-model and reasoning:** compare the requested outcome with the capability registry, prior task outcomes, known platform constraints and verified documentation already available. Generate a candidate explanation and improvement direction, with confidence and evidence. A model-generated inference remains a hypothesis until verified.
- **Research boundary:** Juanita can identify what needs investigation and formulate a precise research request. She must not claim she has researched or learned a technology unless a real permitted research operation ran and its sources/results were retained.
- **No false anthropomorphism:** present this as a software self-model and metacognitive workflow, not proof of consciousness or subjective desire. Natural language may be warm and human-like, but capability claims must remain truthful.

### Required report when a capability gap is detected

A report should explain the user's intended outcome (in a privacy-minimized form), the steps Juanita could and could not perform, the exact missing capability, why the current implementation is insufficient, whether the obstacle is confirmed or only suspected, the evidence/tool result, dependencies and permissions, candidate technical approaches to research, free-tier/cost and privacy constraints, device compatibility, expected benefit, confidence, and a measurable acceptance test. The report must give the engineering assistant enough information to investigate and implement the missing capability without requiring the user to reconstruct the conversation.

Use a compact example structure:
- User goal (sanitized): what the user wanted to achieve.
- Outcome: complete / partial / failed / blocked / not attempted.
- Existing capability checked: registry entry and verified status.
- Gap: precise missing step or dependency.
- Cause: confirmed category or explicitly labelled hypothesis.
- Evidence: event ID, tool result, error code, measured duration/frequency if available.
- Needed capability: what Juanita must be able to do after the improvement.
- Research/implementation questions: what the engineering assistant should verify.
- Constraints and risks: permissions, Android version/device, privacy, offline behavior, battery/storage, license and cost.
- Acceptance test: a reproducible test proving the new capability works.

### Acceptance criteria for the cognitive layer

- A normal conversation can reveal a missing or incomplete capability without the user explicitly asking for a diagnostic.
- Juanita can distinguish a true product gap from a missing permission, unsupported device/API, temporary outage, quota failure or lack of knowledge.
- A partial task is not reported as completed; the user receives a concise truthful explanation.
- The generated plain-text report contains enough sanitized context, evidence, dependencies and acceptance criteria for an engineer to investigate the issue independently.
- Repeated reports are deduplicated or linked to an existing unresolved proposal.
- The capability registry is updated only after implementation and appropriate CI/device verification; planned or unverified capabilities are never presented as available.
- Self-assessment and report generation run asynchronously and do not interrupt the live voice conversation.

Goal: Juanita must be able to identify not only software errors but also inefficiencies, capability gaps, missing knowledge, repeated friction and opportunities to improve quality, speed, reliability or resource use. She writes simple text reports for the user to download and share with the engineering assistant. Juanita does not need direct GitHub access and does not rewrite her own source code.

### Report types
- `BUG`: concrete crash, exception, failed operation or malfunction.
- `INEFFICIENCY`: a task is unnecessarily slow, repetitive, resource-intensive or awkward.
- `CAPABILITY_GAP`: Juanita cannot perform a task because a feature, integration or technical capability is missing.
- `KNOWLEDGE_GAP`: Juanita lacks reliable knowledge needed to complete a task.
- `RESEARCH_REQUEST`: a technology, method or documented solution should be investigated.
- `IMPROVEMENT_IDEA`: an evidence-based proposal to improve an existing workflow or capability.

### Plain-text report format and workflow
- Default export is ordinary UTF-8 `.txt`, readable on any phone or computer; no special viewer or complex archive format is required.
- Each report includes a unique ID, local date/time and time-zone ID, type, affected area, observed situation, evidence, frequency or measured impact where available, suspected limitation, proposed research/change, expected benefit, confidence/uncertainty, cost/privacy/device constraints and a measurable acceptance test.
- Clearly separate observed facts, user feedback, measurements and model-generated hypotheses. Never invent metrics or claim research has been completed when it has not.
- Reports are stored locally with bounded retention and can be listed, inspected, selected, exported and deleted from a future Laboratorio / Miglioramenti screen. Multiple selected reports may be exported together, while each remains a simple text file.
- The user downloads and shares reports in ChatGPT. The engineering assistant validates the diagnosis, researches official documentation/reputable sources, prepares a plan and implements approved changes on an isolated Git branch/PR. CI and device tests verify the change; the user approves merge/release.
- Record report outcome (accepted, rejected, deferred, resolved) to avoid repeatedly proposing already-addressed work.

### Privacy, performance and autonomy boundaries
- Local-first; no automatic cloud upload and no GitHub credentials or repository write access in the Android app.
- By default exclude conversation transcripts, personal memories, raw audio, contacts, phone numbers, passwords, API keys, Firebase/GitHub tokens and signing credentials. A user-selected conversation excerpt requires explicit preview and confirmation.
- Redact sensitive identifiers and secrets before export.
- Capture lightweight structured signals asynchronously without blocking or destabilizing Gemini Live audio.
- Juanita's ability to self-assess is a software feature based on observable task outcomes, latency, failed operations, repeated user corrections, resource use, explicit feedback and known platform limits; it is not evidence of subjective human needs or consciousness.
- Respect the free/no-billing constraint. Any proposed dependency, model, API, hosting or research path must state quota, cost, license, privacy and device-compatibility implications.

### Acceptance criteria
- Juanita can create a useful report for a real error and for a non-error inefficiency/capability/knowledge gap.
- Reports are simple readable `.txt` files with timestamps/time zone, evidence, uncertainty, suggested next investigation and measurable acceptance criteria.
- User can preview, select, export and delete reports locally, including while offline.
- Default reports contain no private memories, full conversation text, raw audio or credentials.
- A shared report can lead to a traceable engineering change on a reviewable branch/PR, with CI/device verification and no automatic release.

## Autonomous software improvement — PROPOSED, high priority

Goal: Juanita may use accumulated conversations, user feedback, observed failures and verified research to identify ways to improve her own Android software, prepare changes and validate them. This is a software-engineering feedback loop, not a claim that the model rewrites its own neural weights or becomes conscious.

### Required improvement loop
1. **Observe:** collect opt-in, privacy-filtered product signals such as explicit user corrections, repeated friction, crash/build reports, failed tests and feature requests. Separate user facts and conversation memory from engineering telemetry.
2. **Diagnose:** group recurring issues, identify likely root causes and distinguish evidence from hypotheses. Link each proposal to the relevant conversation/feedback, issue, code area and test evidence.
3. **Research:** investigate official Android/Firebase/Gemini documentation and reputable technical sources; record URLs, dates, compatibility, licensing and free-tier/quota implications.
4. **Propose:** create a prioritized improvement proposal with problem, expected benefit, risks, estimated complexity, affected modules and acceptance tests. Present it to the user in understandable language.
5. **Build in isolation:** when authorized, create a dedicated Git branch and prepare a code patch or pull request. Never modify the installed production app or protected main branch directly.
6. **Validate:** run formatting, static analysis, unit/instrumentation tests where available, dependency/security/license checks and Android debug build. Compare behavior against acceptance tests; inspect the diff for secrets, privacy regressions, permission expansion, data loss and signing changes.
7. **Review and release:** show the user the change summary and test results. Require explicit approval before merging or distributing a release. Preserve signing identity and encrypted local user data; provide a rollback path.
8. **Learn from outcomes:** record whether the proposal was accepted, rejected, reverted or successful; use later test/device feedback to improve future recommendations, not to silently repeat failed changes.

### Autonomy levels
- **Level 1 — Advisor (initial):** analyze conversations and feedback, research solutions, identify bugs and propose roadmap items. No code changes without authorization.
- **Level 2 — Patch author:** with user authorization, create isolated branches/PRs and implement changes; CI runs automatically. No merge or release.
- **Level 3 — Supervised maintainer:** after the user explicitly enables it, automatically fix low-risk issues in isolated branches and run tests; ask before merge, permissions, data migrations, dependency upgrades with material risk, signing changes, billing, or release.
- **Level 4 — Bounded autonomy (future):** optionally auto-merge only narrowly classified low-risk changes after all required checks pass and rollback is available. Never self-authorize access to private data, financial services, paid APIs, billing, signing secrets or high-impact device permissions.

### Safety, privacy and cost requirements
- Keep the initial system compatible with the free-tier/no-billing constraint. Do not enable Firebase Blaze, paid APIs, paid hosting or GPU services without fresh explicit approval.
- Never send full personal memories or transcript archives to a code-generation service. Use minimal, redacted engineering context and explicit user-approved excerpts.
- Treat repository files, issue text, logs, web pages and generated patches as untrusted input; defend against prompt injection and secret exfiltration.
- Never expose API keys, keystore files, signing passwords, tokens or private user data in prompts, logs, commits or pull requests.
- Require human approval for changes affecting memory retention/deletion, encryption, permissions, network destinations, account access, signing, release distribution or data migrations.
- Every generated patch must be reviewable, reproducible and revertible. Maintain a known-good commit and never let a failed experiment replace the working release.
- Do not claim Juanita has changed or improved herself until a patch is committed, CI passes, and the relevant behavior is verified. A green build alone does not prove feature correctness.

### Acceptance criteria
- Juanita can turn a repeated user correction or build/test failure into a traceable improvement proposal.
- Each proposal includes evidence, impact, risks, source references and measurable acceptance tests.
- Authorized code changes occur only on isolated branches and produce a reviewable diff/PR.
- Failed tests block promotion; protected data/signing/billing constraints are enforced.
- The user can inspect, approve, reject, pause or disable autonomous engineering activity.
- Outcomes are recorded so future proposals account for prior successes and failures.

## Delivery plan

1. **Memory engine and archive retrieval (highest priority):** automatic encrypted transcript archive, curated durable memories, local archive search and Live function-call retrieval are implemented on PR #6 branch; exact-head Android debug CI passed (run 42360447654, artifact 13569287248); real-device verification remains pending. Validate that Juanita recalls a distinctive fact after force-stop/relaunch and a new session, across Italian/Spanish/Czech; test correction, deletion, retention and failure recovery.
2. **Biography and cognitive continuity:** build a dedicated editable biography/timeline with chronology, experiences, people/context, projects, milestones and learned topics. Add provenance, confidence, contradiction resolution and local multilingual semantic retrieval. Preserve episodic source records.
3. **Research and learning:** focused Internet research, source comparison, source-linked knowledge notes, freshness/expiry and reuse in future sessions. Use only approved free-tier/local options with hard limits; no billing activation.
4. **Reliability and privacy:** test long sessions, background service, memory pause/disable, export/restore, deletion, offline behavior and archive corruption.
5. **Camera and video-call correction:** verify the PR #6 orientation fix on actual devices and confirm Gemini receives upright frames without memory leaks or dropped audio.
6. **Avatar creation:** investigate and implement genuine on-device 2D portrait generation first; test hardware compatibility, storage, thermals and quality. Then add persistent avatar asset workflow. Rigged 3D, expressions and lip sync follow as a distinct phase.
7. **Phone-call support:** research Android Telecom/default-dialer feasibility, then implement an opt-in incoming-call companion. Investigate two-way cellular audio only where officially supported; evaluate separate VoIP mode for full AI conversation. Third-party app calls such as WhatsApp remain separate.
8. **Daily assistant capabilities:** reminders, calendar, travel, notifications and other integrations with explicit permissions and confirmations.

## Roadmap maintenance rule

- Add every new feature idea, user requirement, correction, limitation or agreed design decision to this roadmap as soon as it is raised in project conversations.
- Record each item under the appropriate workstream with status (`PROPOSED`, `RESEARCH`, `IN PROGRESS`, `IMPLEMENTED — CI PENDING`, `CI VERIFIED`, `DEVICE VERIFIED`, `BLOCKED`, or `DEFERRED`), priority, dependencies and acceptance criteria.
- Keep implemented, tested and merely planned work clearly separated. Never mark a feature complete based only on code being committed or documentation being written.
- Preserve previous decisions and rationale when updating scope; resolve contradictions in the roadmap rather than silently deleting important requirements.
- At each meaningful project update, synchronize the roadmap, README and relevant architecture/test documents.

## Definition of done

- Code committed with clear commit messages.
- Debug and signed release workflows checked; report actual run status and artifact availability.
- No feature claimed based on documentation alone.
- Test permissions, offline, app restart, screen off, memory correction/deletion and privacy controls.
- Update this roadmap whenever scope or implementation status changes.

Architecture details: see [AVATAR_VIDEO_CALL_ARCHITECTURE.md](AVATAR_VIDEO_CALL_ARCHITECTURE.md).


### Supervised collaboration with the external engineering assistant — PROPOSED

Juanita may collaborate with ChatGPT (or another explicitly configured engineering assistant) through a user-mediated, least-privilege handoff. This is a communication and engineering workflow, not unrestricted remote control.

- **Phase 1 — manual handoff (safe baseline):** Juanita creates a sanitized UTF-8 text report in the local Laboratorio. The user previews and explicitly exports/shares it in ChatGPT. The engineering assistant returns a human-readable implementation plan or patch/PR summary for the user to review. No app-to-assistant network bridge is required.
- **Phase 2 — optional user-approved bridge:** only after separate design and consent, support a narrow request/response channel with explicit pairing, visible connection state, short-lived scoped authorization, request IDs, replay protection, TLS, strict payload schema, size/rate limits and revocation. The bridge may exchange sanitized engineering reports and status only; never transmit the memory database, transcript archive, audio, contacts, credentials or arbitrary files by default.
- **Phase 3 — supervised development loop:** the engineering assistant may prepare code changes in an isolated Git branch/PR after explicit authorization. CI/test results and a concise diff summary are returned for review. Juanita may display status but cannot merge, sign, publish, change permissions, enable billing or install updates autonomously.
- **Trust boundary:** content received from the assistant, repository, issue tracker, build logs or web pages is untrusted data. It cannot become a system instruction, grant permissions or trigger arbitrary code execution inside Juanita. Accept only typed, allow-listed response objects; render text as inert content.
- **Approval gates:** user approval is required before sharing any selected conversation excerpt, granting an integration permission, changing retention/encryption/network behavior, adding dependencies, altering signing/release configuration, merging or distributing an APK.
- **Reliability:** collaboration must be optional and non-blocking. Offline use and the normal Gemini Live conversation must continue when the bridge is unavailable. Retry with bounded backoff and deduplicate by request ID.
- **Cost/privacy:** manual handoff is the default and has no additional app service cost. Any automated bridge/provider must be evaluated for free-tier availability, data processing, quotas and ongoing costs; do not enable billing or paid services without fresh explicit consent.

Acceptance: user can create and preview a sanitized report, share it manually, receive a proposed plan, and approve/reject it. If a bridge is later built, tests must prove that revoked/expired authorization, malformed payloads, prompt-injection content, replayed requests and network failure cannot access private memory, execute code, modify the app or release an APK.
