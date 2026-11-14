# Juanita AI — Autonomous Engineering and Quality Process

**Purpose:** create a disciplined, repeatable development loop in which a senior engineering agent, deterministic CI, an independent reviewer and the project coordinator cooperate. Automation must increase engineering throughput without putting user data, the stable release or the device at risk.

## Roles

### 1. Chief Engineer — Juanita Chief Engineer (GitHub Copilot custom agent)
Principal Android/Kotlin engineer. Owns technical diagnosis, architecture, implementation plans, code changes, regression tests and technical reports. The agent profile is `.github/agents/juanita-chief-engineer.agent.md`.

### 2. Independent quality reviewer
A separate review pass (human or a different AI model/agent) checks the patch against requirements, data integrity, privacy, concurrency, lifecycle, test coverage and scope. The authoring agent must not be the sole approver of its own work.

### 3. Deterministic CI — GitHub Actions
The existing Android workflow remains the source of truth for build and test status. It must compile the debug APK, run JVM tests, compile instrumentation tests, run emulator instrumentation on API 31 and API 35, verify the debug package identity, and run Detekt once its baseline is properly bootstrapped. A skipped check is not a passing check.

### 4. Project coordinator
ChatGPT coordinates the user-approved roadmap, reviews the agent's reports, cross-checks CI and GitHub state, prioritizes the next task, and explains decisions in plain Italian. The user retains release and merge authority.

## Continuous development loop

1. Select exactly one bounded task from the highest-priority incomplete roadmap phase.
2. Read the current roadmap, architecture decisions, issue/PR history and relevant source/tests.
3. Establish a reproducible baseline and acceptance criteria.
4. Chief Engineer proposes a plan and identifies risks before implementation.
5. Implement on the development PR branch only; add regression tests.
6. Run focused checks locally/in the agent environment.
7. Push the patch to a pull request and wait for the exact-head GitHub Actions result.
8. If CI fails, classify the failure:
   - product/code regression;
   - test defect;
   - runner/emulator/infrastructure failure;
   - missing configuration/secret or unavailable external dependency.
9. Fix only evidence-backed product/test defects. Retry infrastructure failures separately and preserve the original failure logs.
10. Run an independent review of the final diff and test results.
11. Repeat until all applicable gates pass or a bounded stop condition is reached.
12. Update the roadmap with verified status and residual risks. Do not start a dependent phase while a critical gate remains blocked.

## Bounded autonomy and safety

The agent may autonomously inspect, plan, edit code/tests/docs on the approved development branch, run tests and create reviewable commits/PRs when the repository's agent configuration permits it.

The agent must NOT:
- merge or enable auto-merge;
- publish a release or install an APK on the user's phone;
- modify the stable release branch/tag;
- change application ID, signing key/certificate or upgrade path;
- delete, overwrite or migrate user data without tested reversible migration and explicit approval;
- add paid services, API keys, hosted infrastructure or billing;
- relax tests, suppress new Detekt findings or claim a skipped check passed;
- continue after repeated failures without a root-cause report.

Require user approval for destructive migrations, sensitive permission changes, signing/release operations, external paid dependencies and phase acceptance.

## Required gates by change type

| Change area | Required automated evidence | Additional manual evidence |
|---|---|---|
| Audio/service lifecycle | Unit + instrumentation tests for stop/start, cancellation, recovery, timeout and process death | Samsung microphone indicator, audio route, repeated switching, background/Recents |
| Memory/archive | Encryption, atomicity, retry/idempotency, corruption, migration and process-death tests | Existing data preservation and retrieval quality on device |
| Performance/thermal | Benchmarks, bounded queue/heap, no main/audio-thread blocking | Long-running real-device thermal and battery tests |
| UI/persona editor | UI/state tests, accessibility and persistence tests | Touch/readability and complete user flow on Samsung |
| Organizer/Agenda | Time-zone, DST, reboot, notification and persistence tests | Real notification delivery and Android permission flows |
| Avatar (last phase) | Privacy, resource, rendering and lifecycle tests | User acceptance and device performance |

## Current blockers that automation must not hide

- API 35 instrumentation has had an intermittent emulator/runner failure; collect full logs and determine whether tests actually started before classifying it.
- Detekt is skipped until `config/detekt/baseline.xml` is committed through the documented bootstrap. Do not call static analysis green until it runs.
- CI cannot verify physical Samsung microphone teardown, thermal/battery behavior, or the permanent-signature upgrade path.
- Memory architecture is currently a design specification; source implementation and data-integrity regression tests are still required.

## Initial activation checklist

1. Enable GitHub Copilot cloud agent / coding-agent access for this repository if the account/organization plan and policy support it.
2. Select the repository custom agent **Juanita Chief Engineer** for implementation tasks.
3. Optionally enable Copilot Agent Merge's **Fix failed Actions workflows** for PR #40, but leave merge authority disabled. This feature requires a compatible paid Copilot plan and repository policy permission.
4. For GitHub Agentic Workflows, install the official `gh-aw` extension, choose an AI engine and configure only the required credential. Compile Markdown workflow sources into generated `.lock.yml` files, inspect both, and submit them for review before enabling.
5. Keep all write outputs limited to reviewable commits, issues, comments or pull requests. No merge/release outputs.
6. Verify the first run manually before enabling broader triggers.

## Current implementation status

- [x] Repository-wide AI guardrails drafted.
- [x] Senior Android Chief Engineer custom-agent profile drafted.
- [x] Autonomous process and quality gates documented.
- [ ] GitHub Copilot cloud agent availability and repository policy verified by owner.
- [ ] Agentic workflow engine/credentials configured by owner (no credentials are created or assumed).
- [ ] First agent-assisted task completed and independently reviewed.
- [ ] API 35 CI failure classified and stabilized.
- [ ] Detekt baseline committed and Detekt running on normal PRs.
- [ ] Physical Samsung validation completed.

**Important:** these repository files define the engineering role and guardrails. They do not silently activate a paid AI agent or create credentials. GitHub's Agentic Workflows require compilation with the official `gh aw` CLI extension and an authenticated AI engine. Generated workflow lock files must be reviewed before use.
