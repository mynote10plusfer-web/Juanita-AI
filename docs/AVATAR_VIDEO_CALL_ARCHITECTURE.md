# Juanita AI — Photorealistic Avatar for Video Call

Status: architecture/design spike only. No application source, stable release, signing configuration, or user data is changed by this document.

## Approved product decisions

- Avatar appears only inside the existing in-app video-call screen.
- The user can create it from either a written description or an uploaded photo.
- Visual target: photorealistic human.
- Avatar creation/processing is intended to use an online provider, subject to explicit privacy consent and a cost review.
- The current Gemini Live voice, language model, microphone capture, interruptions, and background audio behavior remain authoritative and must not be replaced by the avatar provider.
- If avatar generation, streaming, or rendering fails, the existing voice conversation must continue and the call screen must fall back to the saved static portrait.
- No paid plan, API, hosted GPU, billing, or payment method may be enabled without fresh explicit user approval.
- Preserve the current production package ID, permanent signing certificate, local data, and update path. Do not merge or release without approval.

## Source audit at 0.11.12 candidate

Repository: `mynote10plusfer-web/Juanita-AI`
Baseline commit: `e82f61857c3d50f60cf642136831a119811bd365`
Baseline branch: `feature/onboarding-alarm-voice-notifications-preview-0.11.2`
Production identity: `ai.juanita.app`, versionName `0.11.12`, versionCode `51`.

### Existing call path

- Home starts/stops the Gemini Live foreground service.
- The video-call screen can only be opened while Gemini Live is active.
- `AvatarCallScreen` currently displays a static portrait with slight scale/tilt idle motion; its source explicitly says this is not facial animation or true lip sync.
- CameraX shows the user's own camera in a small tile and sends a frame to Gemini Live about once per second.
- Closing/dismissing the current call screen explicitly stops the voice service. Keep this existing behavior unless the user later asks to change it.
- The main call screen currently resolves the avatar display name from the active profile, but supplies a fixed portrait path (`filesDir/juanita_portrait.jpg`) rather than the active profile's `assetPath`. Correct profile-asset selection is required as part of avatar integration.
- The existing audio pipeline is in `GeminiLiveAssistant.startAudioOnSession()` and uses Firebase AI Logic `LiveSession.startAudioConversation()`. This SDK helper owns the microphone/audio playback pipeline and exposes transcript, function-call and GoAway callbacks; the current app code does not receive assistant PCM audio chunks.

### Existing Avatar tab

- `AvatarStudioSection` already provides description, photo-reference and random profile metadata choices.
- Photo selection uses Android's document picker and persists the content URI read permission.
- Profiles are stored by `AvatarProfileStore` in app-private SharedPreferences as metadata. It does not upload or generate a portrait/model and does not store a rendered avatar.
- `AvatarProfile.assetPath` is only a future app-relative asset reference.
- Current default style is REALISTIC_3D, not PHOTOREALISTIC. The new Juanita profile default must be PHOTOREALISTIC.
- Current save status correctly says that only a profile is saved and no animated model is generated yet.
- The user's new selection supersedes the old roadmap preference for on-device generation: evaluate online generation/processing, but preserve the no-paid-service rule until separately approved.

## Architecture

Keep three independent layers. Do not couple avatar transport to the Gemini voice service.

### 1. Avatar identity and asset lifecycle

Introduce an app-level provider-neutral contract:

- `AvatarCreationRequest`: source = DESCRIPTION or PHOTO_REFERENCE; prompt; selected style = PHOTOREALISTIC; consent receipt; stable profile ID.
- `AvatarCreationProvider`: submit request, poll job, cancel job, fetch preview/result, report quota/cost estimate and provider error.
- `AvatarAssetManifest`: provider/model/version, asset type, dimensions, duration if video, checksum, creation time, source type, consent state, local relative asset path, remote asset ID only if required.
- `AvatarAssetStore`: copy accepted assets into app-private storage, validate type/size/checksum, atomically replace accepted asset, retain prior accepted portrait until new preview is explicitly accepted, and delete local/remote assets when supported and requested.
- The Android UI must never hold provider master secrets. All cloud credentials belong on a controlled backend/proxy; issue short-lived scoped session tokens only if the selected provider supports them.

Flow:
1. User chooses description or photo.
2. Explain what is uploaded and to which provider; ask explicit consent before photo/prompt transmission.
3. Generate an avatar preview online.
4. User previews, accepts, regenerates, edits, or cancels.
5. Only an accepted result becomes the active avatar asset.
6. Failure/cancellation leaves the previous active portrait untouched.

A photo is only a visual reference. This feature does not perform face recognition, identify the photographed person, infer sensitive traits, or authorize biometric processing.

### 2. Live speech-to-avatar bridge

The avatar needs the exact speech audio Juanita is playing, not only the text transcript and not the microphone input.

Required interface:
- `AvatarAudioFrame`: PCM payload, sample rate, channel count, sample format, monotonic timestamp, utterance ID, end-of-utterance marker.
- `AvatarSpeechBridge`: bounded, non-blocking, back-pressured fan-out from the assistant output audio to the avatar engine.
- `AvatarPlaybackState`: IDLE, SPEAKING, INTERRUPTED, ENDED, ERROR.
- On barge-in, clear queued avatar audio and reset the avatar mouth state immediately.
- On end-of-utterance, flush the final audio frames and transition to natural idle animation.
- Do not archive raw audio. Process it transiently and discard it after rendering.

Critical integration blocker:
Firebase AI Logic `startAudioConversation()` currently owns output playback and the app has no assistant PCM callback. Transcript text is not an adequate substitute for phoneme-accurate lip sync. Do not add a second AudioRecord/microphone reader, do not capture the loudspeaker with the phone microphone, and do not synthesize a second unrelated voice.

Before changing the stable audio path, run a separate proof of concept:
1. Verify the documented lower-level LiveSession receive API and the exact PCM chunk representation for the selected Firebase SDK version.
2. Build an isolated experimental transport that receives assistant PCM once, sends the same chunks both to Android AudioTrack and the avatar bridge, and preserves interruption/turn boundaries.
3. Compare output latency, route selection, AEC, VAD, barge-in, recovery, CPU, memory, battery and background continuity against the existing SDK helper.
4. If parity cannot be demonstrated, stop the experiment and retain the existing audio helper. Do not ship a lip-sync implementation driven only by text or a second TTS voice.

### 3. Call-only avatar renderer

- The renderer is instantiated only while `AvatarCallScreen` is visible and the Live session is active.
- It receives only assistant output audio and accepted avatar identity/assets.
- It returns rendered frames to the large main video surface; the user's camera remains the small picture-in-picture tile.
- Between utterances, use the provider's idle/blink/gaze behavior where available. Facial expression cues may be added only when the provider exposes documented controls or reliable metadata; do not invent emotion labels from transcript text.
- On call close, explicit end, lifecycle loss or provider failure, stop rendering, close network sessions, clear bounded audio/frame queues and release resources.
- If the avatar path fails, keep Gemini Live audio working and display the accepted static portrait. Avatar errors must never call `JuanitaVoiceService.stop()`.
- The existing call-screen close action continues to end the conversation, as it does today.

## Provider evaluation and current findings

### bitHuman — first technical candidate, not selected or authorized for paid use

Official docs currently describe:
- Essence 2 as a photoreal human avatar generated from one portrait.
- The Android SDK accepts 16 kHz mono speech and returns rendered frames; it renders on the phone, not in the cloud.
- The Android SDK requires a physical arm64 device and a Creator plan or higher. Essence 2 currently has a first-run model download of about 226–281 MB and a 12.1 MB AAR. The SDK is pre-1.0.
- The on-device SDK is metered at 2 credits/minute (published equivalent about US$0.02/minute at the stated top-up rate); cloud rendering is 4 credits/minute (published equivalent about US$0.04/minute). Both require Creator or higher.
- Cloud rendering requires the network for the whole session and sends audio/conversation to the provider's servers in the US.
- A shipped Android app must not embed the provider's master API secret; the docs explicitly recommend retrieving scoped credentials from a backend.

Conclusion: bitHuman appears technically promising for photorealistic lip sync, but its SDK is not a free online renderer. Online creation plus local rendering and fully cloud-rendered video are different deployment choices. No integration or account activation until price, privacy, and user approval are settled.

### Other providers

Evaluate at least one independent real-time avatar provider (for example, HeyGen LiveAvatar) against the same criteria: custom external audio input, real-time WebRTC/video output, ability to retain Gemini as the conversation brain and voice, photo-based avatar creation, API/SDK support for Android, latency, concurrency limits, data region/retention, commercial license, free trial and recurring price. Do not choose based on marketing demos alone.

## Non-functional requirements and acceptance tests

- Audio-only Home conversation is unchanged; avatar resources are not initialized outside the call screen.
- Existing voice works if avatar network is unavailable, quota is exhausted, avatar decode fails, or the provider returns an error.
- Lip-sync alignment is measured against the exact audio played; target average audiovisual offset no greater than 150 ms, with no cumulative drift over a 10-minute call.
- User interruption stops mouth motion within 250 ms of the actual playback interruption and does not replay stale queued frames.
- First frame and subsequent frame delivery remain responsive; show an honest loading/fallback state instead of freezing the call screen.
- Test 10-, 30- and 60-minute calls; app background/foreground; screen lock/unlock; network loss/recovery; provider timeout; explicit end; repeated call starts.
- Test Note10+ Android 12 and S22 Ultra Android 15, then a broader Android arm64 device matrix.
- Measure PSS/RAM, CPU/GPU, battery, thermal state, network traffic and frame drops. Thermal or performance throttling must reduce avatar quality/frame rate or fall back to the static portrait, never destabilize Gemini Live.
- CI build success is not proof of audiovisual quality or device compatibility; physical-device tests are mandatory.

## Staged implementation

0. **This commit — architecture only:** source audit, contracts, risks, provider evaluation criteria. No application code changes.
1. **Provider feasibility spike:** compare provider documentation, data terms, cost, free tier and custom audio streaming. No paid account or billing.
2. **Offline audio/render harness:** feed a known test WAV into the selected renderer and verify frames, lip sync, interruption reset and lifecycle without Gemini.
3. **Gemini audio bridge spike:** only after SDK receive API is verified; implement on a separate experimental transport and compare with current audio path.
4. **Avatar creation flow:** online description/photo generation, explicit consent, preview/accept/regenerate, local asset persistence.
5. **Call-screen integration:** activate renderer only in video-call mode, preserve camera PiP and static fallback.
6. **Device validation and release candidate:** tests on both target Samsung devices, then signed APK with unchanged package ID/certificate and strictly increasing versionCode.

## Explicitly out of scope for this slice

- Changing or replacing Gemini Live, the selected Gemini voice, microphone/VAD/AEC, background foreground service, reconnect/recovery or continuity snapshots.
- Always-on avatar, home-screen avatar animation, or avatar display in audio-only sessions.
- Facial identity recognition, biometric face matching, voice identification or emotion inference.
- Automatic upload of the user's photo or conversation without consent.
- Any paid provider, paid subscription, billing activation, hosted GPU or API-key purchase without fresh explicit approval.
- Merge to main, APK release or signing-key changes.


## CameraX regression boundary retained from the previous avatar design

The existing call UI's CameraX work is separate from the new assistant-avatar renderer and must remain unchanged:
- Camera access is explicit and revocable; it is separate from microphone permission.
- Preview and ImageAnalysis use cases are owned and released by the call screen. Never call a global CameraX `unbindAll()` from a reusable composable.
- When Live is connected and camera permission is granted, sample at a bounded cadence (currently about one frame per second), apply `imageInfo.rotationDegrees`, and forward upright frames to Gemini Live.
- Allow at most one image upload in flight. Do not queue unbounded frames, persist camera frames, or add them to memory/archive.
- If Live disconnects, stop frame upload; a local preview may remain with an honest status.
- Denying camera permission must leave voice-only conversation available. Closing the call or revoking permission releases only the call screen's own camera use cases.
- The earlier orientation correction is present in source, but physical-device orientation still requires confirmation on Note10+ Android 12 and S22 Ultra Android 15.
- The prior roadmap proposed local/open-source TalkingHead/GLB/WebGL exploration. The user's latest explicit decision is now online avatar creation/processing; that supersedes the earlier generation-location preference. It does not waive the no-paid-service approval gate.


## Updated feasibility decision — 2026-11-10 (free-only requirement)

The user has explicitly set a hard constraint: **only genuinely free solutions**. Exclude any paid provider, paid subscription, credit-metered SDK, trial-credit dependency, paid hosted GPU, or payment method. A temporary free trial is not a qualifying solution.

### Rejected for this project

- **bitHuman Essence 2:** technically attractive but requires a paid Creator plan and metered credits. Explicitly excluded.
- **HeyGen LiveAvatar:** API is credit-metered/paid; excluded.
- **Talking Avatar Realistic Avatars hosted service:** paid per minute; excluded.
- **Talking Avatar Open Source:** MIT/free, but it is a browser-rendered stylized 3D avatar, not a photorealistic photo-to-human avatar. It also requires a GLB already rigged with 15 Oculus viseme morph targets. It does not satisfy the product's visual bar or generate the avatar asset from a description/photo. Do not ship as a substitute.
- **MuseTalk:** promising open-source photo-driven lip-sync, with MIT code and upstream statement that its released weights may be used commercially. However, it reports 30fps+ on an NVIDIA Tesla V100; its README also documents limitations including 256x256 face-region resolution, identity-detail loss and frame jitter. It requires GPU inference infrastructure. No qualifying zero-cost, reliable always-on GPU host has been established. Do not represent the software license as free compute or promise free online operation.
- **LivePortrait:** not selected. Some upstream detection/model dependencies carry non-commercial research restrictions unless replaced; a permissive code license alone does not clear the model/dependency chain.

### Confirmed Firebase SDK integration constraint

The app currently uses Firebase AI Logic BOM 35.1.0 and LiveSession.startAudioConversation(LiveAudioConversationConfig). The helper owns microphone capture and assistant audio playback and exposes transcript/function/GoAway callbacks, not assistant PCM frames.

The current Android API reference documents LiveSession.receive(): Flow<LiveServerMessage>, and the Live API's raw streaming examples show assistant audio represented as InlineDataPart with audio/pcm data. However, receive() is the lower-level receive path; it cannot simply be collected in parallel with the SDK-managed startAudioConversation() because the session already has an active receiver (SessionAlreadyReceivingException). To fan out the exact assistant audio, a separate experimental transport would need to own microphone capture, LiveSession receive, AudioTrack playback, interruption handling, AEC/VAD and recovery. That is a substantial replacement of the current stable audio path, not a safe additive callback.

Therefore:
- Do not tap the loudspeaker with a second microphone.
- Do not drive lip sync from transcript text.
- Do not play a second TTS voice.
- Do not replace the stable audio path merely to obtain avatar audio.
- Any lower-level transport experiment must remain isolated from the production branch and must prove audio parity on physical devices before any integration is considered.

### Decision / release gate

**No avatar renderer or provider currently passes all three non-negotiable gates together: photorealistic quality, exact live audio synchronization, and genuinely zero recurring infrastructure cost.**

Do not ship the stylized 3D SDK or a low-resolution/jittery photo animation as a compromise. Keep the existing static portrait/video-call screen and Gemini Live audio unchanged for the stable release. Continue only if a demonstrably free, legally usable, sufficiently photorealistic solution and a safe exact-audio bridge are found. Otherwise explicitly close this feature as not feasible under the current constraints rather than degrade Juanita.
