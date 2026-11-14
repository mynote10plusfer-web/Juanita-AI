# Juanita AI — repository-wide AI engineering rules

All AI coding agents and human contributors must follow these rules.

## Project invariants
- Stable release v0.11.12 / versionCode 51 / application ID `ai.juanita.app` is protected.
- All implementation work belongs on the approved development branch or a dedicated PR branch; never commit to the stable release branch.
- Debug builds must keep application ID `ai.juanita.app.debug`.
- Preserve the permanent signing certificate and upgrade compatibility.
- Exactly one voice persona is active at a time. Personas share user memory but retain distinct character configuration.
- No paid APIs, services, hosted databases, GPUs, subscriptions or usage-based billing without explicit user approval.
- Avatar is the final roadmap phase. Do not begin substantive avatar generation, animation or lip-sync work before phases 1–5 are accepted.
- Do not retain raw microphone audio or expose private transcripts, memory, credentials or keys in logs.

## Required reading
Before substantial work, read `docs/PRODUCT_ROADMAP.md`, `docs/MEMORY_ARCHITECTURE.md` and `docs/AUTONOMOUS_ENGINEERING_PROCESS.md`, then inspect the current implementation and tests.

## Definition of done
- Reproduce or isolate the issue; do not guess.
- Add regression tests for the failure mode.
- Make the smallest safe change.
- Run focused tests and all applicable build, unit, instrumentation and static-analysis checks.
- Review the complete diff for unrelated changes, weakened tests, security/privacy regressions and migration/data-loss risks.
- Report exact test results, skipped checks and residual risks.
- Never equate emulator success with physical-device validation.
- Never claim 100% reliability.
- Never merge, release, publish or alter stable signing without explicit user approval.

## Fail-safe rules
- Audio shutdown must be confirmed before reporting the microphone stopped or starting a replacement session.
- Never silently discard transcript events or treat unavailable encrypted memory as an empty archive.
- Never replace an Android Keystore key when encrypted data already exists.
- Keep durable source records independent of RAM caches, summaries and search indexes.
- Never weaken a quality gate merely to obtain a green CI result.
