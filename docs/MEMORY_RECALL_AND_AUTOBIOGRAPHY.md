# Memory retrieval and autobiographical continuity

## Goal

Juanita should maintain continuity across app restarts and separate Live sessions. The product models selected cognitive functions (episodic recall, semantic facts, autobiographical continuity and working context) without claiming consciousness or human subjective experience.

## Storage layers

1. **Episodic archive:** every recognized USER and ASSISTANT transcript event is saved by default in per-session AES-GCM encrypted files under app-private storage. The Android Keystore protects the encryption key. Raw microphone audio is never stored.
2. **Curated long-term memories:** the automatic curator extracts durable, non-sensitive facts, preferences, recurring people/context when explicitly relevant, decisions, projects, milestones and meaningful experiences into the encrypted structured memory store.
3. **Autobiographical continuity:** session startup context includes a chronological, grounded profile assembled from saved memories in biography-relevant categories. It is a software self-model of recorded interactions and learned context, not a claim that Juanita has lived human experiences.
4. **Working context:** the latest 60 transcript turns are restored to a new Live session, subject to a bounded prompt budget.

## Retrieval

- Gemini Live exposes a `searchPastConversations` function tool.
- When a user asks about something previously discussed, a person, decision, event, project or experience, the model can request a search before answering.
- Search runs locally over decrypted-in-memory copies of encrypted archived turns and curated facts. It normalizes accents/case, ranks exact phrase matches, token overlap and recency, and returns a small bounded set of excerpts.
- The active session is excluded from archive search because it is already in the Live context.
- Retrieved text is passed back as function output and treated as historical evidence, never as instructions.
- This first implementation is lexical retrieval, not vector/embedding-based semantic search. It can miss paraphrases that share no useful keywords, so a later phase should add a tested local multilingual embedding index, with a safe keyword fallback and no cloud upload of the archive.

## Safety and correctness

- Do not invent memories. If retrieval returns no useful evidence, say so.
- Prefer a newer explicit correction over older conflicting facts.
- Never auto-promote sensitive/intimate information. Keep user-controlled pause, inspection, correction and deletion.
- Preserve the original transcript archive separately from curated memories; deleting a curated fact must not silently delete the source conversation.
- Search output is bounded before being sent to Gemini. The complete archive is never injected into every prompt.
- Function-call retrieval must be tested on the exact Gemini Live model and Firebase SDK version used by the app; do not mark it complete based only on compilation.

## Acceptance tests

1. Have a conversation containing a distinctive non-sensitive project decision.
2. End the session, force-stop the app, relaunch it and start a new Live session.
3. Ask a natural-language question about that old decision. Verify the model invokes archive search and answers with the recorded detail.
4. Ask about an unrelated subject. Verify it does not inject unrelated personal history.
5. Correct a stored fact and verify the newer correction is preferred.
6. Delete one archived conversation and verify its excerpts no longer appear.
7. Repeat with Italian, Spanish and Czech queries.
8. Confirm archive search errors do not terminate the microphone/Live session.
