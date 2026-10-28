package com.kissa.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

sealed class VoiceInputState {
    object Idle : VoiceInputState()
    data class Listening(val partial: String, val level: Float, val keepingAudio: Boolean, val startedAt: Long) : VoiceInputState()
    object Processing : VoiceInputState()
    data class Result(val text: String, val audioPath: String?) : VoiceInputState()
    data class Error(val message: String, val canRetry: Boolean = true) : VoiceInputState()
}

/**
 * Speech-to-text with the platform SpeechRecognizer. On Android 13+ we capture the microphone ourselves with
 * AudioRecord and feed the recognizer through EXTRA_AUDIO_SOURCE, so the same audio is also saved as a WAV —
 * that's how the player's real voice can be played back. On older devices (or if the recognizer refuses an
 * external audio source) we fall back to plain recognition and the player's line is voiced with TTS instead.
 * Must be used from the main thread.
 */
class VoiceInput(private val ctx: Context, private val audioDir: File) {
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var recordThread: Thread? = null
    @Volatile private var recording = false
    private var pcm = ByteArrayOutputStream()
    private var pipeWrite: ParcelFileDescriptor? = null
    private var pipeRead: ParcelFileDescriptor? = null
    private var usingPipe = false
    private var pipeFailedOnce = false
    private var lastLang = "hi-IN"
    private var startedAt = 0L
    private var gotResult = false
    private val main = Handler(Looper.getMainLooper())
    private var heardSpeech = false
    private var silenceSince = 0L

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun reset() { if (_state.value !is VoiceInputState.Listening) _state.value = VoiceInputState.Idle }

    fun start(langTag: String, keepAudio: Boolean) {
        cancel()
        if (!available) { _state.value = VoiceInputState.Error("Speech recognition isn't available on this device. Install or enable Google speech services.", false); return }
        lastLang = langTag
        gotResult = false; heardSpeech = false; silenceSince = 0L
        startedAt = System.currentTimeMillis()
        val pipeMode = keepAudio && Build.VERSION.SDK_INT >= 33 && !pipeFailedOnce
        usingPipe = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
        }
        if (pipeMode && startCapture()) {
            usingPipe = true
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipeRead)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(listener)
        _state.value = VoiceInputState.Listening("", 0f, usingPipe, startedAt)
        try { r.startListening(intent) } catch (e: Exception) {
            stopCapture(save = false)
            _state.value = VoiceInputState.Error("Couldn't start the microphone: ${e.message}")
        }
    }

    /** User pressed "done": finish and let the recognizer return what it heard. */
    fun finish() {
        if (_state.value !is VoiceInputState.Listening) return
        _state.value = VoiceInputState.Processing
        if (usingPipe) closePipe() // end of stream tells the recognizer we're done
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        stopCapture(save = false)
        if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.Processing) _state.value = VoiceInputState.Idle
    }

    fun consume() { _state.value = VoiceInputState.Idle }

    @SuppressLint("MissingPermission")
    private fun startCapture(): Boolean {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096) * 2)
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
            val pipe = ParcelFileDescriptor.createPipe()
            pipeRead = pipe[0]; pipeWrite = pipe[1]
            pcm = ByteArrayOutputStream()
            recording = true
            rec.startRecording()
            val out = FileOutputStream(pipeWrite!!.fileDescriptor)
            recordThread = Thread {
                val buf = ByteArray(2048)
                var pipeOpen = true
                try {
                    while (recording) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        synchronized(this) { pcm.write(buf, 0, n) }
                        if (pipeOpen) try { out.write(buf, 0, n) } catch (e: Exception) { pipeOpen = false }
                        val lvl = rms(buf, n)
                        main.post { onLevel(lvl) }
                        if (pcm.size() > SAMPLE_RATE * 2 * 60) { main.post { finish() }; break } // 60 s cap
                    }
                } finally {
                    runCatching { rec.stop() }; runCatching { rec.release() }
                    runCatching { out.close() }
                }
            }.apply { name = "kissa-mic"; start() }
            true
        } catch (e: Exception) { false }
    }

    private fun onLevel(level: Float) {
        val s = _state.value as? VoiceInputState.Listening ?: return
        _state.value = s.copy(level = level)
        // simple voice-activity detection: after speech, 1.8 s of quiet ends the turn
        val now = System.currentTimeMillis()
        if (level > 0.08f) { heardSpeech = true; silenceSince = 0L }
        else if (heardSpeech) {
            if (silenceSince == 0L) silenceSince = now
            else if (now - silenceSince > 1800) finish()
        }
    }

    private fun closePipe() {
        runCatching { pipeWrite?.close() }; pipeWrite = null
    }

    private fun stopCapture(save: Boolean): String? {
        recording = false
        runCatching { recordThread?.join(400) }
        recordThread = null
        closePipe(); runCatching { pipeRead?.close() }; pipeRead = null
        if (!save) return null
        val bytes = synchronized(this) { pcm.toByteArray() }
        if (bytes.size < SAMPLE_RATE / 2) return null // < 0.25 s
        return runCatching {
            val f = File(audioDir, "rec_${System.currentTimeMillis()}.wav")
            WavUtil.write(f, bytes, SAMPLE_RATE); f.absolutePath
        }.getOrNull()
    }

    private fun rms(buf: ByteArray, n: Int): Float {
        var sum = 0.0; var cnt = 0
        var i = 0
        while (i + 1 < n) {
            val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort() / 32768.0
            sum += v * v; cnt++; i += 2
        }
        return if (cnt == 0) 0f else (sqrt(sum / cnt) * 6).toFloat().coerceIn(0f, 1f)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { heardSpeech = true }
        override fun onRmsChanged(rmsdB: Float) {
            if (usingPipe) return
            val lvl = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            (_state.value as? VoiceInputState.Listening)?.let { _state.value = it.copy(level = lvl) }
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (_state.value is VoiceInputState.Listening) _state.value = VoiceInputState.Processing
            if (usingPipe) closePipe()
        }
        override fun onError(error: Int) {
            val path = stopCapture(save = false)
            runCatching { recognizer?.destroy() }; recognizer = null
            if (gotResult) return
            // The recogniser may not accept an external audio source: retry once in plain mode.
            if (usingPipe && System.currentTimeMillis() - startedAt < 1500 &&
                error in listOf(SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, 13 /*CANNOT_LISTEN_TO_DOWNLOAD_EVENTS*/, 14)) {
                pipeFailedOnce = true
                main.post { start(lastLang, keepAudio = false) }
                return
            }
            _state.value = VoiceInputState.Error(message(error), error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            path?.let { File(it).delete() }
        }
        override fun onResults(results: Bundle?) {
            gotResult = true
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            val path = stopCapture(save = usingPipe && text.isNotEmpty())
            runCatching { recognizer?.destroy() }; recognizer = null
            _state.value = if (text.isEmpty()) VoiceInputState.Error("I didn't catch that. Try again?") else VoiceInputState.Result(text, path)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            (_state.value as? VoiceInputState.Listening)?.let { if (text.isNotBlank()) _state.value = it.copy(partial = text) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun message(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Try again?"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a connection right now."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy. Try again in a second."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language isn't available for speech recognition on your device."
        else -> "Voice input failed (code $code)."
    }

    companion object { const val SAMPLE_RATE = 16000 }
}
