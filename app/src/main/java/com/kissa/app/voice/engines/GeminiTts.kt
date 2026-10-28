package com.kissa.app.voice.engines

import com.kissa.app.ai.AiException
import com.kissa.app.voice.WavUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.ZoneId
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Gemini native TTS called directly from the app with the user's own API key (port of backend/src/gemini.js tts()).
 * Gemini 3.8 TTS models use the Interactions API (`speech_metadata.style` for turn-level delivery, inline <tags> for
 * vocal bursts, unary WAV output); older preview models use generateContent (raw L16 PCM). Model names per
 * ai.google.dev/gemini-api/docs/speech-generation (checked 2026-10-29).
 *
 * Free tier is tiny (Google publishes no numbers; AI Studio/project reports show ~3 RPM and ~10 requests/day per TTS
 * model), so every 429 parks that model — until midnight Pacific for a daily quota, ~65 s for a per-minute one — and
 * the caller falls back to the Microsoft neural voice.
 */
class GeminiTts(private val http: OkHttpClient = defaultHttp()) {
    companion object {
        const val API = "https://generativelanguage.googleapis.com/v1beta"
        val MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts")
        val LEGACY_MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

        val EMOTION_STYLE = mapOf(
            "neutral" to "casual, natural conversational",
            "happy" to "warm and cheerful, smiling while talking",
            "shy" to "shy and a little flustered, softer, with small hesitations",
            "surprised" to "surprised, slightly faster, rising intonation",
            "confused" to "puzzled and unsure, thoughtful pauses",
            "sad" to "quiet and sad, slower, a little heavy",
            "angry" to "annoyed and sharp, scolding, clipped",
            "amused" to "teasing and playful, amused",
        )
        val VOCAL_TAGS = mapOf("laugh" to "<laugh>", "chuckle" to "<chuckle>", "giggle" to "<giggle>", "sigh" to "<sigh>",
            "gasp" to "<gasp>", "tsk" to "<tsk>", "hmm" to "<short pause>", "breath" to "<breath>")

        fun buildStyle(emotion: String?, delivery: String?, lang: String): String {
            val d = delivery?.replace(Regex("[<>|]"), "")?.trim()?.take(80).orEmpty()
            val parts = mutableListOf(d.ifEmpty { EMOTION_STYLE[emotion?.lowercase() ?: "neutral"] ?: EMOTION_STYLE.getValue("neutral") })
            if (lang == "hinglish") parts += "relaxed Hinglish"
            return parts.joinToString(", ").take(140)
        }

        /** Verbatim transcript: emoji/stage directions removed, natural pause after an ellipsis, optional vocal burst. */
        fun transcript(text: String, vocal: String?): String {
            val clean = EdgeTts.speakable(text).replace(Regex("[<>]"), "").take(600)
            val paused = clean.replace(Regex("(\\.\\.\\.|…)\\s+"), "... <short pause> ")
            val tag = vocal?.lowercase()?.let { VOCAL_TAGS[it] }
            return if (tag != null) "$tag $paused" else paused
        }

        fun interactionBody(model: String, text: String, voice: String, style: String): JsonObject = buildJsonObject {
            put("model", model)
            putJsonArray("input") { addJsonObject {
                put("type", "user_input")
                putJsonArray("content") { addJsonObject {
                    put("type", "text"); put("text", text)
                    putJsonArray("annotations") { addJsonObject { put("type", "speech_metadata"); put("style", style) } }
                } }
            } }
            putJsonObject("response_format") { put("type", "audio") }
            putJsonObject("generation_config") { putJsonArray("speech_config") { addJsonObject { put("voice", voice) } } }
        }

        fun legacyBody(text: String, voice: String, style: String): JsonObject = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", "Say this $style: $text") } } } }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") { putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } } }
            }
        }

        fun isWav(b: ByteArray) = b.size > 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte()
        fun rateFromMime(mime: String?): Int = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mime.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 24000

        private fun toWav(b64: String, mime: String?): ByteArray {
            val bytes = Base64.getDecoder().decode(b64)
            return if (isWav(bytes)) bytes else WavUtil.header(bytes.size, rateFromMime(mime)) + bytes
        }

        /** Audio from an Interactions response: steps[].content[] {type:"audio", data, mime_type} or output_audio. */
        fun extractInteractionAudio(body: String): ByteArray? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            var found: JsonObject? = null
            root["steps"]?.jsonArray?.forEach { st ->
                val o = st.jsonObject
                val type = o["type"]?.jsonPrimitive?.contentOrNull
                if (type != null && type != "model_output") return@forEach
                o["content"]?.jsonArray?.forEach { c -> val co = c.jsonObject; if (co["type"]?.jsonPrimitive?.contentOrNull == "audio" && co["data"] != null) found = co }
            }
            val f = found ?: root["output_audio"]?.jsonObject ?: return null
            val data = f["data"]?.jsonPrimitive?.contentOrNull ?: return null
            return toWav(data, (f["mime_type"] ?: f["mimeType"])?.jsonPrimitive?.contentOrNull ?: "audio/wav")
        }

        fun extractLegacyAudio(body: String): ByteArray? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
            val inline = parts.mapNotNull { it.jsonObject["inlineData"]?.jsonObject }.firstOrNull() ?: return null
            val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: return null
            return toWav(data, inline["mimeType"]?.jsonPrimitive?.contentOrNull)
        }

        /** Epoch millis of the next midnight in Pacific time (when Gemini daily quotas reset). */
        fun nextPacificMidnight(nowMs: Long = System.currentTimeMillis()): Long {
            val zone = ZoneId.of("America/Los_Angeles")
            val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }

    private val blockedUntil = ConcurrentHashMap<String, Long>()

    /** True while at least one TTS model is not parked by a quota error. */
    fun available(now: Long = System.currentTimeMillis()): Boolean = (MODELS + LEGACY_MODELS).any { (blockedUntil[it] ?: 0L) <= now }

    /** When every model is parked: the soonest time one becomes usable again. */
    fun nextAvailableAt(): Long = (MODELS + LEGACY_MODELS).minOf { blockedUntil[it] ?: 0L }

    fun resetQuotaMarks() = blockedUntil.clear()

    /** Synthesises one line. Returns WAV bytes. Throws AiException (QUOTA when every model is out of quota). */
    suspend fun synthesize(key: String, text: String, voice: String, emotion: String, lang: String, delivery: String?, vocal: String?): ByteArray =
        withContext(Dispatchers.IO) {
            val style = buildStyle(emotion, delivery, lang)
            val script = transcript(text, vocal)
            if (script.isBlank()) throw AiException("Nothing to say", AiException.Kind.BAD_RESPONSE, false)
            var last: AiException? = null
            val now = System.currentTimeMillis()
            for (model in MODELS) {
                if ((blockedUntil[model] ?: 0L) > now) continue
                try {
                    val body = post("$API/interactions", key, interactionBody(model, script, voice, style), model)
                    extractInteractionAudio(body)?.let { return@withContext it }
                    last = AiException("No audio from $model", AiException.Kind.BAD_RESPONSE, true)
                } catch (e: AiException) { last = e; if (e.kind == AiException.Kind.AUTH) throw e }
            }
            for (model in LEGACY_MODELS) {
                if ((blockedUntil[model] ?: 0L) > now) continue
                try {
                    val body = post("$API/models/$model:generateContent", key, legacyBody(script.replace(Regex("<[^>]+>"), ""), voice, style), model)
                    extractLegacyAudio(body)?.let { return@withContext it }
                    last = AiException("No audio from $model", AiException.Kind.BAD_RESPONSE, true)
                } catch (e: AiException) { last = e; if (e.kind == AiException.Kind.AUTH) throw e }
            }
            throw if (!available()) AiException("Gemini voice quota used up", AiException.Kind.QUOTA, true)
            else last ?: AiException("No Gemini voice model answered", AiException.Kind.SERVER, true)
        }

    private fun post(url: String, key: String, body: JsonObject, model: String): String {
        val req = Request.Builder().url(url).header("x-goog-api-key", key.trim()).post(body.toString().toRequestBody(JSON_MT)).build()
        val resp = try { http.newCall(req).execute() } catch (e: java.net.SocketTimeoutException) {
            throw AiException("Voice generation timed out", AiException.Kind.TIMEOUT, true)
        } catch (e: IOException) { throw AiException("Network problem", AiException.Kind.NETWORK, true) }
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return text
            val msg = runCatching { Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull() ?: text.take(200)
            when (r.code) {
                429 -> {
                    val daily = Regex("per_day|PerDay|daily|per day", RegexOption.IGNORE_CASE).containsMatchIn(text)
                    blockedUntil[model] = if (daily) nextPacificMidnight() else System.currentTimeMillis() + 65_000
                    throw AiException("Gemini voice quota reached ($model)", AiException.Kind.QUOTA, true)
                }
                400 -> if (msg.contains("API key", true)) throw AiException("Invalid API key", AiException.Kind.AUTH, false)
                       else { blockedUntil[model] = System.currentTimeMillis() + 10 * 60_000; throw AiException("Bad voice request: $msg", AiException.Kind.BAD_RESPONSE, false) }
                401, 403 -> throw AiException("API key rejected", AiException.Kind.AUTH, false)
                404 -> { blockedUntil[model] = System.currentTimeMillis() + 6 * 3600_000; throw AiException("Voice model $model not available", AiException.Kind.MODEL_NOT_FOUND, false) }
                else -> throw AiException("Voice service error ${r.code}", AiException.Kind.SERVER, true)
            }
        }
    }
}

