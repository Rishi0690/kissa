// Kissa backend — Cloudflare Worker. Holds provider secrets; the Android app only ever talks to this.
//
// Secrets (wrangler secret put):  GEMINI_API_KEY (required), TOKEN_SECRET (required, >= 32 random chars)
// Optional vars:                   CHAT_MODELS, TTS_MODELS, LEGACY_TTS_MODELS, STT_MODELS (comma lists), RATE_LIMITS (JSON)
// Optional binding:                RATE_KV (KV namespace) for shared rate-limit counters + health cache
import { ApiError, errorResponse } from './errors.js';
import { mintToken, verifyToken, bearer } from './auth.js';
import { enforce, parseLimits } from './ratelimit.js';
import * as gemini from './gemini.js';
import { bytesToBase64 } from './wav.js';

export const VERSION = '2.0.0';

const CORS = { 'access-control-allow-origin': '*', 'access-control-allow-headers': 'authorization, content-type', 'access-control-allow-methods': 'GET, POST, OPTIONS' };

function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json; charset=utf-8', ...CORS, ...extra } });
}

async function readJson(request, maxBytes) {
  const len = parseInt(request.headers.get('content-length') || '0', 10);
  if (len && len > maxBytes) throw new ApiError('PAYLOAD_TOO_LARGE', 'Request body too large');
  const text = await request.text();
  if (text.length > maxBytes) throw new ApiError('PAYLOAD_TOO_LARGE', 'Request body too large');
  try { return JSON.parse(text || '{}'); } catch { throw new ApiError('BAD_REQUEST', 'Body must be JSON'); }
}

function cfg(env) {
  return {
    key: env.GEMINI_API_KEY,
    chatModels: gemini.splitList(env.CHAT_MODELS, gemini.DEFAULT_CHAT_MODELS),
    ttsModels: gemini.splitList(env.TTS_MODELS, gemini.DEFAULT_TTS_MODELS),
    legacyTtsModels: gemini.splitList(env.LEGACY_TTS_MODELS, gemini.DEFAULT_LEGACY_TTS_MODELS),
    sttModels: gemini.splitList(env.STT_MODELS, gemini.DEFAULT_STT_MODELS),
    limits: parseLimits(env.RATE_LIMITS),
  };
}

async function auth(request, env) {
  const payload = await verifyToken(env.TOKEN_SECRET, bearer(request));
  return payload.d;
}

// ------------------------------------------------------------------ health (cached so it can't be used to burn quota)

let healthCache = null; // { at, body }
const HEALTH_TTL_MS = 10 * 60 * 1000;

export function _resetHealthCache() { healthCache = null; }

async function timed(fn) {
  const t0 = Date.now();
  try {
    const r = await fn();
    return { ok: true, latencyMs: Date.now() - t0, ...r };
  } catch (e) {
    return { ok: false, latencyMs: Date.now() - t0, error: e instanceof ApiError ? e.code : 'INTERNAL' };
  }
}

export async function runHealth(env, fetchImpl = fetch) {
  const c = cfg(env);
  const configured = { gemini: !!c.key, tokenSecret: !!(env.TOKEN_SECRET && env.TOKEN_SECRET.length >= 16), rateKv: !!env.RATE_KV };
  if (!c.key) {
    const s = { ok: false, error: 'NOT_CONFIGURED' };
    return { ok: false, version: VERSION, configured, services: { chat: s, tts: s, stt: s }, checkedAt: new Date().toISOString() };
  }
  let ttsWav = null;
  const [chatS, ttsS] = await Promise.all([
    timed(async () => {
      const r = await gemini.chat({ key: c.key, models: c.chatModels, fetchImpl, timeoutMs: 20000, request: {
        contents: [{ role: 'user', parts: [{ text: 'Reply with the single word OK.' }] }], generationConfig: { maxOutputTokens: 16, temperature: 0 } } });
      return { model: r.model };
    }),
    timed(async () => {
      const r = await gemini.tts({ key: c.key, models: c.ttsModels, legacyModels: c.legacyTtsModels, fetchImpl, timeoutMs: 25000,
        input: { text: 'Namaste, main theek hoon.', voice: 'Kore', emotion: 'happy', lang: 'hinglish' } });
      ttsWav = r.wav;
      return { model: r.model, bytes: r.wav.length };
    }),
  ]);
  // STT round-trip on the audio TTS just produced (proves both directions really work)
  const sttS = ttsWav
    ? await timed(async () => {
      const r = await gemini.stt({ key: c.key, audioBase64: bytesToBase64(ttsWav), mimeType: 'audio/wav', lang: 'hinglish', models: c.sttModels, chatModels: c.chatModels, fetchImpl, timeoutMs: 20000 });
      return { model: r.model, heard: (r.text || '').slice(0, 60) };
    })
    : { ok: false, error: 'SKIPPED_NO_AUDIO' };
  return {
    ok: chatS.ok && ttsS.ok && sttS.ok,
    version: VERSION,
    configured,
    services: { chat: chatS, tts: ttsS, stt: sttS },
    checkedAt: new Date().toISOString(),
  };
}

async function health(request, env, fetchImpl) {
  const url = new URL(request.url);
  const now = Date.now();
  if (!url.searchParams.has('fresh') && healthCache && now - healthCache.at < HEALTH_TTL_MS) return json({ ...healthCache.body, cached: true });
  if (env.RATE_KV && !url.searchParams.has('fresh')) {
    const kvBody = await env.RATE_KV.get('health:last', 'json');
    if (kvBody && now - Date.parse(kvBody.checkedAt) < HEALTH_TTL_MS) { healthCache = { at: Date.parse(kvBody.checkedAt), body: kvBody }; return json({ ...kvBody, cached: true }); }
  }
  // fresh checks are only for the owner (bearer = TOKEN_SECRET) or after the cache expired
  if (url.searchParams.has('fresh') && bearer(request) !== env.TOKEN_SECRET) throw new ApiError('UNAUTHORIZED', 'fresh health checks are owner-only');
  await enforce({ kv: env.RATE_KV, route: 'health', subject: request.headers.get('cf-connecting-ip') || 'anon', limits: cfg(env).limits });
  const body = await runHealth(env, fetchImpl);
  healthCache = { at: now, body };
  if (env.RATE_KV) await env.RATE_KV.put('health:last', JSON.stringify(body), { expirationTtl: 3600 });
  return json(body, body.configured.gemini ? 200 : 503);
}

// ------------------------------------------------------------------ router

export async function handle(request, env, fetchImpl = fetch) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  const c = cfg(env);

  if (path === '/v1/health' && request.method === 'GET') return health(request, env, fetchImpl);

  if (path === '/v1/device/register' && request.method === 'POST') {
    const ip = request.headers.get('cf-connecting-ip') || 'anon';
    await enforce({ kv: env.RATE_KV, route: 'register', subject: ip, limits: c.limits });
    const body = await readJson(request, 4096);
    const t = await mintToken(env.TOKEN_SECRET, body.installId);
    return json({ ...t, services: { chat: !!c.key, tts: !!c.key, stt: !!c.key }, version: VERSION });
  }

  if (path === '/v1/chat' && request.method === 'POST') {
    const device = await auth(request, env);
    await enforce({ kv: env.RATE_KV, route: 'chat', subject: device, limits: c.limits });
    const body = await readJson(request, 200_000);
    const r = await gemini.chat({ key: c.key, request: body.request, models: c.chatModels, fetchImpl });
    return json({ text: r.text, model: r.model });
  }

  if (path === '/v1/tts' && request.method === 'POST') {
    const device = await auth(request, env);
    await enforce({ kv: env.RATE_KV, route: 'tts', subject: device, limits: c.limits });
    const body = await readJson(request, 8192);
    const r = await gemini.tts({ key: c.key, input: body, models: c.ttsModels, legacyModels: c.legacyTtsModels, fetchImpl });
    return new Response(r.wav, {
      status: 200,
      headers: { 'content-type': 'audio/wav', 'x-kissa-voice': r.voice, 'x-kissa-model': r.model, 'cache-control': 'private, max-age=86400', ...CORS },
    });
  }

  if (path === '/v1/stt' && request.method === 'POST') {
    const device = await auth(request, env);
    await enforce({ kv: env.RATE_KV, route: 'stt', subject: device, limits: c.limits });
    const body = await readJson(request, 3_000_000);
    const r = await gemini.stt({ key: c.key, audioBase64: body.audioBase64, mimeType: body.mimeType, lang: body.lang, models: c.sttModels, chatModels: c.chatModels, fetchImpl });
    return json({ text: r.text, model: r.model });
  }

  if (path === '/' || path === '/v1') return json({ name: 'kissa-backend', version: VERSION, endpoints: ['/v1/health', '/v1/device/register', '/v1/chat', '/v1/tts', '/v1/stt'] });
  throw new ApiError('NOT_FOUND', 'No such endpoint');
}

export async function serve(request, env, fetchImpl = fetch) {
  try {
    return await handle(request, env, fetchImpl);
  } catch (e) {
    if (!(e instanceof ApiError)) console.error('unhandled', (e && e.stack) || e);
    return errorResponse(e, CORS);
  }
}

