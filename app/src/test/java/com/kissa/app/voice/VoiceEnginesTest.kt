package com.kissa.app.voice

import com.kissa.app.model.Emotion
import com.kissa.app.voice.engines.EdgeDrm
import com.kissa.app.voice.engines.EdgeTts
import com.kissa.app.voice.engines.GeminiTts
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class VoiceEnginesTest {

    @Test fun edgeTokenMatchesPythonReference() {
        // reference values from edge-tts 7.3.1 generate_sec_ms_gec_v2(t)
        assertEquals("84E6B49484FD1EEE-3D64CA0CE3AB33BE4AAA10FDC810DFDFE8DE45BCDDBB761839E233C0DC788843", EdgeDrm.secMsGec(1761700000.0))
        assertEquals("D4216A7D3DE0C94F-91D741832FD084A110FDAD52BE2FFA569C6EA5F7D06A9429380A398738F4559A", EdgeDrm.secMsGec(1790000000.0))
        assertEquals("0919602B5750900A-EC9DD394F23CB5635CF62A0E8E24136DCD32B26D10C543B10082E482D7516397", EdgeDrm.secMsGec(1700000123.5))
    }

    @Test fun edgeSsmlCarriesVoiceEmotionAndEscapes() {
        val v = EdgeTts.characterVoice("Leda", true, "hi-IN-SwaraNeural")
        val ssml = EdgeTts.ssml(v, "Tum <sach> mein & aaye? 😳 *blushes*", Emotion.SHY, "soft, nervous", "giggle")
        assertTrue(ssml.contains("name='en-IN-NeerjaExpressiveNeural'"))
        assertTrue(ssml.contains("&lt;sach&gt;") && ssml.contains("&amp;"))
        assertFalse(ssml.contains("😳")); assertFalse(ssml.contains("blushes"))
        assertTrue(ssml.contains("Hehe, "))
        assertTrue(ssml.contains("rate='-14%'")) // shy -8, nervous -6
        assertTrue(ssml.contains("volume='-16%'"))
    }

    @Test fun characterVoiceIsStableAndNeverThePlayersVoice() {
        for (g in NeuralVoices.ALL) {
            val a = EdgeTts.characterVoice(g.name, g.feminine, "hi-IN-SwaraNeural")
            assertEquals(a, EdgeTts.characterVoice(g.name, g.feminine, "hi-IN-SwaraNeural"))
            assertFalse(a.id == "hi-IN-SwaraNeural" && a.pitchHz == 0)
        }
        assertEquals("hi-IN-MadhurNeural", EdgeTts.characterVoice("Achird", false, "en-IN-PrabhatNeural").id)
        assertEquals("hi-IN-SwaraNeural", EdgeTts.playerVoice(null, true).id)
        assertEquals("en-IN-PrabhatNeural", EdgeTts.playerVoice(null, false).id)
        assertEquals("en-US-AvaMultilingualNeural", EdgeTts.playerVoice("en-US-AvaMultilingualNeural", false).id)
    }

    @Test fun prosodyFollowsEmotionAndDelivery() {
        val sad = EdgeTts.prosodyFor(Emotion.SAD, null)
        val happy = EdgeTts.prosodyFor(Emotion.HAPPY, null)
        assertTrue(sad.ratePct < 0 && sad.pitchHz < 0 && happy.pitchHz > 0)
        assertTrue(EdgeTts.prosodyFor(Emotion.NEUTRAL, "whispering").volumePct < 0)
    }

    @Test fun geminiTtsRequestFollowsDocs() {
        val style = GeminiTts.buildStyle("SHY", null, "hinglish")
        assertEquals("shy and a little flustered, softer, with small hesitations, relaxed Hinglish", style)
        assertEquals("teasing", GeminiTts.buildStyle("happy", "teasing", "english"))
        val t = GeminiTts.transcript("Pata nahi... shayad kal 😅", "sigh")
        assertEquals("<sigh> Pata nahi... <short pause> shayad kal", t)
        val body = GeminiTts.interactionBody("gemini-3.8-flash-lite-tts", t, "Leda", style).toString()
        assertTrue(body.contains("\"model\":\"gemini-3.8-flash-lite-tts\""))
        assertTrue(body.contains("\"speech_metadata\""))
        assertTrue(body.contains("\"speech_config\":[{\"voice\":\"Leda\"}]"))
        assertTrue(body.contains("\"response_format\":{\"type\":\"audio\"}"))
    }

    @Test fun geminiAudioParsing() {
        val pcm = ByteArray(480) { (it % 7).toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(pcm)
        val wav = GeminiTts.extractInteractionAudio("""{"steps":[{"type":"model_output","content":[{"type":"audio","data":"$b64","mime_type":"audio/l16;rate=24000"}]}]}""")!!
        assertEquals(44 + 480, wav.size); assertTrue(GeminiTts.isWav(wav))
        val legacy = GeminiTts.extractLegacyAudio("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"$b64"}}]}}]}""")!!
        assertEquals(524, legacy.size)
        assertNull(GeminiTts.extractInteractionAudio("""{"steps":[]}"""))
    }

    @Test fun quotaResetsAtPacificMidnight() {
        val now = java.time.ZonedDateTime.of(2026, 10, 29, 10, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        val next = GeminiTts.nextPacificMidnight(now)
        assertEquals(14L * 3600_000, next - now)
        assertTrue(GeminiTts().available())
    }

    /**
     * Live end-to-end check of the Kotlin Microsoft neural voice client (no key needed): voices a full multi-line
     * Hinglish conversation and writes MP3s to out/voice-samples. Run with -Dlive=true (needs network).
     */
    @Test fun liveMicrosoftConversation() {
        if (System.getProperty("live") != "true") return
        val proxyPort = System.getProperty("testProxyPort")?.toIntOrNull()
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .apply { if (proxyPort != null) proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))) }.build()
        val edge = EdgeTts(http)
        val out = File(System.getProperty("voiceOut") ?: "build/voice-samples").apply { mkdirs() }
        val riya = EdgeTts.characterVoice("Leda", true, EdgeTts.playerVoice(null, false).id)
        val kabir = EdgeTts.playerVoice(null, false)
        data class L(val who: String, val text: String, val emo: Emotion, val delivery: String? = null, val vocal: String? = null)
        val lines = listOf(
            L("riya", "Oye! Dekh ke chalo na... meri saari notes gira di tumne!", Emotion.ANGRY, "annoyed, scolding"),
            L("kabir", "Sorry sorry, galti ho gayi. Main utha deta hoon, ek second.", Emotion.SHY, "nervous, apologetic"),
            L("riya", "Hmm... theek hai. Waise tum naye ho kya? Pehle kabhi dekha nahi.", Emotion.CONFUSED, null, "hmm"),
            L("kabir", "Haan, aaj pehla din hai. Main Kabir. Aur tum?", Emotion.HAPPY),
            L("riya", "Riya. Aur haan, first day pe hi kisi ko dhakka maarna... impressive start hai!", Emotion.AMUSED, "teasing, playful", "laugh"),
            L("kabir", "Arre yaar, ab toh coffee pe le chalna padega, sorry bolne ke liye.", Emotion.HAPPY, "confident"),
            L("riya", "Coffee? Itni jaldi? Acha... chalo, par bill tum doge.", Emotion.SHY, "soft, smiling", "giggle"),
            L("riya", "Waise... sach bolun toh, aaj ka din kaafi bura ja raha tha. Thanks.", Emotion.SAD, "quiet, a little heavy", "sigh"),
        )
        val sizes = lines.mapIndexed { i, l ->
            val v = if (l.who == "riya") riya else kabir
            val mp3 = runBlocking { edge.synthesize(EdgeTts.ssml(v, l.text, l.emo, l.delivery, l.vocal)) }
            File(out, "%02d_%s_%s.mp3".format(i + 1, l.who, l.emo.name.lowercase())).writeBytes(mp3)
            mp3.size
        }
        sizes.forEach { assertTrue("line produced too little audio", it > 8000) }
        File(out, "script.txt").writeText(lines.mapIndexed { i, l ->
            "${i + 1}. ${l.who} [${(if (l.who == "riya") riya else kabir).id}, ${l.emo}, ${l.delivery ?: "-"}, vocal=${l.vocal ?: "-"}]: ${l.text}"
        }.joinToString("\n"))
    }
}
