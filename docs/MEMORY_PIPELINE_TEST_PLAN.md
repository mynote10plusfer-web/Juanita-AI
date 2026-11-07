# Memory pipeline verification — PR #6

## Automated checks
- [x] Android debug APK workflow passed for the memory implementation head 1cccce87d928feec4712173b41f9aa0739f087b8 (run 42360447654; artifact 13569287248).
- [ ] Re-run Android debug CI after roadmap, architecture and signed-release workflow corrections.
- [ ] After merge to main, build the signed release and verify the permanent certificate fingerprint and application update metadata.
- [ ] Install only a correctly signed release over the existing installation; never uninstall or clear app data to work around signing.

## Physical-device checks — Galaxy Note10+ (Android 12) and Galaxy S22 Ultra
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

## Phone-screening research checks (future phase)
- [ ] Confirm ROLE_CALL_SCREENING availability and user opt-in flow on Note10+ Android 12 and S22 Ultra OS version.
- [ ] Verify the five-second CallScreeningService response requirement and test fail-open behavior.
- [ ] Test contact bypass, hidden/restricted caller behavior and carrier STIR/SHAKEN verdict availability.
- [ ] Keep the initial spam decision independent of Gemini Live/network calls; do not block based on an uncertain model score.
- [ ] Prototype post-answer audio access separately before attempting any caller conversation or transcript.
- [ ] Verify Spanish/EU disclosure, consent, transcript retention and deletion requirements before release.

## Memory-management and clock acceptance tests
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