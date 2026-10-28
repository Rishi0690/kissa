// Stable error codes shared with the Android client (see BackendClient.kt). Never leak provider keys or raw upstream bodies.
export const Codes = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  NOT_FOUND: 404,
  PAYLOAD_TOO_LARGE: 413,
  RATE_LIMITED: 429,          // per-device limit of THIS backend
  NOT_CONFIGURED: 503,        // owner has not set GEMINI_API_KEY / TOKEN_SECRET yet
  UPSTREAM_QUOTA: 503,        // provider quota / billing exhausted for the owner key
  UPSTREAM_AUTH: 503,         // owner key rejected by provider (expired / revoked)
  UPSTREAM_UNAVAILABLE: 502,  // provider outage / 5xx / no model answered
  UPSTREAM_TIMEOUT: 504,
  BLOCKED: 422,               // safety filter refused the content
  INTERNAL: 500,
};

export class ApiError extends Error {
  constructor(code, message, { retryAfter, detail } = {}) {
    super(message);
    this.code = code;
    this.status = Codes[code] ?? 500;
    this.retryAfter = retryAfter;
    this.detail = detail;
  }
}

export function errorResponse(err, cors = {}) {
  const e = err instanceof ApiError ? err : new ApiError('INTERNAL', 'Unexpected server error');
  const body = { error: { code: e.code, message: e.message } };
  if (e.retryAfter) body.error.retryAfter = e.retryAfter;
  const headers = { 'content-type': 'application/json; charset=utf-8', ...cors };
  if (e.retryAfter) headers['retry-after'] = String(e.retryAfter);
  return new Response(JSON.stringify(body), { status: e.status, headers });
}

/** Map a provider HTTP status + message to our error code. */
export function classifyUpstream(status, message = '') {
  const m = String(message).toLowerCase();
  if (status === 429 || m.includes('quota') || m.includes('resource_exhausted') || m.includes('billing')) return 'UPSTREAM_QUOTA';
  if (status === 401 || status === 403 || m.includes('api key')) return 'UPSTREAM_AUTH';
  if (status === 404 || m.includes('not found') || m.includes('is not supported')) return 'MODEL_NOT_FOUND';
  if (status === 400 && (m.includes('safety') || m.includes('blocked'))) return 'BLOCKED';
  if (status === 400) return 'BAD_UPSTREAM_REQUEST';
  if (status === 504 || status === 408) return 'UPSTREAM_TIMEOUT';
  return 'UPSTREAM_UNAVAILABLE';
}
