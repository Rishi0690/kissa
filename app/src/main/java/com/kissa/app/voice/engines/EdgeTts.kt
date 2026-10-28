package com.kissa.app.voice.engines

import com.kissa.app.model.Emotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A Microsoft neural voice (free Edge "Read aloud" service) with a fixed per-character pitch/rate offset. */
data class EdgeVoice(val id: String, val label: String, val feminine: Boolean, val pitchHz: Int = 0, val ratePct: Int = 0)

class EdgeTtsException(message: String, val status: Int = 0) : Exception(message)

/**
 * Free secondary natural-voice engine: Microsoft Edge neural voices (hi-IN Swara/Madhur, en-IN Neerja/Prabhat …)
 * over the Read-aloud websocket. No key needed. The service only allows one voice + one <prosody> per request
 * (no <break>, no express-as), so emotion is performed with per-emotion pitch/rate/volume, interjections for vocal
 * bursts and punctuation-driven pauses. Output: 24 kHz 48 kbps mono MP3 (PCM formats are rejected by the service).
 */
class EdgeTts(private val http: OkHttpClient = defaultHttp()) {

    companion object {
        private const val WSS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=${EdgeDrm.TRUSTED_CLIENT_TOKEN}"
        const val FORMAT = "audio-24khz-48kbitrate-mono-mp3"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).pingInterval(0, TimeUnit.SECONDS).build()

        // ---------------------------------------------------------------- voices
        val FEMALE_CHARACTER = listOf(
            EdgeVoice("en-IN-NeerjaExpressiveNeural", "Neerja Expressive", true),
            EdgeVoice("hi-IN-SwaraNeural", "Swara", true, pitchHz = 2),
            EdgeVoice("en-IN-NeerjaNeural", "Neerja (brighter)", true, pitchHz = 10, ratePct = 4),
            EdgeVoice("hi-IN-SwaraNeural", "Swara (soft)", true, pitchHz = -6, ratePct = -4),
        )
        val MALE_CHARACTER = listOf(
            EdgeVoice("hi-IN-MadhurNeural", "Madhur", false),
            EdgeVoice("en-IN-PrabhatNeural", "Prabhat (deeper)", false, pitchHz = -8, ratePct = -3),
            EdgeVoice("hi-IN-MadhurNeural", "Madhur (brighter)", false, pitchHz = 8, ratePct = 4),
            EdgeVoice("en-US-AndrewMultilingualNeural", "Andrew", false),
        )
        /** Voices the player can choose for their typed lines. */
        val PLAYER_CHOICES = listOf(
            EdgeVoice("en-IN-PrabhatNeural", "Prabhat · Indian English", false),
            EdgeVoice("hi-IN-MadhurNeural", "Madhur · Hindi", false),
            EdgeVoice("en-US-AndrewMultilingualNeural", "Andrew · warm", false),
            EdgeVoice("en-US-BrianMultilingualNeural", "Brian · casual", false),
            EdgeVoice("hi-IN-SwaraNeural", "Swara · Hindi", true),
            EdgeVoice("en-IN-NeerjaNeural", "Neerja · Indian English", true),
            EdgeVoice("en-IN-NeerjaExpressiveNeural", "Neerja · expressive", true),
            EdgeVoice("en-US-AvaMultilingualNeural", "Ava · expressive", true),
            EdgeVoice("en-US-EmmaMultilingualNeural", "Emma · cheerful", true),
        )

        fun playerVoice(chosen: String?, feminine: Boolean): EdgeVoice =
            PLAYER_CHOICES.firstOrNull { it.id == chosen } ?: if (feminine) PLAYER_CHOICES.first { it.id == "hi-IN-SwaraNeural" } else PLAYER_CHOICES.first()

        /**
         * Fixed, distinct Microsoft voice for a character, derived from its Gemini voice name so the same character
         * always sounds the same, and never identical to the player's own voice.
         */
        fun characterVoice(geminiVoice: String?, feminine: Boolean, playerVoiceId: String?): EdgeVoice {
            val pool = if (feminine) FEMALE_CHARACTER else MALE_CHARACTER
            val start = Math.floorMod((geminiVoice ?: "").hashCode(), pool.size).let { if (geminiVoice in listOf("Leda", "Achird", null)) 0 else it }
            for (i in pool.indices) {
                val v = pool[(start + i) % pool.size]
                if (v.id != playerVoiceId || v.pitchHz != 0) return v
            }
            return pool[start]
        }

        // ---------------------------------------------------------------- performance
        data class Prosody(val pitchHz: Int, val ratePct: Int, val volumePct: Int)

        fun prosodyFor(emotion: Emotion, delivery: String?): Prosody {
            var p = when (emotion) {
                Emotion.HAPPY -> Prosody(8, 6, 0)
                Emotion.SAD -> Prosody(-10, -14, -12)
                Emotion.ANGRY -> Prosody(-2, 10, 8)
                Emotion.SURPRISED -> Prosody(14, 8, 4)
                Emotion.SHY -> Prosody(4, -8, -16)
                Emotion.CONFUSED -> Prosody(0, -8, -4)
                Emotion.AMUSED -> Prosody(6, 4, 0)
                Emotion.NEUTRAL -> Prosody(0, 0, 0)
            }
            val d = delivery?.lowercase().orEmpty()
            if (d.isNotEmpty()) {
                if (Regex("whisper|softly|quiet|murmur").containsMatchIn(d)) p = p.copy(volumePct = p.volumePct - 25, ratePct = p.ratePct - 6)
                if (Regex("excit|fast|rapid|eager|thrill").containsMatchIn(d)) p = p.copy(ratePct = p.ratePct + 8, pitchHz = p.pitchHz + 4)
                if (Regex("nervous|hesita|unsure|awkward|flustered").containsMatchIn(d)) p = p.copy(ratePct = p.ratePct - 6)
                if (Regex("slow|tired|heavy|sleepy").containsMatchIn(d)) p = p.copy(ratePct = p.ratePct - 10)
                if (Regex("shout|loud|yell|angry|sharp").containsMatchIn(d)) p = p.copy(volumePct = p.volumePct + 10)
                if (Regex("teas|playful|smirk").containsMatchIn(d)) p = p.copy(pitchHz = p.pitchHz + 3)
            }
            return Prosody(p.pitchHz.coerceIn(-30, 30), p.ratePct.coerceIn(-30, 30), p.volumePct.coerceIn(-40, 20))
        }

        /** Spoken form of a vocal burst (Edge can't do <laugh>; a short interjection reads naturally instead). */
        fun interjection(vocal: String?, hindi: Boolean): String = when (vocal?.lowercase()) {
            "laugh" -> if (hindi) "हाहा, " else "Haha, "
            "chuckle", "giggle" -> if (hindi) "हेहे, " else "Hehe, "
            "sigh" -> if (hindi) "हम्म... " else "Haah... "
            "gasp" -> if (hindi) "अरे! " else "Oh! "
            "tsk" -> if (hindi) "च्च, " else "Tch, "
            "hmm" -> if (hindi) "हम्म... " else "Hmm... "
            else -> ""
        }

        private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")

        /** Text as it should be spoken: no emoji, no *stage directions*, tidy whitespace, XML-escaped. */
        fun speakable(text: String): String = text
            .replace(Regex("\\*[^*]{1,80}\\*"), " ")
            .replace(EMOJI, "")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ").trim()

        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;")

        private fun signed(n: Int, unit: String) = (if (n >= 0) "+$n" else "$n") + unit

        fun ssml(voice: EdgeVoice, text: String, emotion: Emotion = Emotion.NEUTRAL, delivery: String? = null, vocal: String? = null): String {
            val pr = prosodyFor(emotion, delivery)
            val hindi = text.any { it in '\u0900'..'\u097F' }
            val body = speakable(text)
            val inter = interjection(vocal, hindi).takeUnless { i -> i.isNotBlank() && body.lowercase().startsWith(i.trim().trimEnd('.', ',', '!').lowercase()) }.orEmpty()
            val spoken = inter + body
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='${voice.id}'><prosody pitch='${signed(pr.pitchHz + voice.pitchHz, "Hz")}' rate='${signed(pr.ratePct + voice.ratePct, "%")}' volume='${signed(pr.volumePct, "%")}'>" +
                escape(spoken) + "</prosody></voice></speak>"
        }

        private fun jsDate(): String = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(System.currentTimeMillis())

        private fun parseHttpDate(d: String?): Double? = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(d!!)!!.time / 1000.0
        }.getOrNull()
    }

    /** Synthesises one SSML document. Returns MP3 bytes. Retries once after correcting clock skew on a 403. */
    suspend fun synthesize(ssml: String, timeoutMs: Long = 20_000): ByteArray {
        return try { once(ssml, timeoutMs) } catch (e: EdgeTtsException) {
            if (e.status == 403) once(ssml, timeoutMs) else throw e
        }
    }

    private suspend fun once(ssml: String, timeoutMs: Long): ByteArray {
        val done = CompletableDeferred<ByteArray>()
        val audio = ByteArrayOutputStream()
        val url = "$WSS&ConnectionId=${UUID.randomUUID().toString().replace("-", "")}" +
            "&Sec-MS-GEC=${EdgeDrm.secMsGec()}&Sec-MS-GEC-Version=${EdgeDrm.SEC_MS_GEC_VERSION}"
        val req = Request.Builder().url(url)
            .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${EdgeDrm.CHROMIUM_MAJOR}.0.0.0 Safari/537.36 Edg/${EdgeDrm.CHROMIUM_MAJOR}.0.0.0")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=${UUID.randomUUID().toString().replace("-", "").uppercase()};")
            .build()
        val ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("X-Timestamp:${jsDate()}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"$FORMAT\"}}}}\r\n")
                webSocket.send("X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${jsDate()}Z\r\nPath:ssml\r\n\r\n$ssml")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    if (audio.size() > 0) done.complete(audio.toByteArray()) else done.completeExceptionally(EdgeTtsException("no audio"))
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val b = bytes.toByteArray()
                if (b.size < 2) return
                val hl = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                if (hl + 2 > b.size) return
                val header = String(b, 2, hl, Charsets.UTF_8)
                if (header.contains("Path:audio") && b.size > hl + 2) audio.write(b, hl + 2, b.size - hl - 2)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!done.isCompleted) done.completeExceptionally(EdgeTtsException("closed by voice service ($code ${reason.take(60)})"))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) done.completeExceptionally(EdgeTtsException("closed before audio finished ($code ${reason.take(60)})"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                if (code == 403) {
                    parseHttpDate(response?.header("Date"))?.let { server -> EdgeDrm.clockSkewSeconds += server - (System.currentTimeMillis() / 1000.0 + EdgeDrm.clockSkewSeconds) }
                }
                if (!done.isCompleted) done.completeExceptionally(EdgeTtsException("voice service unreachable: ${t.message ?: t.javaClass.simpleName}", code))
            }
        })
        return try { withTimeout(timeoutMs) { done.await() } } finally { ws.cancel() }
    }
}
