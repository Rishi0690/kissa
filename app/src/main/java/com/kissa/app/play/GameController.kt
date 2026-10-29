package com.kissa.app.play

import com.kissa.app.ai.LiveAi
import com.kissa.app.ai.Moderation
import com.kissa.app.data.GameEntry
import com.kissa.app.game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ChatMsg(val fromPlayer: Boolean, val text: String, val time: String?, val system: Boolean = false, val seen: Boolean = false, val live: Boolean = false, val id: Long = System.nanoTime())
data class Caption(val speaker: String?, val text: String, val narration: Boolean, val player: Boolean = false, val live: Boolean = false, val id: Long = System.nanoTime())
data class Toast(val text: String, val positive: Boolean?, val id: Long = System.nanoTime())

data class PlayUi(
    val sceneType: SceneType = SceneType.CINEMATIC,
    val sceneTitle: String? = null,
    val showTitle: Boolean = false,
    val caption: Caption? = null,
    val chatWith: String? = null,
    val chatHeader: String? = null,
    val chat: List<ChatMsg> = emptyList(),
    val typing: Boolean = false,
    /** Name of the character "thinking" about a live reply (typing indicator in cinematic/POV). */
    val thinking: String? = null,
    val blocked: Boolean = false,
    val decision: Beat.Decision? = null,
    val reply: Step.OpenReply? = null,
    val toasts: List<Toast> = emptyList(),
    val rankName: String = "",
    val rankScore: Int = 0,
    val rankUp: String? = null,
    val stats: Map<String, Int> = emptyMap(),
    val objective: ObjectiveDef? = null,
    val ending: EndingDef? = null,
    val endingNone: Boolean = false,
    val busy: Boolean = true,
    /** Who a free reply right now would be addressed to (shown in the input bar). */
    val listener: String? = null,
    val liveProvider: String? = null,
)

/**
 * Drives one playthrough: pulls [Beat]s from the pure engine and performs them (painted performers, camera, voice
 * with lip-sync, chat bubbles). v4: the player can type or speak a free reply at ANY moment. With a free AI key the
 * character answers live in character and the words are mapped to the nearest scripted choice / intent (or a tone
 * nudge when no decision is pending); without a key the scripted IntentMatcher maps them offline. Preset choices
 * stay available the whole time and the story never dead-ends.
 */
class GameController(
    val entry: GameEntry,
    val session: GameSession,
    private val voice: VoicePlayer?,
    private val voiceOn: () -> Boolean,
    private val scope: CoroutineScope,
    private val castFor: (String) -> CastSet?,
    val playerCastId: String,
    private val live: LiveAi?,
) {
    val game get() = entry.game
    val stage = StageState()
    private val _ui = MutableStateFlow(PlayUi())
    val ui: StateFlow<PlayUi> = _ui
    private var answer: CompletableDeferred<Answer>? = null
    private var skip: CompletableDeferred<Unit>? = null
    private var job: Job? = null
    @Volatile private var interjection: String? = null
    private val log = ArrayList<Pair<String, String>>()
    private var lastSpeaker: String? = null
    private val feminine get() = session.playerRig == "female"

    private sealed class Answer { data class Pick(val index: Int) : Answer(); data class Free(val text: String, val preset: Boolean) : Answer() }

    fun start() {
        stage.player = Performer("player", castFor(playerCastId)).apply { x = 0.2f; targetX = 0.2f; visible = false; alpha = 0f }
        refreshStatus()
        job = scope.launch { runCatching { loop() }.onFailure { if (it !is CancellationException) android.util.Log.e("Kissa", "game loop", it) } }
    }
    fun stop() { job?.cancel(); voice?.stop() }

    /** Tap on the scene: finish the current line now. */
    fun tapSkip() { voice?.stop(); skip?.complete(Unit) }
    fun choose(index: Int) { answer?.complete(Answer.Pick(index)) }
    /** A preset chip of an open reply (scripted, voiced reaction). */
    fun preset(text: String) { if (text.isNotBlank()) answer?.complete(Answer.Free(text.trim(), preset = true)) }

    /** Free typed / spoken reply — works at any moment of any scene. */
    fun say(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        val a = answer
        if (a != null) { a.complete(Answer.Free(t, preset = false)); return }
        interjection = t
        tapSkip()
    }

    private fun refreshStatus() {
        val st = session.state
        _ui.update { it.copy(rankName = session.rankName, rankScore = st.rankScore, stats = LinkedHashMap(st.stats), objective = game.objective(st.currentObjective), listener = listenerName()) }
    }

    private fun toast(o: Outcome) {
        val ts = o.deltas.map { d -> Toast(d.text, d.amount?.let { it >= 0 }) }
        _ui.update { it.copy(toasts = (it.toasts + ts).takeLast(5), rankUp = if (o.rankChanged) o.rankAfter else it.rankUp) }
        refreshStatus()
        scope.launch { delay(2800); _ui.update { u -> u.copy(toasts = u.toasts.filterNot { t -> ts.any { it.id == t.id } }) } }
        if (o.rankChanged) scope.launch { delay(3000); _ui.update { it.copy(rankUp = null) } }
    }

    private fun note(text: String, positive: Boolean? = null) {
        val t = Toast(text, positive)
        _ui.update { it.copy(toasts = (it.toasts + t).takeLast(5)) }
        scope.launch { delay(3200); _ui.update { u -> u.copy(toasts = u.toasts.filterNot { it.id == t.id }) } }
    }

    private suspend fun hold(ms: Long) {
        val s = CompletableDeferred<Unit>(); skip = s
        withTimeoutOrNull(ms) { s.await() }
        skip = null
    }

    private fun readMs(text: String) = (1100L + text.length * 55L).coerceAtMost(7000L)

    private suspend fun speak(text: String, voiceFile: String?) {
        if (voice != null && voiceOn() && voiceFile != null) {
            val s = CompletableDeferred<Unit>(); skip = s
            coroutineScope {
                val v = async { voice.play(entry, voiceFile, text) }
                while (!v.isCompleted && !s.isCompleted) delay(30)
                if (s.isCompleted) { voice.stop(); v.cancel() }
                else if (v.getCompleted() == false) hold(readMs(text))
            }
            skip = null
            hold(240)
        } else hold(readMs(text))
    }

    private fun performer(id: String): Performer? = stage.actors[id] ?: game.character(id)?.let { c ->
        Performer(id, castFor(c.art)).also { p -> p.x = 0.62f; p.targetX = 0.62f; p.alpha = 0f; p.visible = true; stage.actors[id] = p }
    }

    private fun setupScene(sc: SceneDef) {
        val prevChat = _ui.value.chatWith
        stage.reset(sc.type, sc.background ?: "college_corridor", sc.time, sc.weather)
        stage.audience = sc.audience
        val entering = HashSet<String>()
        fun scan(steps: List<Step>) { steps.forEach { if (it is Step.Enter) entering += it.char; if (it is Step.If) { scan(it.then); scan(it.otherwise) } } }
        scan(sc.steps)
        val many = sc.cast.size > 1
        sc.cast.forEach { p ->
            val c = game.character(p.id) ?: return@forEach
            stage.actors[p.id] = Performer(p.id, castFor(c.art)).apply {
                x = if (many) p.at else 0.5f; targetX = x
                facing = if (many && p.at < 0.5f) -1 else 1
                if (p.id in entering) { alpha = 0f; targetAlpha = 0f } else { alpha = 1f; targetAlpha = 1f }
            }
        }
        val keepChat = sc.type == SceneType.CHAT && sc.chatWith == prevChat
        _ui.update {
            it.copy(sceneType = sc.type, sceneTitle = sc.title, showTitle = sc.title != null, caption = null, decision = null, reply = null, thinking = null,
                chatWith = if (sc.type == SceneType.CHAT) sc.chatWith else null, chatHeader = sc.chatHeader ?: game.character(sc.chatWith)?.name,
                chat = if (keepChat) it.chat else emptyList(), blocked = if (keepChat) it.blocked else false, typing = false)
        }
        if (sc.type == SceneType.CHAT) lastSpeaker = sc.chatWith
        refreshStatus()
        if (sc.title != null) scope.launch { delay(1700); _ui.update { it.copy(showTitle = false) } }
    }

    private fun logLine(who: String, text: String) { log += who to text; if (log.size > 30) log.removeAt(0) }

    private suspend fun loop() {
        while (true) {
            interjection?.let { interjection = null; interject(it) }
            when (val b = session.next()) {
                is Beat.SceneStart -> { setupScene(b.scene); if (b.scene.title != null) delay(800) }
                is Beat.Line -> line(b)
                is Beat.Narration -> {
                    _ui.update { it.copy(caption = Caption(null, b.text, narration = true)) }
                    logLine("Narrator", b.text)
                    speak(b.text, b.voiceFile)
                }
                is Beat.PlayerLine -> {
                    logLine(session.state.playerName, b.text)
                    if (stage.type == SceneType.CHAT) { _ui.update { it.copy(chat = it.chat + ChatMsg(true, b.text, b.time)) }; hold(800) }
                    else { showPlayer("neutral"); _ui.update { it.copy(caption = Caption(session.state.playerName, b.text, false, player = true)) }; hold(readMs(b.text)); hidePlayer() }
                }
                is Beat.Stage -> perform(b.step)
                is Beat.Effects -> { toast(b.outcome); delay(350) }
                is Beat.Decision -> decision(b)
                is Beat.Reply -> openReply(b.step)
                is Beat.Ending -> {
                    voice?.stop()
                    _ui.update { it.copy(ending = b.ending, endingNone = b.ending == null, caption = null, decision = null, reply = null, busy = false) }
                    refreshStatus()
                    return
                }
            }
        }
    }

    private suspend fun line(b: Beat.Line) {
        val s = b.step
        val name = b.character?.name ?: s.char
        lastSpeaker = s.char
        logLine(name, b.text)
        refreshStatus()
        if (stage.type == SceneType.CHAT) {
            if (_ui.value.blocked) return
            _ui.update { u -> u.copy(typing = true, chat = u.chat.map { if (it.fromPlayer) it.copy(seen = true) else it }) }
            hold((s.delayMs.takeIf { it > 0 } ?: 900).toLong().coerceAtMost(2600))
            _ui.update { it.copy(typing = false, chat = it.chat + ChatMsg(false, b.text, s.time)) }
            speak(b.text, s.voiceFile)
            return
        }
        val a = performer(s.char)
        a?.let { it.visible = true; it.setEmotion(s.emotion); it.play(s.move) }
        stage.speaking = s.char
        if (stage.type == SceneType.CINEMATIC) { stage.focusActor = s.char; stage.targetZoom = if (b.text.length < 45) 1.10f else 1.05f }
        _ui.update { it.copy(caption = Caption(name, b.text, false)) }
        speak(b.text, s.voiceFile)
        stage.speaking = null
    }

    private suspend fun perform(st: Step) {
        when (st) {
            is Step.Enter -> { performer(st.char)?.enterFrom(st.from, if (stage.actors.size > 1) st.at else 0.5f); delay(700) }
            is Step.Exit -> { performer(st.char)?.exitTo(st.to); delay(600) }
            is Step.Move -> performer(st.char)?.let { a -> if (st.to != null) a.targetX = st.to else a.play(st.move) }.also { delay(300) }
            is Step.Emote -> { performer(st.char)?.setEmotion(st.emotion); delay(380) }
            is Step.Camera -> {
                st.zoom?.let { stage.targetZoom = 1f + (it - 1f) * 0.6f }
                st.focus?.let { stage.focusActor = it }
                st.pan?.let { stage.targetFocus = it }
                if (st.shake > 0f) stage.shake = st.shake
                delay(400)
            }
            is Step.Fx -> {
                st.time?.let { stage.time = it }; st.weather?.let { stage.weather = it }
                if (st.flash) stage.flash = 1f
                if (st.sparkles) stage.sparkles = 2.5f
                delay(300)
            }
            is Step.Prop -> {}
            is Step.Audience -> { stage.react(st.react); delay(500) }
            is Step.Wait -> hold(st.ms.toLong())
            else -> {}
        }
    }

    private fun showPlayer(expr: String) {
        if (stage.type != SceneType.CINEMATIC) return
        stage.player?.let { p -> if (!p.visible || p.alpha < 0.1f) p.enterFrom("left", 0.18f); p.targetAlpha = 1f; p.setEmotion(expr) }
        stage.speaking = "player"
    }
    private fun hidePlayer() { stage.player?.targetAlpha = 0f; if (stage.speaking == "player") stage.speaking = null }

    private fun styleExpr(style: String) = when (style) { "confident" -> "smug"; "timid" -> "worried"; "rude", "cold" -> "angry"; "funny" -> "happy"; "kind" -> "smile"; else -> "neutral" }

    // ------------------------------------------------------------------ who listens
    private fun listenerId(): String? {
        val sc = session.scene
        if (sc.type == SceneType.CHAT) return sc.chatWith
        lastSpeaker?.takeIf { stage.actors[it]?.targetAlpha?.let { a -> a > 0f } == true }?.let { return it }
        return stage.actors.values.firstOrNull { it.targetAlpha > 0f }?.id ?: sc.cast.firstOrNull()?.id ?: lastSpeaker
    }
    private fun listenerName(): String? = listenerId()?.let { game.character(it)?.name }

    private fun liveContext(ch: CharacterDef, prompt: String?, options: List<LiveAi.Option>, mustPick: Boolean): LiveAi.Context {
        val st = session.state
        val status = game.stats.values.filter { !it.hidden }.joinToString { "${it.label} ${st.stats[it.name] ?: it.start}" } + ", rank ${session.rankName}"
        return LiveAi.Context(game, session.scene, ch, st.playerName, feminine, log.toList(), status, prompt, options, mustPick)
    }

    /** Shows the player's own words in the current surface. */
    private suspend fun showPlayerWords(text: String, style: String) {
        logLine(session.state.playerName, text)
        if (stage.type == SceneType.CHAT) _ui.update { it.copy(chat = it.chat + ChatMsg(true, text, null)) }
        else { showPlayer(styleExpr(style)); _ui.update { it.copy(caption = Caption(session.state.playerName, text, false, player = true)) } }
        hold(if (stage.type == SceneType.CHAT) 450 else 900)
    }

    /** A live (AI) line: text-only, typewriter in the UI, expression + small gesture on the painted character. */
    private suspend fun showLiveLine(ch: CharacterDef, r: LiveAi.Result) {
        logLine(ch.name, r.reply)
        lastSpeaker = ch.id
        _ui.update { it.copy(thinking = null, typing = false, liveProvider = r.provider) }
        if (stage.type == SceneType.CHAT) {
            _ui.update { u -> u.copy(chat = u.chat.map { if (it.fromPlayer) it.copy(seen = true) else it } + ChatMsg(false, r.reply, null, live = true)) }
            hold(readMs(r.reply).coerceAtMost(4500))
            return
        }
        hidePlayer()
        performer(ch.id)?.let { it.visible = true; it.setEmotion(r.emotion); it.play(if (r.emotion in setOf("laugh", "happy")) "laugh" else "nod") }
        stage.focusActor = ch.id; stage.speaking = null
        _ui.update { it.copy(caption = Caption(ch.name, r.reply, false, live = true)) }
        hold(readMs(r.reply))
    }

    private fun setThinking(ch: CharacterDef?) {
        if (stage.type == SceneType.CHAT) _ui.update { it.copy(typing = true) } else _ui.update { it.copy(thinking = ch?.name) }
    }

    private fun blockedReply(text: String): Boolean {
        val v = Moderation.check(text)
        if (v.allowed) return false
        note(if (v.selfHarm) "Agar aap pareshan hain, kisi apne se baat karein. India helpline: 14416 (Tele-MANAS)" else "Ye shabd is kahani mein allowed nahi — kuch aur likho", false)
        return true
    }

    // ------------------------------------------------------------------ decisions (preset choices + free reply)
    private suspend fun decision(d: Beat.Decision) {
        while (true) {
            val ans = CompletableDeferred<Answer>(); answer = ans
            _ui.update { it.copy(decision = d, busy = false, caption = if (stage.type == SceneType.CHAT) null else it.caption, listener = listenerName()) }
            val a = ans.await()
            answer = null
            when (a) {
                is Answer.Pick -> { applyChoice(d, a.index, null); return }
                is Answer.Free -> {
                    if (blockedReply(a.text)) continue
                    _ui.update { it.copy(decision = null, busy = true) }
                    if (freeDecision(d, a.text)) return
                    _ui.update { it.copy(caption = null) }
                }
            }
        }
    }

    private suspend fun applyChoice(d: Beat.Decision, idx: Int, liveSpeaker: String?) {
        val c = d.choices.first { it.index == idx }.value
        _ui.update { it.copy(decision = null, busy = true) }
        if (liveSpeaker == null) {
            val text = session.substitute(c.text)
            logLine(session.state.playerName, text)
            if (stage.type == SceneType.CHAT) {
                when (c.kind) {
                    "ignore" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "Tumne reply nahi kiya.", null, system = true)) }
                    "block" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "Tumne ${_ui.value.chatHeader ?: "unhe"} ko block kar diya.", null, system = true), blocked = true) }
                    "delay" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "⏱ 2 ghante baad…", null, system = true) + ChatMsg(true, text.substringAfter(": ").removeSuffix(")").trim('(', ' ', '\''), null)) }
                    else -> _ui.update { it.copy(chat = it.chat + ChatMsg(true, text, null)) }
                }
            } else {
                if (c.kind == "action") { hidePlayer(); _ui.update { it.copy(caption = Caption(null, text.trim('(', ')'), narration = true, player = true)) } }
                else { showPlayer(styleExpr(c.style)); _ui.update { it.copy(caption = Caption(session.state.playerName, text, narration = false, player = true)) } }
                stage.focusActor = null
            }
        }
        val o = session.choose(idx, liveSpeaker)
        toast(o)
        if (liveSpeaker == null) hold(if (stage.type == SceneType.CHAT) 500 else readMs(session.substitute(c.text)).coerceAtMost(2600))
        hidePlayer()
    }

    /** Free reply at a decision: live AI picks the nearest choice and answers; offline the matcher tries. */
    private suspend fun freeDecision(d: Beat.Decision, text: String): Boolean {
        val chId = listenerId(); val ch = game.character(chId)
        showPlayerWords(text, LiveAi.offlineTone(text))
        if (ch != null && live?.configured == true) {
            setThinking(ch)
            val opts = d.choices.map { (i, c) ->
                val meaning = c.reaction.filterIsInstance<Step.Say>().firstOrNull()?.text ?: (c.goto?.let { g -> game.scenes[g]?.title } ?: "")
                LiveAi.Option("c$i", session.substitute(c.text), c.style, meaning)
            }
            val r = live.respond(liveContext(ch, d.prompt, opts, mustPick = true), text)
            _ui.update { it.copy(thinking = null, typing = false) }
            if (r != null) {
                val idx = r.optionId?.removePrefix("c")?.toIntOrNull()?.takeIf { i -> d.choices.any { it.index == i } } ?: d.choices.first().index
                showLiveLine(ch, r)
                applyChoice(d, idx, ch.id)
                return true
            }
            note("Live AI abhi nahi mila (${live.lastError?.take(60) ?: "network"}) — offline samajh raha hoon", null)
        }
        // offline: scripted matcher over the choice texts
        val intents = d.choices.map { (i, c) -> Intent("c$i", choiceKeywords(session.substitute(c.text), c.style), emptyList(), null, emptyList(), c.style, 0) }
        val m = IntentMatcher.match(text, intents, threshold = 0.9)
        if (m != null) {
            val idx = d.choices[m.index].index
            note("Tumhara jawab → \"${session.substitute(d.choices[m.index].value.text).take(40)}\"", null)
            applyChoice(d, idx, null)
            return true
        }
        note(if (live?.configured == true) "Samajh nahi aaya — kisi choice pe tap karo ya alag shabdon mein likho" else "Offline mode: ye jawab samjha nahi. Choice chuno — ya Settings mein free AI key daalo, phir kuch bhi likh sakte ho", null)
        hidePlayer()
        return false
    }

    private fun choiceKeywords(text: String, style: String): List<String> {
        val stop = setOf("main", "hai", "hoon", "tum", "kya", "aur", "toh", "par", "nahi_x", "the", "and", "to", "ke", "ki", "ka", "se", "ko", "ho", "bhi", "ek", "yeh", "ye")
        val words = IntentMatcher.normalize(text.replace(Regex("\\{m:([^|}]*)\\|f:([^}]*)\\}"), "$1 $2")).filter { it.length >= 3 && it !in stop }
        val styleWords = when (style) {
            "kind" -> listOf("sorry", "thanks", "madad", "saath", "dost")
            "rude" -> listOf("bakwas", "chup", "nikal", "hato")
            "funny" -> listOf("haha", "lol", "mazak")
            "timid" -> listOf("sorry", "darr", "pata_nahi")
            "confident" -> listOf("himmat", "pakka", "dekh")
            "cold" -> listOf("nahi", "zaroorat", "busy")
            else -> emptyList()
        }
        return (words + styleWords).distinct()
    }

    // ------------------------------------------------------------------ open replies
    private suspend fun openReply(r: Step.OpenReply) {
        while (true) {
            val ans = CompletableDeferred<Answer>(); answer = ans
            _ui.update { it.copy(reply = r, busy = false, listener = listenerName()) }
            val a = ans.await()
            answer = null
            val text = when (a) { is Answer.Free -> a.text; is Answer.Pick -> continue }
            if (blockedReply(text)) continue
            _ui.update { it.copy(reply = null, busy = true) }
            val preset = (a as Answer.Free).preset
            val chId = (r.intents.flatMap { it.reaction }.filterIsInstance<Step.Say>().firstOrNull()?.char) ?: listenerId()
            val ch = game.character(chId)
            if (!preset && ch != null && live?.configured == true) {
                showPlayerWords(text, LiveAi.offlineTone(text))
                setThinking(ch)
                val opts = r.intents.mapIndexed { i, it -> LiveAi.Option("i$i", it.id + ": " + it.match.take(4).joinToString(" / "), it.style, it.reaction.filterIsInstance<Step.Say>().firstOrNull()?.text ?: "") } +
                    LiveAi.Option("none", "anything else / unclear", "neutral", r.fallback?.reaction?.filterIsInstance<Step.Say>()?.firstOrNull()?.text ?: "")
                val res = live.respond(liveContext(ch, r.prompt, opts, mustPick = false), text)
                _ui.update { it.copy(thinking = null, typing = false) }
                if (res != null) {
                    showLiveLine(ch, res)
                    val i = res.optionId?.removePrefix("i")?.toIntOrNull()
                    val o = if (i != null && i in r.intents.indices) session.replyIntent(i, ch.id) else session.replyFallback(ch.id).also { session.nudge(res.tone)?.let { n -> toast(n) } }
                    toast(o)
                    return
                }
                note("Live AI abhi nahi mila — offline jawab", null)
                val o = session.reply(text); toast(o); hold(600); hidePlayer(); return
            }
            showPlayerWords(text, LiveAi.offlineTone(text))
            val o = session.reply(text)
            toast(o)
            hold(500)
            hidePlayer()
            return
        }
    }

    // ------------------------------------------------------------------ interjections (no decision pending)
    private val offlineAcks = mapOf(
        "kind" to "हम्म… अच्छा लगा सुनकर।", "rude" to "ज़ुबान संभाल के बात करो।", "funny" to "हाहा, बड़े मज़ाकिया हो।",
        "timid" to "डरो मत, खुलकर बोलो।", "confident" to "अच्छा? देखते हैं।", "cold" to "ठीक है।", "neutral" to "हम्म।",
    )

    private suspend fun interject(text: String) {
        if (blockedReply(text)) return
        voice?.stop()
        val ch = game.character(listenerId())
        val tone = LiveAi.offlineTone(text)
        showPlayerWords(text, tone)
        if (ch == null) { note("Abhi yahan koi sun nahi raha — kahani aage badhne do", null); hidePlayer(); return }
        if (live?.configured == true) {
            setThinking(ch)
            val r = live.respond(liveContext(ch, null, emptyList(), mustPick = false), text)
            _ui.update { it.copy(thinking = null, typing = false) }
            if (r != null) {
                showLiveLine(ch, r)
                session.nudge(r.tone)?.let { toast(it) }
                return
            }
        }
        // offline acknowledgement (text only, clearly marked) + tone nudge so the words still count
        showLiveLine(ch, LiveAi.Result(offlineAcks[tone] ?: "हम्म।", if (tone == "rude") "angry" else if (tone == "kind" || tone == "funny") "smile" else "neutral", null, tone, "offline"))
        session.nudge(tone)?.let { toast(it) }
    }
}
