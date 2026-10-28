package com.kissa.app.ai

import com.kissa.app.BuildConfig
import com.kissa.app.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/** What the app honestly knows about its AI services. Shown as a friendly status, never as API settings. */
data class SetupState(
    val phase: Phase = Phase.CHECKING,
    val chat: Boolean = false,
    val voice: Boolean = false,
    val listening: Boolean = false,
    val message: String = "Getting your stories ready…",
    val checkedAt: Long = 0L,
) {
    enum class Phase { CHECKING, READY, PARTIAL, OFFLINE, NOT_SET_UP, DEV_OVERRIDE }
    val aiAvailable get() = chat
    val premiumVoice get() = voice
}

/**
 * Single entry point for AI text generation + the automatic first-launch setup (anonymous device registration and
 * a real health check of chat, voice and transcription on the backend). Release builds only ever use the backend.
 */
class AiService(
    private val backend: BackendClient,
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()
    private var setupJob: Job? = null

    val backendClient: BackendClient get() = backend

    private fun devKey(): String? = if (BuildConfig.DEBUG) settings.devApiKey() else null

    /** Runs in the background on app start and whenever the user taps "try again". Safe to call repeatedly. */
    fun runSetup(force: Boolean = false) {
        if (setupJob?.isActive == true && !force) return
        setupJob?.cancel()
        setupJob = scope.launch {
            if (devKey() != null) {
                _setup.value = SetupState(SetupState.Phase.DEV_OVERRIDE, chat = true, voice = false, listening = false,
                    message = "Developer key active (debug build) — premium voice needs the backend", checkedAt = now())
                return@launch
            }
            if (!backend.configured) {
                _setup.value = SetupState(SetupState.Phase.NOT_SET_UP, message = "AI service abhi setup nahi hai — stories will come alive once it's switched on.", checkedAt = now())
                return@launch
            }
            _setup.value = _setup.value.copy(phase = SetupState.Phase.CHECKING, message = "Connecting to Kissa…")
            var attempt = 0
            while (isActive) {
                try {
                    backend.ensureToken()
                    _setup.value = _setup.value.copy(message = "Checking voices and story AI…")
                    val h = backend.health()
                    _setup.value = stateFrom(h)
                    return@launch
                } catch (e: AiException) {
                    val offline = e.kind == AiException.Kind.NETWORK || e.kind == AiException.Kind.TIMEOUT
                    if (e.kind == AiException.Kind.SERVICE_SETUP) {
                        _setup.value = SetupState(SetupState.Phase.NOT_SET_UP, message = "AI service abhi setup nahi hai — we're working on it. Try again later.", checkedAt = now())
                        return@launch
                    }
                    _setup.value = SetupState(SetupState.Phase.OFFLINE,
                        message = if (offline) "Aap offline lag rahe ho — AI service tak nahi pahunch paaye." else "AI service abhi busy hai. Thodi der mein phir try karenge.",
                        checkedAt = now())
                    // gentle automatic retries for temporary failures: 5s, 15s, 45s
                    if (attempt >= 3) return@launch
                    delay(5000L * Math.pow(3.0, attempt.toDouble()).toLong())
                    attempt++
                }
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    fun stateFrom(h: HealthReport): SetupState = Companion.stateFrom(h)

    companion object {
    fun stateFrom(h: HealthReport, now: Long = System.currentTimeMillis()): SetupState {
        val phase = when {
            !h.configured -> SetupState.Phase.NOT_SET_UP
            h.chat.ok && h.tts.ok && h.stt.ok -> SetupState.Phase.READY
            h.chat.ok || h.tts.ok -> SetupState.Phase.PARTIAL
            else -> SetupState.Phase.OFFLINE
        }
        val msg = when (phase) {
            SetupState.Phase.READY -> "All set — natural voices and story AI are ready."
            SetupState.Phase.PARTIAL -> buildString {
                append(if (h.chat.ok) "Story AI ready" else "Story AI is unavailable right now")
                append(" · ")
                append(if (h.tts.ok) "natural voices ready" else "using basic phone voice for now")
            }
            SetupState.Phase.NOT_SET_UP -> "AI service abhi setup nahi hai — stories will come alive once it's switched on."
            else -> if (listOf(h.chat.error, h.tts.error).any { it == "UPSTREAM_QUOTA" }) "AI service ki daily limit khatam ho gayi hai. Kal phir try karein." else "AI service abhi offline hai. Thodi der mein phir try karein."
        }
        return SetupState(phase, chat = h.chat.ok, voice = h.tts.ok, listening = h.stt.ok, message = msg, checkedAt = now)
    }
    }

    /** Marks a service as failing after a real request failed (so the UI stops claiming premium voice). */
    fun reportVoiceFailure() { _setup.value = _setup.value.copy(voice = false, phase = if (_setup.value.chat) SetupState.Phase.PARTIAL else _setup.value.phase) }
    fun reportVoiceSuccess() { if (!_setup.value.voice && backend.configured) _setup.value = _setup.value.copy(voice = true) }
    fun reportChatSuccess() { if (!_setup.value.chat) _setup.value = _setup.value.copy(chat = true, phase = if (_setup.value.voice) SetupState.Phase.READY else SetupState.Phase.PARTIAL) }

    /** Generate text for a prompt. Returns raw model text; callers validate (AiParser) before use. */
    suspend fun generate(payload: PromptPayload, schema: JsonObject?, temperature: Double = 0.9, maxTokens: Int = 2048): String {
        devKey()?.let { key -> return gemini.generate(key, null, GeminiClient.DEFAULT_MODEL, payload, schema, temperature, maxTokens) }
        if (!backend.configured) throw AiException("AI service not set up", AiException.Kind.SERVICE_SETUP, false)
        var last: AiException? = null
        repeat(2) { attempt ->
            try {
                val text = backend.chat(gemini.buildBody(payload, schema, temperature, maxTokens))
                reportChatSuccess()
                return text
            } catch (e: AiException) {
                last = e
                if (!e.retryable || e.kind == AiException.Kind.QUOTA || e.kind == AiException.Kind.SERVICE_LIMIT) throw e
                delay(900L * (attempt + 1))
            }
        }
        throw last ?: AiException("AI unavailable", AiException.Kind.SERVER, true)
    }
}
