package com.kissa.app.ai

import com.kissa.app.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/** What the app honestly knows about its AI services. */
data class SetupState(
    val phase: Phase = Phase.CHECKING,
    val chat: Boolean = false,
    val voice: Boolean = false,
    val listening: Boolean = false,
    val message: String = "Getting your stories ready…",
    val checkedAt: Long = 0L,
    val mode: Mode = Mode.DIRECT,
    val keySet: Boolean = false,
    val model: String? = null,
) {
    /** NEEDS_KEY / KEY_INVALID: direct-key mode. NOT_SET_UP: optional backend mode without a deployed backend. */
    enum class Phase { CHECKING, READY, PARTIAL, OFFLINE, NOT_SET_UP, NEEDS_KEY, KEY_INVALID }
    enum class Mode { DIRECT, BACKEND }
    val aiAvailable get() = chat
    val premiumVoice get() = voice
}

/**
 * Single entry point for AI text generation + setup/health checks.
 * Default = direct-key mode: the user pastes their own Gemini API key (Onboarding / Settings, with "Test connection"),
 * it is kept in EncryptedSharedPreferences and the app calls Gemini directly. Optional backend mode: when the build sets
 * -PkissaBackendUrl, all AI calls go through the Kissa backend instead (anonymous device token, server-held key).
 */
class AiService(
    private val backend: BackendClient,
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _setup = MutableStateFlow(SetupState(mode = if (backend.configured) SetupState.Mode.BACKEND else SetupState.Mode.DIRECT))
    val setup: StateFlow<SetupState> = _setup.asStateFlow()
    private var setupJob: Job? = null

    val backendClient: BackendClient get() = backend
    val geminiClient: GeminiClient get() = gemini
    val backendMode: Boolean get() = backend.configured

    /** The user's key in direct mode (null in backend mode or when none is saved). */
    fun userKey(): String? = if (backend.configured) null else settings.apiKey()

    /** "Test connection" for a key the user just typed (not saved yet). Returns the model that answered. */
    suspend fun testKey(key: String): Result<String> = runCatching { gemini.ping(key.trim()) }

    /** Runs in the background on app start, after a key change and whenever the user taps "try again". */
    fun runSetup(force: Boolean = false) {
        if (setupJob?.isActive == true && !force) return
        setupJob?.cancel()
        setupJob = scope.launch { if (backend.configured) runBackendSetup() else runDirectSetup() }
    }

    private suspend fun runDirectSetup() {
        val key = settings.apiKey()
        if (key == null) {
            _setup.value = SetupState(SetupState.Phase.NEEDS_KEY, chat = false, voice = true, listening = true, mode = SetupState.Mode.DIRECT,
                message = "Add your Gemini API key to bring the characters to life.", checkedAt = now())
            return
        }
        _setup.value = SetupState(SetupState.Phase.CHECKING, voice = true, listening = true, mode = SetupState.Mode.DIRECT, keySet = true, message = "Checking your Gemini key…")
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                val model = gemini.ping(key)
                _setup.value = SetupState(SetupState.Phase.READY, chat = true, voice = true, listening = true, mode = SetupState.Mode.DIRECT, keySet = true, model = model,
                    message = "All set — story AI is ready with your Gemini key.", checkedAt = now())
                return
            } catch (e: AiException) {
                when (e.kind) {
                    AiException.Kind.AUTH, AiException.Kind.NO_KEY -> {
                        _setup.value = SetupState(SetupState.Phase.KEY_INVALID, voice = true, listening = true, mode = SetupState.Mode.DIRECT, keySet = true,
                            message = "Your Gemini key was rejected. Paste a valid key from aistudio.google.com/apikey.", checkedAt = now())
                        return
                    }
                    AiException.Kind.QUOTA -> {
                        _setup.value = SetupState(SetupState.Phase.PARTIAL, chat = false, voice = true, listening = true, mode = SetupState.Mode.DIRECT, keySet = true,
                            message = "Your key works, but its free Gemini limit is used up for now. Try again later (daily limits reset at 1:30 pm IST).", checkedAt = now())
                        return
                    }
                    else -> {
                        val offline = e.kind == AiException.Kind.NETWORK || e.kind == AiException.Kind.TIMEOUT
                        _setup.value = SetupState(SetupState.Phase.OFFLINE, voice = false, listening = true, mode = SetupState.Mode.DIRECT, keySet = true,
                            message = if (offline) "Aap offline lag rahe ho — Gemini tak nahi pahunch paaye." else "Gemini abhi busy hai. Thodi der mein phir try karenge.", checkedAt = now())
                        if (attempt >= 3) return
                        delay(5000L * Math.pow(3.0, attempt.toDouble()).toLong())
                        attempt++
                    }
                }
            }
        }
    }

    private suspend fun runBackendSetup() {
        _setup.value = _setup.value.copy(phase = SetupState.Phase.CHECKING, mode = SetupState.Mode.BACKEND, message = "Connecting to Kissa…")
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                backend.ensureToken()
                _setup.value = _setup.value.copy(message = "Checking voices and story AI…")
                val h = backend.health()
                _setup.value = stateFrom(h)
                return
            } catch (e: AiException) {
                val offline = e.kind == AiException.Kind.NETWORK || e.kind == AiException.Kind.TIMEOUT
                if (e.kind == AiException.Kind.SERVICE_SETUP) {
                    _setup.value = SetupState(SetupState.Phase.NOT_SET_UP, mode = SetupState.Mode.BACKEND, message = "The Kissa server isn't reachable or isn't configured yet.", checkedAt = now())
                    return
                }
                _setup.value = SetupState(SetupState.Phase.OFFLINE, mode = SetupState.Mode.BACKEND,
                    message = if (offline) "Aap offline lag rahe ho — AI service tak nahi pahunch paaye." else "AI service abhi busy hai. Thodi der mein phir try karenge.",
                    checkedAt = now())
                if (attempt >= 3) return
                delay(5000L * Math.pow(3.0, attempt.toDouble()).toLong())
                attempt++
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
            SetupState.Phase.NOT_SET_UP -> "The Kissa server isn't configured yet."
            else -> if (listOf(h.chat.error, h.tts.error).any { it == "UPSTREAM_QUOTA" }) "AI service ki daily limit khatam ho gayi hai. Kal phir try karein." else "AI service abhi offline hai. Thodi der mein phir try karein."
        }
        return SetupState(phase, chat = h.chat.ok, voice = h.tts.ok, listening = h.stt.ok, message = msg, checkedAt = now, mode = SetupState.Mode.BACKEND)
    }
    }

    /** Marks a service as failing after a real request failed (so the UI stops claiming premium voice). */
    fun reportVoiceFailure() { _setup.value = _setup.value.copy(voice = false, phase = if (_setup.value.chat) SetupState.Phase.PARTIAL else _setup.value.phase) }
    fun reportVoiceSuccess() { if (!_setup.value.voice) _setup.value = _setup.value.copy(voice = true) }
    fun reportChatSuccess() { if (!_setup.value.chat) _setup.value = _setup.value.copy(chat = true, phase = if (_setup.value.voice) SetupState.Phase.READY else SetupState.Phase.PARTIAL) }

    /** Generate text for a prompt. Returns raw model text; callers validate (AiParser) before use. */
    suspend fun generate(payload: PromptPayload, schema: JsonObject?, temperature: Double = 0.9, maxTokens: Int = 2048): String {
        if (!backend.configured) {
            val key = settings.apiKey() ?: throw AiException("No Gemini API key", AiException.Kind.NO_KEY, false)
            val text = gemini.generateDirect(key, payload, schema, temperature, maxTokens)
            reportChatSuccess()
            return text
        }
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
