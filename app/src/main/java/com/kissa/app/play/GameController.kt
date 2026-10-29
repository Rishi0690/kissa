package com.kissa.app.play

import com.kissa.app.data.GameEntry
import com.kissa.app.game.*
import com.kissa.app.rig.Actor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ChatMsg(val fromPlayer: Boolean, val text: String, val time: String?, val system: Boolean = false, val seen: Boolean = false, val id: Long = System.nanoTime())
data class Caption(val speaker: String?, val text: String, val narration: Boolean, val player: Boolean = false)
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
    val basicVoiceUsed: Boolean = false,
    val busy: Boolean = true,
)

/**
 * Drives one playthrough on screen: pulls [Beat]s from the pure engine, performs them on the stage / chat (moves,
 * expressions, camera, voice with lip-sync, typing indicators) and waits for the player at decisions and open replies.
 * No network and no AI at any point (spec §8).
 */
class GameController(
    val entry: GameEntry,
    val session: GameSession,
    private val voice: VoicePlayer?,
    private val voiceOn: () -> Boolean,
    private val scope: CoroutineScope,
    val playerActor: Actor,
) {
    val game get() = entry.game
    val stage = StageState()
    private val _ui = MutableStateFlow(PlayUi())
    val ui: StateFlow<PlayUi> = _ui
    private var answer: CompletableDeferred<Any>? = null
    private var skip: CompletableDeferred<Unit>? = null
    private var job: Job? = null

    fun start() {
        voice?.onFallback = { _ui.update { it.copy(basicVoiceUsed = true) } }
        refreshStatus()
        job = scope.launch { runCatching { loop() }.onFailure { if (it !is CancellationException) android.util.Log.e("Kissa", "game loop", it) } }
    }
    fun stop() { job?.cancel(); voice?.stop() }

    /** Tap on the scene: finish the current line now. */
    fun tapSkip() { voice?.stop(); skip?.complete(Unit) }
    fun choose(index: Int) { answer?.complete(index) }
    fun reply(text: String) { if (text.isNotBlank()) answer?.complete(text.trim()) }

    private fun refreshStatus() {
        val st = session.state
        _ui.update { it.copy(rankName = session.rankName, rankScore = st.rankScore, stats = LinkedHashMap(st.stats), objective = game.objective(st.currentObjective)) }
    }

    private fun toast(o: Outcome) {
        val ts = o.deltas.map { d -> Toast(d.text, d.amount?.let { it >= 0 }) }
        _ui.update { it.copy(toasts = (it.toasts + ts).takeLast(5), rankUp = if (o.rankChanged) o.rankAfter else it.rankUp) }
        refreshStatus()
        scope.launch { delay(2600); _ui.update { u -> u.copy(toasts = u.toasts.filterNot { t -> ts.any { it.id == t.id } }) } }
        if (o.rankChanged) scope.launch { delay(2800); _ui.update { it.copy(rankUp = null) } }
    }

    private suspend fun hold(ms: Long) {
        val s = CompletableDeferred<Unit>(); skip = s
        withTimeoutOrNull(ms) { s.await() }
        skip = null
    }

    private fun readMs(text: String) = (900L + text.length * 55L).coerceAtMost(6500L)

    private suspend fun speak(text: String, voiceFile: String?) {
        if (voice != null && voiceOn()) {
            val s = CompletableDeferred<Unit>(); skip = s
            coroutineScope {
                val v = async { voice.play(entry, voiceFile, text) }
                select2(v, s)
            }
            skip = null
            hold(260)
        } else hold(readMs(text))
    }

    /** Waits for whichever finishes first: the voice line or a tap. */
    private suspend fun select2(v: Deferred<Boolean>, s: CompletableDeferred<Unit>) {
        while (!v.isCompleted && !s.isCompleted) delay(30)
        if (s.isCompleted) { voice?.stop(); v.cancel() }
    }

    private fun setupScene(sc: SceneDef) {
        val prevChat = _ui.value.chatWith
        stage.reset(sc.type, sc.background ?: "college_corridor", sc.time, sc.weather)
        val entering = HashSet<String>()
        fun scan(steps: List<Step>) { steps.forEach { if (it is Step.Enter) entering += it.char; if (it is Step.If) { scan(it.then); scan(it.otherwise) } } }
        scan(sc.steps)
        sc.cast.forEach { p ->
            val c = game.character(p.id) ?: return@forEach
            stage.actors[p.id] = Actor(p.id, c.rig, c.look).apply { x = p.at; targetX = p.at; facing = p.facing; visible = p.id !in entering }
        }
        if (sc.type == SceneType.CINEMATIC) {
            val minX = sc.cast.minOfOrNull { it.at } ?: 0.6f
            stage.player = playerActor.apply { x = (minX - 0.24f).coerceAtLeast(0.12f); targetX = x; facing = 1; visible = true; expression = "neutral"; play("idle") }
        } else stage.player = null
        stage.setAudience(sc.audience, sc.id.hashCode())
        val keepChat = sc.type == SceneType.CHAT && sc.chatWith == prevChat
        _ui.update {
            it.copy(sceneType = sc.type, sceneTitle = sc.title, showTitle = sc.title != null, caption = null, decision = null, reply = null,
                chatWith = if (sc.type == SceneType.CHAT) sc.chatWith else null, chatHeader = sc.chatHeader ?: game.character(sc.chatWith)?.name,
                chat = if (keepChat) it.chat else emptyList(), blocked = if (keepChat) it.blocked else false, typing = false)
        }
        if (sc.title != null) scope.launch { delay(1600); _ui.update { it.copy(showTitle = false) } }
    }

    private fun actorFor(id: String): Actor? = stage.actors[id] ?: game.character(id)?.let { c ->
        Actor(id, c.rig, c.look).also { a -> a.x = 0.75f; a.targetX = 0.75f; stage.actors[id] = a }
    }

    private suspend fun loop() {
        while (true) {
            when (val b = session.next()) {
                is Beat.SceneStart -> { setupScene(b.scene); refreshStatus(); if (b.scene.title != null) delay(700) }
                is Beat.Line -> line(b)
                is Beat.Narration -> {
                    _ui.update { it.copy(caption = Caption(null, b.text, narration = true)) }
                    speak(b.text, b.voiceFile)
                }
                is Beat.PlayerLine -> {
                    if (stage.type == SceneType.CHAT) { _ui.update { it.copy(chat = it.chat + ChatMsg(true, b.text, b.time)) }; hold(700) }
                    else { _ui.update { it.copy(caption = Caption(session.state.playerName, b.text, false, player = true)) }; hold(readMs(b.text)) }
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
        if (stage.type == SceneType.CHAT) {
            if (_ui.value.blocked) return
            _ui.update { u -> u.copy(typing = true, chat = u.chat.map { if (it.fromPlayer) it.copy(seen = true) else it }) }
            hold((s.delayMs.takeIf { it > 0 } ?: 900).toLong().coerceAtMost(2600))
            _ui.update { it.copy(typing = false, chat = it.chat + ChatMsg(false, b.text, s.time)) }
            speak(b.text, s.voiceFile)
            return
        }
        val a = actorFor(s.char)
        a?.let {
            it.visible = true
            s.emotion?.let { e -> it.expression = e }
            s.move?.let { m -> if (m in Moves2.locomotion) { if (m == "walk_away") it.play("walk_away") else it.play(m) } else it.play(m) }
            s.prop?.let { p -> it.props[Catalog.defaultAnchor(p)] = p }
        }
        stage.speaking = s.char
        if (stage.type == SceneType.CINEMATIC) { stage.focusActor = s.char; stage.targetZoom = if (b.text.length < 40) 1.18f else 1.08f }
        _ui.update { it.copy(caption = Caption(name, b.text, false)) }
        speak(b.text, s.voiceFile)
        stage.speaking = null
    }

    private suspend fun perform(st: Step) {
        when (st) {
            is Step.Enter -> { actorFor(st.char)?.enterFrom(st.from, st.at); delay(1300) }
            is Step.Exit -> { actorFor(st.char)?.exitTo(st.to); delay(900) }
            is Step.Move -> actorFor(st.char)?.let { a -> if (st.to != null) a.walkTo(st.to) else a.play(st.move) }.also { delay(400) }
            is Step.Emote -> { actorFor(st.char)?.expression = st.emotion; delay(450) }
            is Step.Camera -> {
                st.zoom?.let { stage.targetZoom = it }
                st.focus?.let { stage.focusActor = it }
                st.pan?.let { stage.targetFocus = it }
                if (st.shake > 0f) stage.shake = st.shake
                delay(450)
            }
            is Step.Fx -> {
                st.time?.let { stage.time = it }; st.weather?.let { stage.weather = it }
                if (st.flash) stage.flash = 1f
                if (st.sparkles) stage.sparkles = 2.5f
                delay(300)
            }
            is Step.Prop -> actorFor(st.char)?.let { a -> if (st.prop == null) a.props.remove(st.anchor) else a.props[st.anchor] = st.prop }
            is Step.Audience -> { stage.react(st.react); delay(650) }
            is Step.Wait -> hold(st.ms.toLong())
            else -> {}
        }
    }

    private suspend fun decision(d: Beat.Decision) {
        val ans = CompletableDeferred<Any>(); answer = ans
        _ui.update { it.copy(decision = d, busy = false, caption = if (stage.type == SceneType.CHAT) null else it.caption) }
        val idx = ans.await() as Int
        answer = null
        val c = d.choices.first { it.index == idx }.value
        _ui.update { it.copy(decision = null, busy = true) }
        val text = session.substitute(c.text)
        if (stage.type == SceneType.CHAT) {
            when (c.kind) {
                "ignore" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "Tumne reply nahi kiya.", null, system = true)) }
                "block" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "Tumne ${_ui.value.chatHeader ?: "unhe"} ko block kar diya.", null, system = true), blocked = true) }
                "delay" -> _ui.update { it.copy(chat = it.chat + ChatMsg(false, "⏱ 2 ghante baad…", null, system = true) + ChatMsg(true, text.substringAfter(": ").removeSuffix(")").trim('(', ' ', '\''), null)) }
                else -> _ui.update { it.copy(chat = it.chat + ChatMsg(true, text, null)) }
            }
        } else {
            playerActor.expression = when (c.style) { "confident" -> "smug"; "timid" -> "worried"; "rude" -> "angry"; "funny" -> "laugh"; "kind" -> "smile"; "cold" -> "bored"; else -> "neutral" }
            if (c.style == "confident") playerActor.play("hands_on_hips") else if (c.style == "timid") playerActor.play("look_down")
            _ui.update { it.copy(caption = Caption(if (c.kind == "action") null else session.state.playerName, text, narration = c.kind == "action", player = true)) }
            stage.speaking = "player"; stage.focusActor = null
        }
        val o = session.choose(idx)
        toast(o)
        hold(if (stage.type == SceneType.CHAT) 500 else readMs(text).coerceAtMost(2600))
        stage.speaking = null
    }

    private suspend fun openReply(r: Step.OpenReply) {
        val ans = CompletableDeferred<Any>(); answer = ans
        _ui.update { it.copy(reply = r, busy = false) }
        val v = ans.await()
        answer = null
        _ui.update { it.copy(reply = null, busy = true) }
        val text = v as String
        if (stage.type == SceneType.CHAT) _ui.update { it.copy(chat = it.chat + ChatMsg(true, text, null)) }
        else _ui.update { it.copy(caption = Caption(session.state.playerName, text, false, player = true)) }
        val o = session.reply(text)
        if (o.fallback) _ui.update { it.copy(toasts = it.toasts + Toast("Unmatched reply", null)) }
        toast(o)
        hold(if (stage.type == SceneType.CHAT) 600 else 1400)
    }
}

/** Local alias so the controller does not depend on rig internals beyond the public move names. */
private object Moves2 { val locomotion = com.kissa.app.rig.Moves.locomotion }
