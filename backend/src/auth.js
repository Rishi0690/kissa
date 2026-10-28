// Anonymous device tokens: base64url(JSON payload) + "." + base64url(HMAC-SHA256(TOKEN_SECRET, payload)).
// No user account, no PII: the app sends a random install id; we mint a device id bound to it.
import { ApiError } from './errors.js';

const enc = new TextEncoder();

export function b64url(bytes) {
  let s = '';
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function b64urlDecode(str) {
  const s = str.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((str.length + 3) % 4);
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function hmacKey(secret) {
  return crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
}

export async function sha256hex(text) {
  const d = await crypto.subtle.digest('SHA-256', enc.encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export const TOKEN_TTL_SEC = 60 * 60 * 24 * 30; // 30 days; the app re-registers silently when it expires

export async function mintToken(secret, installId, nowSec = Math.floor(Date.now() / 1000)) {
  if (!secret || secret.length < 16) throw new ApiError('NOT_CONFIGURED', 'TOKEN_SECRET is not set on the backend');
  if (typeof installId !== 'string' || !/^[A-Za-z0-9_-]{16,80}$/.test(installId)) {
    throw new ApiError('BAD_REQUEST', 'installId must be 16-80 url-safe characters');
  }
  const deviceId = 'd_' + (await sha256hex(secret + ':' + installId)).slice(0, 24);
  const payload = { d: deviceId, iat: nowSec, exp: nowSec + TOKEN_TTL_SEC, v: 1 };
  const p = b64url(enc.encode(JSON.stringify(payload)));
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', await hmacKey(secret), enc.encode(p)));
  return { token: `${p}.${b64url(sig)}`, deviceId, expiresAt: payload.exp };
}

export async function verifyToken(secret, token, nowSec = Math.floor(Date.now() / 1000)) {
  if (!secret) throw new ApiError('NOT_CONFIGURED', 'TOKEN_SECRET is not set on the backend');
  if (!token || typeof token !== 'string') throw new ApiError('UNAUTHORIZED', 'Missing device token');
  const parts = token.split('.');
  if (parts.length !== 2) throw new ApiError('UNAUTHORIZED', 'Malformed device token');
  let sig;
  try { sig = b64urlDecode(parts[1]); } catch { throw new ApiError('UNAUTHORIZED', 'Malformed device token'); }
  const ok = await crypto.subtle.verify('HMAC', await hmacKey(secret), sig, enc.encode(parts[0]));
  if (!ok) throw new ApiError('UNAUTHORIZED', 'Invalid device token');
  let payload;
  try { payload = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[0]))); } catch { throw new ApiError('UNAUTHORIZED', 'Malformed device token'); }
  if (!payload.d || typeof payload.exp !== 'number') throw new ApiError('UNAUTHORIZED', 'Malformed device token');
  if (payload.exp < nowSec) throw new ApiError('UNAUTHORIZED', 'Device token expired');
  return payload;
}

export function bearer(request) {
  const h = request.headers.get('authorization') || '';
  const m = /^Bearer\s+(.+)$/i.exec(h);
  return m ? m[1].trim() : null;
}
