package com.kissa.app.game

import kotlinx.serialization.json.*

/** Objective lifecycle. */
enum class ObjStatus { NONE, AVAILABLE, ACTIVE, DONE, FAILED, DECLINED }

/**
 * Complete game state (spec §2.6): scene, stats, rank score, flags, inventory, objectives, plus bookkeeping
 * (visited scenes, reached checkpoints). Deep-copyable and JSON-serialisable for saves.
 */
data class GameState(
    var sceneId: String,
    val stats: LinkedHashMap<String, Int>,
    var rankScore: Int,
    val flags: LinkedHashMap<String, V>,
    val items: LinkedHashSet<String>,
    val objectives: LinkedHashMap<String, ObjStatus>,
    var currentObjective: String?,
    val visited: LinkedHashSet<String>,
    var playerName: String,
    var ending: String? = null,
) {
    fun copy(): GameState = GameState(sceneId, LinkedHashMap(stats), rankScore, LinkedHashMap(flags), LinkedHashSet(items), LinkedHashMap(objectives), currentObjective, LinkedHashSet(visited), playerName, ending)

    /** Key used by the reachability search: everything that can influence future play (not bookkeeping). */
    fun stateKey(): String = buildString {
        append(stats.values.joinToString(",")); append('|'); append(rankScore); append('|')
        flags.entries.sortedBy { it.key }.forEach { append(it.key).append('=').append(it.value).append(';') }
        append('|'); append(items.sorted().joinToString(",")); append('|')
        objectives.entries.sortedBy { it.key }.forEach { append(it.key).append(it.value.ordinal) }
        append('|'); append(currentObjective)
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("scene", sceneId); put("rank_score", rankScore); put("player_name", playerName)
        currentObjective?.let { put("current_objective", it) }
        ending?.let { put("ending", it) }
        put("stats", buildJsonObject { stats.forEach { (k, v) -> put(k, v) } })
        put("flags", buildJsonObject { flags.forEach { (k, v) -> put(k, v.toJson()) } })
        put("items", JsonArray(items.map { JsonPrimitive(it) }))
        put("objectives", buildJsonObject { objectives.forEach { (k, v) -> put(k, v.name) } })
        put("visited", JsonArray(visited.map { JsonPrimitive(it) }))
    }

    companion object {
        fun initial(game: Game, playerName: String): GameState = GameState(
            sceneId = game.start,
            stats = LinkedHashMap(game.stats.mapValues { it.value.start.coerceIn(it.value.min, it.value.max) }),
            rankScore = game.rank.score,
            flags = LinkedHashMap(game.flags),
            items = LinkedHashSet(),
            objectives = LinkedHashMap(game.objectives.associate { it.id to ObjStatus.NONE }),
            currentObjective = null,
            visited = LinkedHashSet(),
            playerName = playerName,
        )

        fun fromJson(o: JsonObject): GameState = GameState(
            sceneId = o.str("scene") ?: "",
            stats = LinkedHashMap(o.obj("stats")?.mapValues { (it.value as JsonPrimitive).double.toInt() } ?: emptyMap()),
            rankScore = o.int("rank_score") ?: 0,
            flags = LinkedHashMap(o.obj("flags")?.mapValues { V.of(it.value as JsonPrimitive) } ?: emptyMap()),
            items = LinkedHashSet(o.strList("items")),
            objectives = LinkedHashMap(o.obj("objectives")?.mapValues { runCatching { ObjStatus.valueOf((it.value as JsonPrimitive).content) }.getOrDefault(ObjStatus.NONE) } ?: emptyMap()),
            currentObjective = o.str("current_objective"),
            visited = LinkedHashSet(o.strList("visited")),
            playerName = o.str("player_name") ?: "",
            ending = o.str("ending"),
        )
    }
}

/** One visible consequence of an effect, for the "trust -5" toasts and the status view. */
data class Delta(val label: String, val amount: Int?, val text: String) {
    override fun toString() = text
}

data class Outcome(
    val deltas: List<Delta>,
    val rankBefore: String,
    val rankAfter: String,
    val style: String?,
    val intentId: String? = null,
    val fallback: Boolean = false,
) { val rankChanged get() = rankBefore != rankAfter }

/** What the presentation layer has to show next. */
sealed class Beat {
    data class SceneStart(val scene: SceneDef, val checkpoint: String?) : Beat()
    data class Line(val step: Step.Say, val text: String, val character: CharacterDef?) : Beat()
    data class Narration(val text: String, val voiceFile: String?) : Beat()
    data class PlayerLine(val text: String, val time: String?) : Beat()
    data class Stage(val step: Step) : Beat()
    data class Effects(val outcome: Outcome) : Beat()
    data class Decision(val prompt: String?, val choices: List<IndexedValue<Choice>>, val style: SceneType) : Beat()
    data class Reply(val step: Step.OpenReply) : Beat()
    data class Ending(val ending: EndingDef?) : Beat()
}

/**
 * Runs one playthrough. Pure Kotlin: no Android, no IO, so the same engine drives the app, the tests and the
 * validator's reachability search. Call [next] until it returns a [Beat.Decision] / [Beat.Reply]; answer with
 * [choose] / [reply]; [Beat.Ending] ends play.
 */
class GameSession private constructor(val game: Game, val state: GameState, private val frames: ArrayDeque<Frame>, private var pending: Beat?, private var started: Boolean) {
    private data class Frame(val steps: List<Step>, var index: Int, val sceneLevel: Boolean)

    constructor(game: Game, state: GameState) : this(game, state, ArrayDeque(), null, false)

    /** Checkpoint hook: called with the state at entry of every scene that is a checkpoint (and every scene for autosave). */
    var onSceneEntered: ((scene: SceneDef, snapshot: GameState) -> Unit)? = null
    var onEnding: ((EndingDef?, GameState) -> Unit)? = null

    fun copy(): GameSession = GameSession(game, state.copy(), ArrayDeque(frames.map { it.copy() }), pending, started).also { it.playerRig = playerRig }

    val scene: SceneDef get() = game.scenes[state.sceneId] ?: throw GameFormatException("missing scene ${state.sceneId}")
    val rankName: String get() = game.rank.name(state.rankScore)
    val awaiting: Beat? get() = pending
    val finished: Boolean get() = pending is Beat.Ending

    /** Player rig ("male"/"female") picks the variant in `{m:karta|f:karti}` (player-facing text only; voiced lines stay neutral). */
    var playerRig: String = "male"

    fun substitute(text: String): String = text.replace("{player}", state.playerName).replace("{name}", state.playerName)
        .replace(GENDER) { m -> if (playerRig == "female") m.groupValues[2] else m.groupValues[1] }

    // ---------------- scope for conditions ----------------
    val scope = Condition.Scope { name ->
        when {
            name in state.stats -> V.Num(state.stats.getValue(name).toDouble())
            name == "rank" || name == "rank_score" -> V.Num(state.rankScore.toDouble())
            name == "rank_name" -> V.Str(rankName)
            name == "rank_level" -> V.Num(game.rank.levelIndex(state.rankScore).toDouble())
            name in state.flags -> state.flags[name]
            name.startsWith("has(") -> V.Bool(name.removePrefix("has(").removeSuffix(")") in state.items)
            name.startsWith("item.") -> V.Bool(name.removePrefix("item.") in state.items)
            name.startsWith("obj.") -> V.Str((state.objectives[name.removePrefix("obj.")] ?: ObjStatus.NONE).name.lowercase())
            name.startsWith("visited.") -> V.Bool(name.removePrefix("visited.") in state.visited)
            name.startsWith("visited(") -> V.Bool(name.removePrefix("visited(").removeSuffix(")") in state.visited)
            name == "player_name" -> V.Str(state.playerName)
            else -> null
        }
    }

    fun test(cond: String?): Boolean = cond.isNullOrBlank() || Condition.eval(cond, scope)

    // ---------------- running ----------------
    fun next(): Beat {
        pending?.let { return it }
        if (!started) { started = true; return enter(state.sceneId) }
        while (true) {
            val f = frames.lastOrNull()
            if (f == null) return sceneEnd()
            if (f.index >= f.steps.size) { frames.removeLast(); continue }
            val s = f.steps[f.index++]
            when (s) {
                is Step.Say -> return Beat.Line(s, substitute(s.text), game.character(s.char))
                is Step.Narrate -> return Beat.Narration(substitute(s.text), s.voiceFile)
                is Step.PlayerSay -> return Beat.PlayerLine(substitute(s.text), s.time)
                is Step.SetFx -> { val o = apply(s.effects, null, 0); if (o.deltas.isNotEmpty() || o.rankChanged) return Beat.Effects(o) }
                is Step.Objective -> { val o = apply(listOf(Effect.ObjectiveChange(s.id, s.action)), null, 0); return Beat.Effects(o) }
                is Step.If -> { val branch = if (test(s.condition)) s.then else s.otherwise; if (branch.isNotEmpty()) frames.addLast(Frame(branch, 0, false)) }
                is Step.Goto -> return enter(s.scene)
                is Step.ResolveEnding -> return resolveEnding()
                is Step.Checkpoint -> onSceneEntered?.invoke(scene.copy(checkpoint = s.label), state.copy())
                is Step.OpenReply -> { pending = Beat.Reply(s); return pending!! }
                else -> return Beat.Stage(s)
            }
        }
    }

    private fun enter(sceneId: String): Beat {
        val sc = game.scenes[sceneId] ?: throw GameFormatException("goto missing scene $sceneId")
        state.sceneId = sceneId
        frames.clear()
        val snapshot = state.copy()
        state.visited += sceneId
        sc.endingId?.let { state.ending = it }
        frames.addLast(Frame(sc.steps, 0, true))
        onSceneEntered?.invoke(sc, snapshot)
        return Beat.SceneStart(sc, sc.checkpoint)
    }

    private fun sceneEnd(): Beat {
        val sc = scene
        val visible = sc.choices.withIndex().filter { test(it.value.condition) }
        if (visible.isNotEmpty()) { pending = Beat.Decision(sc.choicePrompt?.let(::substitute), visible, sc.type); return pending!! }
        sc.next?.let { return enter(it) }
        if (sc.endingId != null) { val e = game.ending(sc.endingId); pending = Beat.Ending(e); onEnding?.invoke(e, state.copy()); return pending!! }
        return resolveEnding()
    }

    /** Endings are an ordered condition list; the first one the state satisfies wins (spec §6). */
    fun pickEnding(): EndingDef? = game.endings.firstOrNull { test(it.condition) }

    private fun resolveEnding(): Beat {
        val e = pickEnding()
        if (e != null && e.scene.isNotBlank() && game.scenes.containsKey(e.scene) && state.sceneId != e.scene) return enter(e.scene)
        state.ending = e?.id
        pending = Beat.Ending(e); onEnding?.invoke(e, state.copy()); return pending!!
    }

    /** Pick preset choice [index] (index into the scene's full choice list, as given in [Beat.Decision]). */
    fun choose(index: Int): Outcome {
        val d = pending as? Beat.Decision ?: throw IllegalStateException("no decision pending")
        val c = d.choices.firstOrNull { it.index == index }?.value ?: throw IllegalArgumentException("choice $index not available")
        pending = null
        val o = apply(c.effects, c.style, c.score)
        frames.clear()
        val after = ArrayList<Step>(c.reaction)
        after += if (c.goto != null) Step.Goto(c.goto) else Step.ResolveEnding()
        frames.addLast(Frame(after, 0, false))
        return o
    }

    /** Answer the pending open reply with free text (typed or dictated). Unmatched text takes the fallback. */
    fun reply(text: String): Outcome {
        val r = pending as? Beat.Reply ?: throw IllegalStateException("no open reply pending")
        val m = IntentMatcher.match(text, r.step.intents)
        return if (m != null) replyIntent(m.index) else replyFallback()
    }

    fun replyIntent(i: Int): Outcome {
        val r = pending as? Beat.Reply ?: throw IllegalStateException("no open reply pending")
        val intent = r.step.intents[i]
        pending = null
        val o = apply(intent.effects, intent.style, intent.score).copy(intentId = intent.id)
        val after = ArrayList<Step>(intent.reaction)
        intent.goto?.let { after += Step.Goto(it) }
        frames.addLast(Frame(after, 0, false))
        return o
    }

    fun replyFallback(): Outcome {
        val r = pending as? Beat.Reply ?: throw IllegalStateException("no open reply pending")
        pending = null
        val fb = r.step.fallback ?: Fallback(emptyList(), emptyList(), null)
        val o = apply(fb.effects, null, 0).copy(fallback = true)
        val after = ArrayList<Step>(fb.reaction)
        fb.goto?.let { after += Step.Goto(it) }
        frames.addLast(Frame(after, 0, false))
        return o
    }

    // ---------------- effects ----------------
    fun apply(effects: List<Effect>, style: String?, score: Int): Outcome {
        val before = rankName
        val deltas = ArrayList<Delta>()
        if (score != 0) { state.rankScore += score; deltas += Delta("Rank", score, "Rank ${sign(score)}") }
        for (e in effects) applyOne(e, deltas)
        return Outcome(deltas, before, rankName, style)
    }

    private fun sign(n: Int) = if (n >= 0) "+$n" else "$n"

    private fun applyOne(e: Effect, deltas: MutableList<Delta>) {
        when (e) {
            is Effect.StatDelta -> {
                val def = game.stats[e.stat] ?: return
                val old = state.stats[e.stat] ?: def.start
                val nv = (old + e.delta).coerceIn(def.min, def.max)
                state.stats[e.stat] = nv
                if (!def.hidden) deltas += Delta(def.label, nv - old, "${def.label} ${sign(e.delta)}")
            }
            is Effect.FlagSet -> state.flags[e.flag] = e.value
            is Effect.Rank -> { state.rankScore += e.delta; deltas += Delta("Rank", e.delta, "Rank ${sign(e.delta)}") }
            is Effect.AddItem -> if (state.items.add(e.item)) deltas += Delta(game.items[e.item] ?: e.item, null, "+ ${game.items[e.item] ?: e.item}")
            is Effect.RemoveItem -> if (state.items.remove(e.item)) deltas += Delta(game.items[e.item] ?: e.item, null, "- ${game.items[e.item] ?: e.item}")
            is Effect.ObjectiveChange -> {
                val def = game.objective(e.id) ?: return
                when (e.action) {
                    "offer" -> if (state.objectives[e.id] == ObjStatus.NONE) state.objectives[e.id] = ObjStatus.AVAILABLE
                    "start" -> { state.objectives[e.id] = ObjStatus.ACTIVE; state.currentObjective = e.id; deltas += Delta("Objective", null, "New objective: ${def.text}") }
                    "decline" -> { state.objectives[e.id] = ObjStatus.DECLINED; if (state.currentObjective == e.id) state.currentObjective = null }
                    "complete" -> if (state.objectives[e.id] != ObjStatus.DONE) {
                        state.objectives[e.id] = ObjStatus.DONE
                        deltas += Delta("Objective", null, "Objective complete: ${def.text}")
                        def.onComplete.forEach { applyOne(it, deltas) }
                        def.unlocks?.let { u -> if (game.objective(u) != null && state.objectives[u] == ObjStatus.NONE) state.objectives[u] = ObjStatus.AVAILABLE }
                        if (state.currentObjective == e.id) state.currentObjective = state.objectives.entries.lastOrNull { it.value == ObjStatus.ACTIVE }?.key
                    }
                    "fail" -> if (state.objectives[e.id] != ObjStatus.FAILED) {
                        state.objectives[e.id] = ObjStatus.FAILED
                        deltas += Delta("Objective", null, "Objective failed: ${def.text}")
                        def.onFail.forEach { applyOne(it, deltas) }
                        if (state.currentObjective == e.id) state.currentObjective = state.objectives.entries.lastOrNull { it.value == ObjStatus.ACTIVE }?.key
                    }
                }
            }
        }
    }

    companion object {
        val GENDER = Regex("\\{m:([^|}]*)\\|f:([^}]*)\\}")
        /** Restore a saved snapshot (taken at a scene entry): play resumes at the start of that scene with that exact state. */
        fun resume(game: Game, snapshot: GameState): GameSession = GameSession(game, snapshot.copy())
    }
}
