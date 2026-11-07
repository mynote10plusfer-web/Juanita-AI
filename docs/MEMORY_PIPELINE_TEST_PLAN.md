# Memory pipeline verification — PR #4 and PR #5

This checklist is required before describing automatic memory as device-verified.

## Automated checks
- [ ] Android debug APK workflow completes successfully for the combined pull request.
- [ ] Release APK is built only after the change is merged to main.
- [ ] Permanent signing verification succeeds; never install a debug APK over the signed release.

## Physical-device checks (Galaxy Note10+ / Android 12 and Galaxy S22 Ultra)
- [ ] Start a Live voice session and speak three separate, durable, non-sensitive facts.
- [ ] Confirm transcript-received status changes, then confirm the saved-memory count/list changes after extraction.
- [ ] Start a new session and verify a stored fact is included in the local session context.
- [ ] Disable transcript archiving and verify automatic memory still works independently.
- [ ] Pause automatic memory and verify no new memories are created.
- [ ] Simulate network loss during extraction; verify the pending batch remains encrypted and the Live transcript worker remains responsive.
- [ ] Restore network and speak another turn; verify background retry completes without losing the earlier pending batch.
- [ ] Fill the bounded pending queue and verify the UI reports queue-full without overwriting the existing batch.
- [ ] Stop the session; verify microphone capture stops before background memory cleanup.
- [ ] Force-stop/reopen the app and verify pending batches are retried on the next session.
- [ ] Clear memories during an in-flight extraction; verify stale results are not re-added.
- [ ] Verify no raw audio is stored and the optional transcript archive remains off unless explicitly enabled.

## Known limitations after these changes
- Retrieval currently injects a bounded set of recent stored memories at session start; semantic/query-specific retrieval is not implemented.
- Contradiction resolution, source provenance, biography generation and web knowledge research remain future milestones.
- A successful CI build does not replace the device checks above.
