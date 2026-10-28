package com.kissa.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
)

/**
 * Single audio queue for the whole app. TTS lines and recorded voice clips are played strictly one at a
 * time (Mutex), so speech never overlaps; stop() cancels whatever is playing and releases the waiter.
 */
class SpeechDirector(private val ctx: Context) {
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
        if (u.audioFile != null && File(u.audioFile).exists()) return@withLock playFile(u, myGen)
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

    private suspend fun playFile(u: Utterance, myGen: Long): Boolean {
        val done = CompletableDeferred<Boolean>()
        val env = runCatching { WavUtil.envelope(File(u.audioFile!!)) }.getOrNull()
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
                _speaking.value = SpeakingState(u.speakerId, u.text, highlightEnd = u.text.length, envelope = env, isRecording = true)
                mp.start()
            } catch (e: Exception) { done.complete(false) }
        }
        val ok = withTimeoutOrNull(120_000) { done.await() } ?: false
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
