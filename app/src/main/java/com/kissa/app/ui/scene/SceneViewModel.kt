package com.kissa.app.ui.scene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kissa.app.KissaApp
import com.kissa.app.ai.*
import com.kissa.app.data.MessageEntity
import com.kissa.app.data.MemoryNoteEntity
import com.kissa.app.data.PlaythroughEntity
import com.kissa.app.data.ReportEntity
import com.kissa.app.model.*
import com.kissa.app.scene.SceneEngine
import com.kissa.app.voice.Utterance
import com.kissa.app.voice.SpeechDirector
import com.kissa.app.voice.VoiceInput
import com.kissa.app.voice.VoiceInputState
import com.kissa.app.voice.NeuralVoices
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Phase { LOADING, INTRO, IDLE, PLAYER_SPEAKING, THINKING, CHARACTER_SPEAKING, REPLAY }

data class Line(val speaker: String, val text: String, val action: String? = null, val isPlayer: Boolean = false, val messageId: Long = 0)

data class SceneUi(
    val story: Story? = null,
    val playerName: String = "",
    val phase: Phase = Phase.LOADING,
    val mood: Int = 50,
    val relationship: Int = 30,
    val chapter: Int = 0,
    val line: Line? = null,
    val suggestions: List<String> = emptyList(),
    val error: String? = null,
    val errorNeedsKey: Boolean = false,
    val canRetry: Boolean = false,
    val banner: String? = null,
    val ending: String? = null,
    val ended: Boolean = false,
    val language: Lang = Lang.HINGLISH,
    val continuousVoice: Boolean = false,
    val toast: String? = null,
    val lastNote: String? = null,
    val turnCount: Int = 0,
    /** Numbered choice cards over the dimmed scene (optional — free text / mic stay available). */
    val showChoices: Boolean = false,
    /** The last turn went badly (big relationship drop / she walked off): show the red X + rewind offer. */
    val badTurn: Boolean = false,
    val canRewind: Boolean = false,
    /** Set while the VHS rewind plays. */
    val rewinding: Boolean = false,
    /** Rank label that was just earned (badge glow + toast). */
    val rankUp: String? = null,
    val errorIsSetup: Boolean = false,
)

/** Relationship rank shown in the small glass badge (like the reference's "PLAYER NOOB -> PLAYER PRO"). */
fun rankFor(relationship: Int): String = when {
    relationship < 30 -> "NOOB"
    relationship < 50 -> "ROOKIE"
    relationship < 70 -> "PRO"
    relationship < 88 -> "LEGEND"
    else -> "SOULMATE"
}

private data class TurnSnapshot(
    val pt: PlaythroughEntity,
    val cutoffMessageId: Long,
    val line: Line?,
    val suggestions: List<String>,
    val noteIds: MutableList<Long> = mutableListOf(),
    val npcEmotion: Emotion,
)

class SceneViewModel(private val storyId: String, private val replayCheckpoint: Long?) : ViewModel() {
    private val app = KissaApp.instance
    private val repo = app.repo
    private val dao = repo.dao
    private val settings = app.settings
    val speech: SpeechDirector = app.speech
    val voice = VoiceInput(app, repo.audioDir(),
        serverStt = if (app.backend.configured) { file, _ -> app.backend.stt(file.readBytes(), NeuralVoices.langCode(_ui.value.language)) }
            else { file, _ -> app.ai.userKey()?.let { k -> app.gemini.transcribe(k, file.readBytes(), NeuralVoices.langCode(_ui.value.language)) } },
        scope = viewModelScope)
    val voiceMode = speech.mode
    val setup = app.ai.setup

    private val _ui = MutableStateFlow(SceneUi())
    val ui: StateFlow<SceneUi> = _ui.asStateFlow()
    val messages: StateFlow<List<MessageEntity>> = dao.messagesFlow(storyId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settingsState = settings.state

    var engine: SceneEngine? = null; private set
    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    private lateinit var story: Story
    private var state: PlaythroughEntity? = null
    private var turnJob: Job? = null
    private var lastFailedInput: Pair<String?, String?>? = null
    private var summarizing = false
    private val snapshots = ArrayDeque<TurnSnapshot>()
    /** Session memory of how each line was performed, so replays reuse the same cached natural-voice audio. */
    private val deliveries = HashMap<Long, Pair<String?, String?>>()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            // continuous voice: auto-send recognised speech
            voice.state.collect { s ->
                if (s is VoiceInputState.Result && _ui.value.continuousVoice) {
                    voice.consume(); send(s.text, s.audioPath)
                }
            }
        }
        viewModelScope.launch { settings.state.collect { speech.volume = it.volume; speech.naturalVoicesEnabled = it.naturalVoices; speech.geminiVoicesEnabled = it.voiceEngine != "microsoft"; engine?.reduceMotion = it.reduceMotion } }
    }

    private suspend fun load() {
        val s = repo.story(storyId) ?: run { _ui.update { it.copy(error = "Story not found", phase = Phase.IDLE) }; return }
        story = s
        val st = settings.current
        val pt = repo.ensurePlaythrough(s).copy(lastPlayed = System.currentTimeMillis())
        dao.upsertPlaythrough(pt)
        state = pt
        val lang = pt.language?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: if (s.isCustom) s.language else st.language
        val playerApp = if (s.isCustom) s.playerAppearance else s.playerAppearance.copy(body = st.playerBody).let {
            if (st.playerBody == BodyType.FEM && s.playerAppearance.body == BodyType.MASC) it.copy(hairStyle = HairStyle.PONYTAIL, outfitStyle = OutfitStyle.JACKET) else it
        }
        engine = SceneEngine(s.setting, playerApp, s.character).also { it.reduceMotion = st.reduceMotion }
        _engineReady.value = true
        val playerName = s.playerNameOverride?.takeIf { it.isNotBlank() } ?: st.playerName
        val msgs = dao.messages(storyId)
        _ui.update {
            it.copy(story = s, playerName = playerName, mood = pt.mood, relationship = pt.relationship, chapter = pt.chapter,
                ended = pt.ended, ending = null, language = lang, turnCount = pt.turnCount)
        }
        if (replayCheckpoint != null) { startReplay(replayCheckpoint); return }
        if (msgs.isEmpty()) {
            _ui.update { it.copy(phase = Phase.INTRO) }
            engine?.startEntrance(withWalk = true)
            delay(2600)
            requestTurn(null, null, playerAlreadyShown = true)
        } else {
            engine?.startEntrance(withWalk = false)
            val lastNpc = msgs.lastOrNull { it.role == Role.CHARACTER.name }
            lastNpc?.let { m ->
                engine?.react(s.character.id, Emotion.fromAny(m.emotion) ?: Emotion.NEUTRAL, Gesture.NONE)
            }
            _ui.update {
                it.copy(phase = Phase.IDLE, line = lastNpc?.let { m -> Line(s.character.name, m.text, m.action, false, m.id) },
                    suggestions = AiParser.defaultReplies(lang), showChoices = false, banner = "Chapter ${pt.chapter + 1} · ${s.chapters.getOrNull(pt.chapter)?.title ?: ""}")
            }
            delay(2500); _ui.update { it.copy(banner = null) }
        }
    }

    // ------------------------------------------------------------------ turn pipeline

    fun send(textRaw: String, audioPath: String? = null) {
        val text = textRaw.trim()
        if (text.isEmpty()) return
        val p = _ui.value.phase
        if (p == Phase.THINKING || p == Phase.LOADING || p == Phase.REPLAY || turnJob?.isActive == true && p != Phase.CHARACTER_SPEAKING) return
        val verdict = Moderation.check(text)
        if (!verdict.allowed) {
            _ui.update {
                it.copy(toast = if (verdict.selfHarm) "If you're going through something hard, please talk to someone now: Tele-MANAS 14416 (free, 24×7, India)." else "That's outside what Kissa stories allow. Try a different line.")
            }
            return
        }
        speech.stop() // interrupt anything still playing
        turnJob?.cancel()
        _ui.update { it.copy(showChoices = false, badTurn = false) }
        requestTurn(text, audioPath, playerAlreadyShown = false)
    }

    fun retry() {
        val (t, a) = lastFailedInput ?: (null to null)
        _ui.update { it.copy(error = null, canRetry = false, errorNeedsKey = false, errorIsSetup = false) }
        if (app.ai.setup.value.phase != com.kissa.app.ai.SetupState.Phase.READY) app.ai.runSetup(force = true)
        requestTurn(t, a, playerAlreadyShown = true)
    }

    private fun requestTurn(text: String?, audioPath: String?, playerAlreadyShown: Boolean) {
        val eng = engine ?: return
        turnJob = viewModelScope.launch {
            _ui.update { it.copy(error = null, canRetry = false, errorNeedsKey = false, errorIsSetup = false, suggestions = emptyList(), showChoices = false) }
            val st = settings.current
            val ui0 = _ui.value
            // 1. persist + voice the player's line, while the AI request runs in parallel
            if (text != null && !playerAlreadyShown) {
                state?.let { pt ->
                    snapshots.addLast(TurnSnapshot(pt, dao.messages(storyId).lastOrNull()?.id ?: 0L, ui0.line, ui0.suggestions, npcEmotion = eng.npc.emotion))
                    while (snapshots.size > 20) snapshots.removeFirst()
                }
                val id = dao.insertMessage(MessageEntity(storyId = storyId, role = Role.PLAYER.name, speaker = ui0.playerName, text = text, audioPath = audioPath))
                _ui.update { it.copy(phase = Phase.PLAYER_SPEAKING, line = Line(ui0.playerName, text, null, true, id)) }
            }
            // the character's natural-voice audio starts generating the moment the AI reply lands
            val aiDeferred = async { fetchAi(text).also { r -> r.getOrNull()?.let { speech.prefetch(characterUtterance(it.dialogue, it.emotion, it.delivery, it.vocal)) } } }
            if (text != null && !playerAlreadyShown) {
                eng.focusId = "player"
                val (pe, pg) = playerBodyLanguage(text)
                eng.react("player", pe, pg)
                val useRecording = audioPath != null && st.keepRealVoice
                if (useRecording || st.speakPlayerLines) {
                    speech.play(playerUtterance(text, pe, if (useRecording) audioPath else null))
                } else delay(600)
                eng.focusId = null
                delay(280) // a natural beat before she answers
            }
            // 2. thinking state until the reply lands
            if (!aiDeferred.isCompleted) {
                _ui.update { it.copy(phase = Phase.THINKING) }
                eng.setThinking(story.character.id, true)
            }
            val result = try { aiDeferred.await() } catch (e: CancellationException) { eng.setThinking(story.character.id, false); throw e }
            eng.setThinking(story.character.id, false)
            result.fold(
                onSuccess = { turn -> applyTurn(turn) },
                onFailure = { err ->
                    lastFailedInput = text to audioPath
                    val kind = (err as? AiException)?.kind
                    val needsKey = kind == AiException.Kind.NO_KEY || (kind == AiException.Kind.AUTH && !app.ai.backendMode)
                    val isSetup = kind == AiException.Kind.SERVICE_SETUP
                    eng.react(story.character.id, Emotion.CONFUSED, Gesture.HEAD_TILT)
                    _ui.update {
                        it.copy(phase = Phase.IDLE, error = friendlyError(err), canRetry = true, errorNeedsKey = needsKey, errorIsSetup = isSetup,
                            suggestions = AiParser.defaultReplies(it.language))
                    }
                },
            )
        }
    }

    private fun localeFor(text: String): String = if (SpeechDirector.hasDevanagari(text)) "hi-IN" else _ui.value.language.ttsTag

    private fun langCode() = NeuralVoices.langCode(_ui.value.language)

    /** The character's line: fixed natural voice per character + emotion/delivery-driven performance. */
    private fun characterUtterance(text: String, emotion: Emotion, delivery: String?, vocal: String?): Utterance {
        val st = settings.current
        val v = story.character.voice
        return Utterance(story.character.id, text, localeFor(text), st.voiceOverrides[story.character.id] ?: v.voiceName, v.feminine, v.pitch, v.rate, emotion,
            neuralVoice = NeuralVoices.forCharacter(story.character, st.neuralOverrides[story.character.id]), delivery = delivery, vocal = vocal, lang = langCode(),
            edgeVoice = NeuralVoices.edgeForCharacter(story.character, st.neuralOverrides[story.character.id], st.playerEdgeVoice, st.playerBody), useGemini = true)
    }

    /** The player's line: their real recording when they spoke, otherwise their chosen natural voice. */
    private fun playerUtterance(text: String, emotion: Emotion, audioPath: String?): Utterance {
        val st = settings.current
        return Utterance("player", text, localeFor(text), st.playerVoiceName, st.playerBody == BodyType.FEM, st.playerPitch, st.playerRate, emotion, audioPath,
            neuralVoice = NeuralVoices.forPlayer(st.playerNeuralVoice, st.playerBody), lang = langCode(),
            edgeVoice = com.kissa.app.voice.engines.EdgeTts.playerVoice(st.playerEdgeVoice, st.playerBody == BodyType.FEM), useGemini = false)
    }

    private fun friendlyError(e: Throwable): String {
        val name = story.character.name.substringBefore(' ')
        return when ((e as? AiException)?.kind) {
            AiException.Kind.SERVICE_SETUP -> "AI service abhi setup nahi hai — $name will talk as soon as it's switched on. Tap retry later."
            AiException.Kind.SERVICE_LIMIT -> "Aaj ki AI limit khatam ho gayi hai. $name will be back soon — try again later."
            AiException.Kind.NO_KEY -> "$name needs your Gemini API key to talk. Add it in Settings → Story AI (free at aistudio.google.com/apikey)."
            AiException.Kind.AUTH -> if (app.ai.backendMode) "Couldn't verify this device with the AI service. Tap retry." else "Your Gemini key was rejected. Check it in Settings → Story AI."
            AiException.Kind.QUOTA -> if (app.ai.backendMode) "$name needs a breather (too many messages too fast). Tap retry in a few seconds." else "Your Gemini key hit its free limit (per-minute or daily). Wait a bit and tap retry."
            AiException.Kind.NETWORK -> "$name can't hear you — aap offline lag rahe ho. Tap retry when you're connected."
            AiException.Kind.TIMEOUT -> "$name is lost in thought (the AI is slow right now). Tap retry."
            AiException.Kind.BLOCKED -> "$name won't go there. Try saying something else."
            AiException.Kind.MODEL_NOT_FOUND, AiException.Kind.SERVER -> "AI service abhi busy hai. Tap retry in a moment."
            else -> "$name looks confused (${e.message?.take(80)}). Tap retry."
        }
    }

    private suspend fun fetchAi(playerText: String?): Result<AiTurn> = runCatching {
        val pt = state ?: repo.ensurePlaythrough(story)
        val all = dao.messages(storyId)
        // the just-inserted player line is passed separately as the new input
        val history = all.let { if (playerText != null && it.lastOrNull()?.role == Role.PLAYER.name) it.dropLast(1) else it }
            .map { HistoryLine(Role.valueOf(it.role), it.text, Emotion.fromAny(it.emotion), it.action) }
        val notes = dao.notes(storyId).map { it.text }
        val payload = ContextBuilder.build(
            story, _ui.value.playerName, _ui.value.language,
            StateSnapshot(pt.mood, pt.relationship, pt.chapter, pt.summary, pt.turnCount, pt.ended), notes, history, playerText,
        )
        // the backend picks the model (with fallback); we validate and retry once on an unreadable reply
        var lastErr: Throwable? = null
        repeat(2) { attempt ->
            val raw = app.ai.generate(payload, ContextBuilder.responseSchema(), temperature = if (attempt == 0) 0.95 else 0.6)
            when (val r = AiParser.parse(raw, _ui.value.language)) {
                is ParseResult.Ok -> return@runCatching r.turn
                is ParseResult.Fail -> lastErr = AiException("Unreadable reply: ${r.reason}", AiException.Kind.BAD_RESPONSE, true)
            }
        }
        throw lastErr ?: AiException("No reply", AiException.Kind.BAD_RESPONSE, true)
    }

    private suspend fun applyTurn(turn: AiTurn) {
        val eng = engine ?: return
        val pt0 = state ?: repo.ensurePlaythrough(story)
        var pt = pt0.copy(
            mood = applyMeter(pt0.mood, turn.moodDelta),
            relationship = applyMeter(pt0.relationship, turn.relationshipDelta),
            turnCount = pt0.turnCount + 1,
            lastPlayed = System.currentTimeMillis(),
        )
        val msgId = dao.insertMessage(
            MessageEntity(storyId = storyId, role = Role.CHARACTER.name, speaker = story.character.name, text = turn.dialogue,
                action = turn.action, emotion = turn.emotion.name, gesture = turn.gesture.name)
        )
        turn.memoryNote?.let { val nid = dao.insertNote(MemoryNoteEntity(storyId = storyId, text = it)); snapshots.lastOrNull()?.noteIds?.add(nid) }
        val oldRank = rankFor(pt0.relationship)
        val newRank = rankFor(pt.relationship)
        val bad = turn.relationshipDelta <= -4 || turn.moodDelta <= -7 || turn.sceneEvent == SceneEvent.CHARACTER_EXITS

        var banner: String? = null
        var ending: String? = null
        when (turn.sceneEvent) {
            SceneEvent.CHAPTER_COMPLETE -> if (pt.chapter < story.chapters.size - 1) {
                pt = pt.copy(chapter = pt.chapter + 1)
                banner = "Chapter ${pt.chapter + 1} · ${story.chapters[pt.chapter].title}"
            } else { ending = endingTitle(pt.relationship); pt = pt.copy(ended = true, endingTitle = ending) }
            SceneEvent.ENDING -> if (!pt.ended) { ending = endingTitle(pt.relationship); pt = pt.copy(ended = true, endingTitle = ending) }
            else -> {}
        }
        dao.upsertPlaythrough(pt)
        state = pt
        if (banner != null) repo.createCheckpoint(storyId, banner)
        if (ending != null) repo.createCheckpoint(storyId, "Ending · $ending")

        // drive the scene, then speak; the player's avatar reacts to her too
        val st = settings.current
        val utter = characterUtterance(turn.dialogue, turn.emotion, turn.delivery, turn.vocal)
        deliveries[msgId] = turn.delivery to turn.vocal
        // keep "thinking" until her natural-voice audio is ready, so the mouth never moves over silence
        if (st.characterVoicesOn) withTimeoutOrNull(22_000) { speech.neuralFile(utter) }

        eng.react(story.character.id, turn.emotion, turn.gesture)
        eng.react("player", mirror(turn.emotion), Gesture.NONE)
        if (turn.sceneEvent == SceneEvent.PLOT_TWIST) eng.react("player", Emotion.SURPRISED, Gesture.STEP_BACK)
        if (turn.sceneEvent != SceneEvent.CHARACTER_EXITS) eng.event(turn.sceneEvent)
        if (turn.sceneEvent in listOf(SceneEvent.CHAPTER_COMPLETE, SceneEvent.ENDING) && banner != null) eng.fadeOutIn()
        _ui.update {
            it.copy(phase = Phase.CHARACTER_SPEAKING, line = Line(story.character.name, turn.dialogue, turn.action, false, msgId),
                mood = pt.mood, relationship = pt.relationship, chapter = pt.chapter, banner = banner, turnCount = pt.turnCount,
                lastNote = turn.memoryNote, ended = pt.ended, rankUp = if (newRank != oldRank && pt.relationship > pt0.relationship) newRank else null)
        }
        if (st.characterVoicesOn) speech.play(utter) else delay(1200L + turn.dialogue.length * 45L)
        // she walks off only after she has said her piece (like the reference)
        if (turn.sceneEvent == SceneEvent.CHARACTER_EXITS) { eng.event(turn.sceneEvent); delay(3200) }
        _ui.update {
            it.copy(phase = Phase.IDLE, suggestions = turn.suggestedReplies, banner = null, ending = ending, lastNote = null,
                showChoices = st.showSuggestions && ending == null && !bad, badTurn = bad && snapshots.isNotEmpty(), canRewind = snapshots.isNotEmpty())
        }
        maybeSummarize()
        if (_ui.value.continuousVoice && ending == null && !bad) startListening()
    }

    /** Lightweight body language for the player's own line (their words are theirs; this is just animation). */
    private fun playerBodyLanguage(text: String): Pair<Emotion, Gesture> {
        val t = text.lowercase()
        return when {
            Regex("\\b(hi|hello|hey|namaste|hii+)\\b").containsMatchIn(t) -> Emotion.HAPPY to Gesture.WAVE
            Regex("sorry|maaf|my bad|galti").containsMatchIn(t) -> Emotion.SHY to Gesture.NONE
            Regex("haha|lol|hehe|😂").containsMatchIn(t) -> Emotion.AMUSED to Gesture.LAUGH
            t.contains("!") && t.length < 40 -> Emotion.SURPRISED to Gesture.NONE
            t.trim().endsWith("?") -> Emotion.CONFUSED to Gesture.HEAD_TILT
            Regex("\\b(no|nahi|nope)\\b").containsMatchIn(t) -> Emotion.NEUTRAL to Gesture.SHAKE_HEAD
            Regex("\\b(yes|haan|ha|okay|ok|theek)\\b").containsMatchIn(t) -> Emotion.HAPPY to Gesture.NOD
            else -> Emotion.NEUTRAL to Gesture.NONE
        }
    }

    private fun mirror(e: Emotion) = when (e) {
        Emotion.HAPPY, Emotion.AMUSED, Emotion.SHY -> Emotion.HAPPY
        Emotion.ANGRY -> Emotion.SHY
        Emotion.SAD -> Emotion.SAD
        Emotion.SURPRISED -> Emotion.SURPRISED
        Emotion.CONFUSED -> Emotion.CONFUSED
        Emotion.NEUTRAL -> Emotion.NEUTRAL
    }

    private fun endingTitle(rel: Int) = when {
        rel >= 75 -> "A Bond Worth Keeping"
        rel >= 50 -> "The Start of Something"
        rel >= 30 -> "Bittersweet Maybe"
        else -> "Paths Apart"
    }

    /** Rolling summary: when many turns aren't covered yet, fold the older ones into the summary. */
    private fun maybeSummarize() {
        if (summarizing) return
        viewModelScope.launch {
            val pt = state ?: return@launch
            val uncovered = dao.messagesAfter(storyId, pt.summarizedUpToId)
            if (uncovered.size <= ContextBuilder.RECENT_TURNS + 12) return@launch
            summarizing = true
            try {
                val toFold = uncovered.dropLast(ContextBuilder.RECENT_TURNS)
                val payload = ContextBuilder.summaryPrompt(story, _ui.value.playerName, pt.summary,
                    toFold.map { HistoryLine(Role.valueOf(it.role), it.text) })
                val text = app.ai.generate(payload, null, temperature = 0.3, maxTokens = 1024)
                val cur = state ?: return@launch
                val upd = cur.copy(summary = text.trim().take(ContextBuilder.MAX_SUMMARY), summarizedUpToId = toFold.last().id)
                dao.upsertPlaythrough(upd); state = upd
            } catch (_: Exception) {
                // summary is best-effort; the recent window + notes still keep continuity
            } finally { summarizing = false }
        }
    }

    // ------------------------------------------------------------------ voice

    fun startListening() {
        speech.stop()
        val lang = _ui.value.language
        voice.start(lang.sttTag, settings.current.keepRealVoice)
    }

    fun toggleContinuous() {
        val on = !_ui.value.continuousVoice
        _ui.update { it.copy(continuousVoice = on, toast = if (on) "Voice conversation on — just talk after each reply" else "Voice conversation off") }
        if (!on) voice.cancel()
    }

    fun stopAudio() = speech.stop()

    fun replayLine() {
        val l = _ui.value.line ?: return
        if (_ui.value.phase != Phase.IDLE) return
        viewModelScope.launch {
            val st = settings.current
            val m = messages.value.firstOrNull { it.id == l.messageId }
            if (l.isPlayer) {
                speech.play(playerUtterance(l.text, Emotion.NEUTRAL, m?.audioPath?.takeIf { st.keepRealVoice }))
            } else {
                val emo = Emotion.fromAny(m?.emotion) ?: Emotion.NEUTRAL
                engine?.react(story.character.id, emo, Gesture.fromAny(m?.gesture) ?: Gesture.NONE)
                // replays reuse the cached natural-voice audio when the delivery matches (cache key includes it)
                val d = deliveries[l.messageId]
                speech.play(characterUtterance(l.text, emo, d?.first, d?.second))
            }
        }
    }

    /** Replays the conversation after a checkpoint (or from the start) with voices and reactions. */
    fun startReplay(checkpointId: Long?) {
        turnJob?.cancel(); speech.stop()
        turnJob = viewModelScope.launch {
            val from = checkpointId?.let { dao.checkpoint(it)?.lastMessageId } ?: 0L
            val msgs = dao.messagesAfter(storyId, from).takeLast(30)
            val toPlay = if (msgs.isEmpty()) dao.messages(storyId).takeLast(12) else msgs
            _ui.update { it.copy(phase = Phase.REPLAY, suggestions = emptyList(), toast = "Replaying scene…") }
            engine?.startEntrance(withWalk = false)
            delay(800)
            val st = settings.current
            for (m in toPlay) {
                val isPlayer = m.role == Role.PLAYER.name
                _ui.update { it.copy(line = Line(m.speaker, m.text, m.action, isPlayer, m.id)) }
                if (isPlayer) {
                    engine?.focusId = "player"
                    speech.play(playerUtterance(m.text, Emotion.NEUTRAL, m.audioPath?.takeIf { st.keepRealVoice }))
                    engine?.focusId = null
                } else {
                    val emo = Emotion.fromAny(m.emotion) ?: Emotion.NEUTRAL
                    engine?.react(story.character.id, emo, Gesture.fromAny(m.gesture) ?: Gesture.NONE)
                    val d = deliveries[m.id]
                    speech.play(characterUtterance(m.text, emo, d?.first, d?.second))
                }
                delay(350)
            }
            _ui.update { it.copy(phase = Phase.IDLE, toast = "Replay finished — continue the story", suggestions = AiParser.defaultReplies(it.language)) }
        }
    }

    fun stopReplay() {
        if (_ui.value.phase != Phase.REPLAY) return
        turnJob?.cancel(); speech.stop()
        _ui.update { it.copy(phase = Phase.IDLE, suggestions = AiParser.defaultReplies(it.language)) }
    }

    // ------------------------------------------------------------------ misc actions

    fun setLanguage(l: Lang) {
        viewModelScope.launch {
            val pt = state ?: return@launch
            val upd = pt.copy(language = l.name); dao.upsertPlaythrough(upd); state = upd
            _ui.update { it.copy(language = l, toast = "Dialogue language: ${l.label}") }
        }
    }

    fun saveCheckpoint() {
        viewModelScope.launch {
            val ch = story.chapters.getOrNull(state?.chapter ?: 0)?.title ?: "Story"
            repo.createCheckpoint(storyId, "Saved · $ch · turn ${state?.turnCount ?: 0}")
            _ui.update { it.copy(toast = "Checkpoint saved") }
        }
    }

    fun report(messageId: Long, reason: String) {
        viewModelScope.launch {
            val m = messages.value.firstOrNull { it.id == messageId }
            dao.insertReport(ReportEntity(storyId = storyId, messageId = messageId, reason = reason, excerpt = m?.text?.take(200) ?: ""))
            dao.hideMessage(messageId)
            if (_ui.value.line?.messageId == messageId) _ui.update { it.copy(line = null) }
            _ui.update { it.copy(toast = "Reported and hidden. Thanks — it won't be used as story context.") }
        }
    }

    fun restart() {
        viewModelScope.launch {
            turnJob?.cancel(); speech.stop()
            repo.resetProgress(storyId)
            snapshots.clear()
            state = repo.ensurePlaythrough(story)
            engine?.react(story.character.id, Emotion.NEUTRAL, Gesture.NONE)
            _ui.update { it.copy(line = null, ending = null, ended = false, mood = state!!.mood, relationship = state!!.relationship, chapter = 0, turnCount = 0, phase = Phase.INTRO) }
            engine?.startEntrance(withWalk = true)
            delay(2600)
            requestTurn(null, null, playerAlreadyShown = true)
        }
    }

    // ------------------------------------------------------------------ choices, rewind, skip

    fun pickChoice(text: String) { _ui.update { it.copy(showChoices = false) }; send(text) }
    fun hideChoices() = _ui.update { it.copy(showChoices = false) }
    fun showChoicesAgain() { if (_ui.value.suggestions.isNotEmpty()) _ui.update { it.copy(showChoices = true) } }
    fun dismissBadTurn() = _ui.update { it.copy(badTurn = false, showChoices = settings.current.showSuggestions) }
    fun consumeRankUp() = _ui.update { it.copy(rankUp = null) }

    /** Tap-to-skip: cut the current line short (the next beat continues immediately). */
    fun skipLine() { if (speech.speaking.value != null) speech.stop() }

    /** Undo the last exchange: VHS rewind back to before the player's line (meters, memory and lines restored). */
    fun rewind() {
        val snap = snapshots.removeLastOrNull() ?: return
        turnJob?.cancel(); speech.stop(); voice.cancel()
        turnJob = viewModelScope.launch {
            _ui.update { it.copy(rewinding = true, badTurn = false, showChoices = false, error = null) }
            engine?.rewindFx(1.5f)
            withContext(Dispatchers.IO) {
                dao.audioPathsAfter(storyId, snap.cutoffMessageId).forEach { runCatching { java.io.File(it).delete() } }
                dao.deleteMessagesAfter(storyId, snap.cutoffMessageId)
                dao.deleteCheckpointsAfter(storyId, snap.cutoffMessageId)
                snap.noteIds.forEach { dao.deleteNote(it) }
                dao.upsertPlaythrough(snap.pt)
            }
            state = snap.pt
            engine?.react(story.character.id, snap.npcEmotion, Gesture.NONE)
            engine?.react("player", Emotion.NEUTRAL, Gesture.NONE)
            delay(1500)
            _ui.update {
                it.copy(rewinding = false, phase = Phase.IDLE, line = snap.line, suggestions = snap.suggestions.ifEmpty { AiParser.defaultReplies(it.language) },
                    mood = snap.pt.mood, relationship = snap.pt.relationship, chapter = snap.pt.chapter, turnCount = snap.pt.turnCount, ended = snap.pt.ended,
                    showChoices = settings.current.showSuggestions, canRewind = snapshots.isNotEmpty(), toast = "Rewound — try a different line")
            }
        }
    }

    fun dismissEnding() = _ui.update { it.copy(ending = null) }
    fun consumeToast() = _ui.update { it.copy(toast = null) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    fun onPause() { speech.stop(); voice.cancel() }

    override fun onCleared() {
        speech.stop(); voice.cancel()
        super.onCleared()
    }

    class Factory(private val storyId: String, private val replay: Long?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SceneViewModel(storyId, replay) as T
    }
}
