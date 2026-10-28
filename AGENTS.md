# AGENTS.md — Kissa

Guide for AI coding agents (and humans) working on this repo. Read this before changing code.

## What Kissa is
Kissa is an Android app for interactive 2D AI storytelling. The user enters an animated scene as their own character, types or speaks a line, hears it spoken, and watches an AI character listen, react (expression + gesture), and answer aloud. Stories branch, relationships develop, and progress persists.

It is **not** a chat app. The animated scene is the main surface; dialogue UI stays compact.

Core loop: `input (text / voice) -> player speaks -> AI turn (Gemini JSON) -> validate -> drive scene (emotion, gesture, events) -> character speaks (TTS + lip-sync + subtitles) -> save state`.

## Tech stack
- Kotlin 1.9.22, Jetpack Compose (Material 3), Navigation Compose
- AGP 8.2.2, Gradle 8.5 (wrapper), JDK 17, compileSdk/targetSdk 34, minSdk 26
- Room 2.6.1 (KSP) for stories, messages, memory notes, summaries; schema exported to `app/schemas/`
- OkHttp 4.12 + kotlinx.serialization for the Gemini REST API
- EncryptedSharedPreferences (androidx.security) for the API key and settings
- Android `SpeechRecognizer` (STT), `TextToSpeech` (TTS), `MediaRecorder`/WAV for the player's real voice
- No external game engine: the 2D scene is drawn in Compose `Canvas`

## Repo layout
```
app/src/main/java/com/kissa/app/
  KissaApp.kt, MainActivity.kt      app entry, navigation host
  ai/
    AiTurn.kt         AI output model + parser/validator (clamps lengths and deltas, maps enum synonyms, strips stage directions)
    ContextBuilder.kt builds the prompt: persona + story state + rolling summary + memory notes + last 12 turns
    GeminiClient.kt   request/response schema + typed errors; direct-key path only in debug builds
    BackendClient.kt  talks to the Kissa backend: silent device registration, health, chat, tts, stt
    AiService.kt      startup setup + health check, routes all AI calls through the backend
    Moderation.kt     local content filter for input/output
  content/SeedContent.kt  built-in stories and characters (8 stories)
  data/               Room database, StoryRepository, SettingsRepository
  model/Models.kt     Emotion, Gesture, SceneEvent, Lang, Genre, SettingId, character appearance enums, Role
  scene/
    SceneEngine.kt      animation state machine: per-character emotion/gesture/speaking state, camera, entrances
    ShotDirector.kt     cinematic shot logic: close-up on speaker, OTS reaction, medium/two-shot/establishing, walk-away, hard cuts
    CinematicRenderer.kt full-bleed 9:16 painted shots: push-in, DOF blur, breathing/sway, blink + amplitude lip-sync patches, VHS rewind
    CastLibrary.kt      painted cast sets (assets/cast/): expressions + mouth-open/eyes-closed patches at fixed offsets
    SceneRenderer.kt    legacy background layers/particles (used by stories without painted cast)
    CharacterPainter.kt procedural vector characters (fallback for the 7 stories without painted cast yet)
  ui/
    scene/SceneScreen.kt, SceneViewModel.kt   the interactive scene + the turn pipeline
    screens/           Onboarding, Home, Discover, StoryDetails, CharacterProfile, Creator, Library/History, Settings
    components/, theme/  shared glass UI components, 5 themes x 7 accents
  voice/
    NeuralVoices.kt    backend neural TTS (per-character voice, emotion/delivery style), per-line cache
    SpeechDirector.kt  single audio queue (no overlapping speech), TTS voices per character, word-timing lip-sync, replay/volume
    VoiceInput.kt      mic flow: permission on demand, record, transcript review/retry/send, continuous mode
    WavUtil.kt         WAV writing + amplitude envelope for real-voice lip-sync
app/src/main/assets/cast/         painted anime cast (Riya, Kabir) WebP
backend/                          Cloudflare Worker (src/index.js entry -> src/app.js), see backend/README.md
app/src/main/res/drawable-nodpi/  painted backgrounds (+ _blur variants) (corridor, cafe, library, forest, park, space station, classroom...)
app/src/test/                     JVM unit tests
testshim/                         minimal android.graphics shim so scene rendering can be tested on the JVM (renders PNGs)
```

## Build and run
```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # release APK (signed only if keystore.properties exists, else debug-signed)
./gradlew testDebugUnitTest      # unit tests
./gradlew testDebugUnitTest -Dshots=true   # also render scene preview PNGs via testshim
```
Release signing reads `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). It and `keystore/` are gitignored. **Never commit keystores or passwords.**

## Runtime setup
- Users never enter keys. App calls the backend at `BuildConfig` URL set by `-PkissaBackendUrl=https://...workers.dev`; registers an anonymous device token silently and runs /v1/health before reporting setup complete.
- Backend secrets (Worker secrets only): `GEMINI_API_KEY` (owner), `TOKEN_SECRET`. Optional KV `RATE_KV` for rate limits. Deploy steps: backend/README.md. Tests: `cd backend && npm test`.
- Endpoints: POST /v1/device/register, /v1/chat, /v1/tts (Gemini native TTS, emotion style), /v1/stt; GET /v1/health.
- If backend is down/unconfigured: honest message + retry; phone TTS used with a visible "Basic voice" chip. Never claim premium voice when it isn't active.
- Player's recorded voice always plays as the real recording.

## Rules for agents
1. **Keep the scene the focus.** No chat-bubble lists over the characters; dialogue panel stays compact and expandable.
2. **AI output is untrusted.** Every Gemini response goes through `AiTurn` parsing/validation before it touches the scene or DB. Add new fields to the responseSchema, the parser, and `AiParserTest` together.
3. **Separate dialogue from control data.** Spoken text never contains stage directions; emotion/gesture/events are enums.
4. **One audio queue.** All speech (player TTS, real voice, character TTS) goes through `SpeechDirector`. Never play audio around it.
5. **Context budget.** Don't send full history. Use `ContextBuilder` (summary + notes + last `RECENT_TURNS`). Update `ContextBuilderTest` when changing it.
6. **No fake controls.** Every visible button must work. If a feature isn't built (account/cloud sync), say so in the UI instead of a dead button.
7. **No secrets in the client or repo.** Provider keys live only as Worker secrets. No key UI in release builds. Never commit backend/.dev.vars.
11. **Match the reference video** (notes/ref/analysis.md in the Hark project): painterly anime close-ups, shot/reverse-shot, glass choice cards, rank badge, red X + VHS rewind.
8. **Room migrations.** Bump the DB version and add a migration when changing entities; keep `app/schemas/` committed.
9. **Default dialogue language is Hinglish**, selectable (Hinglish / Hindi / English) globally and per story.
10. **Be honest about testing.** State what was only compiled vs. run on a device.

## Current status (v2.0.0)
- Painted cast + cinematic shot system for the corridor story; rank badge, choice cards, red X + rewind (undo turn); backend + no-key setup.
- Compiles; 37/37 JVM tests; backend 15/15 tests; `wrangler dev` serves /v1/health + /v1/device/register locally.
- Backend NOT deployed yet (needs owner Cloudflare account + Gemini key). APK built with placeholder URL shows "AI service abhi setup nahi hai".
- Not verified on device/emulator: overlays, voice timing, real Gemini chat/TTS/STT, full flow.
- Limits: other 7 stories still use vector characters; hair sway is whole-image transform (no mesh); one AI character per scene; Gemini free TTS quota is small.

## Roadmap
- Deploy backend, rebuild APK with real URL, end-to-end test
- Backup TTS provider in backend (free Gemini TTS quota is low)
- Painted cast for the other 7 stories; mesh-warp hair/body motion
- Multiple speaking AI characters per scene; account + cloud sync

## Git workflow
- Single remote: `origin` = github.com/Rishi0690/kissa, branch `main`. Do not push this project to any other repo.
- Commit and push after every change. Never force-push.
