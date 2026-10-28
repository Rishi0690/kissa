package com.kissa.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiException(message: String, val kind: Kind, val retryable: Boolean) : Exception(message) {
    enum class Kind { NO_KEY, AUTH, QUOTA, NETWORK, TIMEOUT, BLOCKED, BAD_RESPONSE, MODEL_NOT_FOUND, SERVER,
        /** Backend not deployed / owner credentials missing or rejected: an operator problem, not the user's. */ SERVICE_SETUP,
        /** The owner's provider quota/billing is exhausted. */ SERVICE_LIMIT }
}

/**
 * Builds Gemini generateContent bodies (sent to the Kissa backend by [BackendClient.chat]) and parses responses.
 * Direct calls with a key are ONLY used by the hidden developer override in debug builds; release builds never
 * hold a provider key. Talks to Gemini generateContent over REST. If a proxy URL is configured, the same request body is
 * POSTed to `<proxy>/generate?model=<model>` and the key is NOT sent from the device (the proxy holds it).
 */
class GeminiClient(private val http: OkHttpClient = defaultHttp()) {

    companion object {
        val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-flash-latest", "gemini-2.5-flash")
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun buildBody(payload: PromptPayload, schema: JsonObject?, temperature: Double, maxTokens: Int): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", payload.system) } } }
        putJsonArray("contents") {
            payload.contents.forEach { c ->
                addJsonObject {
                    put("role", c.role)
                    putJsonArray("parts") { addJsonObject { put("text", c.text) } }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            put("maxOutputTokens", maxTokens)
            if (schema != null) {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
            }
        }
        putJsonArray("safetySettings") {
            listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT").forEach {
                addJsonObject { put("category", it); put("threshold", "BLOCK_MEDIUM_AND_ABOVE") }
            }
        }
    }

    /** Returns the raw text of the first candidate. Retries transient failures with backoff. */
    suspend fun generate(
        apiKey: String?, proxyUrl: String?, model: String, payload: PromptPayload,
        schema: JsonObject?, temperature: Double = 0.9, maxTokens: Int = 2048, attempts: Int = 3,
    ): String = withContext(Dispatchers.IO) {
        var last: AiException? = null
        var useSchema = schema
        repeat(attempts) { attempt ->
            try {
                return@withContext once(apiKey, proxyUrl, model, buildBody(payload, useSchema, temperature, maxTokens))
            } catch (e: AiException) {
                last = e
                if (e.kind == AiException.Kind.BAD_RESPONSE && useSchema != null && e.message?.contains("schema", true) == true) {
                    useSchema = null // older/other models may reject the schema; fall back to prompt-only JSON
                } else if (!e.retryable) throw e
                delay(700L * (attempt + 1) * (attempt + 1))
            }
        }
        throw last ?: AiException("unknown failure", AiException.Kind.SERVER, true)
    }

    private fun once(apiKey: String?, proxyUrl: String?, model: String, body: JsonObject): String {
        val proxy = proxyUrl?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") }
        val req = if (proxy != null) {
            Request.Builder().url("$proxy/generate?model=$model").post(body.toString().toRequestBody(JSON)).build()
        } else {
            val key = apiKey?.trim().orEmpty()
            if (key.isEmpty()) throw AiException("No Gemini API key set", AiException.Kind.NO_KEY, false)
            Request.Builder().url("$BASE$model:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toString().toRequestBody(JSON)).build()
        }
        val resp = try { http.newCall(req).execute() } catch (e: java.net.SocketTimeoutException) {
            throw AiException("The AI took too long to answer", AiException.Kind.TIMEOUT, true)
        } catch (e: IOException) {
            throw AiException("Network problem: ${e.message ?: "offline"}", AiException.Kind.NETWORK, true)
        }
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching {
                    Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull() ?: text.take(200)
                throw when (r.code) {
                    400 -> if (msg.contains("API key", true)) AiException("Invalid API key", AiException.Kind.AUTH, false)
                           else AiException("Bad request: $msg", AiException.Kind.BAD_RESPONSE, false)
                    401, 403 -> AiException("API key rejected: $msg", AiException.Kind.AUTH, false)
                    404 -> AiException("Model '$model' not available: $msg", AiException.Kind.MODEL_NOT_FOUND, false)
                    429 -> AiException("Rate limit / quota reached. Wait a moment.", AiException.Kind.QUOTA, true)
                    in 500..599 -> AiException("AI service error ${r.code}", AiException.Kind.SERVER, true)
                    else -> AiException("HTTP ${r.code}: $msg", AiException.Kind.SERVER, false)
                }
            }
            return extractText(text)
        }
    }

    fun extractText(responseJson: String): String {
        val root = try { Json.parseToJsonElement(responseJson).jsonObject } catch (e: Exception) {
            throw AiException("Unreadable AI response", AiException.Kind.BAD_RESPONSE, true)
        }
        root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw AiException("Blocked by safety filter ($it)", AiException.Kind.BLOCKED, false)
        }
        val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiException("Empty AI response", AiException.Kind.BAD_RESPONSE, true)
        val finish = cand["finishReason"]?.jsonPrimitive?.contentOrNull
        val parts = cand["content"]?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o["text"]?.jsonPrimitive?.contentOrNull
        }?.joinToString("").orEmpty()
        if (text.isBlank()) {
            if (finish == "SAFETY" || finish == "PROHIBITED_CONTENT") throw AiException("Blocked by safety filter", AiException.Kind.BLOCKED, false)
            throw AiException("Empty AI response ($finish)", AiException.Kind.BAD_RESPONSE, true)
        }
        return text
    }
}
