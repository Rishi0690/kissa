# AGENTS.md — Kissa

Guide for AI coding agents (and humans) working on this repo. Read this before changing code.

## What Kissa is
Kissa is an Android app for interactive 2D AI storytelling. The user enters an animated scene as their own character, types or speaks a line, hears it spoken, and watches an AI character listen, react (expression + gesture), and answer aloud. Stories branch, relationships develop, and progress persists.

It is **not** a chat app. The animated scene is the main surface; dialogue UI stays compact.

Core loop: `input (text / voice) -> player speaks -> AI turn (Gemini JSON) -> validate -> drive scene (emotion, gesture, events) -> character speaks (neural TTS + lip-sync + subtitles) -> save state`.

## Tech stack
- Kotlin 1.9.22, Jetpack Compose (Material 3), Navigation Compose
- AGP 8.2.2, Gradle 8.5 (wrapper), JDK 17, compileSdk/targetSdk 34, minSdk 26
- Room 2.6.1 (KSP) for stories, messages, memory notes, summaries; schema exported to `app/schemas/`
- OkHttp 4.12 + kotlinx.serialization for the Gemini REST API (chat, TTS, transcription) and the Microsoft Edge neural-voice websocket
- EncryptedSharedPreferences (androidx.security) for the user's Gemini API key
- Android `SpeechRecognizer` (STT), `TextToSpeech` (TTS), `MediaRecorder`/WAV for the player's real voice
- No external game engine: the 2D scene is drawn in Compose `Canvas`

## Repo layout
```
app/src/main/java/com/kissa/app/
  KissaApp.kt, MainActivity.kt      app entry, navigation host
  ai/
    AiTurn.kt         AI output model + parser/validator (clamps lengths and deltas, maps enum synonyms, strips stage directions)
    ContextBuilder.kt builds the prompt: persona + story state + rolling summary + memory notes + last 12 turns
    GeminiClient.kt   request/response schema + typed errors; direct-key generate with model fallback, ping (Test connection), transcribe
    BackendClient.kt  OPTIONAL backend mode: silent device registration, health, chat, tts, stt
    AiService.kt      setup/health state (direct-key default; backend mode if a URL is built in), routes AI calls
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
    NeuralVoices.kt    Gemini prebuilt voice per character/player + mapping to the Microsoft backup voice
    SpeechDirector.kt  single audio queue (no overlapping speech), voice-engine chain + per-line cache, lip-sync, replay/volume
    engines/GeminiTts.kt  Gemini 3.8 TTS (Interactions API) + legacy generateContent TTS, direct with the user's key; quota parking
    engines/EdgeTts.kt    Microsoft Edge neural voices (websocket, SSML prosody per emotion), voice pools, player voice choices
    engines/EdgeDrm.kt    Sec-MS-GEC v2 token (port of edge-tts 7.3.1 drm.py; verified by EdgeDrm vectors in VoiceEnginesTest)
    engines/Mp3Decoder.kt MediaCodec MP3 -> WAV so Microsoft audio gets an amplitude envelope for lip-sync
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
./gradlew testDebugUnitTest -Dlive=true -DvoiceOut=/abs/dir [-DtestProxyPort=3128]   # live: voice a full Hinglish conversation with the Microsoft engine
```
Release signing reads `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). It and `keystore/` are gitignored. **Never commit keystores or passwords.**

## Runtime setup (v2.1: direct-key is the default)
- **Direct-key mode (default release build, no backend):** the user pastes their own Gemini API key (Onboarding page 3 or
  Settings → Story AI) → "Test & save" makes a real request (`GeminiClient.ping`) → the key is stored in
  EncryptedSharedPreferences (`SettingsRepository.apiKey()`) and the app calls `generativelanguage.googleapis.com` directly.
  Chat models (fallback on 404/429): gemini-3.5-flash-lite, gemini-3.8-flash, gemini-3.6-flash, gemini-flash-latest, gemini-2.5-flash.
  Setup states: NEEDS_KEY, CHECKING, READY, KEY_INVALID, PARTIAL (quota), OFFLINE. Never claim ready without a successful ping.
- **Backend mode (optional):** build with `-PkissaBackendUrl=https://...workers.dev` → all AI goes through the Worker (backend/,
  anonymous device token, server-held key, /v1/health). Not deployed. Key UI is hidden in this mode.
- **Voice engines (character lines), best first:** Kissa server (backend mode only) → Gemini TTS with the user's key
  (`gemini-3.8-flash-lite-tts`, `gemini-3.8-flash-tts`, then legacy `gemini-3.1-flash-tts-preview`, `gemini-2.5-flash-preview-tts`;
  fixed prebuilt voice per character, style from emotion/delivery, inline vocal tags, `<short pause>` after ellipses, unary WAV) →
  Microsoft Edge neural voice (free, no key; fixed distinct voice per character: Neerja Expressive / Swara / Madhur / Prabhat + pitch offsets;
  per-emotion prosody; MP3 decoded to WAV for lip-sync) → phone TTS with the visible "Basic voice" chip.
  Player typed lines: Microsoft voice chosen in Settings (saves the tiny Gemini quota). Player recordings: always the real recording.
  Settings → Voice engines shows the active engine, Gemini quota state ("back around …") and Microsoft reachability; option
  "Microsoft only (consistent)" avoids the once-a-day timbre switch when the Gemini quota runs out.
- **Gemini TTS free tier:** Google no longer publishes numbers (rate-limits page points to AI Studio). Reports: ~3 RPM and
  ~10 requests/day per TTS model on free tier; daily quotas reset at midnight Pacific. A 429 parks that model (daily → until midnight PT, else 65 s).
- Every generated line is cached per engine (cacheDir/tts, 60 MB cap) so replays sound identical and don't spend quota.
- Edge service quirks: only `audio-24khz-48kbitrate-mono-mp3` (PCM formats return nothing), one `<voice>` + one `<prosody>` (no `<break>`,
  no express-as), requires Sec-MS-GEC v2 (v1 handshake → 403). If Microsoft changes the token again, update EdgeDrm from upstream edge-tts.

## Rules for agents
1. **Keep the scene the focus.** No chat-bubble lists over the characters; dialogue panel stays compact and expandable.
2. **AI output is untrusted.** Every Gemini response goes through `AiTurn` parsing/validation before it touches the scene or DB. Add new fields to the responseSchema, the parser, and `AiParserTest` together.
3. **Separate dialogue from control data.** Spoken text never contains stage directions; emotion/gesture/events are enums.
4. **One audio queue.** All speech (player TTS, real voice, character TTS) goes through `SpeechDirector`. Never play audio around it.
5. **Context budget.** Don't send full history. Use `ContextBuilder` (summary + notes + last `RECENT_TURNS`). Update `ContextBuilderTest` when changing it.
6. **No fake controls.** Every visible button must work. If a feature isn't built (account/cloud sync), say so in the UI instead of a dead button.
7. **No secrets in the repo or APK.** The only key in the app is the one the user pastes (EncryptedSharedPreferences, sent only to Google). Never commit keystore/, keystore.properties, backend/.dev.vars, node_modules.
11. **Match the reference video** (notes/ref/analysis.md in the Hark project): painterly anime close-ups, shot/reverse-shot, glass choice cards, rank badge, red X + VHS rewind.
8. **Room migrations.** Bump the DB version and add a migration when changing entities; keep `app/schemas/` committed.
9. **Default dialogue language is Hinglish**, selectable (Hinglish / Hindi / English) globally and per story.
10. **Be honest about testing.** State what was only compiled vs. run on a device.

## Current status (v2.1.0, 2026-10-29)
- Direct-key AI setup (Onboarding + Settings key card with Test connection); backend kept as optional mode.
- Natural voice engine chain: Gemini TTS (user key) → Microsoft neural → phone; player voice picker; character natural-voice picker on Character Profile.
- Settings contrast bug fixed (LocalContentColor provided by the theme; OEM force-dark off).
- JVM tests 45/45 (incl. EdgeDrm vectors vs Python reference and a live 8-line Hinglish conversation through the Kotlin Microsoft client → out/voice-samples).
- Requirement audit: notes/audit.md (Done 57 · Partial 49 · Missing 2 · changed by user 2).
- NOT verified on a device/emulator: anything visual or audio on Android, mic/STT, MediaCodec MP3 decode, Gemini chat/TTS with a real key.

## Roadmap
- Device test of the full loop with a real key; tune Microsoft prosody by ear
- Painted cast for the other 7 stories; mesh-warp hair/body motion; bedroom setting
- Gemini Voice design (custom `voice_...` per character) for more consistent character voices
- Multiple speaking AI characters per scene; image generation for custom characters; account + cloud sync

## Git workflow
- Single remote: `origin` = github.com/Rishi0690/kissa, branch `main`. Do not push this project to any other repo.
- Commit and push after every change. Never force-push.
