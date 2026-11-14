> **Decisione architetturale successiva (2026-11-10):** il piano B1-B4 sottostante descrive la prima ipotesi di memoria isolata per identità ed è superato. La decisione approvata dall'utente è una sola memoria cifrata comune a Juanita e José, con personalità/configurazioni separate e una sola identità attiva per sessione. La specifica aggiornata e vincolante è `DIGITAL_IDENTITY_ENGINE_SPEC.md`.

# Digital Identity Engine — Phase B implementation plan

**Branch:** `feature/digital-identity-engine-phase-b`  
**Parent:** Phase A PR #35 head `0188ccdfe43c62464c3758607c07328aa4ea660f`  
**Baseline:** 0.11.12 candidate; no merge or release authorized.

## Audit findings before implementation

The Phase A identity archive is isolated and encrypted, but the current conversation runtime is not identity-aware yet:

- `MainActivity` builds one `PersonaProfile` from global assistant preferences and passes a prebuilt instruction to the voice service.
- `JuanitaVoiceService` currently constructs one default `EncryptedMemoryStore` and one `AutomaticMemoryEngine` for all conversations.
- `EncryptedMemoryStore` currently uses one shared preferences namespace and one shared conversation-archive directory.
- The user's `PersonalProfileStore` is a separate owner profile and must remain separate/shared only as explicitly permitted context.
- The existing six bottom tabs are Home, Avatar, Sveglia, Settaggi, Memoria and Altro. Do not add a seventh tab.
- The Live audio path is SDK-managed in `GeminiLiveAssistant`; Phase B must not replace it or add another microphone capture.

A simple identity selector without memory isolation would be unsafe: it could make Juanita and José share autobiographical memories. Therefore the selection must not be connected to Live until namespaced storage and session routing are implemented together.

## Implementation sequence

### B1 — Identity-scoped encrypted storage
- Add an optional immutable identity UUID namespace to `EncryptedMemoryStore`.
- Preserve the exact legacy storage names for the default/null namespace so existing 0.11.12 user data remains readable without migration.
- Use identity-specific preferences, encrypted-record key alias and conversation archive directory for each UUID.
- Reject malformed namespaces; never silently fall back from an invalid identity namespace to the global user store.
- Keep the current retention and record-size limits independently applied per identity.

### B2 — Active identity selection
- Persist the selected identity UUID in `AssistantPreferences`; null means the existing legacy assistant configuration.
- Add an explicit “Usa questa identità” action to each saved identity card within Settaggi.
- Show which identity is active. Editing must preserve its UUID; deleting it must clear the active selection.
- Refresh the identity list after create/edit/delete without blocking the UI thread.

### B3 — Live session binding
- Pass the selected UUID as a separate service intent extra, not embedded in free-form prompt text.
- At service startup, validate that the UUID still resolves in `DigitalIdentityStore`; fail closed if it does not.
- Bind that session's encrypted memory store and automatic memory engine to the selected UUID before loading context.
- Keep `PersonalProfileStore` separate and include only the existing bounded owner context; never read another persona's namespace.
- Compose system instructions from the selected identity's approved fields: name/presentation, preferred language, origin, user-authored biography, personality and only an approved cultural dossier.
- Legacy mode (no active digital identity) must preserve the existing 0.11.12 behavior exactly.
- Do not change microphone, playback, VAD, interruption, reconnect, foreground service or camera code.

### B4 — Memory controls and deletion
- Provide a way to inspect, edit and delete the selected identity's memories and archive in the existing Memoria/Identità screens.
- Deleting an identity requires confirmation and clears only that identity's namespace; it must never clear the owner profile or other identities.
- Keep identity memory retention bounded and independently configurable; make automatic memory opt-in/visible rather than silently enabling a hidden archive.

## Required tests

- Legacy/default store reads existing data using unchanged keys and paths.
- Two UUID namespaces have different preferences, ciphertext records, Keystore aliases and archive directories.
- Same identity namespace survives store recreation and process restart.
- Invalid UUID never opens the default store.
- Juanita memory writes and searches cannot read José records; reverse test too.
- Identity selection survives restart; missing/deleted UUID is cleared or rejected safely.
- Deleting one identity clears only its namespace.
- The owner profile remains separate and unchanged.
- Session startup failure, preview/alarm startup and legacy calls preserve their current behavior.
- Full API 31 instrumentation suite and debug APK build must pass before proceeding to Phase C.

## Explicit non-goals in Phase B

No online cultural research, no generated biography, no automatic changes to stable personality, no computational affect engine, no avatar generation/lip-sync, no new language expansion and no audio transport refactor. Those remain later roadmap phases and must not be mixed into this storage/session boundary change.
