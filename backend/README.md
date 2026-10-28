# Kissa backend (Cloudflare Worker)

The Android app never holds a provider key. It talks only to this Worker, which keeps the owner's Gemini key as a
Worker **secret**, gives every install an anonymous signed device token, rate-limits per device, and proxies:

| Endpoint | Auth | What it does |
|---|---|---|
| `POST /v1/device/register` | none (rate-limited per IP) | Body `{"installId":"<16-80 url-safe chars>"}` → `{token, deviceId, expiresAt}`. Token = base64url(payload).HMAC-SHA256(`TOKEN_SECRET`), valid 30 days; the app re-registers silently. Same install id → same device id. |
| `POST /v1/chat` | `Authorization: Bearer <token>` | Body `{"request": <Gemini generateContent body>}`. The body is whitelisted (text parts only, no tools, `maxOutputTokens` ≤ 2048, server-side safety settings) and sent to `CHAT_MODELS` in order, falling back on not-found / quota / 5xx / timeout. Retries once without `responseSchema` if a model rejects it. → `{text, model}` |
| `POST /v1/tts` | Bearer | Body `{text, voice, emotion, lang, delivery?, vocal?}` → `audio/wav`. Gemini native TTS via the Interactions API (`gemini-3.8-flash-lite-tts`, then `gemini-3.8-flash-tts`), with `speech_metadata.style` built from the delivery/emotion (e.g. "soft and embarrassed, a nervous laugh") and an optional inline vocal tag (`<laugh>`, `<sigh>`, …). Falls back to the older generateContent TTS models (raw PCM wrapped into WAV). `voice` must be one of the 30 prebuilt voices (else `Kore`). Max 600 chars. |
| `POST /v1/stt` | Bearer | Body `{audioBase64, mimeType:"audio/wav", lang:"hinglish"\|"hindi"\|"english"}` → `{text}`. `gemini-3.5-transcribe` first, then a chat model with an explicit transcription instruction (Hinglish in Roman script). ≤ ~60 s audio. |
| `GET /v1/health` | none (cached 10 min) | Really calls chat, TTS, and an STT round-trip on the TTS audio. → `{ok, configured:{gemini,tokenSecret,rateKv}, services:{chat,tts,stt:{ok,model,latencyMs,error}}}`. `?fresh` bypasses the cache only with `Authorization: Bearer <TOKEN_SECRET>`. HTTP 503 when the key isn't set. |

Error responses are always `{"error":{"code","message","retryAfter?"}}` with these codes:
`BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `PAYLOAD_TOO_LARGE`, `RATE_LIMITED` (this device is over its limit),
`NOT_CONFIGURED` (owner hasn't set secrets), `UPSTREAM_QUOTA` (owner's Gemini quota/billing exhausted),
`UPSTREAM_AUTH` (owner key rejected/expired), `UPSTREAM_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, `BLOCKED` (safety), `INTERNAL`.
Upstream bodies and keys are never echoed to clients.

Default per-device limits (override with the `RATE_LIMITS` JSON var): chat 20/min & 800/day, tts 40/min & 2000/day,
stt 15/min & 600/day, register 5/min & 30/day per IP. With a `RATE_KV` binding counters are shared across isolates;
without it they are per-isolate (still stops runaway loops).

## Deploy (owner, ~5 minutes)

Prerequisites: a Cloudflare account (free plan works) and a Gemini API key from https://aistudio.google.com/apikey
(the owner's key — paid tier recommended for TTS volume; the free tier has low TTS limits).

```bash
cd backend
npm install                      # installs wrangler locally
npx wrangler login               # opens a browser to authorise your Cloudflare account
# recommended: shared rate-limit counters
npx wrangler kv namespace create RATE_KV
#   -> paste the printed id into wrangler.toml under [[kv_namespaces]] (uncomment the block)
npx wrangler secret put GEMINI_API_KEY      # paste the owner Gemini key
openssl rand -base64 48 | tr -d '\n' | npx wrangler secret put TOKEN_SECRET
npx wrangler deploy              # prints https://kissa-backend.<your-subdomain>.workers.dev
curl https://kissa-backend.<your-subdomain>.workers.dev/v1/health
```

Then build the app against it:

```bash
cd ..   # repo root
./gradlew assembleRelease -PkissaBackendUrl=https://kissa-backend.<your-subdomain>.workers.dev
```

(or put `kissaBackendUrl=https://…` in `~/.gradle/gradle.properties`). Until this is set the app shows
"AI service abhi setup nahi hai" and uses the basic phone voice — it never pretends to be configured.

Rotating secrets: `wrangler secret put TOKEN_SECRET` invalidates all device tokens; apps re-register automatically.
Changing models: edit the `*_MODELS` vars in `wrangler.toml` and redeploy — no app update needed.

## Develop / test

```bash
npm test          # node --test, no network: Gemini is faked
npx wrangler dev  # local server; put secrets in .dev.vars (gitignored): GEMINI_API_KEY=… TOKEN_SECRET=…
```

Layout: `src/index.js` (router, health), `src/auth.js` (device tokens), `src/gemini.js` (chat/TTS/STT + fallback),
`src/ratelimit.js`, `src/errors.js`, `src/wav.js`, `test/backend.test.js`.

## Not done yet (honest list)
- Not deployed: needs the owner's Cloudflare account + Gemini key.
- No app attestation (Play Integrity) yet — device tokens are anonymous, so limits are per install, not per person.
- Health/models were checked against ai.google.dev docs on 2026-10-28; real provider responses were not exercised here.
