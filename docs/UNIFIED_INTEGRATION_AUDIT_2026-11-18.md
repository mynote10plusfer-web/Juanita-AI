# Unified cumulative integration audit — 2026-11-18

## Purpose

This branch is the single cumulative development lane for the 0.11.12 code line. It is intentionally based on the most advanced battery/thermal/voice candidate (PR #49 head), not on the older default `main` source or on the narrower PR #57 branch.

## Verified baseline

- Baseline commit: `416be802e551e9b691ab8bbbdf57d90ac9e45106` (PR #49 head).
- App metadata: `versionName=0.11.12`, `versionCode=51`, `applicationId=ai.juanita.app`.
- Debug builds remain isolated with `.debug` suffix.
- This baseline already contains the earlier archive/journal, durable encrypted memory queue, memory association engine, reflection cues, C3 policy foundation, alarm system, vision/search, Avatar call foundation, voice/TTS routing, regional manual microphone ownership, VAD configuration, background Live idle safety and thermal/battery diagnostics.
- Code-level checks confirmed the regional TTS AudioRecord owner/receiver lifecycle and the server VAD configuration already exist in this baseline; those old branch patches are not copied over again.

## Ported into this lane

- PR #50 identity-group memory boundaries:
  - durable facts are shared within feminine or masculine presentation groups;
  - private archived episodes remain tied to the exact character ID;
  - automatic-memory queues and extraction are scoped by group;
  - session context and live recall receive the selected identity's group scope;
  - manually saved memories and voice previews bind to the selected identity;
  - identity language instructions and orderly service-stop handoff are retained.
- Standard Czech and a restrained Moravian Czech profile are restored in the language selector, global dialect guidance and digital-identity instructions; unit tests cover both without Slovak mixing or dialect caricature.
- PR #57 incremental memory work:
  - existing baseline already contains the continuity metadata-only write optimization, provisional automatic-memory recall, association engine and reflection cues;
  - added the provisional-memory association regression test and retained the richer scoped retrieval path without replacing the more complete encrypted archive/journal implementation.
- PR #56 offline wake-word foundation:
  - added the isolated Sherpa-ONNX detector, fail-closed runtime policy, tests, JitPack and the Android dependency;
  - model weights are not bundled; there is no always-on listener or microphone service connection yet.

## Explicitly not claimed complete

- Wake phrase recognition is not yet wired to `JuanitaVoiceService`; model provisioning/checksum verification, user opt-in UI, serialized microphone handoff and physical-device false-positive/battery testing remain required.
- Battery and thermal policies are code-level safeguards; no new overnight physical-device battery result is claimed.
- CI and physical validation must run on this exact unified head before any APK is offered for installation.

## Safety and release boundaries

- Draft PR only. Do not merge without explicit user approval.
- No changes to production signing identity, release key, package ID, encrypted data format or stable release.
- No destructive memory migration or data deletion.
- Six existing tabs remain unchanged; no new tab.
- No paid APIs, subscriptions or hosted audio processing are introduced.
- The photoreal avatar feasibility architecture is retained as documentation only; its free-only audit concludes no current option meets photorealism, exact live-audio synchronization and zero recurring cost together. Avatar animation remains deferred; the existing static portrait and voice path are preserved.


## 2026-11-18 — Reconciliation after full PR and runtime audit

**CURRENT SOURCE OF TRUTH — supersedes any older status statement below where they conflict.**

### Git topology and cumulative lane
- PR #58 is the sole cumulative development lane and remains draft/open/unmerged. Its head is based on PR #49 head `416be802e551e9b691ab8bbbdf57d90ac9e45106`; comparison confirms exactly 35 commits ahead of that baseline. GitHub reports 710 commits relative to `main` because that is the complete historical ancestry from the older default branch, not 710 new commits added by this integration.
- PR #58 contains 80 changed files. Do not restart from PR #57, #50, #56 or another sibling branch; their selected changes are already ported or their remaining work must be ported as reviewed slices onto this lane.
- Stable production release, signing identity, package ID, local user data and main remain untouched. No merge or release without explicit user approval.

### Voice and TTS — explicit cost decision
- **Conversation voice remains Firebase SDK-managed Gemini Live.** The separate Gemini Flash TTS generation experiment remains paused because its quota/cost behavior is unacceptable for the free-only project. Do not re-enable it, audition it, or make automatic TTS calls. No Android system TTS or third-party speech provider is a fallback.
- The repository still contains the experimental `GeminiTtsSpeechOutput` implementation and regional TTS lab metadata/UI. The regional cloud test button is disabled and the normal `JuanitaVoiceService` / Home conversation path does not reference the separate generator. Treat the class as dormant legacy experiment, not an enabled capability; remove or isolate it only after reference/dependency review.
- Continue fixing native Live microphone continuity, first audible output, repeated user/assistant turns, barge-in, routing, reconnect and deterministic shutdown without creating a second active audio path.

### Offline wake phrase — requested feature, not complete
- The Settings screen currently stores a custom phrase and explicitly tells the user automatic recognition is not implemented. `SherpaOfflineWakeWordDetector` and fail-closed policy/tests exist, but the detector is not connected to `JuanitaVoiceService`; no verified model provisioning/checksum, explicit enable/disable control, foreground notification, serialized mic handoff, or device battery/false-positive acceptance exists.
- The requested phrase (default `Hola Juanita`, identity-aware for José) is a priority implementation item, not a completed feature. It must be offline after model installation, disabled by default until the user opts in, visibly listening through Android's required foreground notification, and release AudioRecord before Gemini Live acquires the microphone. On Live stop, re-arm only when consent remains enabled. Never claim DSP-level low power; measure on both Samsung devices.

### Memory, contextual reasoning and learning
- Identity-group durable knowledge, exact-character private episodes, encrypted transcript archive, automatic-memory queue, provisional automatic-memory recall, topic routing and conservative one-hop associations are present in this candidate.
- Retrieval is currently lexical with a small curated multilingual alias map; the association graph is not a general semantic model. The character prompt includes contextual/social/psychological reasoning and uncertainty rules, but the quality of reasoning depends on retrieving the right memories and supplying the right context. Remaining work: broaden tested paraphrase/context retrieval, preserve provisional-vs-confirmed provenance, detect contradictions and corrections, rank relevant episodes, measure retrieval latency, and validate large archives without re-decrypting everything.
- Never present an inference, mood estimate or association as a confirmed fact. Never silently confirm provisional memories or merge private relationship episodes between identities.

### Internet research — distinguish image search from web research
- Anonymous Wikimedia Commons image search with Openverse fallback is implemented for image retrieval and Gemini visual analysis. This is **not** general text-web research.
- General web search/grounded research is not enabled. The roadmap records Google Search grounding's free quota and subsequent metering; it must not be enabled in a way that can incur charges or require Firebase Blaze. Research must use a verified no-billing source strategy (for example, open encyclopedic/catalogue APIs with source URLs, dates, attribution and rate limits) and clearly state that this does not cover the entire Web. Do not scrape search engines or imply comprehensive web coverage.
- Juanita must keep personal memories separate from externally sourced facts, cite sources in the answer, track freshness and ask before saving durable conclusions from research.

### Presence, battery and current CI
- C3 presence policies and reflection cues exist, but the complete user-visible controls, lifecycle integration and safe proactive behavior are not accepted as complete. Any always-listening wake phrase is a separate, explicit opt-in path; it must not silently activate C3 proactive speech.
- Background Live idle timeout, WakeLock safeguards, thermal stop policy and sparse privacy-safe power diagnostics exist in code. No physical battery improvement is claimed until measured on Note10+ Android 12 and S22 Ultra Android 15.
- CI on the prior PR #58 merge candidate built the debug APK, passed JVM tests and instrumentation compilation/package identity, but API 31 instrumentation had 1 failure among 70 tests: `DigitalIdentityStoreTest.systemInstructionTurnsCharacterTraitsIntoBehaviorAndRejectsStereotypes`. The percentage formatter truncated binary floating-point values (e.g. 0.9f could become 89); it is corrected on this lane using `roundToInt()` in commit `4ef742ee0026ddc1c8a4d942205188259ec488b3`. Exact-head CI is queued; do not call it fixed until the rerun passes.

### Ordered completion gates
1. Pass exact-head CI and finish native Gemini Live audio/microphone reliability; keep separate cloud TTS paused.
2. Complete the offline wake phrase end-to-end (model provisioning/checksum, opt-in UI, foreground service, serialized audio ownership, cancellation, tests and measured device power).
3. Complete memory/context reasoning: robust recall across accumulated conversations, provisional/confirmed provenance, contradiction/correction handling, performance and backup/restore.
4. Add free-only general web research with transparent source citations, freshness, quota/rate-limit controls and no billing path.
5. Finish C3 visible controls and C4/C5 acceptance; then Personal Organizer/Agenda; Avatar animation remains last.

The six existing tabs remain the product structure. Do not add a seventh tab or split these capabilities into another application.
