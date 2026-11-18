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
