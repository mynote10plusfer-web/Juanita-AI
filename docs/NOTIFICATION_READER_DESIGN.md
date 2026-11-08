# Notification reading — implementation plan (0.10.5)

## User experience

1. The user explicitly asks Juanita to check notifications.
2. Juanita first reports a local summary by app (for example: WhatsApp: 4, Gmail: 2). This inventory contains app labels and counts only; it does not expose message bodies.
3. The user chooses an app or a specific notification. Juanita reads only the selected items.
4. A further question such as “read the next one” remains scoped to the app/list the user selected in that interaction. Never silently expand to all apps.
5. If there are no exposed notifications, say so plainly. Never imply access to WhatsApp's private database or historical chats.

## Consent and data boundaries

- Android notification access remains a separate, revocable system-level permission.
- Add a distinct in-app consent setting explaining that selected notification text is sent to the configured Gemini Live session only when the user explicitly asks Juanita to read it.
- Keep this setting OFF by default. Enabling it is not blanket permission to read all notifications: each read is still user-initiated and scoped.
- If the setting is OFF, Juanita can show local counts in the app UI but must not return notification bodies to Gemini. Explain how to enable the feature.
- Do not upload notifications in the background, proactively summarize them, or include them in ordinary conversation context.
- Do not add notification text to transcript archive, curated memories, logs, analytics, crash reports or diagnostics.
- Keep notification contents in volatile in-memory storage only, with a short bounded queue; clear after explicit read, user clear, process death, or revocation of access.
- Exclude OTP/security-code-like content where identifiable; never promise perfect detection. Respect Android redaction and hidden previews.
- Never dismiss, mark read, reply to, open or otherwise mutate source notifications in this phase.

## Gemini Live tools

Implement two narrowly scoped function tools:
- `listNotificationApps`: return only app display names and counts, no titles or bodies.
- `readRecentNotifications`: require an explicit app identifier supplied from the current local inventory and a small bounded limit; return only title/body for that app. Reject missing/unknown app IDs, disabled consent, and excessive limits.

The local provider must be the only source of notification data. The function handler must fail closed (empty/denied result) on any error. Tool descriptions and system instructions must tell Gemini to ask the user which app to read before calling the content tool, and to read only the requested app/items.

## Runtime diagnostics

Expose permission enabled, listener connected/disconnected, last notification time and current in-memory count. Distinguish Android permission from service connection and from Gemini content-sharing consent. Never report “connected” merely because the settings permission string exists.

## Voice

Use the existing Gemini Live audio response path and configured voice only. No Android TextToSpeech fallback. If the Live function-call response is not spoken reliably by the SDK/model, report the limitation and keep the visual reader usable rather than introducing a different voice.

## Acceptance tests

- No notification body reaches Gemini when cloud-read consent is OFF.
- App inventory returns labels/counts only.
- Selecting WhatsApp returns only WhatsApp entries; selecting another app cannot leak WhatsApp entries.
- Missing/invalid app ID, no consent, listener disconnected and empty inbox fail closed.
- User can revoke cloud-read consent immediately; subsequent tool calls return no content.
- Notifications are absent from transcript archive, personal memories, logs and analytics.
- Limits, ordering, deduplication, process death and clearing are tested.
- Android hidden-preview/OTP redaction behavior is documented as platform-dependent.
- CI build and unit/instrumentation tests pass; actual spoken readout must still be verified on Note10+ Android 12 and S22 Ultra.

## Platform constraints

The Android `NotificationListenerService` is a privileged user-granted notification-listener binding, not a normal runtime permission. Keep its manifest service non-exported and protected by `BIND_NOTIFICATION_LISTENER_SERVICE`. Do not change it to `exported=true`. Android's API requires waiting for `onListenerConnected()` before accessing active notifications.

This phase does not use Accessibility Services, notification database scraping, WhatsApp private APIs, root access, or any workaround that bypasses Android permission controls.
