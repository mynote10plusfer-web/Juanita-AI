# Memory pipeline verification — PR #6

## Automated checks
- [x] Merged main head a6836e3676b937cc0ce4499cca7f715a6cf54b90 builds successfully in Android debug CI (run 42393573784).
- [x] Signed Release compilation succeeded in run 42393574023; certificate verification correctly blocked publication because the workflow's pinned digest was incorrect.
- [x] Extracted SHA-256 from the prior successful 0.9.5 APK (run 42325310890): 9092CEFDC0DE218921A723810575B029550CEFFA6D82593BE22F5F1F6EBA7BE1; it matches the signer reported by the new Release build.
- [ ] Merge the corrective signing-pin PR, rerun Signed Release, and verify certificate plus application/version metadata before downloading an APK.
- [ ] Install only a correctly signed release over the existing installation; never uninstall or clear app data to work around signing.

## Physical-device checks — Galaxy Note10+ (Android 12) and Galaxy S22 Ultra
- [ ] Confirm the persistent bottom menu remains visible while section content scrolls.
- [ ] Confirm Home contains the Live voice session, Avatar contains Avatar Studio and camera preview, Impostazioni contains identity/language/voice/personality controls, Memoria contains personal-memory and conversation-archive controls, and Altro contains project status/debug tools.
- [ ] Open Memoria from the bottom menu and verify the personal-memory list, edit/delete, multi-select, clear-all, conversation deletion, date-range deletion and archive clear controls are visible and functional.
- [ ] Switch repeatedly between tabs and confirm no settings, selected avatar, archive state or active Live session is lost.
- [ ] Confirm archive setting defaults to enabled on a fresh install and can be paused independently of curated automatic memory.
- [ ] Start a Live voice session; speak three separate durable, non-sensitive facts and confirm user transcript events are captured.
- [ ] Confirm automatic memory status reports transcript receipt and then extraction/save outcome; inspect the curated memory list.
- [ ] End the session, force-stop and relaunch the app. Start a new session and ask a natural question about the earlier conversation.
- [ ] Verify Gemini Live invokes searchPastConversations when relevant and answers from the returned historical evidence.
- [ ] Ask an unrelated question and verify irrelevant memories are not injected.
- [ ] Repeat recall with Italian, Spanish and Czech queries; note lexical search may miss paraphrases without shared keywords.
- [ ] Confirm both USER and ASSISTANT transcript turns appear in the encrypted archive, while only USER statements are evidence for automatic personal-memory extraction.
- [ ] Disable transcript archiving and verify curated automatic memory can still operate independently.
- [ ] Pause automatic memory and verify no new curated memories are created while the voice assistant remains usable.
- [ ] Simulate network/quota failure during extraction; verify the encrypted pending batch survives and Live transcript intake remains responsive.
- [ ] Restore network and verify a later retry processes the retained batch without losing newer queued turns.
- [ ] Fill the bounded pending queue and verify queue-full is surfaced without overwriting pending turns.
- [ ] Clear or delete a memory during in-flight extraction; verify stale results are not re-added.
- [ ] Delete one archived conversation and verify its turns no longer appear in search or future context.
- [ ] Check retention settings (manual, 7, 30 and 90 days), expiry and deletion.
- [ ] Verify app restart, process death and device reboot do not corrupt the encrypted archive or lose pending extraction batches.
- [ ] Confirm no raw audio is stored and no full archive is uploaded to cloud services.

## Improvement Lab acceptance checks — Phase 1
- [x] Android debug CI passed for the Improvement Lab implementation (run 42394260616; artifact 13582442394).
- [ ] Altro shows the capability catalogue and clearly distinguishes integrated-but-unverified, planned, under-research and unimplemented functions.
- [ ] Create each report type and verify required fields, timestamp and IANA time-zone ID are saved.
- [ ] Force-stop/relaunch and verify reports remain available after restart.
- [ ] Preview a report and export it as UTF-8 .txt to a user-selected location.
- [ ] Select multiple reports and verify the exported text contains each selected report exactly once.
- [ ] Delete selected reports and verify personal memories and conversation archive remain unchanged.
- [ ] Confirm reports are stored encrypted locally; no transcript, memory, audio or credentials are included automatically.
- [ ] Verify the UI states that automatic gap detection is not yet implemented.

## Phone-screening research checks (future phase)
- [ ] Confirm ROLE_CALL_SCREENING availability and user opt-in flow on Note10+ Android 12 and S22 Ultra OS version.
- [ ] Verify the five-second CallScreeningService response requirement and test fail-open behavior.
- [ ] Test contact bypass, hidden/restricted caller behavior and carrier STIR/SHAKEN verdict availability.
- [ ] Keep the initial spam decision independent of Gemini Live/network calls; do not block based on an uncertain model score.
- [ ] Prototype post-answer audio access separately before attempting any caller conversation or transcript.
- [ ] Verify Spanish/EU disclosure, consent, transcript retention and deletion requirements before release.

## Memory-management and clock acceptance tests
- [ ] Tap the home-screen “GESTISCI RICORDI E CONVERSAZIONI” shortcut and verify it scrolls directly to the Memory & Privacy panel.
- [ ] Enter an inclusive local date range and verify only archived transcript turns in that range are deleted, including partial conversations, while curated personal memories remain.
- [ ] Refresh the archive list after range deletion and verify removed turns are no longer returned by historical search or future context.
- [ ] Confirm every curated memory displays category plus local date and time.
- [ ] Select and delete one memory; confirm it disappears from the list and historical recall.
- [ ] Select several memories across categories and delete the group; verify only selected IDs are removed.
- [ ] Confirm the group-delete dialog shows the exact selected count and requires explicit confirmation.
- [ ] Clear all curated memories and verify the conversation archive remains intact.
- [ ] Delete a whole archived conversation and verify curated memories remain intact.
- [ ] Verify deletion invalidates pending automatic-memory batches and stale extraction cannot restore deleted facts.
- [ ] Confirm the main screen clock updates each second and displays the device time-zone ID.
- [ ] Compare displayed time and `getCurrentDateTime` tool output with Android system clock across a minute boundary and after time-zone/DST changes.
- [ ] Ask Juanita for current date/time and for relative-time questions (today, tomorrow, yesterday, this evening); verify it uses fresh device time and local zone.
- [ ] Ask about a dated historical conversation; verify the archive timestamp is clearly treated as past, not as current time.

## Known limitations
- Archive search is local lexical phrase/token matching with recency ranking, not semantic vector retrieval; multilingual paraphrases may be missed.
- The Live function tool has compiled but its real invocation/response cycle must be verified on the target devices and selected model.
- A successful CI build does not prove automatic extraction, recall, deletion, retention or camera behavior works end to end.
- Biography timeline UI, robust contradiction consolidation, local semantic embeddings and source-linked web research remain future milestones.

## Calendar archive and selective deletion
- [ ] Calendar days with conversations show the correct local-date session count.
- [ ] Sessions spanning midnight appear on every local day they cover.
- [ ] Opening a session shows timestamped transcript turns.
- [ ] Deleting a time interval affects only turns in that session and interval.
- [ ] Deleting one day or multiple non-contiguous selected days affects only those days.
- [ ] Deleting a conversation or transcript never deletes curated personal memories.
- [ ] Cancelling confirmation leaves all data unchanged; retention options remain available.
- [ ] Home screen prioritizes the avatar, displays device date/time and has distinguishable navigation items.
