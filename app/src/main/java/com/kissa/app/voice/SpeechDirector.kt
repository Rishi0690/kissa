package com.kissa.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.kissa.app.ai.AiException
import com.kissa.app.ai.AiService
import com.kissa.app.ai.BackendClient
import com.kissa.app.ai.TtsRequest
import com.kissa.app.model.Emotion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** What is being heard right now. The scene reads this to drive mouths and subtitles. */
data class SpeakingState(
    val speakerId: String,
    val text: String,
    val highlightEnd: Int = 0,
    val lastWordAt: Long = 0L,
    val hasWordEvents: Boolean = false,
    val envelope: FloatArray? = null, // amplitude per 50ms for recorded audio
    val startedAt: Long = System.currentTimeMillis(),
    val isRecording: Boolean = false,
)

data class VoiceOption(val name: String, val locale: String, val feminine: Boolean?, val offline: Boolean, val quality: Int)

data class Utterance(
    val speakerId: String,
    val text: String,
    val localeTag: String,
    val voiceName: String?,
    val feminine: Boolean,
    val pitch: Float,
    val rate: Float,
    val emotion: Emotion = Emotion.NEUTRAL,
    val audioFile: String? = null,
    /** Gemini prebuilt voice (direct with the user's key, or via the backend); null = no Gemini voice for this line. */
    val neuralVoice: String? = null,
    /** Microsoft neural voice (free secondary engine); null = none. */
    val edgeVoice: com.kissa.app.voice.engines.EdgeVoice? = null,
    /** Character lines may use the (tiny) Gemini TTS quota; the player's typed lines use the Microsoft voice. */
    val useGemini: Boolean = false,
    val delivery: String? = null,
    val vocal: String? = null,
    val lang: String = "hinglish",
)

/** Voice engines, best first. PHONE = the device's own TTS ("Basic voice"). */
enum class VoiceEngine(val label: String) { SERVER("Gemini voice (Kissa server)"), GEMINI("Gemini voice (your key)"), MICROSOFT("Microsoft neural voice"), PHONE("Basic phone voice") }

/** Which voice engine is actually being heard. The UI shows "Basic voice" whenever premium isn't really active. */
data class VoiceMode(val premium: Boolean, val reason: String? = null, val engine: VoiceEngine? = null)

/**
 * Single audio queue for the whole app. TTS lines and recorded voice clips are played strictly one at a
 * time (Mutex), so speech never overlaps; stop() cancels whatever is playing and releases the waiter.
 */
class SpeechDirector(private val ctx: Context, private val backend: BackendClient? = null, private val ai: AiService? = null) {
    private val _mode = MutableStateFlow(VoiceMode(premium = false, reason = null))
    val mode: StateFlow<VoiceMode> = _mode.asStateFlow()
    /** Set from settings: false = always use the phone voice. */
    @Volatile var naturalVoicesEnabled = true
    /** Set from settings: false = never spend the Gemini TTS quota (Microsoft neural voices only). */
    @Volatile var geminiVoicesEnabled = true
    val geminiTts = com.kissa.app.voice.engines.GeminiTts()
    val edgeTts = com.kissa.app.voice.engines.EdgeTts()
    private val _edgeReachable = MutableStateFlow<Boolean?>(null)
    /** null = not checked yet. */
    val edgeReachable: StateFlow<Boolean?> = _edgeReachable.asStateFlow()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = ConcurrentHashMap<String, Deferred<File?>>()
    private val cacheDir: File by lazy { File(ctx.cacheDir, "tts").apply { mkdirs() } }
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _speaking = MutableStateFlow<SpeakingState?>(null)
    val speaking: StateFlow<SpeakingState?> = _speaking.asStateFlow()
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()
    var initError: String? = null; private set

    var volume: Float = 1f
    private val mutex = Mutex()
    private val ids = AtomicLong(0)
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var player: MediaPlayer? = null
    private var playerWaiter: CompletableDeferred<Boolean>? = null
    @Volatile private var chunkOffset = 0
    @Volatile private var generation = 0L

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            refreshVoices(); _ready.value = true
        } else initError = "Text-to-speech engine is unavailable on this device"
    }

    init {
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { utteranceId?.let { waiters.remove(it)?.complete(true) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { waiters.remove(it)?.complete(false) } }
            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { waiters.remove(it)?.complete(false) } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let { waiters.remove(it)?.complete(false) } }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val s = _speaking.value ?: return
                _speaking.value = s.copy(highlightEnd = (chunkOffset + end).coerceAtMost(s.text.length), lastWordAt = System.currentTimeMillis(), hasWordEvents = true)
            }
        })
    }

    fun refreshVoices() {
        val list = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        _voices.value = list.filter { v ->
            val lang = v.locale.language
            (lang == "en" || lang == "hi") && !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.map { VoiceOption(it.name, it.locale.toLanguageTag(), guessFeminine(it.name), !it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(compareBy<VoiceOption>({ !it.locale.endsWith("IN") }, { it.locale }, { it.name }))
    }

    private fun pickVoice(localeTag: String, feminine: Boolean, preferred: String?): Voice? {
        val all = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        if (preferred != null) all.firstOrNull { it.name == preferred }?.let { return it }
        val loc = Locale.forLanguageTag(localeTag)
        val sameLocale = all.filter { it.locale.language == loc.language && it.locale.country.equals(loc.country, true) }
        val pool = sameLocale.ifEmpty { all.filter { it.locale.language == loc.language } }
        if (pool.isEmpty()) return null
        return pool.sortedWith(
            compareBy<Voice>(
                { guessFeminine(it.name)?.let { g -> if (g == feminine) 0 else 2 } ?: 1 },
                { if (it.isNetworkConnectionRequired) 1 else 0 },
                { -it.quality },
                { it.name },
            )
        ).firstOrNull()
    }

    /** Plays one utterance and suspends until it finished or was stopped. Returns true if fully played. */
    suspend fun play(u: Utterance): Boolean = mutex.withLock {
        val myGen = generation
        if (u.audioFile != null && File(u.audioFile).exists()) return@withLock playFile(u, myGen, isRecording = true)
        if (canUseNeural(u)) {
            // wait for the audio, but stay interruptible by stop() (tap-to-skip / new input)
            val d = ioScope.async { neuralFile(u) }
            val deadline = System.currentTimeMillis() + 26_000
            var f: File? = null
            while (generation == myGen && System.currentTimeMillis() < deadline) {
                if (d.isCompleted) { f = d.await(); break }
                delay(40)
            }
            if (generation != myGen) return@withLock false
            if (f != null) {
                val ok = playFile(u.copy(audioFile = f.absolutePath), myGen, isRecording = false)
                if (ok || generation != myGen) return@withLock ok
            }
        }
        if ((u.neuralVoice != null || u.edgeVoice != null) && (_mode.value.premium || !canUseNeural(u))) {
            _mode.value = VoiceMode(false, when {
                !naturalVoicesEnabled -> "Phone voice (natural voices are off)"
                _mode.value.engine == VoiceEngine.PHONE && _mode.value.reason != null -> _mode.value.reason
                else -> "Natural voice unavailable — basic phone voice"
            }, VoiceEngine.PHONE)
        }
        if (!_ready.value) {
            // wait up to 3s for the engine
            withTimeoutOrNull(3000) { while (!_ready.value) delay(50) }
            if (!_ready.value) return@withLock false
        }
        val (pitchMul, rateMul) = prosody(u.emotion)
        val voice = pickVoice(u.localeTag, u.feminine, u.voiceName)
        withContext(Dispatchers.Main) {
            if (voice != null) tts.voice = voice else tts.language = Locale.forLanguageTag(u.localeTag)
            tts.setPitch((u.pitch * pitchMul).coerceIn(0.5f, 2f))
            tts.setSpeechRate((u.rate * rateMul).coerceIn(0.5f, 2f))
        }
        _speaking.value = SpeakingState(u.speakerId, u.text)
        var ok = true
        try {
            val chunks = splitSentences(u.text)
            var offset = 0
            for ((i, chunk) in chunks.withIndex()) {
                if (generation != myGen) { ok = false; break }
                val start = u.text.indexOf(chunk, offset).let { if (it < 0) offset else it }
                chunkOffset = start
                val id = "u${ids.incrementAndGet()}"
                val done = CompletableDeferred<Boolean>()
                waiters[id] = done
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
                val res = tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, id)
                if (res != TextToSpeech.SUCCESS) { waiters.remove(id); ok = false; break }
                val est = 4000L + chunk.length * 160L
                val finished = withTimeoutOrNull(est) { done.await() } ?: false
                if (!finished) { ok = false; if (generation != myGen) break }
                offset = start + chunk.length
                _speaking.value = _speaking.value?.copy(highlightEnd = offset.coerceAtMost(u.text.length))
                if (i < chunks.lastIndex) delay(pauseAfter(chunk))
            }
        } finally {
            _speaking.value = null
        }
        ok
    }

    // ------------------------------------------------------------------ natural (neural) voices
    // Order: Kissa server (only when a backend URL is built in) -> Gemini TTS with the user's key (character lines,
    // while its free quota lasts) -> Microsoft neural (free, no key) -> phone TTS ("Basic voice").

    private fun engineChain(u: Utterance): List<VoiceEngine> = buildList {
        if (!naturalVoicesEnabled) return@buildList
        if (backend?.configured == true) { if (u.neuralVoice != null) add(VoiceEngine.SERVER) }
        else if (u.useGemini && geminiVoicesEnabled && u.neuralVoice != null && ai?.userKey() != null && geminiTts.available()) add(VoiceEngine.GEMINI)
        if (u.edgeVoice != null) add(VoiceEngine.MICROSOFT)
    }

    private fun canUseNeural(u: Utterance): Boolean = engineChain(u).isNotEmpty()

    private fun lineKey(u: Utterance) = sha1(listOf(u.emotion.name, u.delivery.orEmpty(), u.vocal.orEmpty(), u.lang, u.text).joinToString("|"))
    private fun voiceKey(u: Utterance, e: VoiceEngine) = when (e) {
        VoiceEngine.MICROSOFT -> u.edgeVoice?.let { "${it.id}${it.pitchHz}${it.ratePct}" }.orEmpty()
        else -> u.neuralVoice.orEmpty()
    }
    private fun sha1(raw: String) = java.security.MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun cacheFile(u: Utterance, e: VoiceEngine, ext: String) = File(cacheDir, "${e.name.lowercase()}_${sha1(voiceKey(u, e) + "|" + lineKey(u))}.$ext")

    /** Already-generated audio for this line (any engine), so replays sound exactly like the first time. */
    private fun cached(u: Utterance): Pair<File, VoiceEngine>? {
        for (e in listOf(VoiceEngine.SERVER, VoiceEngine.GEMINI, VoiceEngine.MICROSOFT)) {
            if (e == VoiceEngine.MICROSOFT && u.edgeVoice == null) continue
            if (e != VoiceEngine.MICROSOFT && u.neuralVoice == null) continue
            for (ext in listOf("wav", "mp3")) {
                val f = cacheFile(u, e, ext)
                if (f.exists() && f.length() > 64) { f.setLastModified(System.currentTimeMillis()); return f to e }
            }
        }
        return null
    }

    /** Fetches (or reuses the cached) natural-voice audio for a line. Null when no natural engine can voice it right now. */
    suspend fun neuralFile(u: Utterance): File? {
        if (!naturalVoicesEnabled) return null
        cached(u)?.let { (f, e) -> _mode.value = VoiceMode(true, null, e); return f }
        val chain = engineChain(u)
        if (chain.isEmpty()) return null
        val key = (voiceKey(u, chain.first()) + lineKey(u))
        val job = inflight.getOrPut(key) {
            ioScope.async {
                try { synthChain(u, chain) } finally { inflight.remove(key) }
            }
        }
        return job.await()
    }

    private suspend fun synthChain(u: Utterance, chain: List<VoiceEngine>): File? {
        var geminiNote: String? = null
        var lastErr: String? = null
        for (e in chain) {
            try {
                val f: File = when (e) {
                    VoiceEngine.SERVER -> {
                        val bytes = backend!!.tts(TtsRequest(u.text, u.neuralVoice!!, u.emotion.name, u.lang, u.delivery, u.vocal))
                        writeAtomic(cacheFile(u, e, "wav"), bytes)
                    }
                    VoiceEngine.GEMINI -> {
                        val key = ai?.userKey() ?: continue
                        val bytes = geminiTts.synthesize(key, u.text, u.neuralVoice!!, u.emotion.name, u.lang, u.delivery, u.vocal)
                        writeAtomic(cacheFile(u, e, "wav"), bytes)
                    }
                    VoiceEngine.MICROSOFT -> {
                        val ssml = com.kissa.app.voice.engines.EdgeTts.ssml(u.edgeVoice!!, u.text, u.emotion, u.delivery, u.vocal)
                        val mp3 = writeAtomic(cacheFile(u, e, "mp3"), edgeTts.synthesize(ssml))
                        _edgeReachable.value = true
                        val wav = cacheFile(u, e, "wav")
                        if (com.kissa.app.voice.engines.Mp3Decoder.toWav(mp3, wav)) { mp3.delete(); wav } else mp3
                    }
                    VoiceEngine.PHONE -> continue
                }
                _mode.value = VoiceMode(premium = true, reason = if (e == VoiceEngine.MICROSOFT) geminiNote else null, engine = e)
                ai?.reportVoiceSuccess()
                trimCache()
                return f
            } catch (ce: CancellationException) { throw ce
            } catch (ex: AiException) {
                lastErr = when (ex.kind) {
                    AiException.Kind.NETWORK, AiException.Kind.TIMEOUT -> "Offline — basic phone voice"
                    AiException.Kind.QUOTA, AiException.Kind.SERVICE_LIMIT -> "Voice limit reached — basic phone voice"
                    AiException.Kind.SERVICE_SETUP -> "Natural voices not set up — basic phone voice"
                    else -> "Natural voice unavailable — basic phone voice"
                }
                if (e == VoiceEngine.GEMINI) geminiNote = when (ex.kind) {
                    AiException.Kind.QUOTA -> "Gemini's free voice quota is used up for now — using the Microsoft neural voice"
                    AiException.Kind.AUTH -> "Gemini key rejected for voice — using the Microsoft neural voice"
                    else -> "Gemini voice unavailable — using the Microsoft neural voice"
                }
            } catch (ex: Exception) {
                if (e == VoiceEngine.MICROSOFT) _edgeReachable.value = false
                lastErr = if (e == VoiceEngine.MICROSOFT) "Microsoft voice unreachable (offline?) — basic phone voice" else "Natural voice unavailable — basic phone voice"
            }
        }
        _mode.value = VoiceMode(premium = false, reason = lastErr ?: "Natural voice unavailable — basic phone voice", engine = VoiceEngine.PHONE)
        ai?.reportVoiceFailure()
        return null
    }

    private fun writeAtomic(f: File, bytes: ByteArray): File {
        val tmp = File(cacheDir, f.name + ".tmp"); tmp.writeBytes(bytes); tmp.renameTo(f); return f
    }

    /** Quick reachability check of the free Microsoft voice (shown honestly in Settings). */
    fun checkMicrosoftVoice() {
        ioScope.launch {
            _edgeReachable.value = runCatching {
                edgeTts.synthesize(com.kissa.app.voice.engines.EdgeTts.ssml(com.kissa.app.voice.engines.EdgeTts.PLAYER_CHOICES.first(), "Hi"), 12_000).size > 200
            }.getOrDefault(false)
        }
    }

    /** Starts generating a line's audio early (e.g. while the player's own line is still playing). */
    fun prefetch(u: Utterance) {
        if (!canUseNeural(u)) return
        ioScope.launch { runCatching { neuralFile(u) } }
    }

    private fun trimCache() {
        val files = cacheDir.listFiles()?.filter { it.name.endsWith(".wav") || it.name.endsWith(".mp3") } ?: return
        var total = files.sumOf { it.length() }
        if (total < 60L * 1024 * 1024) return
        for (f in files.sortedBy { it.lastModified() }) { total -= f.length(); f.delete(); if (total < 40L * 1024 * 1024) break }
    }

    fun clearVoiceCache() { cacheDir.listFiles()?.forEach { it.delete() } }

    fun noteBasic(reason: String) { _mode.value = VoiceMode(false, reason) }

    private suspend fun playFile(u: Utterance, myGen: Long, isRecording: Boolean): Boolean {
        val done = CompletableDeferred<Boolean>()
        val env = runCatching { WavUtil.envelope(File(u.audioFile!!)) }.getOrNull()
        var ticker: Job? = null
        withContext(Dispatchers.Main) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(u.audioFile)
                mp.setVolume(volume, volume)
                mp.setOnCompletionListener { done.complete(true) }
                mp.setOnErrorListener { _, _, _ -> done.complete(false); true }
                mp.prepare()
                player = mp; playerWaiter = done
                val dur = mp.duration.coerceAtLeast(1)
                _speaking.value = SpeakingState(u.speakerId, u.text, highlightEnd = if (isRecording) u.text.length else 0, envelope = env,
                    isRecording = isRecording, hasWordEvents = !isRecording && env != null)
                mp.start()
                if (!isRecording) { ticker = ioScope.launch {
                    // karaoke-style subtitle progress from the real playback position
                    while (isActive) {
                        val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                        val s = _speaking.value ?: break
                        _speaking.value = s.copy(highlightEnd = (u.text.length * pos / dur.toFloat() * 1.08f).toInt().coerceAtMost(u.text.length))
                        delay(90)
                    }
                } }
                Unit
            } catch (e: Exception) { done.complete(false); Unit }
        }
        val ok = withTimeoutOrNull(120_000) { done.await() } ?: false
        ticker?.cancel()
        withContext(Dispatchers.Main) {
            runCatching { player?.release() }; player = null; playerWaiter = null
            _speaking.value = null
        }
        return ok && generation == myGen
    }

    /** Stop everything that's playing or queued (interruptions, leaving the scene, user pressed stop). */
    fun stop() {
        generation++
        runCatching { tts.stop() }
        waiters.values.forEach { it.complete(false) }; waiters.clear()
        runCatching { player?.stop() }
        playerWaiter?.complete(false)
        _speaking.value = null
    }

    fun shutdown() { stop(); runCatching { tts.shutdown() } }

    companion object {
        fun guessFeminine(name: String): Boolean? {
            val n = name.lowercase()
            if (n.contains("female") || n.contains("#f") || n.contains("smtf")) return true
            if (Regex("(^|[^e])male").containsMatchIn(n) || n.contains("smtm")) return false
            Regex("-x-([a-z]{3})").find(n)?.groupValues?.get(1)?.let { code ->
                return when (code) {
                    "ena", "enc", "hia", "hic", "sfg", "iob", "tpc", "cxx", "ena-local" -> true
                    "end", "ene", "hid", "hie", "iom", "tpd", "tpf", "iol" -> false
                    else -> null
                }
            }
            return null
        }

        fun prosody(e: Emotion): Pair<Float, Float> = when (e) {
            Emotion.HAPPY -> 1.06f to 1.05f
            Emotion.SAD -> 0.92f to 0.88f
            Emotion.ANGRY -> 0.96f to 1.08f
            Emotion.SURPRISED -> 1.12f to 1.06f
            Emotion.SHY -> 1.04f to 0.94f
            Emotion.CONFUSED -> 1.0f to 0.93f
            Emotion.AMUSED -> 1.05f to 1.02f
            Emotion.NEUTRAL -> 1f to 1f
        }

        fun splitSentences(text: String): List<String> {
            val parts = Regex("(?<=[.!?।…])\\s+").split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            // merge very short fragments so pauses don't feel choppy
            val out = mutableListOf<String>()
            for (p in parts) if (out.isNotEmpty() && (out.last().length < 18 || p.length < 6)) out[out.size - 1] = out.last() + " " + p else out += p
            return out.ifEmpty { listOf(text) }
        }

        fun pauseAfter(chunk: String): Long = when {
            chunk.endsWith("…") || chunk.endsWith("...") -> 420
            chunk.endsWith("?") -> 260
            chunk.endsWith("!") -> 220
            else -> 180
        }

        fun hasDevanagari(text: String) = text.any { it in '\u0900'..'\u097F' }
    }
}
