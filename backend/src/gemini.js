// Provider layer: Gemini chat (generateContent), Gemini native TTS (Interactions API, legacy generateContent fallback)
// and Gemini transcription. All calls use the owner key held as a Worker secret; nothing here is exposed to clients.
import { ApiError, classifyUpstream } from './errors.js';
import { pcmToWav, isWav, rateFromMime, base64ToBytes } from './wav.js';

export const API_BASE = 'https://generativelanguage.googleapis.com/v1beta';

export const DEFAULT_CHAT_MODELS = ['gemini-3.5-flash-lite', 'gemini-3.8-flash', 'gemini-flash-latest', 'gemini-2.5-flash'];
// Gemini 3.8 TTS (Interactions API, returns WAV) per ai.google.dev/gemini-api/docs/speech-generation (checked 2026-10-28)
export const DEFAULT_TTS_MODELS = ['gemini-3.8-flash-lite-tts', 'gemini-3.8-flash-tts'];
// Older generateContent TTS models (return raw audio/L16 PCM) kept as a fallback
export const DEFAULT_LEGACY_TTS_MODELS = ['gemini-3.1-flash-tts-preview', 'gemini-2.5-flash-preview-tts'];
export const DEFAULT_STT_MODELS = ['gemini-3.5-transcribe'];

export const PREBUILT_VOICES = [
  'Zephyr', 'Puck', 'Charon', 'Kore', 'Fenrir', 'Leda', 'Orus', 'Aoede', 'Callirrhoe', 'Autonoe', 'Enceladus', 'Iapetus',
  'Umbriel', 'Algieba', 'Despina', 'Erinome', 'Algenib', 'Rasalgethi', 'Laomedeia', 'Achernar', 'Alnilam', 'Schedar',
  'Gacrux', 'Pulcherrima', 'Achird', 'Zubenelgenubi', 'Vindemiatrix', 'Sadachbia', 'Sadaltager', 'Sulafat',
];

// Emotion (app enum) -> short delivery style. Short, constant strings keep the voice stable (per Google's prompting guide).
export const EMOTION_STYLE = {
  neutral: 'casual, natural conversational',
  happy: 'warm and cheerful, smiling while talking',
  shy: 'shy and a little flustered, softer, with small hesitations',
  surprised: 'surprised, slightly faster, rising intonation',
  confused: 'puzzled and unsure, thoughtful pauses',
  sad: 'quiet and sad, slower, a little heavy',
  angry: 'annoyed and sharp, scolding, clipped',
  amused: 'teasing and playful, amused',
};

export const VOCAL_TAGS = { laugh: '<laugh>', chuckle: '<chuckle>', giggle: '<giggle>', sigh: '<sigh>', gasp: '<gasp>', tsk: '<tsk>', hmm: '<short pause>', breath: '<breath>' };

export function splitList(v, fallback) {
  if (!v) return fallback;
  const l = String(v).split(',').map((s) => s.trim()).filter(Boolean);
  return l.length ? l : fallback;
}

async function call(fetchImpl, url, key, body, timeoutMs) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), timeoutMs);
  let res;
  try {
    res = await fetchImpl(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify(body),
      signal: ctl.signal,
    });
  } catch (e) {
    if (e && e.name === 'AbortError') throw new ApiError('UPSTREAM_TIMEOUT', 'The AI provider took too long');
    throw new ApiError('UPSTREAM_UNAVAILABLE', 'Could not reach the AI provider');
  } finally {
    clearTimeout(t);
  }
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-json */ }
  if (!res.ok) {
    const msg = json?.error?.message || text.slice(0, 200);
    const kind = classifyUpstream(res.status, msg);
    const err = new ApiError(kind === 'MODEL_NOT_FOUND' || kind === 'BAD_UPSTREAM_REQUEST' ? 'UPSTREAM_UNAVAILABLE' : kind, publicMessage(kind));
    err.kind = kind;
    err.upstreamMessage = msg;
    throw err;
  }
  return json;
}

function publicMessage(kind) {
  switch (kind) {
    case 'UPSTREAM_QUOTA': return 'The AI service has reached its usage limit. Please try again later.';
    case 'UPSTREAM_AUTH': return 'The AI service credentials on the server are invalid or expired.';
    case 'BLOCKED': return 'The AI refused this content.';
    case 'UPSTREAM_TIMEOUT': return 'The AI provider took too long';
    default: return 'The AI service is temporarily unavailable';
  }
}

const FALLBACK_KINDS = new Set(['MODEL_NOT_FOUND', 'UPSTREAM_QUOTA', 'UPSTREAM_UNAVAILABLE', 'UPSTREAM_TIMEOUT', 'BAD_UPSTREAM_REQUEST']);

// ------------------------------------------------------------------ chat

const SAFETY = ['HARM_CATEGORY_HARASSMENT', 'HARM_CATEGORY_HATE_SPEECH', 'HARM_CATEGORY_SEXUALLY_EXPLICIT', 'HARM_CATEGORY_DANGEROUS_CONTENT']
  .map((category) => ({ category, threshold: 'BLOCK_MEDIUM_AND_ABOVE' }));

/** Whitelists the client's generateContent body so the endpoint can't be used for arbitrary/expensive calls. */
export function sanitizeChatRequest(req) {
  if (!req || typeof req !== 'object') throw new ApiError('BAD_REQUEST', 'request must be an object');
  const contents = Array.isArray(req.contents) ? req.contents : null;
  if (!contents || contents.length === 0 || contents.length > 40) throw new ApiError('BAD_REQUEST', 'contents must have 1-40 entries');
  const clean = contents.map((c) => ({
    role: c.role === 'model' ? 'model' : 'user',
    parts: (Array.isArray(c.parts) ? c.parts : []).filter((p) => typeof p?.text === 'string').map((p) => ({ text: p.text.slice(0, 8000) })),
  })).filter((c) => c.parts.length > 0);
  if (clean.length === 0) throw new ApiError('BAD_REQUEST', 'contents had no text parts');
  const sys = req.systemInstruction || req.system_instruction;
  const sysText = sys?.parts?.map((p) => p?.text || '').join('\n').slice(0, 16000);
  const g = req.generationConfig || {};
  const gen = {
    temperature: clampNum(g.temperature, 0, 1.5, 0.9),
    maxOutputTokens: Math.round(clampNum(g.maxOutputTokens, 16, 2048, 1024)),
  };
  if (typeof g.topP === 'number') gen.topP = clampNum(g.topP, 0, 1, 0.95);
  if (g.responseMimeType === 'application/json') gen.responseMimeType = 'application/json';
  if (gen.responseMimeType && g.responseSchema && typeof g.responseSchema === 'object') gen.responseSchema = g.responseSchema;
  const out = { contents: clean, generationConfig: gen, safetySettings: SAFETY };
  if (sysText) out.systemInstruction = { parts: [{ text: sysText }] };
  const totalChars = JSON.stringify(out).length;
  if (totalChars > 120000) throw new ApiError('PAYLOAD_TOO_LARGE', 'Prompt too large');
  return out;
}

function clampNum(v, lo, hi, dflt) {
  const n = typeof v === 'number' && Number.isFinite(v) ? v : dflt;
  return Math.min(hi, Math.max(lo, n));
}

export function extractCandidateText(json) {
  const block = json?.promptFeedback?.blockReason;
  if (block) throw new ApiError('BLOCKED', 'The AI refused this content.');
  const cand = json?.candidates?.[0];
  const text = (cand?.content?.parts || []).filter((p) => !p.thought && typeof p.text === 'string').map((p) => p.text).join('');
  if (!text.trim()) {
    if (cand?.finishReason === 'SAFETY' || cand?.finishReason === 'PROHIBITED_CONTENT') throw new ApiError('BLOCKED', 'The AI refused this content.');
    const e = new ApiError('UPSTREAM_UNAVAILABLE', 'The AI returned an empty answer');
    e.kind = 'UPSTREAM_UNAVAILABLE';
    throw e;
  }
  return text;
}

export async function chat({ key, request, models = DEFAULT_CHAT_MODELS, fetchImpl = fetch, timeoutMs = 30000 }) {
  if (!key) throw new ApiError('NOT_CONFIGURED', 'AI chat is not configured on the server');
  const body = sanitizeChatRequest(request);
  let last;
  for (const model of models) {
    let b = body;
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const json = await call(fetchImpl, `${API_BASE}/models/${encodeURIComponent(model)}:generateContent`, key, b, timeoutMs);
        return { text: extractCandidateText(json), model };
      } catch (e) {
        last = e;
        // some models reject responseSchema: retry once on the same model without it
        if (attempt === 0 && e.kind === 'BAD_UPSTREAM_REQUEST' && /schema/i.test(e.upstreamMessage || '') && b.generationConfig.responseSchema) {
          b = { ...b, generationConfig: { ...b.generationConfig } };
          delete b.generationConfig.responseSchema;
          continue;
        }
        break;
      }
    }
    if (!FALLBACK_KINDS.has(last?.kind)) throw last;
  }
  throw last || new ApiError('UPSTREAM_UNAVAILABLE', 'No AI model answered');
}

// ------------------------------------------------------------------ TTS

export function buildStyle({ emotion, delivery, lang }) {
  const parts = [];
  const d = typeof delivery === 'string' ? delivery.replace(/[<>|]/g, '').trim().slice(0, 80) : '';
  if (d) parts.push(d);
  else parts.push(EMOTION_STYLE[String(emotion || 'neutral').toLowerCase()] || EMOTION_STYLE.neutral);
  if (lang === 'hinglish') parts.push('relaxed Hinglish');
  return parts.join(', ').slice(0, 140);
}

export function sanitizeTtsInput(input) {
  const text = typeof input?.text === 'string' ? input.text.replace(/[<>]/g, '').replace(/\s+/g, ' ').trim() : '';
  if (!text) throw new ApiError('BAD_REQUEST', 'text is required');
  if (text.length > 600) throw new ApiError('PAYLOAD_TOO_LARGE', 'text is too long (max 600 characters)');
  const voice = PREBUILT_VOICES.includes(input.voice) ? input.voice : 'Kore';
  const lang = ['hinglish', 'english', 'hindi'].includes(input.lang) ? input.lang : 'hinglish';
  const vocal = typeof input.vocal === 'string' && VOCAL_TAGS[input.vocal.toLowerCase()] ? input.vocal.toLowerCase() : null;
  return { text, voice, lang, vocal, style: buildStyle({ emotion: input.emotion, delivery: input.delivery, lang }) };
}

export function extractInteractionAudio(json) {
  // REST: steps[].content[] with {type:"audio", data, mime_type}; SDK convenience: output_audio
  let found = null;
  for (const step of json?.steps || []) {
    if (step.type && step.type !== 'model_output') continue;
    for (const c of step.content || []) if (c.type === 'audio' && c.data) found = c;
  }
  if (!found && json?.output_audio?.data) found = json.output_audio;
  if (!found) return null;
  const bytes = base64ToBytes(found.data);
  const mime = found.mime_type || found.mimeType || 'audio/wav';
  return isWav(bytes) ? bytes : pcmToWav(bytes, rateFromMime(mime));
}

export function extractInteractionText(json) {
  if (typeof json?.output_text === 'string') return json.output_text;
  const out = [];
  for (const step of json?.steps || []) {
    if (step.type && step.type !== 'model_output') continue;
    for (const c of step.content || []) if (c.type === 'text' && typeof c.text === 'string') out.push(c.text);
  }
  return out.join('').trim();
}

export async function tts({ key, input, models = DEFAULT_TTS_MODELS, legacyModels = DEFAULT_LEGACY_TTS_MODELS, fetchImpl = fetch, timeoutMs = 30000 }) {
  if (!key) throw new ApiError('NOT_CONFIGURED', 'Voice generation is not configured on the server');
  const s = sanitizeTtsInput(input);
  let last;
  const transcript = s.vocal ? `${VOCAL_TAGS[s.vocal]} ${s.text}` : s.text;
  for (const model of models) {
    try {
      const json = await call(fetchImpl, `${API_BASE}/interactions`, key, {
        model,
        input: [{ type: 'user_input', content: [{ type: 'text', text: transcript, annotations: [{ type: 'speech_metadata', style: s.style }] }] }],
        response_format: { type: 'audio' },
        generation_config: { speech_config: [{ voice: s.voice }] },
      }, timeoutMs);
      const wav = extractInteractionAudio(json);
      if (wav) return { wav, model, voice: s.voice, style: s.style };
      last = Object.assign(new ApiError('UPSTREAM_UNAVAILABLE', 'The voice service returned no audio'), { kind: 'UPSTREAM_UNAVAILABLE' });
    } catch (e) {
      last = e;
      if (!FALLBACK_KINDS.has(e.kind)) throw e;
    }
  }
  for (const model of legacyModels) {
    try {
      const json = await call(fetchImpl, `${API_BASE}/models/${encodeURIComponent(model)}:generateContent`, key, {
        contents: [{ parts: [{ text: `Say this ${s.style}${s.vocal ? ` (with a ${s.vocal})` : ''}: ${s.text}` }] }],
        generationConfig: { responseModalities: ['AUDIO'], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: s.voice } } } },
      }, timeoutMs);
      const part = (json?.candidates?.[0]?.content?.parts || []).find((p) => p.inlineData?.data);
      if (part) {
        const bytes = base64ToBytes(part.inlineData.data);
        const wav = isWav(bytes) ? bytes : pcmToWav(bytes, rateFromMime(part.inlineData.mimeType));
        return { wav, model, voice: s.voice, style: s.style };
      }
      last = Object.assign(new ApiError('UPSTREAM_UNAVAILABLE', 'The voice service returned no audio'), { kind: 'UPSTREAM_UNAVAILABLE' });
    } catch (e) {
      last = e;
      if (!FALLBACK_KINDS.has(e.kind)) throw e;
    }
  }
  throw last || new ApiError('UPSTREAM_UNAVAILABLE', 'No voice model answered');
}

// ------------------------------------------------------------------ STT

const LANG_CODES = { hinglish: ['hi-IN', 'en-IN'], hindi: ['hi-IN'], english: ['en-IN'] };

export async function stt({ key, audioBase64, mimeType = 'audio/wav', lang = 'hinglish', models = DEFAULT_STT_MODELS, chatModels = DEFAULT_CHAT_MODELS, fetchImpl = fetch, timeoutMs = 30000 }) {
  if (!key) throw new ApiError('NOT_CONFIGURED', 'Speech recognition is not configured on the server');
  if (typeof audioBase64 !== 'string' || audioBase64.length < 100) throw new ApiError('BAD_REQUEST', 'audioBase64 is required');
  if (audioBase64.length > 2_800_000) throw new ApiError('PAYLOAD_TOO_LARGE', 'Audio too long (max ~60 s)');
  const mime = /^audio\/[a-z0-9.+-]+$/i.test(mimeType) ? mimeType : 'audio/wav';
  let last;
  for (const model of models) {
    try {
      const json = await call(fetchImpl, `${API_BASE}/interactions`, key, {
        model,
        input: [{ type: 'audio', data: audioBase64, mime_type: mime }],
        generation_config: { transcription_config: { language_codes: LANG_CODES[lang] || [] } },
      }, timeoutMs);
      return { text: extractInteractionText(json), model };
    } catch (e) {
      last = e;
      if (!FALLBACK_KINDS.has(e.kind)) throw e;
    }
  }
  // Fallback: general multimodal model with an instruction
  const instruction = lang === 'hindi'
    ? 'Transcribe this speech exactly, in Devanagari. Output only the transcript.'
    : 'Transcribe this speech exactly. If it is Hindi or Hinglish, write it in Roman (Latin) script as young Indians type. Output only the transcript, nothing else.';
  for (const model of chatModels) {
    try {
      const json = await call(fetchImpl, `${API_BASE}/models/${encodeURIComponent(model)}:generateContent`, key, {
        contents: [{ role: 'user', parts: [{ text: instruction }, { inlineData: { mimeType: mime, data: audioBase64 } }] }],
        generationConfig: { temperature: 0, maxOutputTokens: 512 },
      }, timeoutMs);
      return { text: extractCandidateText(json).trim(), model };
    } catch (e) {
      last = e;
      if (!FALLBACK_KINDS.has(e.kind)) throw e;
    }
  }
  throw last || new ApiError('UPSTREAM_UNAVAILABLE', 'No transcription model answered');
}
