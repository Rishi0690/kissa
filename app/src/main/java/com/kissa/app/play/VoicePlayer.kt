package com.kissa.app.play

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.kissa.app.data.GameEntry
import com.kissa.app.data.GameRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Plays a line's pre-generated voice file from the game pack (spec §3). One line at a time.
 * [amplitude] returns the current lip-sync level from the pack's envelope. When a file is missing (e.g. a line that
 * contains the player's name) it falls back to the phone's TTS — the emergency path — and reports it via [onFallback].
 */
class VoicePlayer(private val ctx: Context, private val repo: GameRepository) {
    private var player: MediaPlayer? = null
    private var doneRef: CompletableDeferred<Unit>? = null
    private var envStart = 0L
    private var env: com.kissa.app.data.VoiceEnv? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = CompletableDeferred<Boolean>()
    private var ttsSpeaking = false
    var volume = 1f
    var onFallback: ((String) -> Unit)? = null

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) tts?.language = Locale("hi", "IN")
            ttsReady.complete(ok)
        }
    }

    fun amplitude(): Float {
        val p = player
        if (p != null) return runCatching { if (p.isPlaying) env?.at(p.currentPosition / 1000f) ?: 0.5f else 0f }.getOrDefault(0f)
        if (ttsSpeaking) { val t = (System.currentTimeMillis() - envStart) / 1000f; return (0.35f + 0.35f * kotlin.math.sin(t * 22f)).coerceAtLeast(0f) }
        return 0f
    }

    /** Suspends until the line finished (or [stop] was called). Returns false when the emergency TTS fallback was used. */
    suspend fun play(entry: GameEntry, voiceFile: String?, text: String): Boolean {
        stop()
        if (voiceFile != null && repo.audioExists(entry, voiceFile)) {
            val done = CompletableDeferred<Unit>(); doneRef = done
            val ok = withContext(Dispatchers.Main) {
                runCatching {
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    val f = repo.file(entry, voiceFile)
                    if (f != null) mp.setDataSource(f.absolutePath) else repo.assetFd(entry, voiceFile)!!.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    mp.setVolume(volume, volume)
                    mp.setOnCompletionListener { done.complete(Unit) }
                    mp.setOnErrorListener { _, _, _ -> done.complete(Unit); true }
                    mp.prepare(); mp.start()
                    env = entry.env[voiceFile]; envStart = System.currentTimeMillis(); player = mp
                    true
                }.getOrElse { Log.w("Kissa", "voice play failed $voiceFile: $it"); false }
            }
            if (ok) { done.await(); withContext(Dispatchers.Main) { player?.release(); player = null }; return true }
        }
        // emergency fallback: phone TTS (flagged)
        onFallback?.invoke(voiceFile ?: "(no voice file)")
        withContext(Dispatchers.Main) { ensureTts() }
        if (!ttsReady.await()) return false
        suspendCancellableCoroutine<Unit> { c ->
            val id = "l${System.nanoTime()}"
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { ttsSpeaking = true; envStart = System.currentTimeMillis() }
                override fun onDone(utteranceId: String?) { ttsSpeaking = false; if (c.isActive) c.resume(Unit) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { ttsSpeaking = false; if (c.isActive) c.resume(Unit) }
            })
            val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            c.invokeOnCancellation { tts?.stop(); ttsSpeaking = false }
        }
        return false
    }

    fun stop() {
        doneRef?.complete(Unit); doneRef = null
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        if (ttsSpeaking) { tts?.stop(); ttsSpeaking = false }
    }

    fun release() { stop(); tts?.shutdown(); tts = null; ttsReady = CompletableDeferred() }
}
