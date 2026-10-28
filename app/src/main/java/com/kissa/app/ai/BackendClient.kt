package com.kissa.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Persisted device credentials (stored in EncryptedSharedPreferences by SettingsRepository). */
interface DeviceTokenStore {
    fun installId(): String
    fun token(): String?
    fun tokenExpiry(): Long
    fun saveToken(token: String?, expiresAtSec: Long)
}

data class ServiceHealth(val ok: Boolean, val model: String? = null, val latencyMs: Long? = null, val error: String? = null)

data class HealthReport(
    val ok: Boolean,
    val configured: Boolean,
    val chat: ServiceHealth,
    val tts: ServiceHealth,
    val stt: ServiceHealth,
    val version: String? = null,
)

data class TtsRequest(
    val text: String,
    val voice: String,
    val emotion: String,
    val lang: String,
    val delivery: String? = null,
    val vocal: String? = null,
)

/**
 * The app's only network client for AI. Talks to the Kissa backend (Cloudflare Worker) which holds all provider
 * secrets. The device gets an anonymous HMAC token automatically on first use — no keys, no sign-in, no user action.
 */
class BackendClient(
    val baseUrl: String,
    private val store: DeviceTokenStore,
    private val http: OkHttpClient = defaultHttp(),
) {
    companion object {
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** A real deployment URL: https and not the placeholder that ships in source. */
        fun isConfiguredUrl(url: String?): Boolean {
            val u = url?.trim().orEmpty()
            return u.startsWith("https://") && !u.contains("example") && !u.contains("REPLACE") && u.length > 12
        }

        /** Maps a backend error body / HTTP status to the app's typed error. */
        fun errorFrom(status: Int, body: String?): AiException {
            val err = runCatching { json.parseToJsonElement(body.orEmpty()).jsonObject["error"]?.jsonObject }.getOrNull()
            val code = err?.get("code")?.jsonPrimitive?.contentOrNull
            val msg = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "HTTP $status"
            return when (code) {
                "UNAUTHORIZED" -> AiException(msg, AiException.Kind.AUTH, true)
                "RATE_LIMITED" -> AiException(msg, AiException.Kind.QUOTA, true)
                "UPSTREAM_QUOTA" -> AiException(msg, AiException.Kind.SERVICE_LIMIT, true)
                "NOT_CONFIGURED", "UPSTREAM_AUTH" -> AiException(msg, AiException.Kind.SERVICE_SETUP, false)
                "UPSTREAM_TIMEOUT" -> AiException(msg, AiException.Kind.TIMEOUT, true)
                "BLOCKED" -> AiException(msg, AiException.Kind.BLOCKED, false)
                "BAD_REQUEST", "PAYLOAD_TOO_LARGE" -> AiException(msg, AiException.Kind.BAD_RESPONSE, false)
                "UPSTREAM_UNAVAILABLE" -> AiException(msg, AiException.Kind.SERVER, true)
                else -> when (status) {
                    401, 403 -> AiException(msg, AiException.Kind.AUTH, true)
                    404 -> AiException("Backend endpoint not found — is the backend deployed?", AiException.Kind.SERVICE_SETUP, false)
                    429 -> AiException(msg, AiException.Kind.QUOTA, true)
                    in 500..599 -> AiException(msg, AiException.Kind.SERVER, true)
                    else -> AiException(msg, AiException.Kind.SERVER, false)
                }
            }
        }

        fun parseRegister(body: String): Pair<String, Long> {
            val o = json.parseToJsonElement(body).jsonObject
            val token = o["token"]?.jsonPrimitive?.contentOrNull ?: throw AiException("Bad register response", AiException.Kind.BAD_RESPONSE, true)
            val exp = o["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0L
            return token to exp
        }

        fun parseHealth(body: String): HealthReport {
            val o = json.parseToJsonElement(body).jsonObject
            val services = o["services"]?.jsonObject
            fun svc(name: String): ServiceHealth {
                val s = services?.get(name)?.jsonObject ?: return ServiceHealth(false, error = "MISSING")
                return ServiceHealth(
                    ok = s["ok"]?.jsonPrimitive?.booleanOrNull == true,
                    model = s["model"]?.jsonPrimitive?.contentOrNull,
                    latencyMs = s["latencyMs"]?.jsonPrimitive?.longOrNull,
                    error = s["error"]?.jsonPrimitive?.contentOrNull,
                )
            }
            val configured = o["configured"]?.jsonObject?.get("gemini")?.jsonPrimitive?.booleanOrNull ?: false
            return HealthReport(
                ok = o["ok"]?.jsonPrimitive?.booleanOrNull == true,
                configured = configured,
                chat = svc("chat"), tts = svc("tts"), stt = svc("stt"),
                version = o["version"]?.jsonPrimitive?.contentOrNull,
            )
        }

        fun parseText(body: String): String =
            json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?: throw AiException("Empty reply from backend", AiException.Kind.BAD_RESPONSE, true)

        fun ttsBody(r: TtsRequest): JsonObject = buildJsonObject {
            put("text", r.text); put("voice", r.voice); put("emotion", r.emotion.lowercase()); put("lang", r.lang)
            r.delivery?.let { put("delivery", it) }
            r.vocal?.let { put("vocal", it) }
        }
    }

    val configured: Boolean get() = isConfiguredUrl(baseUrl)
    private val base get() = baseUrl.trim().trimEnd('/')

    private fun exec(req: Request): Response = try {
        http.newCall(req).execute()
    } catch (e: java.net.SocketTimeoutException) {
        throw AiException("The AI service took too long", AiException.Kind.TIMEOUT, true)
    } catch (e: IOException) {
        throw AiException("Network problem: ${e.message ?: "offline"}", AiException.Kind.NETWORK, true)
    }

    private fun requireConfigured() {
        if (!configured) throw AiException("The AI service hasn't been set up yet", AiException.Kind.SERVICE_SETUP, false)
    }

    /** Registers this install anonymously (idempotent: same install id -> same device id on the server). */
    suspend fun register(): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val body = buildJsonObject { put("installId", store.installId()); put("platform", "android") }.toString()
        exec(Request.Builder().url("$base/v1/device/register").post(body.toRequestBody(JSON_MT)).build()).use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFrom(r.code, text)
            val (token, exp) = parseRegister(text)
            store.saveToken(token, exp)
            token
        }
    }

    suspend fun ensureToken(): String {
        val t = store.token()
        val nowSec = System.currentTimeMillis() / 1000
        return if (t != null && store.tokenExpiry() > nowSec + 3600) t else register()
    }

    /** Runs an authorised call; on 401 re-registers once (token expired / server secret rotated). */
    private suspend fun <T> authed(block: (String) -> T): T {
        requireConfigured()
        val token = ensureToken()
        return try {
            withContext(Dispatchers.IO) { block(token) }
        } catch (e: AiException) {
            if (e.kind != AiException.Kind.AUTH) throw e
            store.saveToken(null, 0)
            val fresh = register()
            withContext(Dispatchers.IO) { block(fresh) }
        }
    }

    suspend fun health(): HealthReport = withContext(Dispatchers.IO) {
        requireConfigured()
        exec(Request.Builder().url("$base/v1/health").get().build()).use { r ->
            val text = r.body?.string().orEmpty()
            // 503 with a health body still carries per-service status
            runCatching { parseHealth(text) }.getOrElse { throw errorFrom(r.code, text) }
        }
    }

    /** Proxies a generateContent body (built by GeminiClient.buildBody) through the backend. */
    suspend fun chat(request: JsonObject): String = authed { token ->
        val body = buildJsonObject { put("request", request) }.toString()
        exec(Request.Builder().url("$base/v1/chat").header("Authorization", "Bearer $token").post(body.toRequestBody(JSON_MT)).build()).use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFrom(r.code, text)
            parseText(text)
        }
    }

    /** Neural TTS: returns WAV bytes. */
    suspend fun tts(req: TtsRequest): ByteArray = authed { token ->
        exec(Request.Builder().url("$base/v1/tts").header("Authorization", "Bearer $token").post(ttsBody(req).toString().toRequestBody(JSON_MT)).build()).use { r ->
            if (!r.isSuccessful) throw errorFrom(r.code, r.body?.string())
            val bytes = r.body?.bytes() ?: ByteArray(0)
            if (bytes.size < 64) throw AiException("Empty audio from voice service", AiException.Kind.BAD_RESPONSE, true)
            bytes
        }
    }

    /** Server-side transcription fallback. */
    suspend fun stt(wav: ByteArray, lang: String): String = authed { token ->
        val b64 = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
        val body = buildJsonObject { put("audioBase64", b64); put("mimeType", "audio/wav"); put("lang", lang) }.toString()
        exec(Request.Builder().url("$base/v1/stt").header("Authorization", "Bearer $token").post(body.toRequestBody(JSON_MT)).build()).use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFrom(r.code, text)
            parseText(text).trim()
        }
    }
}
