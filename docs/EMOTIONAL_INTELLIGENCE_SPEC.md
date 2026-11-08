# Juanita AI — Contextual emotional understanding

**Status: USER-REQUESTED PRODUCT REQUIREMENT — SPECIFIED; NOT IMPLEMENTED OR VALIDATED.**  
**Requested:** 2026-11-08

## Product goal

Juanita should recognize possible emotional cues in the interlocutor's communication and adapt her response with tact, warmth and appropriate restraint. This is contextual assistance, not mind-reading, diagnosis or a claim that the system knows a person's inner state.

The goal is to understand not only the literal content of a message, but also the likely emotional tone and conversational need: for example, whether the person may be frustrated and needs a practical solution, worried and needs calm clarity, excited and wants to share good news, or sad and may prefer to be heard before receiving advice.

## Signals and staged capability

1. **Text (first stage):** analyze wording, punctuation, conversational context, changes from the person's usual communication style where relevant, and explicit statements such as “sono preoccupato”. Do not infer a stable personality or emotional condition from isolated wording.
2. **Voice (later stage):** optionally analyze prosody-related cues such as pace, pauses, intensity, pitch variation and vocal tension, only if the current audio architecture exposes the signal reliably. The existing Gemini Live transcription callback provides text, not a validated local prosody stream; do not claim voice-emotion recognition is implemented from transcript text alone. Do not create a second competing microphone capture path.
3. **Video (optional future stage):** if the user explicitly enables camera processing, investigate visible non-identifying conversational cues such as facial expression and posture. A facial expression is ambiguous and must never be treated as proof of an emotion. Camera use and any transmission of frames require separate clear consent.
4. **Context over time:** use the immediate conversation and only relevant, consented profile/context memories. A person's baseline, culture, language, neurodiversity, fatigue, environment and communication style can change how cues appear; avoid universal assumptions.

## Response behavior

- Represent emotional interpretation as a tentative hypothesis with calibrated confidence, not as a fact. Internally distinguish explicit self-report, observed communication cue, model inference and unknown.
- Prefer gentle reflection over categorical labeling: “Mi sembri un po' contrariato, ho capito bene?” rather than “Sei arrabbiato.”
- When confidence is low, do not announce an emotion. Respond to the actual content and, if useful, ask one short, non-leading question.
- Adapt the response to the likely need: listen before advising; be concise and practical when the person appears overwhelmed; share enthusiasm without exaggerating; offer calm reassurance without minimizing real problems.
- Never manipulate, pressure, shame, exploit vulnerability, manufacture intimacy or use emotional cues to maximize engagement.
- Never diagnose depression, anxiety, trauma, personality disorders or other health conditions from text, voice or images. If a person expresses immediate danger or self-harm intent, follow the app's separately defined safety response rather than relying on emotion classification.
- Do not infer sensitive traits or store emotional labels as permanent profile facts. Temporary emotional states should normally remain ephemeral. Save only when the user explicitly asks or clearly confirms a durable preference relevant to future support.
- Respect corrections immediately: if the person says the interpretation is wrong, accept it without arguing and adjust the current conversation.

## Architecture and privacy

- Start with a lightweight contextual layer in the conversation orchestrator; do not add a separate always-on microphone or camera recorder.
- Keep raw audio and video out of persistent storage. No emotion surveillance in the background.
- Any future voice/video signal analysis must be opt-in, transparent, revocable and separately evaluated for privacy and legal compliance in Spain/EU.
- Keep emotional hypotheses out of the permanent Personal Profile by default. If a user explicitly asks Juanita to remember a support preference (for example, “when I'm stressed, help me make a short plan”), store the preference as user-confirmed, not the inferred emotional episode.
- Do not send additional audio/video to external services solely for emotion analysis without clear disclosure and consent. No paid APIs or services without explicit approval.
- Maintain the core truthfulness protocol: no claim that Juanita can know exactly what someone feels; distinguish what the person said from what the model inferred.

## Evaluation and acceptance tests

- Test identical words in different contexts to ensure the system does not over-label emotion.
- Test sarcasm, jokes, short replies, punctuation, multilingual speech, accents and ambiguous phrases.
- Test explicit self-report versus inferred cues; explicit correction must override the hypothesis.
- Test that neutral or insufficient evidence yields no emotion label.
- Test that the assistant can listen and ask a gentle clarifying question without derailing the user's original request.
- Test that inferred temporary states are not silently written to permanent memory.
- Test privacy controls: camera/microphone off means no corresponding analysis; revoke consent and verify processing stops.
- Evaluate false positives and false negatives across languages and demographic groups; do not ship a feature based only on a prompt instruction or successful compilation.
- Physical-device tests on Note10+ (Android 12) and S22 Ultra are required for any audio/video implementation.

## Delivery sequence

1. Textual/contextual emotional sensitivity with uncertainty and correction handling.
2. Research and prototype whether a reliable, single-capture local voice-prosody path is feasible without regressing Gemini Live.
3. Only after explicit approval, research optional visual cues with strict camera consent and privacy safeguards.
4. Add regression datasets, calibration and user-facing controls before describing the capability as available.

**Honest limitation:** emotional states are not directly observable. Text, voice and facial cues can be misleading; the feature must remain probabilistic, context-aware and correctable. The intended outcome is more considerate communication, not a machine that claims to read minds.
