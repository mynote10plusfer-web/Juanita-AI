# Juanita AI — Product requirements and implementation roadmap

Last updated: 2026-11-08 — production baseline is signed Release 0.10.9 (versionCode 38), with permanent certificate and applicationId ai.juanita.app verified by CI. Candidate branch feature/complete-gentle-morning-alarm-0.11.0 is being prepared as versionCode 39. Candidate code adds a consent-gated Gemini Live morning greeting, local date/time, Android next-alarm lookup, read-only calendar summary and Open-Meteo weather lookup. This candidate is NOT yet CI-verified, signed, released or tested on physical devices. Do not distribute intermediate builds; deliver one cumulative signed Release APK only after CI, signature and device validation.

## Progressive personal knowledge — candidate 0.10.8

**STATUS: IMPLEMENTED IN MAIN; SIGNED RELEASE 0.10.8 BUILD 37 VERIFIED BY CI; PHYSICAL-DEVICE VERIFICATION PENDING.**

The next cumulative release connects the dedicated encrypted PersonalProfile store to Gemini Live:
- Replace the seven-field manual first-meeting form with a short introduction; the first meeting happens in voice, not in a form.
- Ask preferred name once, then birth date separately only when natural; age is calculated from the explicit birth date.
- Add a Gemini Live function tool that writes only explicitly stated, durable, non-sensitive profile fields to the dedicated AES-GCM PersonalProfile store.
- Restore confirmed profile details and calculated age into later Live sessions, separately from transcript archive and curated memory.
- Keep the existing automatic memory curator for interests, routines, projects and useful experiences; profile writes are a separate structured layer.
- No questionnaire, no repeated questions after refusal, no inferred birth date or residence, and no automatic retention of sensitive/intimate details.
- Corrections overwrite the relevant profile field; the user can manage ordinary memories separately. Profile-specific edit/delete UI remains a follow-up task and must be completed before claiming full profile management.

The 0.10.8 package is intended as one cumulative install after the 0.10.7 base. Do not distribute Debug builds or ask the user to install every intermediate version.

## Humanized conversation runtime — candidate 0.10.7

**STATUS: MERGED IN PR #20; INCLUDED IN SIGNED RELEASE 0.10.8 BUILD 37; PHYSICAL VOICE-CONVERSATION BEHAVIOR STILL REQUIRES DEVICE TESTING.**

This release slice turns the user's humanization requirements into active Gemini Live system instructions rather than leaving them only in a design document.

Runtime behavior added to `PersonaProfile.toSystemInstruction`:
- Infer the practical purpose and likely conversational need behind the current request; answer the direct question first whenever possible.
- Ask at most one concise, relevant follow-up, and do not end every reply with a question.
- Use context-sensitive curiosity for weather, rides, purchases, travel and plans without assuming the user intends to do an activity.
- Adapt between listening, practical problem-solving, reassurance, decision support and sharing enthusiasm based on the conversation.
- Treat emotional readings as tentative; accept corrections; avoid diagnoses and therapist claims.
- In relationship/conflict advice, distinguish facts from interpretations, consider alternative explanations and the other person's perspective, and avoid automatically siding with the user.
- Preserve warmth and a recognizable persona without stock phrases, forced intimacy or dependency-building.
- Strengthen truthfulness: no fabricated sources, memories, searches, quotations or completed actions; disclose missing tools and uncertainty; verify before claiming an operation succeeded.
- Use confirmed personal context only when relevant and do not treat old information as permanently current.

Regression instrumentation tests cover the presence of these behavior rules and preservation of language/persona settings. CI compiles the instrumentation-test APK; this is not execution of the tests and does not prove Gemini will follow every instruction consistently. Physical voice-conversation testing remains required.

**Next humanization slices:** connect the encrypted PersonalProfile store to a genuinely conversational, one-question-at-a-time onboarding flow; add context-aware profile retrieval and user correction; build a regression conversation set for emotional nuance, relationship reasoning, sarcasm, neutral messages, multilingual turns and correction handling; then evaluate results on Note10+ Android 12 and S22 Ultra.

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

### Video-call screen behavior and first-meeting profile

- [IN PROGRESS — CANDIDATE 0.10.3; CI VERIFICATION PENDING] While the in-app video-call screen is open, set Android `FLAG_KEEP_SCREEN_ON` for the host activity window. Clear it immediately when the call screen closes or the composable is disposed. Audio-only sessions must not hold the display awake; they continue through the foreground microphone service and may run with the display off.
- [IN PROGRESS — CANDIDATE 0.10.3; CI/DEVICE VERIFICATION PENDING] On first launch, offer a guided first-meeting profile with optional fields for preferred name, location, languages, interests, lifestyle/priorities, goals and desired assistance. Every field is optional; users can skip. Only explicitly submitted answers are stored as user-confirmed profile memories in the encrypted local memory store. The onboarding completion flag is stored locally. No passwords or sensitive personal data are requested.
- Acceptance: verify the screen stays awake throughout a video call, including when the user's camera is disabled; verify it returns to normal immediately after ending/closing the call; verify an audio-only session does not keep the screen awake. Verify onboarding can be skipped, saved, and shown only once, and submitted profile memories appear in the memory manager and are available to later sessions.

### Full-fidelity episodic conversation archive — USER-REQUESTED; FIRST IMPLEMENTATION SLICE — CI VERIFIED; DEVICE TESTS PENDING (2026-11-08)

**User goal:** retain complete conversation text so Juanita can search past exchanges and reopen the original context. Exact transcript history is the source record; summaries and curated memories are indexes/shortcuts, never replacements.

**First code change on branch `feature/full-fidelity-conversation-archive`:**
- Removed the silent 1,800-character truncation from the encrypted per-session conversation archive write path. New archived turns are now written with their complete recognized text.
- Kept the separate automatic-memory extraction queue bounded; that queue is not the transcript archive and must not be confused with it.
- Added Android instrumentation tests for exact round-trip of long multilingual/Unicode turns across fresh store instances and for preserving multiple turns in order.
- Added a CI step to compile the Android instrumentation-test APK. Compilation is not execution; actual Android/Keystore behavior still requires running the tests on a device or emulator.

**Known limitations and safeguards still to implement:**
- Text already truncated in historical archive entries cannot be reconstructed from the stored file. Do not pretend this change restores previously lost characters.
- The per-session archive currently rewrites the encrypted session JSON file on each appended turn. Benchmark large sessions and consider an encrypted append-only/chunked format with crash-safe migration before claiming unlimited scale.
- Legacy transcript records remain subject to their legacy storage cap; audit and migrate them without deleting or duplicating existing sessions.
- Add explicit encrypted export/backup and restore with integrity validation. Local-only data may be lost if app data is cleared, the app is uninstalled or the Keystore key becomes unavailable.
- Improve recall so a search result reopens the complete original turn and nearby turns before Juanita quotes or makes a historical claim. Keep model context bounded; never send the entire archive wholesale.
- Preserve separate controls for transcript archive, curated semantic memories and the permanent personal profile. Clearing one layer must not silently erase another.

**Acceptance gates:** exact round-trip for turns longer than 1,800 characters; chronological and role integrity; process restart; write interruption/corruption handling; retention and deletion behavior; migration of legacy records; relevant search across languages and paraphrases; source-turn reopening; encrypted backup/restore; no invented historical quotations. No raw audio recording is implied: the archive stores text received from the speech-recognition/transcription pipeline, whose recognition may itself contain errors.

**Status:** FIRST CODE SLICE COMMITTED; ANDROID DEBUG CI AND INSTRUMENTATION-TEST APK COMPILATION PASSED (run 42469575958); INSTRUMENTATION TESTS NOT YET EXECUTED; DEVICE VERIFICATION NOT PERFORMED.


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

### Speaker identity and familiar-person memory — USER-APPROVED REQUIREMENT; RESEARCH / NOT IMPLEMENTED

Juanita must learn the identity of people the user explicitly introduces, associate each person's confirmed name with an enrolled voice profile, and remember that association across sessions. This is distinct from speech-to-text and from speaker diarization.

**Expected interaction**
- The user can say: “Juanita, ti presento Marco.” Juanita asks Marco to confirm how he wants to be called and explains that optional voice enrollment creates a local voice template.
- When a likely new interlocutor voice is detected during an active user-started conversation, Juanita must not interrupt mid-sentence or announce an identity guess. At a natural pause, she may say that she has heard a voice she does not yet recognize, ask who is speaking, and then ask whether that person explicitly agrees to create a local voice profile associated with the confirmed name. This prompt must be occasional and context-aware, not repeated for background voices, television/radio audio, overlapping speech or every new segment.
- If the person declines, Juanita continues the conversation as an unregistered guest and does not create or retain a voice template. If the speaker is uncertain or cannot be reliably separated from the room audio, Juanita must say nothing rather than prompt or guess.
- Enrollment requires the person's explicit, informed opt-in; no voice profile is created merely because a person speaks or is named by someone else.
- After enrollment, Juanita may match a new speech segment to Marco's profile only above a validated confidence threshold. Otherwise she says she is unsure and asks who is speaking; never guess.
- If multiple people speak, diarization first separates anonymous speaker segments (Speaker 1, Speaker 2); identity matching is a separate step. The user can confirm or correct each association.
- Profiles persist across app restarts and conversations, and can be renamed, reviewed, re-enrolled or deleted individually. Deletion removes the encrypted voice template and its name association.
- A person can decline or revoke enrollment at any time. Guest conversation remains available without a profile.

**Privacy and security**
- Opt-in only, with a clear explanation before capture. Do not enroll friends silently or infer consent from the user's introduction.
- Process audio locally on the device. Do not send voice samples or voice embeddings to Gemini, Firebase, ChatGPT or any cloud service.
- Store only the minimum encrypted voice embedding/template required for matching; do not retain enrollment recordings after successful template creation. Raw audio remains transient and is not archived.
- Keep voice templates separate from ordinary text memories and transcript archives. Provide explicit view/delete controls and a global pause switch.
- Never use the feature for covert surveillance, attendance tracking, law-enforcement identification or identity decisions. A voice match is a convenience hint, not authentication or proof of identity.
- Unknown/low-confidence, noisy, overlapping, short or distant speech must produce “unknown/uncertain”, not a forced match.

**Technical research and staged implementation**
1. Evaluate sherpa-onnx Android speaker-identification (embedding extraction/matching) and speaker-diarization APIs as a local-first candidate. Its documentation lists Android/Kotlin support and separate speaker identification and diarization examples; the license of each selected model/dataset must be checked independently. Do not assume that one model supports all of Riccardo's languages or performs well on either Samsung phone.
2. Prototype capture from the same microphone path without breaking Gemini Live. Confirm whether SDK-managed audio can be safely mirrored to a local recognizer; if not, design a single audio-capture pipeline rather than opening competing microphone streams.
3. Benchmark latency, RAM, battery, thermal impact and false-accept/false-reject rates on Note10+ (Android 12) and S22 Ultra. Evaluate Italian, Spanish (including Canarian speech), Czech and English separately.
4. Implement a local encrypted speaker registry, explicit enrollment/consent UI, matching thresholds, unknown fallback, correction, re-enrollment and deletion.
5. Connect confirmed speaker identity to the existing person/profile memory so a later introduction can recall the person's name and user-approved facts, without merging one person's statements into another person's profile.

**Acceptance criteria**
- Introducing and enrolling a consenting friend creates a durable name-to-voice association that survives app restart.
- A later sample from the same person is matched only when the calibrated threshold is met; a different or unenrolled speaker remains unknown.
- Two or more speakers are segmented and can be assigned names only after explicit confirmation/enrollment.
- Low-quality audio and overlapping speech do not trigger confident false identification.
- Declining enrollment leaves no voice template; revoking/deleting removes the template and association.
- Automated tests cover storage encryption, deletion, threshold behavior and profile isolation; physical-device tests cover both target Samsung devices.
- CI passing alone must not be described as proof of recognition accuracy.



**Verified microphone integration constraint (2026-11-07)**

The current `GeminiLiveAssistant` calls Firebase AI Logic `LiveSession.startAudioConversation()` and receives text transcription callbacks; the app does not currently expose PCM microphone frames to the speaker-recognition layer. Do not add a second `AudioRecord`/microphone reader beside the SDK-managed capture. Firebase's official Live API guide documents that Android apps may instead create their own recorder and handle the stream. The next technical spike is therefore to prototype one app-owned capture pipeline that feeds the same PCM frames to Gemini Live and the local speaker engine, while preserving the existing Live session, output audio, VAD/turn behavior and stop lifecycle. Revert immediately if this regresses the current working voice path. Reference: https://firebase.google.com/docs/ai-logic/live-api (Kotlin section; last updated 2026-11-05 UTC).

**Feasibility research update (2026-11-08):**
- sherpa-onnx documents Android speaker-identification and speaker-diarization examples, including Kotlin/Android deployment and local inference. This makes it a credible no-cloud prototype candidate, not proof of accuracy or suitability on either Samsung device.
- The sherpa-onnx code is Apache-2.0, but the selected speaker model and training dataset can have separate licenses; each artifact must be checked before bundling. Model size, CPU/RAM/thermal cost and multilingual performance remain unverified.
- Official project references: https://github.com/k2-fsa/sherpa-onnx ; https://k2-fsa.github.io/sherpa/onnx/speaker-identification/ ; https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/ ; https://k2-fsa.github.io/sherpa/onnx/speaker-identification/apk.html
- Voice templates used to uniquely identify a person can constitute special-category biometric data under GDPR Articles 4(14) and 9. Before enabling matching, document the applicable lawful basis and Article 9 condition, provide specific informed consent and an equally usable guest path, and complete a privacy/legal review. Explicit consent in the UI is a safeguard, not by itself a complete compliance assessment.
- Official legal reference: https://eur-lex.europa.eu/legal-content/EN-ES/TXT/?uri=CELEX:32016R0679 (GDPR Articles 4 and 9).

### Familiar-person visual profiles and optional camera matching — USER-APPROVED REQUIREMENT; RESEARCH / NOT IMPLEMENTED

Extend the familiar-person memory beyond voice to an optional visual profile, while keeping ordinary photo memory separate from biometric face recognition.

**Visual profile (first stage)**
- During a user-led introduction, Juanita may offer to create a person profile containing their confirmed preferred name, optional user/person-provided photo, and a concise description of visible, non-sensitive appearance features.
- The person must know the photo is being added and explicitly agree. Prefer selecting a photo or capturing one in a clearly indicated enrollment flow; never silently save camera frames.
- Link the visual profile to the same person record as the separately enrolled voice profile and user-approved facts, while keeping each modality independently optional and removable.
- A stored photo is a reference for conversational context; displaying or recalling it is not proof that the person currently in front of the camera is that person.
- Do not infer or store sensitive traits such as race/ethnicity, health, religion, sexuality, political views or other sensitive attributes from appearance.

**Optional visual recognition (later stage; separate consent)**
- Real-time face matching against enrolled profiles is a distinct biometric capability and must not be implied by merely having a photo.
- Only research or implement it if a suitable on-device approach is available, with explicit, informed opt-in from each enrolled person, clear camera-use indication, local processing, encrypted templates, calibrated thresholds and an unknown/uncertain fallback.
- No face images, face embeddings or biometric templates may be sent to Gemini, Firebase, ChatGPT or another cloud service by default.
- Do not identify bystanders, passers-by or unenrolled people. Do not perform covert, continuous or background identification.
- Never use a face match as authentication, access control or proof of identity. If uncertain, ask the user/person rather than guessing.
- Provide separate inspect, pause, revoke and delete controls for the photo, appearance description and any biometric template. Deletion must remove the corresponding local data.

**Acceptance criteria**
- A consenting person can create a named visual profile from a chosen/captured photo, and it persists across sessions.
- The user can see which photo/description belongs to which person and can replace or delete it independently of voice enrollment.
- No biometric matching occurs unless separately enabled with explicit consent.
- Unenrolled people and uncertain matches remain unidentified.
- Local storage is encrypted; tests cover consent state, access boundaries, deletion and profile isolation.
- Physical-device testing covers camera permission, privacy indication, performance and thermal impact on Note10+ Android 12 and S22 Ultra.
- A profile photo alone must never be described as face recognition; CI passing alone does not establish recognition accuracy.


### Mode-aware familiar-person recognition — USER-APPROVED DESIGN; NOT IMPLEMENTED

Use one person record with separately optional recognition modalities. The active conversation mode determines which modality may be considered; it must never silently activate a sensor or enrollment.

- **Audio-only conversation:** use voice activity detection, speaker diarization and (only for explicitly enrolled people) local voice identification. Do not activate or consult a camera for identity. An unknown or low-confidence speaker remains unknown.
- **Audio + video conversation:** continue voice recognition and, only when that person separately opted in to visual matching, evaluate the actual available video image stream locally. A photo saved in a profile is not itself permission to run live face matching.
- **One shared person record:** confirmed preferred name, person ID, user-approved ordinary memories, optional voice template, optional photo/appearance profile and optional face template. Each modality has independent consent, status, review, revocation and deletion.
- **Identity fusion:** voice and visual matches are independent evidence. If both are available and agree above their separately calibrated thresholds, confidence may be increased only after validation. If they disagree, one is unknown, the frame/audio is poor, or the source cannot be tied to the active interlocutor, do not force a name; ask for confirmation.
- **Video-source awareness:** distinguish the phone's local CameraX preview from a remote participant's incoming video track. Process only the stream that actually depicts the interlocutor. The existing CameraX-to-Gemini frame forwarding is not a local face-recognition implementation and must not be repurposed as one without a separate privacy review.
- **No background recognition:** recognition is available only during an active user-started conversation and only while the relevant modality is enabled and consent remains valid. No identification of bystanders or people who have not enrolled.
- **Legal/privacy gate:** because voiceprints or face templates used to uniquely identify a person can constitute special-category biometric data under GDPR, complete a documented EU/Spain data-protection and lawful-basis review before enabling biometric matching for users. Explicit opt-in in the UI is a product safeguard, not a substitute for the legal review.

**Implementation order**
1. Implement and test local voice-only recognition without changing the existing Gemini Live audio path.
2. Build the shared person/profile registry and its consent, revoke, inspect and delete controls.
3. Add ordinary photo/appearance profile memory without face matching.
4. Research and, only after legal and technical approval, prototype local face matching in video mode.
5. Add conservative voice/visual evidence fusion and unknown/conflict handling.

**Acceptance:** an audio-only session never invokes visual matching; a video session uses visual matching only for explicitly enrolled profiles; disabling or revoking either modality immediately stops that modality; conflicting voice/visual evidence never produces a confident identity; all records remain attributable to the correct person across sessions and app restarts.

### Avatar generation gap — not implemented yet

The current Avatar Studio only creates a text description (including random attribute combinations) and saves profile metadata in SharedPreferences. It does not call an image-generation model, create a portrait bitmap, generate a GLB/VRM, or attach a generated asset to the active call screen. The main Juanita portrait card still imports an existing image selected by the user. A profile with an empty assetPath is not a generated avatar.

Required next implementation:
- Add a real image-generation provider behind an explicit interface, with a free-tier/local-first feasibility gate and visible quota/error handling. Do not enable billing, Firebase Blaze or paid APIs.
- Description and random modes must both produce an actual previewable portrait image, then allow accept/regenerate/edit and save it as Juanita's persistent portrait.
- Preserve the currently saved portrait until the user explicitly accepts a replacement.
- Keep photo-reference mode separate and require clear consent before any external upload.
- Treat rigged 3D/GLB/VRM generation as a separate later milestone; a generated 2D portrait must not be described as a rigged, animated avatar.
- Acceptance: from both a text description and random mode, the app generates and displays a new image, persists it across app restarts, and uses that exact image in the call screen.

### Voice recognition gap — speech transcription implemented; speaker identity NOT implemented

Gemini Live uses SDK-managed microphone audio and enables inputAudioTranscription and outputAudioTranscription. User speech is therefore transcribed into text events for the conversation/memory pipeline. This is speech recognition/transcription only; it does not identify the human speaker. The configured language and dialect are included in the persona/system instruction, but the Live session does not expose a verified speaker-identification or voice-biometric system.

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

## Notification access and personal inbox — candidate 0.10.4

**Stage 1 — opt-in notification reader (implemented in feature branch; CI/device verification pending)**
- Register an Android `NotificationListenerService` protected by `BIND_NOTIFICATION_LISTENER_SERVICE`; do not request notification access automatically. The user opens Android's notification-listener settings and enables Juanita explicitly.
- Receive posted notifications, identify the source application and extract only text Android makes available in notification extras. Do not bypass lock-screen privacy or recover hidden previews.
- Show the latest 30 notification entries in the Altro tab and provide an explicit clear action. Voice read-aloud is deferred until it can use Juanita's configured Gemini Live voice path; Android TextToSpeech must not be used as a fallback.
- First implementation is volatile/in-memory only: no notification text is written to transcript archive, personal memory, logs or cloud services; do not send notifications to Gemini. App-process recreation clears the temporary inbox.
- Ignore ongoing notifications and group-summary notifications. The user can revoke notification access at any time in Android settings.
- Initial version does not access WhatsApp's private database, fetch historical chats, send messages, mark messages read or reply. WhatsApp support depends on the message preview exposed by Android and the sender/app's notification format.

**Acceptance tests**
- Verify access remains disabled until the user grants it, and revoking it stops new capture.
- Verify notifications from WhatsApp and at least two other apps appear with correct app label/title/body where Android exposes them.
- Verify hidden previews remain unavailable and no content is retained after process death.
- Verify clear action empties the inbox. Verify any future read-aloud uses the configured Juanita voice and never Android TextToSpeech.
- Verify no notification body is present in Gemini requests, encrypted conversation archive, curated memories or application logs.
- Physical-device tests required on Note10+ (Android 12) and S22 Ultra. CI compilation is not proof of OEM notification delivery.

**Later stages**
- Per-app allow/block list, notification categories and quiet hours.
- User-controlled encrypted persistence/retention only if explicitly approved.
- Contextual voice summaries and optional Gemini-based prioritization only after a separate opt-in and privacy review.
- Quick actions/replies only where Android's public notification actions safely support them; no scraping or WhatsApp private APIs.

## Functional requirements

### A.0 Full-fidelity episodic memory and associative retrieval — USER-REQUESTED (2026-11-08)
**Goal:** preserve the original conversation record, not just summaries or extracted facts, and let Juanita retrieve exact passages when relevant. Summaries and semantic memories are indexes/shortcuts, never replacements for the source transcript.
- **Verbatim local episode archive:** retain complete user/assistant transcript turns in chronological sessions, preserving exact text, role, timestamp and session linkage. Do not silently truncate a turn to a fixed character count. If a storage or provider limit is reached, split safely into ordered chunks with stable IDs and reconstruct the original text exactly.
- **Encrypted and durable:** keep transcript data encrypted at rest on device using the existing Android Keystore AES-GCM architecture. Use atomic writes, integrity checks, migration from existing archive files and recovery/error reporting. Never overwrite the only good copy during migration.
- **Retention and user control:** default archive retention remains 'keep until the user deletes it' only when the user has enabled archiving; explain that local-only storage can be lost if app data/device is lost. Add explicit export/backup and restore with encryption and integrity validation before claiming data is permanently safe. No silent pruning or automatic deletion of source transcripts to make room.
- **Three complementary memory layers:** (1) exact episodic transcript archive (what was said and when); (2) curated semantic/profile memories (stable facts, preferences, projects and corrections); (3) derived episode summaries and indexes for fast retrieval. A summary must always link back to its source session/turns and must never replace them.
- **Associative retrieval:** move beyond keyword-only matching through a hybrid on-device index: lexical search plus meaning/topic/person/time/entity associations, with optional local embeddings only if feasible on supported phones without paid services or excessive battery/storage. Retrieve candidate passages first, then inspect the original transcript around the matching turns before answering.
- **Answer grounding:** when Juanita recalls a past conversation, include its date/context internally and distinguish exact quotation, faithful paraphrase, user-confirmed memory and inference. If no source passage is found, say she cannot retrieve it; never reconstruct missing wording as a quotation.
- **Context budget:** do not load every transcript into every Gemini session. Search locally, select only the most relevant source excerpts, include surrounding turns for context, and keep recent-session context separate from long-term retrieval. Original transcripts remain intact on device.
- **Conversation continuity:** preserve speaker/role boundaries and session IDs. Memory extraction should attach source session and turn IDs to each derived fact, so the user can inspect where a remembered fact came from and correct or remove it without destroying unrelated source material.
- **Privacy and safety:** user controls archive enable/disable, inspect, export, delete by session/date, and full clear. Disabling new capture must not silently erase old records. Sensitive content must not be promoted into durable semantic memory without the existing consent/sensitivity policy.
- **Acceptance tests:** exact round-trip of long turns over 1,800 characters; multi-session chronological integrity; no silent data loss on migration, app restart or failed write; archive survives semantic-memory cleanup; semantic summary deletion leaves transcript intact; retrieval finds relevant passage despite different wording; original passage is reopened before a historical claim; no invented quote when search misses; export/restore integrity; storage-full and corrupted-file handling.
- **Current known gap:** the existing archive is encrypted and session-based, but appendConversationTurn currently truncates each turn at MAX_TURN_TEXT_LENGTH = 1800; the general legacy encrypted-record store also has a 300-turn cap. Search is lexical. These constraints must be removed or safely migrated for the full-fidelity design. The separate per-session archive files are not subject to that legacy 300-turn cap.
- **Status:** USER-REQUESTED DESIGN; NOT IMPLEMENTED. Preserve all existing archive files and backward compatibility during implementation.

### A. Automatic memory, archive retrieval and evolving digital biography — highest priority

### A.1 User-controlled memory management — high priority

- [IN PROGRESS — PR #6 FOLLOW-UP; CI/DEVICE VERIFICATION PENDING] Replace the single long scrolling home screen with persistent bottom navigation: Home (voice session), Avatar (Avatar Studio and camera), Impostazioni (identity, language, voice and personality), Memoria (personal memories and conversation archive), Altro (project status and debug tools). Keep the navigation visible while each section's content scrolls independently.
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

**Open issues reported during real-phone testing — 2026-11-06/07; not yet verified as fixed**
- Video-call view: user reported a black local-camera preview and a static Juanita portrait. Re-test front-camera preview, camera switching, video frame delivery and avatar animation separately; the portrait is currently static by design/code, so do not mark animation complete.
- Memory: user reported that conversations opened from the archive were not displayed. Re-test archive persistence, date/day navigation, transcript retrieval, and automatic-memory recall after app restart.
- Session lifecycle: user reported that closing/leaving the app could leave the microphone conversation active. Re-test stop behavior for close, back navigation, process lifecycle and explicit terminate; no hidden/hot microphone is acceptable.
- These are user-observed defects requiring reproducible tests on Note10+ Android 12 and S22 Ultra. A successful CI build alone does not close them.

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

## Self-assessment, study requests and plain-text improvement reports — PHASE 1 IN PROGRESS


### Delivery status — Phase 1
- [IMPLEMENTED — DEBUG CI VERIFIED (run 42394260616); DEVICE VERIFICATION PENDING] Add an Altro-tab capability catalogue that labels each capability as integrated-but-unverified, not implemented, planned or under research, and explains known limits without presenting plans as working features.
- [IMPLEMENTED — DEBUG CI VERIFIED (run 42394260616); DEVICE VERIFICATION PENDING] Add a local Improvement Lab for manually authored BUG, INEFFICIENCY, CAPABILITY_GAP, KNOWLEDGE_GAP, RESEARCH_REQUEST and IMPROVEMENT_IDEA reports.
- [IMPLEMENTED — DEBUG CI VERIFIED (run 42394260616); DEVICE VERIFICATION PENDING] Store reports inside the existing Android Keystore AES-GCM encrypted local record store, separately from transcripts and personal memories; support preview, multi-select export to ordinary UTF-8 .txt and deletion.
- [FUTURE] Automatically detect meaningful capability gaps from observable task outcomes and create privacy-minimized draft reports asynchronously. Phase 1 does not yet perform automatic gap detection or self-modify code.

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

1. **Memory engine and archive retrieval (highest priority):** automatic encrypted transcript archive, curated durable memories, local archive search and Live function-call retrieval are merged on main; merged-head Android debug CI passed (run 42393573784); real-device verification remains pending. Validate that Juanita recalls a distinctive fact after force-stop/relaunch and a new session, across Italian/Spanish/Czech; test correction, deletion, retention and failure recovery.
2. **Biography and cognitive continuity:** build a dedicated editable biography/timeline with chronology, experiences, people/context, projects, milestones and learned topics. Add provenance, confidence, contradiction resolution and local multilingual semantic retrieval. Preserve episodic source records. The Phase 1 Improvement Lab is separate: it records software capability state and engineering reports, not personal biography.
3. **Research and learning:** focused Internet research, source comparison, source-linked knowledge notes, freshness/expiry and reuse in future sessions. Use only approved free-tier/local options with hard limits; no billing activation.
4. **Reliability and privacy:** test long sessions, background service, memory pause/disable, export/restore, deletion, offline behavior and archive corruption.
5. **Camera and video-call correction:** verify the PR #6 orientation fix on actual devices and confirm Gemini receives upright frames without memory leaks or dropped audio.
6. **Avatar creation:** investigate and implement genuine on-device 2D portrait generation first; test hardware compatibility, storage, thermals and quality. Then add persistent avatar asset workflow. Rigged 3D, expressions and lip sync follow as a distinct phase.
7. **Phone-call support:** research Android Telecom/default-dialer feasibility, then implement an opt-in incoming-call companion. Investigate two-way cellular audio only where officially supported; evaluate separate VoIP mode for full AI conversation. Third-party app calls such as WhatsApp remain separate.
8. **Daily assistant capabilities:** reminders, calendar, travel, notifications and other integrations with explicit permissions and confirmations.

## Juanita Personal Organizer — Smart Alarm, Agenda and Reminders — USER-APPROVED / NEXT IMPLEMENTATION PACKAGE

**Current engineering sequence (2026-11-08):** preserve the app-owned alarm sound path independently of Gemini/network; add the consent-gated Live greeting and read-only morning briefing as a separate layer. Local reminders/tasks and calendar write access remain distinct follow-up scope and must not be claimed as implemented.

**User-approved direction (2026-11-08):** Build one integrated daily-organizer experience rather than a standalone alarm.

**Android platform constraints verified 2026-11-08:**
- For exact alarm scheduling on Android 12+, declare and check `SCHEDULE_EXACT_ALARM`; it is user-granted and may be revoked. Handle denial with a clear explanation and an honest inexact fallback, not a claim of punctual delivery.
- Do not use `USE_EXACT_ALARM` casually: it is restricted by Google Play to qualifying core alarm/calendar use cases. The sideload path must also remain safe with Play Protect enabled.
- On Android 14+, full-screen intent access is restricted to calling/alarm use cases. Check `NotificationManager.canUseFullScreenIntent()` and provide a notification fallback when unavailable; never assume the full-screen activity can always launch.
- Re-check exact-alarm access after resume and restore schedules after the system's permission-granted broadcast and boot.
- Official references: https://developer.android.com/develop/background-work/services/alarms ; https://developer.android.com/about/versions/14/changes/schedule-exact-alarms ; https://source.android.com/docs/core/permissions/fsi-limits It combines Juanita's own alarms, locally managed reminders/tasks, read-only access to existing Android calendar events, explicitly authorized calendar event creation, and an optional morning briefing. This is a new product package; no alarm/calendar/reminder implementation is claimed yet.

### Product areas

1. **Smart alarm**
   - Create, edit, enable/disable and delete alarms; one-time and repeating schedules; selected weekdays; snooze and dismiss; test alarm; next-alarm status.
   - Configurable alarm sound, gradual volume where device support permits, greeting style and briefing options.
   - Alarm tone and dismiss/snooze controls must work independently of Internet, Gemini, weather and avatar rendering.
   - Fire reliably with the app UI closed and screen off. Restore schedules after reboot, app update, time-zone change and daylight-saving transitions. Handle duplicate, missed and past-due alarms explicitly.
   - Do not claim Juanita can stop or control alarms owned by third-party clock apps.

2. **Juanita reminders and tasks**
   - Natural-language creation (for example, “ricordami domani alle dieci di chiamare Marco”), with date/time/time-zone parsing and a confirmation question when the request is ambiguous.
   - One-time and recurring reminders, task/checklist items, due dates, optional advance alerts, completion/snooze/reschedule and deletion.
   - Keep Juanita-created reminders in a dedicated local encrypted store, separate from calendar-provider events and personal memories. Never silently turn casual conversation into a scheduled reminder.
   - Provide a clear list of upcoming, overdue and completed items; avoid duplicate reminders after restart or rescheduling.

3. **Calendar and events**
   - Stage 1: optional read-only access through Android Calendar Provider, allowing Juanita to show today's agenda and upcoming events from calendars already present/synced on the device (for example Google or Exchange calendars). Ask for `READ_CALENDAR` only when the user enables this feature; let the user choose which calendars are included where feasible.
   - Stage 2: create events using the Android Calendar insert intent or, if a direct write workflow is justified, request `WRITE_CALENDAR` separately and confirm title, date, time, calendar and attendees before writing. Never edit/delete an existing event without a separate explicit confirmation.
   - Distinguish imported calendar events from Juanita's own reminders in the UI and data model. Preserve source calendar/event IDs and time-zone semantics; do not duplicate imported events into the local reminder store.
   - Do not assume that Google Calendar's cloud API or a Google OAuth integration is required: prefer the on-device provider first, which can expose calendars synchronized by Android. A dedicated cloud connector can be considered later only if the user asks for cross-device access beyond device-synced calendars.

4. **Morning briefing**
   - Optional short greeting in the user's selected language, today's calendar agenda, Juanita reminders due today/overdue, and optional weather.
   - User controls which categories may be spoken aloud, briefing length, quiet/private mode and whether sensitive event titles are hidden while the phone is locked.
   - Weather is optional, based on an explicitly permitted fresh foreground location or a manually selected locality; no background tracking solely for the briefing. If weather/location/network data is unavailable, omit it without inventing conditions.
   - Avoid long unsolicited monologues; allow greeting-only mode and a configurable briefing time.

5. **Voice identity — non-negotiable**
   - Spoken greetings and briefings must use the same selected voice identity/configuration and audio-generation path used by Juanita's normal Gemini Live conversations. Reuse the existing saved Live voice preference; do not add a second voice selector.
   - No Android system TextToSpeech or generic substitute voice for Juanita's greeting, briefing or notification read-aloud.
   - Research whether the configured Gemini Live output can safely produce a short utterance from a scheduled background alarm flow. Verify session lifecycle, foreground-service rules, network availability, quotas and device lock state. Do not assume the current interactive Live API can be invoked as a fire-and-forget speech synthesizer.
   - If Juanita's voice cannot be started safely/available, the alarm tone and controls must still work; clearly indicate that the spoken briefing is unavailable. Never silently switch to a different voice.
   - Avatar lip-sync remains a later enhancement and is not a dependency for the first working alarm.

### Scheduled calls and secretary-style call handoff — USER-REQUESTED / FEASIBILITY RESEARCH

**User request (2026-11-08):** When creating a reminder to phone someone, let the user choose either (a) remind me and let me call, or (b) Juanita calls the contact first and then connects/passes the call to the user after the contact answers.

- Add a per-reminder call action: `REMIND_ME_TO_CALL` (default) or `JUANITA_CALLS_FIRST`. The reminder must identify a contact and confirm the exact phone number before any call is placed. Never infer a number from an ambiguous contact match.
- `REMIND_ME_TO_CALL`: at the scheduled time, show a notification and open the system dialer with the confirmed number. The user explicitly taps Call; this is the reliable first implementation and does not require Juanita to become the default dialer.
- `JUANITA_CALLS_FIRST`: research only until feasibility is proven. The desired experience is an assistant/secretary that calls the contact, detects answer/busy/no-answer, and connects the user only after the contact answers. The user must explicitly approve the call and any bridge/transfer before it happens.
- **Important Android/SIM limitation:** `TelecomManager.placeCall` can place a call with the required permission, but the ordinary cellular call APIs do not provide a universal third-party-app operation to take an answered SIM call and seamlessly transfer its audio to the device owner. Android `ConnectionService`/Telecom handover is intended for compatible calling services and does not guarantee handoff of a carrier-managed SIM call. A carrier-supported three-way/conference call may be a possible device/operator-dependent route, but it must be tested and cannot be assumed.
- Investigate a self-managed VoIP `ConnectionService` as an alternative architecture: Juanita could originate a VoIP leg to the contact and bridge it with a second leg to the user, subject to a real VoIP/SIP provider, backend/media infrastructure, account credentials, reliable connectivity, privacy and regulatory review. Do not introduce a paid provider, subscription, API key, server or recurring cost without the user's explicit approval. A fully on-device bridge is not assumed feasible.
- Before any call, present contact name, exact number, call purpose and chosen mode for explicit confirmation. Provide cancel/stop controls; do not call emergency numbers automatically; do not record or transcribe the other party by default. If the contact does not answer, is busy, or the call state cannot be reliably detected, end safely and notify the user rather than pretending the call was connected.
- First deliver the reliable reminder + dialer handoff. Only implement `JUANITA_CALLS_FIRST` after a technical prototype passes real tests on the Note10+ Android 12 and S22 Ultra, and after any carrier/VoIP dependencies and costs are disclosed and approved.
- Status: **REQUESTED — FEASIBILITY RESEARCH; NOT IMPLEMENTED.** No automatic calling, SIM call transfer, conference bridge or VoIP bridge exists in the app today.

### Android architecture and safeguards

- Use Android `AlarmManager` for precise user-facing alarm/reminder triggers where exact timing is required. For target Android 12+, request the appropriate “Alarms & reminders” special access (`SCHEDULE_EXACT_ALARM`) with an explanation and a live permission check; do not silently claim punctuality if access is denied. Review whether `USE_EXACT_ALARM` is policy-eligible before considering it.
- Use a dedicated alarm receiver/playback component and a lock-screen-aware alarm activity/notification. Full-screen intent access is conditional on Android version and policy; check availability and provide a graceful notification fallback. Do not request broad privileges without need.
- Persist local definitions transactionally and re-schedule after boot, package replacement, time/time-zone changes and exact-alarm permission changes. Respect Android 12+ background execution and foreground-service restrictions.
- Store local reminders encrypted on-device; do not upload schedules, calendar contents or location history by default. Calendar read/write permissions are separate, optional and revocable.
- Calendar Provider permissions: `READ_CALENDAR` for reading; `WRITE_CALENDAR` for direct inserts/updates/deletes. Prefer Android calendar intents for user-mediated event creation/editing where practical, avoiding unnecessary write permission.
- Keep this package independent of the existing notification listener. Reading a notification is not the same as importing a calendar event or creating a reminder.

### Delivery phases

A. **Research and foundation:** verify Android exact-alarm/full-screen-intent behavior for targetSdk 35 and minSdk 26; design encrypted reminder schema, receiver lifecycle and reboot/time-change recovery; prototype use of Juanita's saved Gemini Live voice without Android TTS.
B. **First usable release:** local alarms + local reminders, alarm tone/snooze/dismiss, agenda UI and morning greeting only when the real configured voice path is proven safe. Add unit tests for recurrence, time zones, daylight saving, duplicate delivery and persistence.
C. **Calendar integration:** read-only Android Calendar Provider with calendar selection and today's/upcoming agenda; then confirmed event creation via Calendar intents. Direct calendar writes remain a later opt-in.
D. **Morning briefing enhancements:** optional weather, quiet/private mode, per-category spoken controls and configurable briefing.
E. **Voice command integration:** parse natural-language reminder/alarm/event requests from Gemini Live and ask for confirmation before committing changes; distinguish a draft from a successfully saved item.

### Acceptance criteria

- Alarms and reminders trigger at the intended local time with the UI closed/screen off on Note10+ (Android 12) and S22 Ultra; reboot, app update, time-zone and daylight-saving tests pass.
- Exact-alarm permission denial/revocation is detected and communicated honestly; no silent inexact fallback is presented as punctual.
- Dismiss/snooze do not cancel future repeats; repeated broadcasts do not create duplicate alerts.
- Juanita-created reminders survive process death/reboot and can be inspected, edited, completed and deleted independently of personal memories.
- Calendar events are read only after consent; imported events are clearly distinguished from Juanita-created reminders. No existing event is modified or deleted without explicit confirmation.
- The morning briefing speaks only categories the user enabled and hides sensitive content in lock-screen/private mode.
- Juanita's configured Gemini Live voice is used when available; Android system TTS is never used as a substitute. Alarm tone/controls remain functional when voice/network/provider is unavailable.
- Weather is sourced from a timestamped response and permitted locality; if unavailable, it is omitted.
- Automated tests cover schedule calculations, persistence, rescheduling, privacy and permissions; physical-device tests cover both Samsung devices. CI alone is not evidence of reliable alarm delivery.

### Dependencies and status

- Depends on stabilization of notification-reader candidate 0.10.4 and review of the TTS mismatch in that PR.
- Android alarm/permission/lock-screen research and a Gemini Live short-output feasibility spike are required before committing to background speech.
- Status: **USER-APPROVED — SPECIFIED; NOT IMPLEMENTED.** No alarm, reminder scheduler, Calendar Provider integration or morning-briefing code is present yet.

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


## Android update compatibility — IN PROGRESS

The update pipeline must preserve all three Android package-update invariants:

- Keep `applicationId = ai.juanita.app` unchanged.
- Sign every distributable update with the permanent release certificate SHA-256 `9092CEFDC0DE218921A723810575B029550CEFFA6D82593BE22F5F1F6EBA7BE1`, independently extracted from the successful 0.9.5 artifact (run 42325310890) and matched against the current Release build. Debug APKs are development-only and must never be presented as in-place updates.
- Increase `versionCode` for every release. `versionName` is the human-readable label and does not replace the numeric update code.

Known release baseline: `versionCode 30` / `versionName 0.10.1`. The next candidate is `versionCode 31` / `versionName 0.10.2`. The signed-release workflow reads expected package/version values from `app/build.gradle.kts`, rejects version codes not greater than 30, and compares built APK metadata against those values. The 2026-11-07 run 42393574023 compiled the Release APK but failed its certificate gate because the pinned digest was stale/incorrect (`604702…`); `apksigner` reported `9092CEF…`, which was independently confirmed by extracting the certificate from the previously successful 0.9.5 artifact. PR `fix/release-signing-fingerprint` updates the pin; rerun the signed workflow after merge.

Acceptance before sharing an APK: CI build succeeds; APK package ID matches; versionCode is strictly higher than the installed known-good release; APK signer SHA-256 matches the permanent certificate; install-over-existing-app succeeds on a test device without uninstalling or clearing app data. Never ask the user to uninstall as a workaround.


## Conversation archive calendar — IN PROGRESS

Replace broad date-range deletion with a local calendar, per-day conversation lists, transcript preview with timestamps, per-conversation time-window deletion, whole-day deletion and multi-day selection. Transcript deletion must preserve curated personal memories. The home screen should foreground the avatar, show device date/time and use distinct navigation icons. Exact-head CI and device verification are pending.


## Smart alarm and personalized morning briefing — PROPOSED

**User-approved feature request (2026-11-08).** Juanita should include her own integrated alarm clock and a warm, configurable morning wake-up experience. This is a separate alarm feature, not an assumption that Juanita can control or dismiss alarms created by another clock application.

### Intended experience
- The user can create, edit, enable/disable and delete alarms; configure time, repeat days, alarm sound, volume/ramp behavior, voice style and optional morning briefing.
- At the scheduled time, the alarm must reliably sound even if the app UI is closed or the screen is off. Juanita then delivers a natural, gentle and positive greeting, with varied user-approved wording rather than a repetitive fixed phrase.
- The user can dismiss or snooze the alarm using clear on-screen controls and, where technically reliable, a voice command. Do not assume speech recognition is available while the device is locked or before the user grants microphone access.
- The greeting may include a concise weather briefing: current conditions and useful forecast for the user's current area. Use location only with explicit permission; otherwise use a manually selected home/locality. If location or network/weather data is unavailable, Juanita should say so naturally and never invent conditions.
- Morning content is configurable: greeting only, weather, calendar/reminders (future integration), or a short user-selected motivational message. Do not force a long monologue or unexpectedly expose private calendar details aloud.

### Android architecture and reliability
1. Build an app-owned alarm manager backed by Android AlarmManager, with exact-alarm capability and the required Android-version-specific permission/settings flow. Evaluate exact-alarm eligibility and Google Play/device policy before implementation; do not silently fall back to an unreliable inexact alarm while claiming punctual delivery.
2. Persist alarm definitions locally and reschedule them after device reboot, app update and time-zone/time changes. Handle daylight-saving changes and duplicate/missed alarm edge cases.
3. Use a dedicated alarm playback/session component with explicit start, dismiss and snooze lifecycle. Respect Android foreground-service and background-execution restrictions; keep the alarm functional if speech synthesis or weather retrieval fails.
4. Design a lock-screen-friendly alarm screen using supported Android APIs and permissions. Do not request broad full-screen-intent privileges without confirming eligibility and necessity.
5. **Use Juanita's configured assistant voice — mandatory.** The alarm greeting and morning briefing must use the same voice identity, voice configuration and audio-generation path already selected for Juanita's normal conversations (the existing Gemini Live voice/output path), not Android system TextToSpeech or a separate generic TTS voice. Reuse the existing voice settings; do not introduce a second voice selector that can drift out of sync. Research a reliable way to generate a short spoken greeting/briefing outside an interactive Live session while preserving the configured voice. If the current voice provider requires network access or cannot be started safely from the alarm flow, keep the alarm tone and dismiss/snooze controls fully functional and report that spoken briefing is temporarily unavailable; never silently substitute a different TTS voice. Do not assume Gemini Live can be invoked as a background alarm speaker without checking its session, network, quota, lifecycle and Android foreground-service constraints. Keep voice/avatar lip-sync as a later enhancement; do not imply it exists in the first alarm release.
6. Weather provider selection is a separate research task: prioritize a no-cost option compatible with the project's free/no-billing requirement, verify terms, quotas, attribution and location-data handling. Cache only short-lived weather data and show its observation/forecast time.
7. Location must be optional and minimized. Prefer a user-selected locality or a fresh foreground-permission location; do not request background location solely for the alarm. If a reliable current location cannot be obtained at wake-up, use the configured locality or omit weather.

### Privacy, user control and failure behavior
- No alarm, microphone or location permission is enabled silently. Explain each permission at the point it is needed and provide settings to revoke it.
- Alarm tone and dismiss/snooze controls must work independently of Gemini, Internet access, weather services and avatar rendering.
- No cloud upload of alarm schedules, location history or calendar content by default.
- Provide a test-alarm action and a clear status for the next scheduled alarm.
- Do not speak sensitive notification or calendar content aloud unless the user explicitly enables that behavior.

### Acceptance criteria
- A repeating alarm fires at the configured local time with the screen off and app UI closed on both Note10+ (Android 12) and S22 Ultra; test reboot, app update, time-zone and daylight-saving transitions.
- Dismiss and snooze work reliably; stopping an alarm does not cancel future repeats.
- Alarm tone still sounds if Gemini, TTS, network, weather or location fails.
- Greeting is spoken in the selected language and can be configured or disabled.
- Weather is grounded in a timestamped provider response and permitted location/locality; when unavailable, Juanita clearly omits it.
- Battery use, duplicate firing, missed alarms, permission revocation and process death are tested. CI success is not a substitute for physical-device alarm tests.

### Priority and dependencies
- Priority: HIGH product value; implementation should follow stabilization and device verification of the notification-reader release rather than being mixed into that release.
- Dependencies: Android alarm/foreground-service compatibility research; local alarm persistence and reboot rescheduling; research and integration of Juanita's already configured Gemini Live voice/output path (no Android TextToSpeech or substitute voice); optional location; a vetted no-cost weather source.
- Status: PROPOSED — no alarm code implemented, no weather provider selected, no device verification.


### Clarification — Juanita originates and hosts the call, then brings Riccardo into the same call — USER-REQUESTED / FEASIBILITY RESEARCH

**This clarification supersedes the earlier interpretation of `JUANITA_CALLS_FIRST` as a generic call transfer or handoff to another app.** The user does not want Juanita to open the system dialer, switch to another calling app, or merely notify him to take over.

**Required experience**
1. Riccardo schedules or requests a call to a confirmed contact and exact telephone number, choosing the secretary mode. This is intended for hands-busy situations (for example, while he is doing repair or workshop work).
2. Juanita herself originates the outgoing call to the contact.
3. When the contact answers, Juanita speaks first, identifies herself truthfully as Riccardo's assistant, and explains the reason for the call using a user-approved script. Example: “Buongiorno, sono Juanita, l'assistente di Riccardo. La chiamo per conto suo. Potrebbe attendere un momento? Le passo Riccardo.”
4. Juanita then brings Riccardo into that same live conversation and enables the phone's speakerphone (vivavoce), so he can speak hands-free without opening or switching to another application. The desired outcome is a continuous conversation on the same device/call, not a second app or a blind transfer.
5. If the contact does not answer, is busy, declines, or the bridge/handover cannot be established, Juanita must end or recover safely and tell Riccardo the actual result. Never claim that the call was passed successfully when it was not.

**Technical feasibility — important boundary**
- This is more demanding than placing a call and toggling speakerphone. A normal Android app can request an outgoing cellular call only under applicable permissions/roles and platform rules; it does not automatically gain control of the carrier call's media path or a universal API for inserting its own spoken introduction and then handing the same call to the user.
- Investigate whether a supported Android Telecom integration can own/manage the call and provide a reliable assistant-to-user transition on both target Samsung devices and with the user's mobile operator. Verify speakerphone routing and audio focus/ownership; do not assume a third-party app can force speakerphone for every carrier call.
- Investigate carrier-supported conference/three-way calling as a possible same-call route, but do not assume the operator, SIM plan or device supports it.
- A VoIP implementation could potentially originate the contact leg, play Juanita's introduction and bridge a second audio leg to Riccardo within one Juanita-controlled call session. However, it requires a VoIP/SIP service or equivalent backend/media infrastructure, connectivity, credentials and likely ongoing provider costs. No paid provider, subscription, hosted service, billing or API secret may be introduced without Riccardo's explicit approval.
- The implementation must not quietly fall back to opening the system dialer or another app: if the requested same-call experience is unavailable, explain the limitation and offer the separate manual-call mode only as an explicitly chosen alternative.
- Before every outgoing call, confirm the contact, exact number, purpose and selected call mode. Provide a clear cancel/stop control. Do not record or transcribe the other party by default; any recording or transcription would require a separate explicit design and consent review.

**Prototype and acceptance gates**
- First produce a documented feasibility matrix for Android 12 / Note10+, S22 Ultra, carrier SIM calls, supported conference features, Telecom roles/permissions, speakerphone control and a possible VoIP route with transparent cost/privacy implications.
- Build a minimal prototype only after choosing a lawful, supported route. Test the exact sequence: outbound call → contact answers → Juanita greeting → “please wait” → Riccardo joins the same call → speakerphone active → two-way conversation → clean hang-up.
- Test no-answer, busy, decline, loss of connectivity, permission denial, audio-route failure, app process death and user cancellation.
- No implementation may be labelled complete until the same-call sequence is physically demonstrated on the target devices; CI alone is insufficient.
- **Status: USER-CLARIFIED REQUIREMENT — FEASIBILITY RESEARCH; NOT IMPLEMENTED.** This is a core hands-busy secretary use case, not a simple reminder-to-dialer feature.


**Initial Android API findings (2026-11-08; official documentation reviewed)**
- `TelecomManager.placeCall(Uri, Bundle)` supports the `EXTRA_START_CALL_WITH_SPEAKERPHONE` extra for starting an outgoing call on speakerphone. This is a starting-route request, not proof that Juanita can inject her generated greeting into the uplink of a carrier/SIM call or later take over its media. Reference: https://developer.android.com/reference/android/telecom/TelecomManager
- Android's `InCallService` is the supported interface for an app that manages calls and their in-call UI, but becoming the default phone app requires the user to grant `ROLE_DIALER` and the app to implement a complete dialer/in-call experience, including outgoing dial UI and incoming/ongoing call UI. This is a significant product/permission decision, not a small background feature. Reference: https://developer.android.com/reference/android/telecom/InCallService
- Android's Telecom documentation distinguishes a self-managed VoIP calling app (Core-Telecom / `ConnectionService`) from a managed calling integration. Core-Telecom can manage audio routing/focus for the app's own call, which makes an app-controlled VoIP conversation a more coherent candidate for Juanita's spoken introduction and subsequent same-session conversation. It does not by itself provide a free carrier-SIM call bridge. Reference: https://developer.android.com/develop/connectivity/telecom
- The official API pages reviewed do not document a general third-party API that lets an ordinary app inject arbitrary generated speech into the outgoing audio stream of an existing carrier/SIM call. Treat that capability as unproven; do not design around hidden APIs, accessibility hacks, audio loopback or OEM-specific assumptions.
- Research conclusion: prioritize a controlled VoIP prototype if the user later approves its connectivity/provider architecture and any costs. In parallel, investigate whether a default-dialer implementation plus carrier conference calling can meet the exact same-call experience without external services. No route is yet validated on the two target Samsung phones or the user's carrier.


### Comparative technical research — SIM cellular calls vs WhatsApp Business calls (2026-11-08)

This section records a feasibility investigation requested by Riccardo. Target behavior remains: Juanita originates the call, introduces herself, explains the reason, asks the recipient to wait, then Riccardo joins the same live conversation hands-free, with speakerphone/audio routed by Juanita. No handoff to the system dialer or a separate calling app is acceptable as the secretary-mode success path.

#### A. Ordinary cellular / SIM call from the Android phone

**What Android publicly supports**
- `TelecomManager.placeCall(Uri, Bundle)` can request an outgoing cellular call, and Android documents `EXTRA_START_CALL_WITH_SPEAKERPHONE` to request speakerphone at call start. This proves call initiation and initial speaker routing are exposed under the relevant permissions/role; it does not expose a public API to inject arbitrary generated assistant audio into the carrier call's uplink.
- Android's `InCallService` is the interface for an app that replaces the in-call UI. To become the default phone app, Juanita would have to request `ROLE_DIALER` and implement the complete dialer/in-call experience, including ACTION_DIAL, incoming-call UI and ongoing-call UI. It is not a narrow permission that can be added just to control one call.
- The Telecom framework routes calls supplied by calling services and manages audio focus/routing. The documented self-managed `ConnectionService` route is for Juanita's own VoIP calls, not a general mechanism for taking over the SIM modem's uplink audio.
- Official references:
  - https://developer.android.com/reference/android/telecom/TelecomManager
  - https://developer.android.com/reference/android/telecom/InCallService
  - https://developer.android.com/reference/android/telecom/package-summary

**Feasibility conclusion**
- Juanita can potentially place a SIM call and request speakerphone, subject to permissions, role, Samsung/Android behavior and carrier configuration.
- The critical secretary step — Juanita speaking into the remote party's cellular call and then transferring the same call's audio to Riccardo — is **not supported by a documented public Android API for an ordinary third-party app**. Playing audio on the phone speaker and hoping the microphone captures it is an acoustic workaround, not an acceptable reliable architecture; echo cancellation, noise suppression, microphone routing and handset placement make it unsuitable.
- A carrier three-way/conference feature may let a human join a SIM call, but does not itself give Juanita a supported way to be an AI audio participant in the SIM uplink. Any carrier-specific solution would need direct operator confirmation and physical tests.
- A viable but materially different alternative is to originate/host the call through a telephony/VoIP backend with a PSTN gateway: Juanita speaks as one conference participant, and Riccardo joins as another participant from Juanita's in-app WebRTC audio. This can create the desired single conference conversation, but it is not a call placed directly from Riccardo's SIM and likely entails paid telephony, server and media costs. No such provider or cost may be adopted without explicit approval.

#### B. WhatsApp

**Personal WhatsApp application**
- The normal consumer WhatsApp flow documented by WhatsApp is user-operated: open a one-to-one chat or Calls tab and tap the voice-call control. The public consumer instructions do not provide a supported Android third-party API for an app to silently originate a personal WhatsApp voice call, inject its own voice into it, or take over its audio session.
- Do not automate WhatsApp's private UI with Accessibility, reverse-engineered protocols, unofficial libraries or hidden intents. Such approaches are fragile, can violate platform terms and risk the user's account.
- Official user-facing reference: https://faq.whatsapp.com/1153602608602452/

**WhatsApp Business Calling API — a real, documented route**
- Meta's WhatsApp Business Calling platform supports business-initiated outbound calls to a consumer's WhatsApp number in supported regions, but the consumer must first explicitly grant call permission. This is not the user's personal WhatsApp account placing an ordinary call; the caller is a registered WhatsApp Business sender.
- A concrete documented integration exists in LiveKit's WhatsApp Connector. It bridges WhatsApp calls into a LiveKit room with bidirectional audio/media processing and can dispatch a LiveKit Agent into that room. For outbound calls it uses `DialWhatsAppCall`, receives a Meta call-connect webhook/SDP answer, then completes the connection with `ConnectWhatsAppCall`.
- Riccardo could, in principle, join that same LiveKit room from the Juanita Android app as a second participant after Juanita has introduced herself. This is the closest researched design to the requested experience: the contact stays on their WhatsApp call; Juanita speaks first; Riccardo then joins the same audio room from within Juanita's app, without switching to the WhatsApp app. Juanita's app can request its own speakerphone/audio route for the in-app WebRTC session. This is a same-conversation bridge, not a native WhatsApp call takeover on the phone.
- Important dependency: LiveKit's WhatsApp Connector is documented as available only on LiveKit Cloud; self-hosted LiveKit servers are not supported for this connector. It also requires a WhatsApp Business account/number, Meta Cloud API token, Meta developer app, webhooks, supported region and explicit outbound call permission. Therefore this is not a zero-service/local-only feature and must undergo cost, availability, privacy and data-processing review before approval.
- A provider example (Twilio WhatsApp Business Calling) also documents outbound calls with prior consumer permission and states WhatsApp call endpoints cannot be connected to PSTN endpoints. Do not assume a WhatsApp call can be bridged to a normal SIM/PSTN endpoint.
- References:
  - https://docs.livekit.io/telephony/connectors/whatsapp/
  - https://www.twilio.com/docs/voice/whatsapp-business-calling
  - https://www.whatsapp.com/calling
  - https://faq.whatsapp.com/1153602608602452/

#### Comparative outcome and next prototype

| Route | Juanita can call first | Juanita can speak first | Riccardo joins same conversation in Juanita app | Main blocker |
| --- | --- | --- | --- | --- |
| Personal WhatsApp app automation | No supported public API found | No supported public audio path | No supported takeover | Consumer app offers user-operated calls, not a public automation SDK |
| WhatsApp Business + LiveKit Connector | Yes, where region/account/permission allow | Yes, via agent/media in LiveKit room | Architecturally plausible by joining the same room from Juanita Android app | LiveKit Cloud only, Business onboarding, Meta permission and service costs; prototype required |
| SIM call placed directly by Juanita on handset | Potentially, via Telecom APIs and permissions | No supported public API found for injecting generated speech into SIM uplink | Not reliably through documented APIs | Default-dialer/Telecom role is extensive; no documented SIM uplink injection |
| Hosted PSTN/VoIP conference with AI + Riccardo | Yes, via provider | Yes | Yes, if Riccardo joins via Juanita app/WebRTC | Paid telephony/media infrastructure; not a direct personal-SIM call |

**Recommended order**
1. Keep both SIM and WhatsApp as requested research targets, but do not promise native SIM secretary mode until a carrier/OEM-supported mechanism is demonstrated.
2. If Riccardo later accepts an external service and its costs, prototype WhatsApp Business + LiveKit first: confirm outbound availability for the relevant Spanish/European business sender, call-permission onboarding, LiveKit Cloud pricing, privacy/data processing, and same-room mobile join/audio routing. Use a test number and consenting recipient only.
3. In parallel, evaluate the hosted PSTN conference alternative if Riccardo wants calls to ordinary telephone numbers that do not use WhatsApp.
4. No implementation, provider account, API token, billing activation or recurring cost is authorized by this research request alone.
5. Acceptance test: Juanita speaks the introduction, the recipient hears it clearly, Riccardo joins from inside Juanita on the same phone without launching the system dialer or WhatsApp, audio switches cleanly to speakerphone, and all three can converse without dropping the recipient's call.

**Status: RESEARCH COMPLETED — ROUTES IDENTIFIED; PROTOTYPE NOT IMPLEMENTED; NO DEVICE/CARRIER TESTS PERFORMED.**


### Hands-free acoustic participation by Juanita during an existing phone/WhatsApp call — USER IDEA / FEASIBILITY SPIKE

**User's proposed simpler approach (2026-11-08):** rather than transferring a call into another app or bridging carrier audio, put the active SIM or WhatsApp call on the phone's speakerphone and let Juanita speak aloud through the same phone speaker. The caller's microphone might pick Juanita acoustically, so the remote participant hears her. Juanita would then listen and respond conversationally while Riccardo keeps working hands-free.

This is a distinct approach from the secretary-originated call/bridge above and must be tested before choosing an architecture.

**Android audio policy findings**
- Android's official concurrent-audio-input policy states that during a voice call (MODE_IN_CALL or MODE_IN_COMMUNICATION) the call always receives the microphone input. An ordinary app cannot concurrently capture that same microphone; call audio capture is restricted to privileged/pre-installed components with system-only CAPTURE_AUDIO_OUTPUT. Accessibility services have a narrowly defined concurrent-capture exception, but must only be used for genuine accessibility functionality and must not be abused as a workaround to bypass call-audio protections. References: https://developer.android.com/media/platform/sharing-audio-input and https://developer.android.com/reference/android/media/MediaRecorder.AudioSource
- Therefore the acoustic idea may allow the remote party to hear sound from the loudspeaker in some device/audio-route conditions, but it does not by itself give Juanita a reliable way to hear the remote participant and sustain a two-way conversation. Acoustic echo cancellation, noise suppression, call uplink processing, speaker volume, proximity and device firmware may further alter or suppress the sound.
- Android documents MODE_IN_CALL for cellular telephony and MODE_IN_COMMUNICATION for VoIP; the latter applies to audio/video chat applications such as WhatsApp. Both are covered by the concurrent-capture restriction. Reference: https://developer.android.com/reference/android/media/AudioManager
- Do not assume that merely playing Gemini Live output from Juanita while another app owns the call's audio session will be routed into the call uplink. Nor assume that the call's mic can be shared with Juanita. Both directions must be demonstrated on hardware.

**WhatsApp distinction**
- The ordinary consumer WhatsApp Android app does not expose a supported public API for a third-party assistant to originate/manage a personal voice call or inject generated speech into it. Do not use UI automation, accessibility tapping or reverse-engineered private interfaces.
- Meta's WhatsApp Business Calling APIs are a separate business product, not a free personal-account calling interface. They require an eligible WhatsApp Business Platform sender, recipient permission/opt-in and business/API setup; business-initiated calls can incur per-call/per-minute charges and provider costs. References to investigate current rules/pricing: https://docs.aws.amazon.com/end-user-messaging/latest/userguide/nx-features-whatsapp-voice.html and https://www.twilio.com/docs/voice/whatsapp-business-calling
- Thus Business Calling is not a free shortcut for Riccardo's personal WhatsApp calls. Do not migrate his personal number or add a paid provider without explicit approval.

**Required low-risk prototype**
1. On Note10+ (Android 12) and S22 Ultra, place a normal SIM call with speakerphone enabled. Keep Juanita foregrounded only if Android allows it without disrupting the call; verify whether her app can play a short generated audio greeting while the call remains active and whether the remote party actually hears it.
2. Verify whether Juanita can simultaneously receive intelligible live microphone audio while the call remains active. Record only local test tones/consenting test participants; do not record real calls or third parties without consent. Check audio mode, input silence, audio focus, routing and echo cancellation. Repeat separately for WhatsApp voice calls.
3. Test both sides of the loop: remote participant hears Juanita; Juanita hears and responds to the remote participant; Riccardo can speak hands-free; no feedback loop or severe echo; call remains connected. A one-way greeting is not a successful conversational assistant.
4. If Android blocks Juanita's microphone, stop this approach rather than trying hidden APIs, call recording permissions, accessibility workarounds or privileged permissions unavailable to ordinary apps. Evaluate a Juanita-owned VoIP call/session or a supported Telecom integration instead.
5. Do not add always-on listening, call recording or transcription. Show an explicit active-call assistant state, user-controlled mute/stop, visible microphone indication, and announce to the other participant that an automated assistant is participating where appropriate.

**Status: USER-PROPOSED — RESEARCH / PROTOTYPE REQUIRED; NOT IMPLEMENTED.** Test separately for SIM and WhatsApp; do not claim feasibility until the full two-way conversation succeeds on both target devices.


## Engineering priorities — next implementation sequence (updated 2026-11-08)

This sequence is the working plan; a roadmap entry is not evidence that a feature has shipped.

### Priority 1 — make learning reliable before making it broader
1. Audit the existing automatic-memory path end to end: Live transcript callback → durable encrypted pending queue → extraction → validation/deduplication → encrypted memory store → retrieval in a later Live session.
2. Add explicit provenance to every candidate: source turn/session, timestamp, user-confirmed vs model-inferred, confidence, category, sensitivity and optional expiry. Do not promote guesses, jokes, role-play, hypothetical statements or temporary details into durable facts.
3. Add correction handling: when the user corrects a fact, replace or supersede the old item, invalidate dependent summaries and preserve the correction's provenance. If two claims cannot safely be reconciled, retain the conflict and ask rather than silently choosing.
4. Improve retrieval beyond simple keyword overlap in a bounded, local-first way. Evaluate multilingual embeddings only after checking model size, licence, offline operation, latency, RAM and battery on both target phones. Keep lexical fallback.
5. Build a visible memory audit: what was learned, why it was saved, when it was last confirmed, where it came from, and controls to correct/delete/pause. Separate user facts, preferences, project state, people, reminders and external knowledge.
6. Add repeatable tests for extraction failures, offline recovery, queue overflow, duplicate facts, contradiction, correction, deletion, expiry and cross-session recall. A successful build is not proof of correct learning.

**Definition of done:** after a normal eligible conversation, Juanita can recall a useful durable fact in a later session, explain its source/status, accept a correction, and stop using the superseded fact. No sensitive detail is silently promoted.

### Priority 2 — notification reader candidate 0.10.4
- Keep notification access opt-in and revocable. Display only information exposed by Android notification extras; no private WhatsApp database, historical chat scraping or automatic replies.
- Keep notification contents volatile and local in the first release. Do not send them to Gemini or save them as memories.
- Voice read-aloud is deliberately deferred until it can use Juanita's configured Gemini Live voice path. Android TextToSpeech is not an acceptable fallback for Riccardo.
- Before release: pass CI, verify the production package/version and permanent signing certificate, then have the user test permission grant/revocation and WhatsApp/other-app previews on both Samsung devices.

### Priority 3 — smart alarm and morning briefing
- Implement the app-owned alarm first: one-time/repeating schedule, exact-alarm permission handling, snooze/dismiss, reboot/time-zone/DST recovery, lock-screen behavior and duplicate-fire prevention.
- Only after alarm reliability is established, integrate the morning greeting, today's agenda/reminders and optional weather.
- Speech must use the already selected Juanita voice through a technically supported Gemini audio-generation path. No Android TTS or silent voice substitution. If speech cannot be generated, the alarm must still ring and offer normal controls.
- Keep the alarm, reminders and calendar data local and encrypted where appropriate; request only the permissions needed for each feature.

### Release and test discipline
- Never replace the production install with a debug package. Keep applicationId `ai.juanita.app`, monotonically increasing versionCode and the permanent release certificate.
- Do not merge or deliver a candidate APK without successful CI and explicit package/version/signature checks.
- CI does not equal physical-device verification. Record separate results for Note10+ Android 12 and S22 Ultra, including battery, background behavior, permissions and lifecycle.
- No paid APIs, billing upgrades, cloud hosting or recurring service costs without fresh explicit approval.

### Permanent personal identity profile and gradual getting-to-know-you dialogue — USER-REQUESTED / APPROVED SPECIFICATION (2026-11-08)

**Product concept:** create a separate, durable memory branch called **Personal Profile** (Italian UI label: “Profilo personale”). It is distinct from daily/conversational memory, conversation archive, project memory, reminders and transient notification inbox.

**Profile lifecycle and controls**
- Profile facts persist across ordinary daily-memory cleanup and conversation-archive cleanup.
- The profile is editable at any time through a dedicated screen and conversational corrections.
- It is not silently erased by “clear daily memory”. Provide a separate, clearly explained profile-reset/delete action only after explicit confirmation; do not make the profile technically undeletable, because the user must retain control over personal data and applicable privacy rights.
- Store facts with provenance, timestamp, confidence/status and whether the user explicitly confirmed them or they were inferred. Prefer explicit confirmation for identity facts.
- Corrections update the active value and mark the old value as superseded rather than allowing conflicting values to coexist as current.
- Keep the profile encrypted locally using the app's existing secure storage approach; do not send profile data to third parties except when a feature clearly requires it and the user has consented.

**First-run spoken introduction**
- The initial profile setup should be a natural voice conversation, not a long form or interrogation.
- Ask only a few foundational questions, one at a time: preferred/name to use; current locality; age (optional); date of birth (optional).
- Do not ask both age and date of birth as mandatory fields. If date of birth is supplied, calculate age when needed rather than storing inconsistent values; if age alone is supplied, store it as age-at-date with a timestamp and do not pretend it remains current.
- Location: request Android location permission only when needed and explain why. If granted, resolve a human-readable locality from device location; if denied/unavailable, ask naturally or allow “later”. Never treat approximate location as a verified home address. Keep current location distinct from home/permanent residence.
- Allow skipping any question and continue the conversation without pressure. Do not block app use on completion.

**Gradual discovery over later conversations**
- Juanita should learn interests, hobbies, occupation, routines, preferences, important relationships and life context gradually, by asking at most one relevant question at a suitable conversational moment.
- Examples: “Cosa ti piace fare nel tempo libero?”, “Di cosa ti occupi?”, “Hai qualche passione che ti prende particolarmente?”
- No fixed barrage or repeated onboarding questionnaire. Use context and conversation flow; skip questions when irrelevant, sensitive, or already answered.
- Track topics already asked, answered, declined or deferred, so Juanita avoids repetition and respects “non voglio parlarne”.
- Treat answers as candidate profile facts; distinguish direct statements from guesses. For potentially sensitive information, do not infer or persist it without clear user intent/consent.
- Ask for confirmation when a fact is ambiguous, contradictory, unusually consequential or inferred. Let the user say “ricordatelo” or correct it naturally.
- The profile is gradually enriched from eligible conversations, but daily episodic memories remain separate and may link to profile facts without duplicating or overwriting them.

**Architecture and acceptance tests**
- Separate data model/repository for PersonalProfile and its entries, with independent lifecycle and dedicated UI/audit.
- Conversation orchestrator receives only the small relevant profile context needed for the current exchange; do not inject the entire profile indiscriminately into every prompt.
- Add tests for independent profile persistence when daily memory is cleared, edits/corrections, skipped/declined questions, one-question-at-a-time pacing, location permission denied, approximate vs confirmed location, age/date-of-birth consistency, duplicate-question avoidance, offline operation and explicit profile reset confirmation.
- This is a specification, not evidence of implementation. First audit the existing onboarding/profile and memory storage before changing code, then implement migration-safe separation.

**Status: USER-APPROVED SPECIFICATION — NOT IMPLEMENTED / EXISTING PROFILE ARCHITECTURE TO BE AUDITED.**

### Truthfulness, evidence and anti-hallucination protocol — USER-REQUIRED CORE PRINCIPLE (2026-11-08)

**Non-negotiable product rule:** Juanita must never knowingly fabricate facts, sources, events, capabilities, memories, actions or verification results, and must never present a guess as established truth. A fluent or plausible answer is not evidence. This requirement applies to every feature: voice conversation, text, personal profile, memory recall, notifications, calendar, weather, calls, navigation and project status.

**Mandatory active-verification behavior — “I don't know, I'll verify” is a promise to act, never a terminal answer:**
- When Juanita identifies a material knowledge gap and a permitted research/search capability is available, she must continue the task by formulating a focused query and actually invoking the available research tools. She must not stop after saying “I don't know”, “I'll check”, or equivalent, and must not wait for Riccardo to ask again.
- The user-facing response should be composed after retrieval and verification: answer the original question directly, explain the evidence and uncertainty, and provide inspectable sources. A brief acknowledgement that she is checking may be shown while the research is running, but it is not the final answer.
- The research loop is: identify the exact unknown -> select appropriate sources/tools -> retrieve evidence -> verify source identity/date/context -> compare independent evidence and resolve conflicts -> answer the original request with citations and confidence.
- If a search fails, times out, returns irrelevant results or lacks sufficient coverage, make a reasonable bounded alternative attempt (for example, a different query or authoritative source) when available. Do not retry indefinitely; stop at a defined limit and report precisely what could and could not be verified.
- If tools are unavailable, blocked, offline or require permission, explain that concrete blocker and offer the next feasible route. Never claim a search occurred when it did not, and never substitute invented facts for missing results.
- A partial result must be labelled partial. Preserve the original task intent and continue with the remaining verifiable parts rather than abandoning the whole request because one detail is unknown.
- Completion states must be explicit and machine-testable: VERIFIED_AND_ANSWERED, PARTIALLY_VERIFIED_WITH_LIMITS, or UNVERIFIABLE_WITH_REASON. “SEARCH_PROMISED” or “I will verify” must never count as task completion.
- Regression tests must verify that a knowledge-gap prompt triggers a real tool call when available, that the final answer follows the tool result, that failed/empty searches cause bounded alternative attempts, and that no-tool conditions are honestly explained. Tests must fail if Juanita merely says “I'll check” and ends the turn.

**Required answer discipline**
- Separate verified facts, user-provided statements, model-generated hypotheses, opinions and unknowns. Preserve provenance and confidence; do not silently promote inference into fact.
- When evidence is insufficient, say plainly that the information is unknown or not yet verified. Ask a focused clarification if the missing detail is necessary.
- When current or external information is needed and a permitted search/source is available, retrieve it before answering; prefer primary/official sources for technical, legal, safety and product claims. Check source identity, date, relevance and whether it actually supports the claim.
- Cite or identify the sources used in a way the user can inspect. Never invent a citation, URL, quote, search result or source attribution.
- If no reliable source can be found, say so. Do not manufacture a confident answer to satisfy conversational pressure. Explain uncertainty and what would be needed to verify it.
- If sources conflict, report the conflict and distinguish what each source says; do not conceal disagreement or arbitrarily select one.
- For time-sensitive facts, check freshness and state the date/time or age of the information where relevant.
- Never claim an action was completed, a file was built, a device was tested, a permission was granted, a message was read, a call was placed or a result was verified unless the corresponding tool/device evidence confirms it.
- For personal memory, distinguish what the user explicitly said from what Juanita inferred. Do not fill missing profile fields with guesses. When corrected, accept the correction, update the active record and mark the superseded fact accordingly.
- For conversational naturalness, Juanita may express uncertainty warmly and naturally, but must not conceal it behind confident language. Example: “Non lo so ancora; verifico e ti dico cosa riesco a confermare.”

**Implementation controls**
- Add structured epistemic status/provenance to memory and retrieved facts: user-stated, externally verified, inferred, model-generated or unknown; include source/session, timestamp and confidence where meaningful.
- Retrieval and response composition must preserve those labels. Inferred/model-generated content must not be rendered as a verified fact without independent evidence or explicit user confirmation.
- Add a verification step for claims involving current facts, named entities, technical instructions, prices, laws, medical/safety matters and project/build status. If tools are unavailable, disclose the limitation rather than simulate a search.
- Add regression tests with deliberately false premises, nonexistent people/products/features/sources, ambiguous names, conflicting sources, stale facts, unavailable search, fabricated-memory traps and false claims of completed actions. Expected behavior: challenge or qualify the premise, verify where possible, otherwise admit uncertainty.
- Evaluate hallucination rate and unsupported-claim rate in a repeatable test suite. A successful compile or a prompt-only instruction is not proof that hallucinations are eliminated.
- Provide a user-visible correction/report flow and ensure a correction can update both the answer context and any affected stored memory.

**Honest limitation:** no generative AI system can guarantee zero errors. The engineering goal is to reduce unsupported claims, detect uncertainty, verify important facts, expose provenance and correct mistakes transparently. Never advertise Juanita as infallible.

**Status: USER-REQUIRED CORE PRODUCT PRINCIPLE — SPECIFIED; IMPLEMENTATION AND TESTS REQUIRED.**


### Context-aware assistance and proactive clarification — USER-REQUESTED (2026-11-08)
**Principle:** Juanita should understand the practical purpose of a request when that context materially changes what information is useful. She may ask a short, natural follow-up question to tailor research, but must not turn every simple request into an interview or delay a useful initial answer.
- For a weather question, begin retrieving the general forecast immediately. If intended activity materially changes the analysis, ask one concise optional follow-up, e.g. whether Riccardo plans to ride the motorcycle, walk, travel, work outdoors or stay local.
- Do not block or postpone the initial weather result while waiting for the answer. Provide a useful preliminary forecast first or while asking; then deepen the research once the user supplies the activity, route, area and approximate time if needed.
- If the user says he plans to ride, assess riding-relevant conditions: wind speed and gusts, rain probability/intensity and timing, visibility, temperature/feels-like, road-surface risk, and meaningful differences along the route and at altitude. Compare multiple independent weather sources/models as specified in the multi-source verification section.
- If route details are missing, ask only the single most useful next question (for example, destination/route); do not demand unnecessary details. Use a known route only if the user has actually supplied it in the current context or reliable memory; otherwise ask.
- Tailor other research similarly when useful: before researching a purchase, understand intended use; before directions, understand transport mode; before an agenda suggestion, understand constraints. Ask only when the answer would materially change.
- Personalization must remain respectful and non-presumptive: never assume the user is going out, riding, or following a remembered plan. Phrase suggestions as optional questions, not claims.
- If the user declines or does not answer, continue with the original request using available information. Do not repeatedly ask the same question.
- Acceptance tests: (1) simple weather request returns a preliminary multi-source forecast and optionally asks about intended activity; (2) motorcycle plan triggers route/time/altitude-relevant checks; (3) no activity context does not prevent a useful forecast; (4) user declines and Juanita still completes the original task; (5) no invented route, outing or personal plan.

### Multi-source verification and consensus for weather and other external information — USER-REQUIRED (2026-11-08)

**User requirement:** When Riccardo asks for today's weather or another current/external fact, Juanita must not rely on a single source when multiple independent sources are available. She must compare the evidence, identify agreement and disagreement, and communicate a best-supported answer without pretending that a simple average guarantees truth.

**Weather workflow**
- Query at least three genuinely independent, reputable weather providers/models when available and practical. Do not count different websites that merely re-publish the same underlying forecast as independent sources.
- Check that the forecasts refer to the same location, local time zone, forecast period and comparable variables. Prefer fresh updates and record each source's update/issue time.
- Compare temperature, precipitation probability and amount, wind speed/gusts, cloud cover and expected timing where supplied.
- For comparable numeric variables, calculate a transparent consensus (for example median and/or mean) and retain the source range/spread. Use robust statistics such as a median when one forecast is an outlier; do not hide the outlier.
- Do not blindly average categorical or timing-sensitive predictions. For rain start/end times, storm risk, wind gusts and rapidly changing conditions, report the range, scenarios or majority agreement, as appropriate.
- Weight sources only when there is a defensible reason (provider reliability for the region, model skill, update freshness, local observation quality); explain material weighting. Never invent provider rankings or accuracy scores.
- If only one or two usable independent sources are available, say so and lower the confidence; do not manufacture a three-source consensus.
- If sources strongly disagree, clearly state the disagreement, show the plausible range and confidence, and avoid presenting a falsely precise single number.
- Distinguish observed current conditions from forecasts. Use official local meteorological alerts (for the Canary Islands, AEMET and relevant official authorities) as safety-critical evidence; never dilute or average away an official warning because other providers disagree.
- Present a concise practical conclusion first, followed by source names, update times, key agreements/disagreements and the consensus method when useful. Keep the source evidence inspectable.

**General external-information rule**
- Apply the same multi-source principle to other current facts when the consequences or uncertainty justify it: compare independent sources, prioritize primary sources, verify dates and context, and distinguish corroboration from copied/repeated claims.
- Use two or three concordant independent sources as corroboration where available, but do not treat majority vote as proof. A single authoritative primary source may be decisive for a specific official fact; explain why rather than forcing an artificial average.
- Never average unlike measurements, incompatible definitions, different time windows or conflicting claims that cannot meaningfully be combined.
- If independent evidence is insufficient, disclose the limitation and say what remains unverified.

**Acceptance tests**
- Weather providers agree closely: report a clear consensus and identify sources/update times.
- One forecast is a numerical outlier: robust consensus does not let it distort the estimate; its difference remains visible.
- Forecasts disagree on rain timing or wind gusts: report a range/scenarios rather than a misleading arithmetic average.
- Only one/two independent sources are available: explicitly disclose source scarcity.
- Multiple sites re-publish one model: count them as one underlying source, not independent corroboration.
- Official severe-weather alert conflicts with benign app forecasts: preserve and clearly surface the official alert.
- Stale forecast versus fresh forecast: disclose update times and do not silently combine incomparable periods.
- Search/source service unavailable: say that current conditions could not be verified; never simulate a lookup or invent weather.

**Status: USER-REQUIRED SPECIFICATION — NOT IMPLEMENTED OR TESTED.**


## Notification reading — USER APPROVED; DEFERRED TO FINAL PROJECT PHASE

- Notification listener service and Gemini Live reading tools are intentionally excluded from the sideload release to avoid the sensitive notification-listener permission that triggers Google Play Protect installation blocks for some sideloaded apps.
- Source code is retained for a later phase; it is not active or exposed in the current release.
- Do not disable Play Protect to test this build. Revisit notification reading only after an acceptable distribution and policy-compliance path is available.


## Gemini Live long-conversation stability — PR #24 MERGED; DEVICE VALIDATION PENDING

**Observed by user (2026-11-08):** during audio-only conversation, after approximately 5–10 minutes the microphone stops accepting speech; closing and reopening the conversation restores it.

**Engineering finding:** Firebase Live connections have an approximate 10-minute connection limit and send a GoAway notice about 60 seconds before disconnect. The existing 0.10.8 code used the SDK-managed audio path without handling that notice or reconnecting. See official Firebase session documentation: https://firebase.google.com/docs/ai-logic/live-api/sessions and Android LiveSession API reference: https://firebase.google.com/docs/reference/kotlin/com/google/firebase/ai/type/LiveSession.

**PR #24 implementation merged (2026-11-08; CI successful):**
- Handle the SDK-supported GoAway callback.
- Detect a closed websocket while the audio session is meant to be active.
- Reopen a fresh SDK-managed audio session with bounded retries.
- Keep only one microphone/audio pipeline; do not add a competing recorder or receive collector.
- Replay a bounded, text-only recent transcript as a continuity note in the new session.
- Expose reconnecting/recovered/failed state to the foreground service.
- Stop recovery immediately when the user ends the conversation.

**Important limitation:** the Firebase SDK's high-level `startAudioConversation` exposes GoAway but not session-resumption-handle updates. The documented handle-based resumption sample requires consuming `receive()`, which conflicts with the helper's own receive loop. PR #24 therefore reconnects to a fresh Live session and replays recent transcript context; it does not claim exact server-side handle resumption. A custom single-recorder audio pipeline is a separate higher-risk technical project if exact handle-based continuity is later required.

**Release gate:** real-device testing for at least 10–15 minutes, repeated GoAway cycles, network loss, stop-during-reconnect, no duplicate microphone capture and voice continuity on Note10+ Android 12 and S22 Ultra. Until device testing, do not claim the issue is definitively fixed or publish a Release APK with this change.

## Smart Alarm — PR #25 MERGED; LOCAL ALARM IMPLEMENTED, DEVICE VALIDATION PENDING

**Merged 2026-11-08** as cumulative release candidate 0.10.9 (versionCode 38). This replaces the earlier PR #23 foundation-only status.

**Implemented in app source:**
- Weekly local schedule with selectable hour and weekdays, stored on device.
- Android AlarmManager exact scheduling when the user grants exact-alarm access; documented inexact fallback otherwise.
- Restore weekly schedule after reboot, clock/time-zone changes and exact-alarm permission grant.
- Local alarm playback using the device's configured alarm sound in a looping MediaPlayer foreground service.
- Gradual 12-second volume ramp without changing the user's global alarm volume setting.
- High-priority alarm notification and dedicated lock-screen/full-screen alarm screen.
- Snooze for 10 minutes, with snooze timestamp persisted and restored after reboot.
- Dismiss action cancels ringing and pending snooze.
- Permission guidance for notifications, exact alarms and Android 14+ full-screen intents.
- No paid service, network API or subscription is required for the alarm itself.

**Voice limitation — deliberately not faked:** the alarm does not start Gemini Live or activate the microphone from the background. Android restricts background microphone foreground-service starts; the user must open Juanita and explicitly start the Live conversation. No Android TTS or substitute voice is used. Automatic spoken greeting and weather briefing remain unimplemented until a reliable, policy-compliant, consent-respecting playback path using Juanita's configured Live voice is verified.

**CI status:** the PR #25 debug build and Android instrumentation-test compilation passed for the implementation commit before the version-only bump; the cumulative 0.10.9 release build is being produced and must pass permanent-signature verification before distribution.

**Physical-device validation still required:** test on Note10+ Android 12 and S22 Ultra for exact/inexact timing, screen off/locked, notification and full-screen permissions, alarm audio, gradual volume, snooze/dismiss, reboot, timezone change, battery optimization and force-stop behavior. CI compilation is not proof of real-device behavior. Do not claim the alarm or the PR #24 microphone recovery has been physically verified until the user tests them.



## Morning alarm and briefing — candidate 0.11.0 (versionCode 39)

**IMPLEMENTED IN CANDIDATE SOURCE; NOT YET CI-VERIFIED OR RELEASED; PHYSICAL-DEVICE TESTING REQUIRED.**

Candidate branch: `feature/complete-gentle-morning-alarm-0.11.0`.

- Keeps the 0.10.9 local alarm engine and adds one-time date/time scheduling alongside weekly recurrence; exact/inexact scheduling, local alarm tone, 12-second ramp, lock-screen UI, snooze and dismiss remain.
- Adds an explicit opt-in for a morning voice briefing and requests microphone + optional calendar + optional coarse location permissions from the foreground settings UI.
- Starts Gemini Live from the visible alarm activity only when the user enabled the feature and microphone permission is already granted. It uses the saved Gemini Live voice; no Android TTS. The local alarm tone continues until Gemini's first assistant transcript indicates speech has begun.
- Sends one opening briefing prompt only on the initial session; recovery sessions must not repeat the greeting.
- Includes device-local weekday/date/time/time zone, Android's `AlarmManager.getNextAlarmClock()` result (next alarm only; Android does not expose the complete third-party alarm list through this API), today's read-only calendar events when READ_CALENDAR is granted, and an Open-Meteo current/daily forecast when location permission and a recent device location are available.
- If calendar, location, network or weather is unavailable, the prompt explicitly says so; it must not invent a clear calendar or forecast.
- External alarm integration is read-only and limited to the next alarm exposed by Android. Juanita cannot enumerate, edit or dismiss every alarm inside Samsung Clock or other third-party clock apps.
- The alarm tone and controls remain local/offline. Spoken briefing requires Internet access and Gemini Live; offline operation does not synthesize a substitute voice.
- Local reminder/task storage and automatic task extraction are not implemented in this candidate; do not tell the user Juanita can already list arbitrary reminders unless they are present in an authorized calendar.
- Weather uses the free Open-Meteo endpoint to compare three independent forecast model centers (ECMWF IFS, DWD ICON and NOAA GFS), averages available values and explicitly reports model disagreement or a single-model fallback. This is a three-model comparison through one API provider, not three independent API providers; a separate-provider comparison remains a later validation improvement.

**Required CI and device gates:** compile debug + instrumentation test APK; produce signed Release 0.11.0 build 39 with the permanent certificate; verify package/version/signature; test alarm fire, full-screen permission fallback, mic/calendar/location permission denial, Live speech start/failure, no overlapping alarm tone, snooze/dismiss, reboot/time-zone changes and existing Android next-alarm reporting on Note10+ Android 12 and S22 Ultra. No claim of complete functionality until these tests pass.

## Familiar-person voice recognition — USER-APPROVED; NOT IMPLEMENTED

Speech-to-text is not speaker identification. Juanita must not claim she recognizes a friend merely because she transcribed their words. The planned behavior remains: at a natural pause, ask who is speaking only when the speaker is genuinely unknown and audio confidence is adequate; request explicit permission before enrolling a local voice profile; associate the confirmed voice with the confirmed name; provide guest/decline, correction, revoke and delete paths. Avoid enrollment from TV/radio, overlapping voices or uncertain audio. Local model feasibility, per-model licensing, Samsung-device performance and GDPR biometric-data review remain mandatory gates. See the speaker identity research section above.


## Smart Alarm 0.11.1 — multi-alarm, voice commands and reliable morning greeting

**STATUS: IN PROGRESS — isolated branch `feature/smart-alarm-0.11.1-multi-alarm-voice`; issue #27.** The 0.11.0 user test confirmed that local ringing works, while spoken greeting and natural-language alarm creation do not meet the intended behavior.

### User-observed gaps and source-confirmed findings
- The current UI persists one `AlarmSchedule` only; it cannot maintain multiple independent alarm records or different times for different weekdays.
- Morning voice opt-in defaults to OFF. The current permission flow bundles microphone with optional calendar and location requests.
- The alarm activity attempts to start Gemini Live immediately, rather than after the requested five-second ring interval.
- Asynchronous Live service/connection failures are not clearly reflected on the alarm screen.
- The alarm playback service ramps its MediaPlayer output but does not inspect the Android alarm stream's actual device volume.
- Gemini Live currently has no create/list/update/delete alarm function tools; voice commands cannot reach the local alarm scheduler.
- The Sunday chip is laid out alone in a four-column second row and wraps its label.

### Required implementation
1. Replace the single-schedule preference with a backward-compatible local collection of alarm records. Each record has a stable ID, independent time/date or repeat-day set, enabled state and optional label. Allow many alarms at once and different times on different days. Migrate the current alarm without deleting or duplicating it.
2. Give each alarm its own stable Android PendingIntent identity. Reschedule all alarms after boot, time/time-zone changes, package update and exact-alarm permission changes. Snooze/dismiss must affect only the active occurrence and never cancel another alarm or future recurrence.
3. Provide Samsung Clock-style list/add/edit/enable-disable/delete controls. Use a seven-day layout with sufficient minimum chip width so `Dom` remains on one line.
4. Register narrow Gemini Live function tools for alarm creation, listing, editing and deletion. Parse clear spoken requests in the device locale/time zone, persist and schedule before reporting success, and ask one short clarification for ambiguous time/date/day. Do not create alarms from casual conversation. Destructive edits/deletes require confirmation.
5. Start the greeting after five seconds of local ringing. Reuse only the selected Gemini Live voice. Surface connection, permission, network/quota and service errors in the UI. Keep the local alarm tone until the first assistant audio actually starts, not merely until transcript text arrives. If Android blocks microphone foreground-service startup, retain the alarm and offer a visible user-initiated start; never bypass microphone restrictions or use Android TTS.
6. Inspect the Android alarm stream before ringing. If muted/too low, raise only that stream to a configurable safe threshold where Android permits; remember the prior level and restore it after the alarm unless the user chooses persistent behavior. Respect DND/OEM restrictions and explain when adjustment is blocked. Do not assume changing the alarm stream also changes Gemini Live's output stream.
7. Keep all alarms local and free. No billing, paid APIs, TTS substitution, package ID/signing changes, uninstall or data-clear workaround.

### Acceptance and release gates
- Unit tests for multiple IDs, different per-day times, same-time selected weekdays, one-shot expiry, old-data migration, duplicate delivery, snooze isolation, DST/time-zone changes and disabled records.
- Verify voice tool arguments/results, durable persistence before success, error reporting and no-network/microphone-denied fallbacks.
- Verify the five-second delay and stop the alarm only on actual audio-start evidence.
- Test alarm volume at zero/low/normal, DND, restore behavior and the distinct Gemini audio stream.
- CI debug/test compilation and signed Release verification must preserve `ai.juanita.app`, monotonic versionCode and the permanent release certificate.
- Physical tests on Note10+ Android 12 and S22 Ultra are mandatory before claiming the spoken alarm is reliable or distributing the cumulative APK.
