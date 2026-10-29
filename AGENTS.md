# AGENTS.md — Kissa

Guide for AI coding agents (and humans) working on this repo. Read this before changing code.

## v3.0.0 (2026-10-29) — spec v2.1 Phase 1 (read this first; older sections describe v2)
- **What the app is now:** play-only, offline catalog of standalone story games (spec `notes/spec-v2.1-pdf.md`). No account, no API key, no live AI on the play path.
- **Code map:** `game/` pure engine (GameModel §6 schema, Condition, IntentMatcher, GameEngine/GameSession, Validator + reachability, SaveStore, Catalog IDs) · `rig/` RigKit (rig.json + layer images), Moves (20 keyframe clips), RigRenderer (FK over the shared skeleton, multiply-tinted layers, vector face: 20 expressions, blink, lip-sync) · `play/` Stage (bg 3 layers, camera, audience, day/dusk/night/rain filters), GameController (beats → stage/chat/voice, waits at decisions/open replies), VoicePlayer (pack audio + envelope; phone TTS emergency fallback) · `data/` GameRepository (assets + CATALOG_URL packs), SocialStore, SettingsRepository · `ui/screens` catalog home, search, detail, role setup/customiser, library, settings, onboarding · `ui/play/PlayerScreen` cinematic / POV / chat surfaces, status sheet, ending screen with checkpoint replay.
- **Content:** `app/src/main/assets/games/{aukaat,pehla-hafta,baarish-wali-raat}/` (game.json, voice_env.json, audio/*.ogg, cover.webp); `assets/rig/` (rig.json + 285 WebP parts); `assets/bg/` (10 × back/mid/front + backgrounds.json).
- **Tools:** `tools/voicegen/voicegen.py` (voice_map.json slots → Edge neural; Sarvam shim dormant; hash cache in tools/voicegen/.cache, gitignored) · `tools/riggen/riggen.py` + `bggen.py` (regenerate rig kit / backgrounds; PNG mirror in ../art/v3/png for JVM previews) · `tools/preview/compose_previews.py` (preview mock-ups + sheet).
- **Tests:** `./gradlew :app:testDebugUnitTest` (64 tests). Validator/reachability report → `app/build/reports/reachability.txt`. Renders: add `-Dshots=true -DpreviewOut=<dir>`.
- **Adding a game:** write game.json using only catalog IDs (`game/Catalog.kt`), run voicegen, run tests (LaunchGamesTest validates every bundled game, 0 errors required, every ending reachable).
- **Next phases:** `notes/phase-plan.md` (Phase 2 Cloudflare Worker + D1 + R2 backend; Phase 3 admin creator website).

## What Kissa is (v3)
A play-only Android app for interactive story GAMES (spec: `notes/spec-v2.1-pdf.md` in the Hark project). The player is the protagonist ("you"): names their character, picks a look from the rig catalog, plays cinematic / POV / chat scenes, and their choices move stats, rank, objectives and the ending they earn. Games are declarative JSON + a shared asset kit. All playback is on the phone and works offline. No live AI and no API key on the play path (V1 rule: AI creates, it does not perform). Creation happens on a separate creator website (Phase 3, not built).

## Tech stack
- Kotlin 1.9.22, Jetpack Compose (Material 3), Navigation Compose; AGP 8.2.2, Gradle 8.5, JDK 17, compileSdk/targetSdk 34, minSdk 26
- kotlinx.serialization for game JSON; JSON-file SaveStore for checkpoints; local SocialStore (likes/follows/report/block)
- 2D rig renderer in Compose `Canvas` (FK skeleton, layered WebP parts); no game engine
- Voice: pre-generated Ogg/Opus files per line in each game pack (Microsoft Edge neural Swara/Madhur via tools/voicegen); Android `TextToSpeech` only as emergency fallback (e.g. player-name lines, shows "Basic voice")
- Android `SpeechRecognizer` (hi-IN, offline preferred) fills the input box
- Room/OkHttp/security-crypto still in the build for the dormant v2 code (see Legacy)

## Build and run
```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # release APK (signed only if keystore.properties exists, else debug-signed)
./gradlew testDebugUnitTest      # unit tests
./gradlew testDebugUnitTest -Dshots=true   # also render scene preview PNGs via testshim
./gradlew testDebugUnitTest -Dlive=true -DvoiceOut=/abs/dir [-DtestProxyPort=3128]   # live: voice a full Hinglish conversation with the Microsoft engine
```
Release signing reads `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). It and `keystore/` are gitignored. **Never commit keystores or passwords.**

## Rules for agents (v3)
1. **Spec is law.** `notes/spec-v2.1-pdf.md` V1 scope only; label anything V2+ as later. No live AI calls during play.
2. **Games are data.** game.json may only reference catalog IDs (`game/Catalog.kt`, rig.json, backgrounds.json). Every game must pass `Validator` with 0 errors and every ending reachable (`LaunchGamesTest`).
3. **Every open_reply has a fallback; every choice has an effect and a tone/style.** Intent keyword lists include Roman-Hinglish spelling variants and Devanagari.
4. **Voice:** characters store a logical voice slot; `tools/voicegen/voice_map.json` maps slot -> provider voice. Regenerate only changed lines (hash cache). Phone TTS is emergency-only; any wider use is a defect.
5. **Dialogue + captions in Hindi (Devanagari); player input Roman Hinglish; choice labels may mix.**
6. **Free only for now** (user decision 2026-10-29): no paid services; within free options always pick the best quality. Sarvam shim stays dormant until the user says otherwise.
7. **No fake controls.** Every visible button works, or the UI says the feature isn't available.
8. **No secrets in repo/APK.** Never commit keystore/, keystore.properties, backend/.dev.vars, node_modules.
9. **Readable text everywhere.** Light text on dark backgrounds; check chips/labels in every screen.
10. **Be honest about testing.** State what was only compiled vs. run on a device.
11. **Keep this file current** with every change and push it in the same commit.

## Current status (v3.0.0, 2026-10-29)
- Built + signed release APK (23.4 MB, versionCode 4). JVM tests 64/64 pass. Launch games: Aukaat, Pehla Hafta, Baarish Wali Raat — 12 endings, all reachable, 0 validator errors.
- Asset kit: 2 rigs on one skeleton, 20 expressions, 20 moves, 20 props, 12 hair, 8 outfits per rig, 8 accessories, 3 faces, 5 skin tones (285 parts, 1.17 MB); 10 backgrounds x 3 layers; day/dusk/night/rain filters; 3 painted covers.
- Voice: 193 lines pre-generated (~21 min, 4.3 MB), distinct voice per character, emotion prosody; free-option comparison recorded in notes/decisions.md.
- Gaps: music loops not made; rig art is flat cartoon (not the painterly anime target); customiser chip text contrast needs a check; previews of UI overlays are mock-ups.
- NOT verified on a device/emulator: everything visual/audio on Android, mic/STT, saves/resume on device.
- Backend (Phase 2) and creator website (Phase 3) not built — see notes/phase-plan.md.

## Roadmap
- Device feedback pass; fix whatever breaks
- Art upgrade: painterly-anime layered rig parts matching notes/ref, still one shared skeleton
- Music loops (free/royalty-free or generated), customiser contrast fix
- Phase 2: Cloudflare Worker + D1 + R2 backend (accounts, catalog/feed, saves sync, publish validation, moderation, voice jobs via OpenAI-shape /v1/audio/speech + Bulbul shim) — needs a free Cloudflare account
- Phase 3: admin creator website (AI mode, no-code node editor, design tools, voice panel, playtest debugger)

## Legacy v2 code (dormant, not on the v3 play path)
`ai/`, `content/SeedContent.kt`, `data/StoryRepository.kt` + `Database.kt`, `scene/`, `voice/` (Gemini/Edge live engines), `backend/` are from the v2 live-AI storytelling app. Kept for reference/possible V4 live chat; do not wire into gameplay. Original v2 notes follow.

### v2 overview
Kissa is an Android app for interactive 2D AI storytelling. The user enters an animated scene as their own character, types or speaks a line, hears it spoken, and watches an AI character listen, react (expression + gesture), and answer aloud. Stories branch, relationships develop, and progress persists.

It is **not** a chat app. The animated scene is the main surface; dialogue UI stays compact.

Core loop: `input (text / voice) -> player speaks -> AI turn (Gemini JSON) -> validate -> drive scene (emotion, gesture, events) -> character speaks (neural TTS + lip-sync + subtitles) -> save state`.

### v2 repo layout
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

### v2 runtime setup
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

## Git workflow
- Single remote: `origin` = github.com/Rishi0690/kissa, branch `main`. Do not push this project to any other repo.
- Commit and push after every change. Never force-push.
