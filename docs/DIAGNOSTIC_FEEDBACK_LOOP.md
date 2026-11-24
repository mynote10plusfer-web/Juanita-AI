# Juanita AI — privacy-safe diagnostic and improvement feedback loop

## Current implementation (verified on PR #58)

- `VoiceDiagnosticLog` records bounded, privacy-filtered technical events locally on the device. Writes are serialized on a bounded background queue; the log rotates and reports dropped diagnostic events.
- The voice diagnostic export explicitly excludes transcript text, prompts, audio, API keys and profile values.
- The Improvement Lab stores user-authored engineering reports encrypted on-device. Reports can be previewed, exported as UTF-8 text and deleted.
- There is currently **no automatic cloud upload and no direct GitHub issue creation from the APK**.

## Why the APK must not write directly to GitHub

Creating or updating a GitHub issue requires authenticated write access. A personal access token or repository write token embedded in the APK can be extracted and abused. Never put a GitHub credential, signing secret, Firebase service-account key or other privileged secret in Android code/resources.

A public repository does not make its issue-writing endpoint unauthenticated. GitHub Actions can receive a report only through an authenticated workflow trigger or a trusted server-side bridge.

## Safe staged design

### Stage 1 — local report (existing foundation)

- Keep diagnostics on-device by default.
- Collect only whitelisted technical fields: app version/build, Android API, feature/module, event code, error class, elapsed duration, queue/drop counters and explicitly selected diagnostic state.
- Do not collect conversation text, prompts, raw audio, personal memories, names, contacts, precise location, photos, API keys or authorization headers.
- Keep user-authored Improvement Lab reports encrypted locally and user-deletable.

### Stage 2 — user-controlled share (no backend or cost)

- Add a clear **Preview and share diagnostic report** action.
- Generate a redacted report, show the exact contents, then open Android's share sheet or a GitHub issue form.
- Require a separate tap to share; do not silently upload when an error occurs.
- The user can submit the report to the repository from the phone. No GitHub token is stored in the APK.

### Stage 3 — optional cloud inbox (only after explicit opt-in and free-tier verification)

If the user later approves cloud collection, use a narrowly scoped anonymous intake with:
- opt-in disabled by default and a per-report confirmation;
- strict field allowlist and client-side preview/redaction;
- no transcript, audio, profile/memory text, GPS or stable device identifier;
- bounded report size, rate limits, short retention and a deletion/disable control;
- server/database security rules that allow only create-only reports and prevent public reads;
- documented quota/terms and a fail-closed behavior when the free quota is unavailable.

Firebase Spark may be evaluated as a free-tier candidate, but no cloud database or rules are currently configured/verified for this feature. Do not enable Firebase Blaze or any paid/usage-billed backend.

### Stage 4 — GitHub triage (optional)

A trusted server-side process could turn sanitized reports into GitHub issues using a repository secret. The secret must remain on the server/GitHub Actions side, never in the APK. This requires an authenticated, rate-limited bridge and explicit setup; it is not currently implemented.

## Controlled self-improvement

Cloud reports may help us find repeated failures and prioritize fixes, but they must never:
- modify the app source or release branch automatically;
- trigger an automatic merge or release;
- change memory, persona, permissions or settings on the user's phone;
- be treated as verified facts without reproduction.

The engineering loop remains: collect a sanitized report → inspect/reproduce → add regression test → patch only the cumulative development PR → exact-head CI → independent review → user-approved release.

## Acceptance criteria before any remote report is sent

1. User sees the full sanitized payload and destination.
2. Upload is disabled by default and requires explicit consent.
3. No secrets, transcript, audio, personal memory, precise location or stable identifier appear in the payload.
4. Rate limit, size limit, retention and deletion are tested.
5. A network/quota failure does not affect Live audio, memory storage or app stability.
6. Remote reports cannot contain instructions that are executed by an AI agent; they are untrusted diagnostic data only.
7. No paid service or billing activation is required.
