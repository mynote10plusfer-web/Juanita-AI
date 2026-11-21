# Juanita AI — repository-wide engineering rules

All AI coding agents and human contributors must follow these rules.

## Protected project invariants
- The sole cumulative development lane is PR #58 on `integration/unified-rollup-2026-11-18`. Do not work on superseded PRs or branches.
- PR #58 must remain open/draft and must never be merged, released or published without explicit user approval.
- Stable release v0.11.12 / versionCode 51 / application ID `ai.juanita.app` is protected. Debug application ID remains `ai.juanita.app.debug`.
- Preserve production signing identity, upgrade compatibility, existing Android Keystore keys, encrypted schemas and all user data.
- Exactly one character identity is active per voice session. Shared durable user knowledge may be visible to all characters; transcript episodes and private memories remain scoped to the exact stable DigitalIdentity ID. Never infer ownership from display name or gender.
- Character voice, language, dialect and personality are independent configuration. Gemini Live native voice is the active speech path; separate cloud TTS remains disabled.
- Exactly six tabs: Home, Avatar, Sveglia, Settaggi, Memoria, Altro.
- No paid APIs, services, hosted databases, GPUs, subscriptions, usage-based billing or Firebase Blaze.
- No raw microphone audio retention. Never expose transcripts, personal memories, credentials or keys in logs.
- Avatar generation, animation and lip-sync are the final roadmap phase.

## Required reading
Before substantial work, read `docs/PRODUCT_ROADMAP.md`, `docs/MEMORY_ARCHITECTURE.md`, `.github/agents/juanita-chief-engineer.agent.md`, then inspect current implementation, tests, manifest, Gradle configuration and the latest PR #58 diff.

## Approved execution order
1. Reliability/device safety: audio lifecycle, microphone ownership/teardown, reconnect, audio routing, wake locks, thermal/battery, encrypted storage integrity, Main-thread I/O, diagnostic logging and archive scalability.
2. C3 controlled conversational presence with explicit consent and visible state; no hidden listening or unsolicited activation.
3. C4 character engine: one immutable runtime snapshot per session; independent identity/voice/personality; grounded learning and corrections.
4. C5 encrypted memory/archive: shared user knowledge plus identity-private episodes, provenance, retrieval, corrections, deletion/index consistency, migration, export/restore and scale tests.
5. Personal Organizer and Agenda.
6. Avatar last.

## Definition of done
- Establish exact branch and HEAD before editing.
- Reproduce or isolate the issue; do not guess.
- Identify data-loss, privacy, lifecycle, concurrency, battery and compatibility risks.
- Add regression tests with the smallest safe fix.
- No disk/network/model work on Android Main or real-time audio callbacks.
- Run focused tests, JVM tests, instrumentation, static analysis and debug package identity checks as applicable.
- Review the full diff for scope, weakened tests, privacy leaks, migration hazards and unrelated changes.
- Report exact test outcomes, skipped checks and physical-device requirements.
- Emulator/CI success never proves Samsung microphone teardown, real routing, thermals, battery or upgrade compatibility.
- Never silently drop durable transcript events or claim unavailable encrypted memory is empty.
- Never replace a lost Keystore key while ciphertext exists.
- Never merge, release, publish, change signing or alter stable branch without explicit user approval.
