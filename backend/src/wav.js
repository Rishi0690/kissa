// Minimal WAV helpers: wrap raw 16-bit PCM (legacy Gemini TTS returns audio/L16) and sniff RIFF headers.

export function wavHeader(pcmBytes, sampleRate = 24000, channels = 1, bits = 16) {
  const b = new ArrayBuffer(44);
  const v = new DataView(b);
  const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  const byteRate = (sampleRate * channels * bits) / 8;
  w(0, 'RIFF'); v.setUint32(4, 36 + pcmBytes, true); w(8, 'WAVE');
  w(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, channels, true);
  v.setUint32(24, sampleRate, true); v.setUint32(28, byteRate, true); v.setUint16(32, (channels * bits) / 8, true); v.setUint16(34, bits, true);
  w(36, 'data'); v.setUint32(40, pcmBytes, true);
  return new Uint8Array(b);
}

export function pcmToWav(pcm, sampleRate = 24000) {
  const out = new Uint8Array(44 + pcm.length);
  out.set(wavHeader(pcm.length, sampleRate), 0);
  out.set(pcm, 44);
  return out;
}

export function isWav(bytes) {
  return bytes.length > 12 && bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
    bytes[8] === 0x57 && bytes[9] === 0x41 && bytes[10] === 0x56 && bytes[11] === 0x45;
}

/** Parse "audio/L16;codec=pcm;rate=24000" -> 24000 */
export function rateFromMime(mime, fallback = 24000) {
  const m = /rate=(\d+)/i.exec(mime || '');
  return m ? parseInt(m[1], 10) : fallback;
}

export function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function bytesToBase64(bytes) {
  let s = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) s += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  return btoa(s);
}
