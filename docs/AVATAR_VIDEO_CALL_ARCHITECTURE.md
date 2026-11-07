# Juanita AI — Avatar and bidirectional video-call architecture

Status: CameraX preview and frame forwarding to Gemini Live are implemented in the PR #6 branch and pass Android debug CI. The user has confirmed visual understanding works in an earlier build. The PR #6 camera rotation correction is not yet verified on physical devices. The portrait remains static; true facial animation and lip sync are future work.

## Call-screen architecture

- The call screen presents Juanita's avatar as the primary, large visual and the user's camera as a smaller picture-in-picture tile, with responsive layout for different Android screen sizes.
- Camera access is explicit, revocable and separate from microphone permission.
- CameraX Preview and ImageAnalysis use cases are owned and released by the call screen only. Never use a global unbindAll() from a reusable composable.
- When Gemini Live is connected and camera permission is granted, sample frames at a bounded cadence (initial target: one frame per second), rotate each frame using imageInfo.rotationDegrees, and forward it to the active Live session.
- Allow at most one frame upload in flight. Do not queue unbounded frames, save camera images to memory, or archive camera frames.
- If Live is disconnected, local preview may remain available but frame upload must stop and the UI must explain why.
- Denying camera permission must leave voice-only conversation available. Closing the call or revoking permission releases only the call screen's own camera use cases.
- Keep the existing Gemini Live SDK-managed audio path intact until any alternative has passed independent latency, echo-cancellation, interruption, audio-focus and lifecycle testing.

## Current implementation boundary

- CameraX preview, frame analysis and forwarding are present in the PR #6 branch.
- The user previously confirmed Gemini could understand the camera view, but reported upside-down orientation. PR #6 applies CameraX rotation metadata; this correction is CI-compiled but still requires physical-device confirmation.
- The front-camera preview may be mirrored for the local user's comfort; transmitted frames must have correct upright orientation. Verify whether the model should receive mirrored or unmirrored imagery separately from local preview.
- A static portrait with subtle idle/breathing motion is not a talking avatar.
- Avatar Studio currently saves profile metadata; it does not generate an actual portrait image or rigged model.
- Preserve the user's existing Juanita portrait until a replacement image is generated and explicitly accepted.

## Avatar creation and animation roadmap

1. Evaluate truly on-device, no-billing image generation on Samsung Note10+ and S22 Ultra, including model size, compatibility, speed, thermal load, storage and output quality. Do not enable Firebase Blaze, billing, paid APIs or hosted GPUs.
2. Implement portrait generation from a text description and random mode only after the hardware feasibility gate passes. Add preview, accept, regenerate, edit and persistence; failed/rejected generation must preserve the current portrait.
3. Treat rigged 3D/GLB/VRM generation as a separate milestone. A 2D portrait is not a rigged animated face.
4. Evaluate met4citizen/TalkingHead (MIT software licence) with a compatible rigged GLB and required viseme blend shapes. Check each avatar/animation asset licence separately.
5. True lip sync requires synchronized PCM/audio output or reliable viseme timing. The current Firebase Live SDK-managed audio path does not expose playback PCM to Compose; do not claim live lip sync or replace the working audio path without a validated prototype.
6. Test WebView/WebGL performance, memory, thermals, portrait orientation and context-loss recovery on Note10+ and S22 Ultra.

## Acceptance tests

- Juanita appears large and the user's camera appears in a smaller PiP tile.
- With permission and an active Live session, Gemini receives upright frames and can answer a visual question.
- Camera frames do not accumulate while network upload is slow; no more than one upload is active.
- Denying camera permission leaves voice-only mode operational.
- Closing the call, stopping Live or revoking permission stops frame transmission and releases owned camera use cases.
- Test orientation, front/rear switching, lifecycle and long-session stability on Samsung Note10+ (Android 12) and Galaxy S22 Ultra. CI/emulator success alone is not device acceptance.

## Related architecture

- Memory retrieval, archive search, privacy and autobiographical continuity: MEMORY_RECALL_AND_AUTOBIOGRAPHY.md.
- Product priorities, telephony screening and overall delivery plan: PRODUCT_ROADMAP.md.