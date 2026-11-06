# Juanita AI — Avatar Studio and video-call architecture

Status: local profile model/store and Compose Avatar Studio editor are merged into main. A first full-screen call-style preview with local CameraX picture-in-picture is implemented. Generated 3D models, animated rendering, and PCM/viseme lip sync are not yet implemented.

## Product experience

The call screen is designed like a video call:

- Juanita's avatar occupies the main, large area.
- The current speaker/user appears in a small picture-in-picture window. The first preview currently anchors this tile at the upper-right; drag-to-move is a later refinement.
- The first preview supports camera on/off, front/rear switching, closing the call screen and explicitly ending the call. A microphone mute control is deferred because the current SDK-managed audio path has no verified mute control.
- Camera and microphone are activated only after an explicit user action and permission.
- The call screen must continue to use the existing Gemini Live session; opening or closing the call UI must not silently start or stop the voice service.
- The user can choose a saved avatar and open Avatar Studio without losing existing assistant settings.

## Avatar Studio creation modes

Every profile stores a creation source and a render style:

1. Description: text prompt describing appearance, clothing and visual details.
2. Photo reference: user-selected image from Android Photo Picker. Keep the selected content URI permission when possible. Never upload the photo without a separate, explicit consent step.
3. Random: create a reproducible random appearance brief from a controlled set of attributes. Randomizing metadata is not the same as generating a finished 3D model.

Render styles:

- Photorealistic portrait / talking head.
- Realistic full-body 3D.
- Stylized 3D.
- 2D/cartoon.

The profile model, private local catalogue and Compose editor live in `avatar/AvatarProfile.kt`, `avatar/AvatarProfileStore.kt` and `avatar/AvatarStudioSection.kt`. The editor supports source/style/motion choices, a photo reference via Android document picker, random appearance briefs, and local save/select/delete. These components store profile metadata only; they do not yet generate, rig, or animate a model. `avatar/AvatarCallScreen.kt` adds a full-screen preview using the locally saved Juanita portrait and a user-controlled CameraX PiP. The camera preview is local-only in this phase and is not streamed to Gemini.

## Runtime architecture

- Native Android shell: Kotlin + Jetpack Compose.
- Camera: existing CameraX stack, refactored into a compact PiP preview for the call screen. CameraX supports Android API 21+, while this app currently targets minSdk 26.
- Avatar renderer: a local WebView hosting a bundled Three.js/TalkingHead runtime, with all JS/CSS/model assets packaged in the APK. No runtime CDN dependency and no paid hosted TTS.
- Candidate renderer: [TalkingHead](https://github.com/met4citizen/talkinghead), MIT licensed. It supports GLB full-body avatars, Mixamo-compatible animation rigs, ARKit/Oculus viseme blend shapes, gestures, expressions and streaming PCM16 audio.
- Audio bridge: the current Firebase Gemini Live SDK-managed audio route is known-good but the current wrapper exposes transcript callbacks, not outgoing PCM chunks. Do not replace it until a tested audio-output bridge is available. A later adapter must deliver each Gemini PCM chunk exactly once to both the audible output and the avatar renderer, or use a supported SDK audio callback; never play duplicate audio.
- Motion controller: map conversation states and optional semantic cues to subtle idle, listening, speaking, eye-contact and gesture animations. Keep gestures rate-limited and non-repetitive.
- Camera vision: send images to Gemini only while the user explicitly enables vision. The avatar renderer must not imply that it can see the user when camera permission or preview is off.

## Adaptive performance across Android devices

Do not hard-code behavior for the Note 10+ or S22 Ultra. Detect runtime capability and measure frame time / memory pressure:

- High tier: high-quality model textures, full-body rig, 30–60 FPS target where sustainable.
- Balanced tier: reduced texture resolution, fewer dynamic effects, simpler shadows and capped animation frame rate.
- Compatibility tier: lightweight head/portrait model, reduced frame rate, or static portrait fallback.
- Allow the user to select Auto / Battery Saver / Quality, but Auto is the default.
- Release WebView, WebGL resources, camera use cases and animation loops when the call screen closes.
- Keep the native audio service independent from WebView lifecycle and do not require a GPU renderer for voice-only operation.

These are target profiles, not guaranteed frame rates. Real devices must be benchmarked.

## Free-first creation strategy and honest limits

- The local app can store and display user-created profiles without a paid service.
- A random avatar can be built from a bundled, licensed collection of pre-rigged models and attribute presets once suitable assets are selected and included.
- A description or single photograph does not automatically become a high-quality, rigged, photorealistic 3D human on an Android phone. High-quality image-to-3D generation usually requires substantial compute. Do not silently call a paid cloud API or enable Firebase billing.
- The initial free implementation should prefer bundled, openly licensed models and local customization. Any optional online generation engine must be independently reviewed for license, privacy, quota and no-billing compatibility before integration.
- Preserve the user's existing Juanita portrait as the default identity; do not replace it with a generic model. A generated 3D avatar may be an optional rendering of that identity after the user approves it.

## Delivery phases

1. **Profile foundation** — implemented in this branch: typed profile model, creation source/style/motion choices, local metadata persistence.
2. **Avatar Studio UI** — description editor, Photo Picker, randomize controls, style preview, save/select/delete profiles.
3. **Renderer proof of concept** — bundle TalkingHead and one properly licensed, pre-rigged sample model; test in Android WebView on API 26+.
4. **Call-screen preview** — implemented as a full-screen still-portrait view with CameraX PiP, camera permission/toggle, front/back switch, and separate close-screen/end-call actions. Next replace the still portrait with the adaptive renderer.
5. **Audio synchronization** — investigate and test a supported Gemini audio PCM output path; add streaming lip sync only after audio is available without changing the stable audio route prematurely.
6. **Motion and vision** — pending: listening/speaking states, subtle gaze/blinks, gesture scheduling and optional camera context. The call-screen camera preview currently remains local-only.
7. **Device matrix and release** — test low/mid/high Android hardware, thermal throttling, battery, interruptions, camera lifecycle, WebView recovery and release signing.

## Privacy and safety

- Photo access is user-initiated and revocable.
- Do not store raw camera frames or microphone audio.
- Store profile metadata and generated assets in private app storage.
- Make deletion of a profile remove its app-owned assets, but never delete the user's original photo from shared storage.
- Never infer or label a person's identity from their face. The photo is a visual reference for an avatar, not a face-recognition feature.
- Keep Firebase Spark/free-tier constraints; no billing, Blaze, Vertex AI, paid APIs, paid hosting or paid GPU services without fresh explicit approval.
