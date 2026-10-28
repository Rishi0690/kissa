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
    GeminiClient.kt   REST client, responseSchema JSON, model fallback list, typed errors (NO_KEY, AUTH, QUOTA, TIMEOUT...)
    Moderation.kt     local content filter for input/output
  content/SeedContent.kt  built-in stories and characters (8 stories)
  data/               Room database, StoryRepository, SettingsRepository
  model/Models.kt     Emotion, Gesture, SceneEvent, Lang, Genre, SettingId, character appearance enums, Role
  scene/
    SceneEngine.kt      animation state machine: per-character emotion/gesture/speaking state, camera, entrances
    SceneRenderer.kt    draws background layers, parallax, particles, light, passers-by
    CharacterPainter.kt procedural vector characters (hair, outfit, accessories, 8 expressions, 16 gestures, blink/breath/mouth)
  ui/
    scene/SceneScreen.kt, SceneViewModel.kt   the interactive scene + the turn pipeline
    screens/           Onboarding, Home, Discover, StoryDetails, CharacterProfile, Creator, Library/History, Settings
    components/, theme/  shared glass UI components, 5 themes x 7 accents
  voice/
    SpeechDirector.kt  single audio queue (no overlapping speech), TTS voices per character, word-timing lip-sync, replay/volume
    VoiceInput.kt      mic flow: permission on demand, record, transcript review/retry/send, continuous mode
    WavUtil.kt         WAV writing + amplitude envelope for real-voice lip-sync
app/src/main/res/drawable-nodpi/  painted backgrounds (corridor, cafe, library, forest, park, space station, classroom...)
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
- The user pastes a Gemini API key in Onboarding or Settings -> AI ("Test connection" verifies it). Optional proxy URL lets the key live on a server instead.
- Default model `gemini-3.5-flash-lite`, with automatic fallback through `GeminiClient.MODELS`. Older `gemini-2.0-flash` is shut down; do not reintroduce it.
- Best voices: "Speech Services by Google" with hi-IN / en-IN voice data installed on the phone.

## Rules for agents
1. **Keep the scene the focus.** No chat-bubble lists over the characters; dialogue panel stays compact and expandable.
2. **AI output is untrusted.** Every Gemini response goes through `AiTurn` parsing/validation before it touches the scene or DB. Add new fields to the responseSchema, the parser, and `AiParserTest` together.
3. **Separate dialogue from control data.** Spoken text never contains stage directions; emotion/gesture/events are enums.
4. **One audio queue.** All speech (player TTS, real voice, character TTS) goes through `SpeechDirector`. Never play audio around it.
5. **Context budget.** Don't send full history. Use `ContextBuilder` (summary + notes + last `RECENT_TURNS`). Update `ContextBuilderTest` when changing it.
6. **No fake controls.** Every visible button must work. If a feature isn't built (account/cloud sync), say so in the UI instead of a dead button.
7. **No secrets in the client or repo.** API key stays in EncryptedSharedPreferences or behind the proxy.
8. **Room migrations.** Bump the DB version and add a migration when changing entities; keep `app/schemas/` committed.
9. **Default dialogue language is Hinglish**, selectable (Hinglish / Hindi / English) globally and per story.
10. **Be honest about testing.** State what was only compiled vs. run on a device.

## Current status (v1.0.0)
- Compiles; JVM unit tests pass (AI parsing/validation, context builder, scene render shots).
- Not yet verified on a physical device or emulator: voice, TTS timing, real Gemini calls, taps.
- Known limits: simple vector characters (not painterly sprites), one AI character per scene (others are background passers-by), no account/cloud sync, voice quality depends on phone TTS.

## Roadmap
- Match the visual reference video (character art style, camera, pacing) once provided
- Richer character art / skeletal-style animation
- Multiple speaking AI characters per scene
- Backend proxy (e.g. Cloudflare Worker) so the key never ships on the phone; account + cloud sync
- Higher-quality cloud TTS voices as an option

## Git workflow
- Single remote: `origin` = github.com/Rishi0690/kissa, branch `main`. Do not push this project to any other repo.
- Commit and push after every change. Never force-push.
