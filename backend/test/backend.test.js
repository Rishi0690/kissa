import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { serve, runHealth, _resetHealthCache } from '../src/app.js';
import { mintToken, verifyToken } from '../src/auth.js';
import { enforce, _resetMemory } from '../src/ratelimit.js';
import { sanitizeChatRequest, sanitizeTtsInput, buildStyle, extractInteractionAudio } from '../src/gemini.js';
import { pcmToWav, isWav, bytesToBase64 } from '../src/wav.js';

const SECRET = 'test-secret-0123456789abcdef0123456789';
const ENV = { GEMINI_API_KEY: 'owner-key', TOKEN_SECRET: SECRET };
const INSTALL = 'install_abcdefghijklmnop1234';

beforeEach(() => { _resetMemory(); _resetHealthCache(); });

/** Fake Gemini: routes by URL, records calls, never touches the network. */
function fakeGemini(handlers) {
  const calls = [];
  const f = async (url, init) => {
    const body = init?.body ? JSON.parse(init.body) : null;
    calls.push({ url, body, headers: init?.headers });
    for (const [re, fn] of handlers) if (re.test(url)) return fn(url, body);
    return new Response(JSON.stringify({ error: { message: 'not found' } }), { status: 404 });
  };
  f.calls = calls;
  return f;
}
const ok = (obj) => new Response(JSON.stringify(obj), { status: 200, headers: { 'content-type': 'application/json' } });
const err = (status, message) => new Response(JSON.stringify({ error: { message } }), { status });
const textReply = (t) => ok({ candidates: [{ content: { parts: [{ text: t }] }, finishReason: 'STOP' }] });
const wavB64 = () => bytesToBase64(pcmToWav(new Uint8Array(4800), 24000));

async function register(fetchImpl = fakeGemini([])) {
  const r = await serve(new Request('https://x/v1/device/register', { method: 'POST', body: JSON.stringify({ installId: INSTALL }), headers: { 'cf-connecting-ip': '1.2.3.4' } }), ENV, fetchImpl);
  assert.equal(r.status, 200);
  return (await r.json()).token;
}

test('device tokens round-trip, are bound to the secret and expire', async () => {
  const t = await mintToken(SECRET, INSTALL, 1000);
  const p = await verifyToken(SECRET, t.token, 1001);
  assert.equal(p.d, t.deviceId);
  await assert.rejects(verifyToken('another-secret-0123456789abcdef', t.token, 1001), /Invalid/);
  await assert.rejects(verifyToken(SECRET, t.token, 1000 + 31 * 86400), /expired/);
  await assert.rejects(verifyToken(SECRET, t.token.slice(0, -2) + 'xx', 1001));
  await assert.rejects(mintToken(SECRET, 'short'), /installId/);
  // same install -> same device id (stable rate-limit subject), different install -> different id
  assert.equal((await mintToken(SECRET, INSTALL)).deviceId, t.deviceId);
  assert.notEqual((await mintToken(SECRET, INSTALL + 'x')).deviceId, t.deviceId);
});

test('register works without any user action and reports NOT_CONFIGURED when secret missing', async () => {
  const token = await register();
  assert.match(token, /^[\w-]+\.[\w-]+$/);
  const r = await serve(new Request('https://x/v1/device/register', { method: 'POST', body: JSON.stringify({ installId: INSTALL }) }), { GEMINI_API_KEY: 'k' }, fakeGemini([]));
  assert.equal(r.status, 503);
  assert.equal((await r.json()).error.code, 'NOT_CONFIGURED');
});

test('chat requires a token and proxies with model fallback; key never leaves the server', async () => {
  const noAuth = await serve(new Request('https://x/v1/chat', { method: 'POST', body: '{}' }), ENV, fakeGemini([]));
  assert.equal(noAuth.status, 401);
  assert.equal((await noAuth.json()).error.code, 'UNAUTHORIZED');

  const token = await register();
  const g = fakeGemini([
    [/gemini-3\.5-flash-lite:generateContent/, () => err(404, 'models/gemini-3.5-flash-lite is not found')],
    [/gemini-3\.8-flash:generateContent/, () => textReply('{"dialogue":"Arre!"}')],
  ]);
  const r = await serve(new Request('https://x/v1/chat', {
    method: 'POST', headers: { authorization: `Bearer ${token}` },
    body: JSON.stringify({ request: { systemInstruction: { parts: [{ text: 'sys' }] }, contents: [{ role: 'user', parts: [{ text: 'hi' }] }], generationConfig: { maxOutputTokens: 99999, temperature: 9 } } }),
  }), ENV, g);
  assert.equal(r.status, 200);
  const j = await r.json();
  assert.equal(j.text, '{"dialogue":"Arre!"}');
  assert.equal(j.model, 'gemini-3.8-flash');
  assert.equal(g.calls.length, 2);
  assert.equal(g.calls[1].headers['x-goog-api-key'], 'owner-key');
  assert.equal(g.calls[1].body.generationConfig.maxOutputTokens, 2048); // clamped
  assert.equal(g.calls[1].body.generationConfig.temperature, 1.5);
  assert.ok(!JSON.stringify(j).includes('owner-key'));
});

test('chat maps provider quota to UPSTREAM_QUOTA after trying every model', async () => {
  const token = await register();
  const g = fakeGemini([[/generateContent/, () => err(429, 'Resource has been exhausted (e.g. check quota).')]]);
  const r = await serve(new Request('https://x/v1/chat', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ request: { contents: [{ role: 'user', parts: [{ text: 'hi' }] }] } }) }), ENV, g);
  assert.equal(r.status, 503);
  assert.equal((await r.json()).error.code, 'UPSTREAM_QUOTA');
  assert.equal(g.calls.length, 4);
});

test('chat stops immediately on a rejected owner key (no pointless fallback)', async () => {
  const token = await register();
  const g = fakeGemini([[/generateContent/, () => err(403, 'API key not valid')]]);
  const r = await serve(new Request('https://x/v1/chat', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ request: { contents: [{ role: 'user', parts: [{ text: 'hi' }] }] } }) }), ENV, g);
  assert.equal((await r.json()).error.code, 'UPSTREAM_AUTH');
  assert.equal(g.calls.length, 1);
});

test('chat retries without responseSchema when a model rejects it', async () => {
  const token = await register();
  let n = 0;
  const g = fakeGemini([[/gemini-3\.5-flash-lite:generateContent/, (u, b) => (++n === 1 ? err(400, 'Invalid JSON payload: responseSchema not supported') : (b.generationConfig.responseSchema ? err(500, 'x') : textReply('ok')))]]);
  const r = await serve(new Request('https://x/v1/chat', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ request: { contents: [{ role: 'user', parts: [{ text: 'hi' }] }], generationConfig: { responseMimeType: 'application/json', responseSchema: { type: 'OBJECT' } } } }) }), ENV, g);
  assert.equal(r.status, 200);
  assert.equal((await r.json()).text, 'ok');
});

test('tts returns WAV with a fixed voice + emotion style via the Interactions API', async () => {
  const token = await register();
  const g = fakeGemini([[/\/interactions$/, () => ok({ steps: [{ type: 'model_output', content: [{ type: 'audio', data: wavB64(), mime_type: 'audio/wav' }] }] })]]);
  const r = await serve(new Request('https://x/v1/tts', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ text: 'Acha? Sach mein?', voice: 'Leda', emotion: 'shy', vocal: 'giggle', lang: 'hinglish' }) }), ENV, g);
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-type'), 'audio/wav');
  assert.equal(r.headers.get('x-kissa-voice'), 'Leda');
  assert.ok(isWav(new Uint8Array(await r.arrayBuffer())));
  const sent = g.calls[0].body;
  assert.equal(sent.model, 'gemini-3.8-flash-lite-tts');
  assert.equal(sent.generation_config.speech_config[0].voice, 'Leda');
  assert.equal(sent.input[0].content[0].text, '<giggle> Acha? Sach mein?');
  assert.match(sent.input[0].content[0].annotations[0].style, /shy/);
});

test('tts falls back to legacy PCM models and wraps the PCM in a WAV header', async () => {
  const token = await register();
  const pcm = bytesToBase64(new Uint8Array(960));
  const g = fakeGemini([
    [/\/interactions$/, () => err(404, 'model not found')],
    [/gemini-3\.1-flash-tts-preview:generateContent/, () => ok({ candidates: [{ content: { parts: [{ inlineData: { mimeType: 'audio/L16;codec=pcm;rate=24000', data: pcm } }] } }] })],
  ]);
  const r = await serve(new Request('https://x/v1/tts', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ text: 'Hello', voice: 'NotAVoice' }) }), ENV, g);
  assert.equal(r.status, 200);
  const bytes = new Uint8Array(await r.arrayBuffer());
  assert.ok(isWav(bytes));
  assert.equal(bytes.length, 44 + 960);
  assert.equal(r.headers.get('x-kissa-voice'), 'Kore'); // unknown voice -> safe default
});

test('stt transcribes via the transcribe model and falls back to a chat model', async () => {
  const token = await register();
  const g = fakeGemini([
    [/\/interactions$/, () => err(503, 'overloaded')],
    [/gemini-3\.5-flash-lite:generateContent/, (u, b) => { assert.equal(b.contents[0].parts[1].inlineData.mimeType, 'audio/wav'); return textReply(' sorry yaar \n'); }],
  ]);
  const r = await serve(new Request('https://x/v1/stt', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ audioBase64: wavB64(), mimeType: 'audio/wav', lang: 'hinglish' }) }), ENV, g);
  assert.equal(r.status, 200);
  assert.equal((await r.json()).text, 'sorry yaar');
});

test('per-device rate limiting returns RATE_LIMITED with retry-after', async () => {
  const limits = { chat: { perMinute: 2, perDay: 100 } };
  await enforce({ route: 'chat', subject: 'd1', limits, nowMs: 0 });
  await enforce({ route: 'chat', subject: 'd1', limits, nowMs: 1 });
  await assert.rejects(enforce({ route: 'chat', subject: 'd1', limits, nowMs: 2 }), (e) => e.code === 'RATE_LIMITED' && e.retryAfter > 0);
  await enforce({ route: 'chat', subject: 'd2', limits, nowMs: 3 }); // other device unaffected
  await enforce({ route: 'chat', subject: 'd1', limits, nowMs: 61_000 }); // next window
});

test('rate limiting works with a KV binding too', async () => {
  const store = new Map();
  const kv = { get: async (k) => store.get(k) ?? null, put: async (k, v) => { store.set(k, v); } };
  const limits = { tts: { perMinute: 1, perDay: 5 } };
  await enforce({ kv, route: 'tts', subject: 'd', limits, nowMs: 0 });
  await assert.rejects(enforce({ kv, route: 'tts', subject: 'd', limits, nowMs: 5 }), /slow down/);
});

test('health checks chat, tts and an stt round-trip on the generated audio', async () => {
  const g = fakeGemini([
    [/:generateContent/, () => textReply('OK')],
    [/\/interactions$/, (u, b) => (b.model.includes('tts')
      ? ok({ steps: [{ type: 'model_output', content: [{ type: 'audio', data: wavB64() }] }] })
      : ok({ steps: [{ type: 'model_output', content: [{ type: 'text', text: 'Namaste main theek hoon' }] }] }))],
  ]);
  const h = await runHealth(ENV, g);
  assert.equal(h.ok, true);
  assert.equal(h.services.chat.ok, true);
  assert.equal(h.services.tts.model, 'gemini-3.8-flash-lite-tts');
  assert.equal(h.services.stt.heard, 'Namaste main theek hoon');
  const sttCall = g.calls.find((c) => c.body?.model === 'gemini-3.5-transcribe');
  assert.equal(sttCall.body.input[0].mime_type, 'audio/wav');
});

test('health is honest when the owner key is missing or broken', async () => {
  const h = await runHealth({ TOKEN_SECRET: SECRET }, fakeGemini([]));
  assert.equal(h.ok, false);
  assert.equal(h.services.chat.error, 'NOT_CONFIGURED');
  const g = fakeGemini([[/./, () => err(403, 'API key expired')]]);
  const h2 = await runHealth(ENV, g);
  assert.equal(h2.ok, false);
  assert.equal(h2.services.chat.error, 'UPSTREAM_AUTH');
  assert.equal(h2.services.tts.error, 'UPSTREAM_AUTH');
  assert.equal(h2.services.stt.error, 'SKIPPED_NO_AUDIO');
  const r = await serve(new Request('https://x/v1/health'), { TOKEN_SECRET: SECRET }, fakeGemini([]));
  assert.equal(r.status, 503);
});

test('input sanitising: chat whitelist, tts limits, style building', () => {
  const s = sanitizeChatRequest({ contents: [{ role: 'system', parts: [{ text: 'x' }, { inlineData: {} }] }], tools: [{}], generationConfig: { responseSchema: { a: 1 } } });
  assert.equal(s.contents[0].role, 'user');
  assert.equal(s.contents[0].parts.length, 1);
  assert.equal(s.tools, undefined);
  assert.equal(s.generationConfig.responseSchema, undefined); // schema only with JSON mime type
  assert.throws(() => sanitizeChatRequest({ contents: [] }), /contents/);
  assert.throws(() => sanitizeTtsInput({ text: 'a'.repeat(700) }), /too long/);
  assert.equal(sanitizeTtsInput({ text: 'hi <laugh> there' }).text, 'hi laugh there');
  assert.match(buildStyle({ emotion: 'angry', lang: 'english' }), /annoyed/);
  assert.match(buildStyle({ emotion: 'happy', delivery: 'says it shyly, with a nervous laugh', lang: 'hinglish' }), /^says it shyly, with a nervous laugh, relaxed Hinglish$/);
  assert.equal(extractInteractionAudio({ steps: [] }), null);
});

test('unknown routes and bad bodies give clear error codes', async () => {
  const r = await serve(new Request('https://x/nope'), ENV, fakeGemini([]));
  assert.equal(r.status, 404);
  const bad = await serve(new Request('https://x/v1/device/register', { method: 'POST', body: 'not json' }), ENV, fakeGemini([]));
  assert.equal((await bad.json()).error.code, 'BAD_REQUEST');
});
