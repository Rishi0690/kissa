// Fixed-window per-device (or per-IP) rate limiting.
// Uses the RATE_KV namespace when bound (shared across isolates, eventually consistent — good enough for abuse
// control), otherwise an in-memory map (per isolate; still protects against tight loops from one client).
import { ApiError } from './errors.js';

export const DEFAULT_LIMITS = {
  register: { perMinute: 5, perDay: 30 },     // keyed by client IP
  chat: { perMinute: 20, perDay: 800 },
  tts: { perMinute: 40, perDay: 2000 },
  stt: { perMinute: 15, perDay: 600 },
  health: { perMinute: 6, perDay: 300 },
};

const memory = new Map();

export function _resetMemory() { memory.clear(); }

async function bump(kv, key, ttlSec) {
  if (kv) {
    const cur = parseInt((await kv.get(key)) || '0', 10) || 0;
    const next = cur + 1;
    // KV minimum TTL is 60s
    await kv.put(key, String(next), { expirationTtl: Math.max(60, ttlSec) });
    return next;
  }
  const now = Date.now();
  const e = memory.get(key);
  if (!e || e.until < now) { memory.set(key, { n: 1, until: now + ttlSec * 1000 }); return 1; }
  e.n++;
  if (memory.size > 5000) for (const [k, v] of memory) if (v.until < now) memory.delete(k);
  return e.n;
}

/** Throws RATE_LIMITED when the subject exceeded the minute or day window for this route. */
export async function enforce({ kv, route, subject, limits = DEFAULT_LIMITS, nowMs = Date.now() }) {
  const l = limits[route];
  if (!l) return;
  const minute = Math.floor(nowMs / 60000);
  const day = Math.floor(nowMs / 86400000);
  const m = await bump(kv, `rl:${route}:${subject}:m:${minute}`, 60);
  if (m > l.perMinute) throw new ApiError('RATE_LIMITED', 'Too many requests, slow down a little', { retryAfter: 60 - Math.floor((nowMs / 1000) % 60) });
  const d = await bump(kv, `rl:${route}:${subject}:d:${day}`, 86400);
  if (d > l.perDay) throw new ApiError('RATE_LIMITED', 'Daily limit reached for this device', { retryAfter: 86400 - Math.floor((nowMs / 1000) % 86400) });
}

export function parseLimits(json) {
  if (!json) return DEFAULT_LIMITS;
  try { return { ...DEFAULT_LIMITS, ...JSON.parse(json) }; } catch { return DEFAULT_LIMITS; }
}
