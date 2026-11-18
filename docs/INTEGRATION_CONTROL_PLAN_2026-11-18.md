# Integration control plan — 2026-11-18

Status: active integration baseline; no production merge or release authorized.
Stable `main` / v0.11.12 is protected and unchanged.

## Integration branch
- Branch: `integration/controlled-rollup-2026-11-18`
- Initial parent: PR #50 head `6098c0c2dea0d958573577dda25ab5eddb4be6a1`
- PR #50: identity-scoped memory and reliable Live identity switching.
- PR #56 remains an independent draft prototype and is not production-ready.

## Verified PR topology
- #41 → #42 → #43 → #44 → #45 form a feature chain from #40.
- #46 and #47 are parallel branches from #45; #46 adds continuity-storage performance, #47 adds reflection cues.
- #48 is based on an earlier #47 ancestor and is not an ancestor of #49.
- #49 is based on #47 and contains a separate background Live safety line.
- #50 is based on #40, not #49; compare reports divergence (#49 is 169 commits ahead of #40; #50 is 30 commits ahead).
- #56 is also based on #40 and remains separate. Its CI success does not mean a working wake-word feature.
- #51–#55 are closed, unmerged CI-only PRs, not feature dependencies.

## Integration sequence
1. Preserve `main`, v0.11.12, package/signing identity, encrypted formats and exactly six tabs.
2. Use #50 as the current identity/memory baseline for this integration branch.
3. Selectively reconcile #41–#49 by file and behavior: Live microphone ownership; battery/background/thermal safeguards; continuity storage; automatic memory recall; native Live voice; visual understanding/image search; natural reflection cues.
4. Keep #56 isolated until model provenance/checksums, generated keyword tokens, real Settings opt-in/status, serialized KWS/Live ownership and device evidence exist.
5. Run CI on every coherent integration commit. Compilation alone is not device validation.
6. Test on Note10+ Android 12, then S22 Ultra Android 15. Measure idle baseline, KWS, Live, CPU, temperature and battery delta before overnight use.

## Wake-word acceptance
- Explicit opt-in, default OFF; visible microphone foreground-service notification.
- No network/audio upload while armed; no raw-audio persistence.
- KWS releases AudioRecord before Live starts; Live fully stops before KWS rearms.
- Fail closed on permission loss, service failure, thermal/battery protection, competing capture or uncertain release.
- Never claim Samsung DSP/low-power hotword support.

## Current truth
PR #56 CI run `44274919269` passed build and automated tests for `f86c404...`; its debug artifact lacked model weights and service integration. It is not a usable wake-word APK.

## Change control
No merge to `main`, release publication, signing changes, data deletion/migration or PR auto-merge without explicit user authorization.
