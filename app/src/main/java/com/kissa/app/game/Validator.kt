package com.kissa.app.game

/**
 * Publish validator (spec §6/§7): structure, catalog asset IDs, targets, conditions, open-reply fallbacks, effect-free
 * choices, voice files, content filter — plus a reachability search that plays every branch of the real engine
 * (deduplicating on scene position + full state) and reports which endings can be reached and by which path.
 */
class Validator(
    private val voiceExists: (String) -> Boolean = { true },
    private val bannedWords: List<String> = DEFAULT_BANNED,
    private val maxStates: Int = 200_000,
) {
    enum class Severity { ERROR, WARN }
    data class Issue(val severity: Severity, val where: String, val message: String) {
        override fun toString() = "${severity.name.padEnd(5)} $where: $message"
    }
    data class EndingReach(val ending: EndingDef, val reachable: Boolean, val examplePath: List<String>, val pathsFound: Int)
    data class Report(
        val gameId: String,
        val issues: List<Issue>,
        val endings: List<EndingReach>,
        val statesExplored: Int,
        val truncated: Boolean,
        val unvisitedScenes: List<String>,
        val dialogueChars: Int,
        val voicedLines: Int,
        val missingVoice: Int,
    ) {
        val errors get() = issues.filter { it.severity == Severity.ERROR }
        val warnings get() = issues.filter { it.severity == Severity.WARN }
        val ok get() = errors.isEmpty()
        fun render(): String = buildString {
            appendLine("== Reachability report: $gameId ==")
            appendLine("States explored: $statesExplored${if (truncated) " (TRUNCATED)" else ""}")
            for (e in endings) {
                appendLine("  [${if (e.reachable) "REACHABLE" else "UNREACHABLE"}] ${e.ending.id} \"${e.ending.title}\" if (${e.ending.condition}) — ${e.pathsFound} terminal state(s)")
                if (e.reachable) appendLine("      e.g. " + e.examplePath.joinToString(" → "))
            }
            if (unvisitedScenes.isNotEmpty()) appendLine("Scenes never reached: ${unvisitedScenes.joinToString()}")
            appendLine("Dialogue: $dialogueChars chars, $voicedLines lines, missing voice files: $missingVoice")
            appendLine("Errors: ${errors.size}, warnings: ${warnings.size}")
            issues.forEach { appendLine("  $it") }
        }
    }

    fun validate(game: Game): Report {
        val issues = ArrayList<Issue>()
        fun err(w: String, m: String) { issues += Issue(Severity.ERROR, w, m) }
        fun warn(w: String, m: String) { issues += Issue(Severity.WARN, w, m) }
        val charIds = game.characters.map { it.id }.toSet()
        var chars = 0; var voiced = 0; var missing = 0

        if (game.start !in game.scenes) err("game", "start scene '${game.start}' does not exist")
        if (game.endings.size < 2) err("game", "needs at least two endings (has ${game.endings.size})")
        if (game.stats.isEmpty()) warn("game", "declares no stats")
        if (game.rank.levels.size < 2) warn("rank", "rank ladder has fewer than two levels")
        if (game.endings.lastOrNull()?.condition?.trim() != "true") warn("endings", "last ending is not a catch-all (\"true\"); some final states may reach no ending")

        game.characters.forEach { c ->
            if (c.rig !in Catalog.rigs) err("character ${c.id}", "unknown rig '${c.rig}'")
            if (c.voice.slot.isBlank()) err("character ${c.id}", "no voice slot")
            val l = c.look
            if (l.hair !in Catalog.hairs) err("character ${c.id}", "unknown hair '${l.hair}'")
            if (l.hairColor !in Catalog.hairColors) err("character ${c.id}", "unknown hair colour '${l.hairColor}'")
            if (l.outfit !in Catalog.outfits) err("character ${c.id}", "unknown outfit '${l.outfit}'")
            if (l.outfitColor !in Catalog.outfitColors) err("character ${c.id}", "unknown outfit colour '${l.outfitColor}'")
            if (l.skin !in 0 until Catalog.skins) err("character ${c.id}", "skin ${l.skin} out of range")
            if (l.face !in Catalog.faces) err("character ${c.id}", "unknown face '${l.face}'")
            l.accessories.forEach { if (it !in Catalog.accessories) err("character ${c.id}", "unknown accessory '$it'") }
        }

        val knownIds = HashSet<String>().apply {
            addAll(game.stats.keys); addAll(game.flags.keys); addAll(listOf("rank", "rank_score", "rank_name", "rank_level", "player_name"))
        }
        // flags set anywhere count as declared
        fun collectFlags(effects: List<Effect>) = effects.filterIsInstance<Effect.FlagSet>().forEach { knownIds += it.flag }
        fun walkSteps(steps: List<Step>, f: (Step) -> Unit) {
            for (s in steps) {
                f(s)
                when (s) {
                    is Step.If -> { walkSteps(s.then, f); walkSteps(s.otherwise, f) }
                    is Step.OpenReply -> { s.intents.forEach { walkSteps(it.reaction, f) }; s.fallback?.let { walkSteps(it.reaction, f) } }
                    else -> {}
                }
            }
        }
        game.scenes.values.forEach { sc ->
            walkSteps(sc.steps) { s -> when (s) { is Step.SetFx -> collectFlags(s.effects); is Step.OpenReply -> { s.intents.forEach { collectFlags(it.effects) }; s.fallback?.let { collectFlags(it.effects) } }; else -> {} } }
            sc.choices.forEach { collectFlags(it.effects); walkSteps(it.reaction) { s -> if (s is Step.SetFx) collectFlags(s.effects) } }
        }
        game.objectives.forEach { collectFlags(it.onComplete); collectFlags(it.onFail) }

        fun checkCond(where: String, c: String?) {
            if (c.isNullOrBlank()) return
            try {
                Condition.identifiers(c).forEach { id ->
                    val ok = id in knownIds || id.startsWith("has(") || id.startsWith("item.") || id.startsWith("obj.") || id.startsWith("visited")
                    if (!ok) warn(where, "condition '$c' uses undeclared '$id' (evaluates false)")
                    if (id.startsWith("obj.") && game.objective(id.removePrefix("obj.")) == null) err(where, "condition references unknown objective '$id'")
                    if (id.startsWith("visited.") && id.removePrefix("visited.") !in game.scenes) err(where, "condition references unknown scene '$id'")
                }
            } catch (e: Exception) { err(where, "bad condition '$c': ${e.message}") }
        }
        fun target(where: String, t: String?) { if (t != null && t !in game.scenes) err(where, "target scene '$t' does not exist") }
        fun checkEffects(where: String, effects: List<Effect>) = effects.forEach { e ->
            when (e) {
                is Effect.ObjectiveChange -> if (game.objective(e.id) == null) err(where, "unknown objective '${e.id}'")
                is Effect.AddItem -> if (game.items.isNotEmpty() && e.item !in game.items) warn(where, "item '${e.item}' not declared")
                else -> {}
            }
        }
        fun text(where: String, t: String) {
            val low = t.lowercase()
            bannedWords.firstOrNull { w -> Regex("(^|[^\\p{L}])${Regex.escape(w)}($|[^\\p{L}])").containsMatchIn(low) }?.let { err(where, "text fails content filter ('$it')") }
        }
        fun checkSteps(where: String, sc: SceneDef, steps: List<Step>) {
            steps.forEachIndexed { i, s ->
                val w = "$where step ${i + 1}"
                when (s) {
                    is Step.Say -> {
                        if (s.char !in charIds) err(w, "unknown character '${s.char}'")
                        s.emotion?.let { if (it !in Catalog.expressions) err(w, "unknown expression '$it'") }
                        s.move?.let { if (it !in Catalog.moves) err(w, "unknown move '$it'") }
                        s.prop?.let { if (it !in Catalog.props) err(w, "unknown prop '$it'") }
                        text(w, s.text); chars += s.text.length
                        val dynamic = s.text.contains("{player}") || s.text.contains("{name}")
                        when {
                            s.voiceFile == null && dynamic -> { missing++; warn(w, "line uses the player's name: no pre-generated voice, emergency phone TTS (flagged)") }
                            s.voiceFile == null -> { missing++; warn(w, "no voice_file: emergency phone TTS fallback") }
                            !voiceExists(s.voiceFile) -> { missing++; warn(w, "voice file '${s.voiceFile}' missing: emergency phone TTS fallback") }
                            else -> voiced++
                        }
                    }
                    is Step.Narrate -> { text(w, s.text) }
                    is Step.PlayerSay -> text(w, s.text)
                    is Step.SetFx -> checkEffects(w, s.effects)
                    is Step.Move -> { if (s.char !in charIds) err(w, "unknown character '${s.char}'"); if (s.move !in Catalog.moves) err(w, "unknown move '${s.move}'") }
                    is Step.Enter -> if (s.char !in charIds) err(w, "unknown character '${s.char}'")
                    is Step.Exit -> if (s.char !in charIds) err(w, "unknown character '${s.char}'")
                    is Step.Emote -> { if (s.char !in charIds) err(w, "unknown character '${s.char}'"); if (s.emotion !in Catalog.expressions) err(w, "unknown expression '${s.emotion}'") }
                    is Step.Prop -> { if (s.char !in charIds) err(w, "unknown character '${s.char}'"); s.prop?.let { if (it !in Catalog.props) err(w, "unknown prop '$it'") }; if (s.anchor !in Catalog.anchors) err(w, "unknown anchor '${s.anchor}'") }
                    is Step.Fx -> { s.time?.let { if (it !in Catalog.times) err(w, "unknown time '$it'") }; s.weather?.let { if (it !in Catalog.weathers) err(w, "unknown weather '$it'") } }
                    is Step.Music -> s.track?.let { if (it !in Catalog.music) err(w, "unknown music '$it'") }
                    is Step.If -> { checkCond(w, s.condition); checkSteps("$w/then", sc, s.then); checkSteps("$w/else", sc, s.otherwise) }
                    is Step.Goto -> target(w, s.scene)
                    is Step.Objective -> if (game.objective(s.id) == null) err(w, "unknown objective '${s.id}'")
                    is Step.OpenReply -> {
                        if (s.fallback == null) err(w, "open_reply has no fallback branch (mandatory)")
                        if (s.intents.isEmpty()) err(w, "open_reply has no intents")
                        s.intents.forEach { it2 ->
                            if (it2.match.isEmpty()) err("$w intent ${it2.id}", "no keywords")
                            if (it2.match.size < 3) warn("$w intent ${it2.id}", "only ${it2.match.size} keyword(s); add synonyms and Roman-Hinglish spellings")
                            target("$w intent ${it2.id}", it2.goto); checkEffects("$w intent ${it2.id}", it2.effects); checkSteps("$w intent ${it2.id} reaction", sc, it2.reaction)
                        }
                        s.fallback?.let { target("$w fallback", it.goto); checkEffects("$w fallback", it.effects); checkSteps("$w fallback reaction", sc, it.reaction) }
                        s.presets.forEach { p -> if (IntentMatcher.match(p, s.intents) == null) warn(w, "preset '$p' matches no intent (takes fallback)") }
                    }
                    else -> {}
                }
            }
        }
        game.scenes.values.forEach { sc ->
            val w = "scene ${sc.id}"
            if (sc.type != SceneType.CHAT) { if (sc.background == null) err(w, "no background") else if (sc.background !in Catalog.backgrounds) err(w, "unknown background '${sc.background}'") }
            if (sc.time !in Catalog.times) err(w, "unknown time '${sc.time}'")
            if (sc.weather !in Catalog.weathers) err(w, "unknown weather '${sc.weather}'")
            sc.music?.let { if (it !in Catalog.music) err(w, "unknown music '$it'") }
            sc.cast.forEach { if (it.id !in charIds) err(w, "cast has unknown character '${it.id}'") }
            if (sc.type == SceneType.CHAT && sc.chatWith != null && sc.chatWith !in charIds) err(w, "chat partner '${sc.chatWith}' unknown")
            target(w, sc.next)
            sc.endingId?.let { if (game.ending(it) == null) err(w, "ending '$it' not declared") }
            checkSteps(w, sc, sc.steps)
            sc.choices.forEachIndexed { i, c ->
                val cw = "$w choice ${i + 1}"
                target(cw, c.goto); checkCond(cw, c.condition); checkEffects(cw, c.effects); checkSteps("$cw reaction", sc, c.reaction); text(cw, c.text)
                if (c.style !in Catalog.styles) warn(cw, "unknown style '${c.style}'")
                if (c.effects.isEmpty() && c.score == 0) warn(cw, "choice '${c.text}' changes nothing (effect-free)")
            }
            val hasExit = sc.choices.isNotEmpty() || sc.next != null || sc.endingId != null || sc.resolveEnding ||
                sc.steps.any { it is Step.Goto || it is Step.ResolveEnding || it is Step.OpenReply || it is Step.If }
            if (!hasExit) warn(w, "scene has no exit: play resolves the ending here")
        }
        game.endings.forEach { e ->
            checkCond("ending ${e.id}", e.condition)
            if (e.scene.isNotBlank() && e.scene !in game.scenes) err("ending ${e.id}", "scene '${e.scene}' does not exist")
        }
        game.objectives.forEach { o ->
            checkEffects("objective ${o.id}", o.onComplete); checkEffects("objective ${o.id}", o.onFail)
            if (o.givenBy != null && o.givenBy !in charIds) err("objective ${o.id}", "given_by unknown character '${o.givenBy}'")
            if (o.unlocks != null && o.unlocks !in game.scenes && game.objective(o.unlocks) == null) err("objective ${o.id}", "unlocks unknown '${o.unlocks}'")
        }

        // ---- reachability: exhaustive search over the real engine ----
        val reach = HashMap<String, Pair<List<String>, Int>>()
        val visitedScenes = HashSet<String>()
        var explored = 0; var truncated = false
        if (issues.none { it.severity == Severity.ERROR && it.message.contains("does not exist") }) {
            val seen = HashSet<String>()
            val queue = ArrayDeque<Pair<GameSession, List<String>>>()
            queue.add(GameSession(game, GameState.initial(game, "Player")) to emptyList())
            while (queue.isNotEmpty()) {
                if (explored >= maxStates) { truncated = true; break }
                val (s, path) = queue.removeFirst()
                var beat: Beat
                var guard = 0
                try {
                    while (true) {
                        beat = s.next(); visitedScenes += s.state.sceneId
                        if (beat is Beat.Decision || beat is Beat.Reply || beat is Beat.Ending) break
                        if (++guard > 10_000) { err("scene ${s.state.sceneId}", "infinite loop without a decision"); beat = Beat.Ending(null); break }
                    }
                } catch (e: Exception) { err("scene ${s.state.sceneId}", "engine error: ${e.message}"); continue }
                explored++
                when (beat) {
                    is Beat.Ending -> {
                        val id = beat.ending?.id ?: "(none)"
                        if (beat.ending == null) err("endings", "a path ends with no ending matching: ${path.joinToString(" → ")}")
                        val prev = reach[id]
                        reach[id] = (prev?.first ?: path) to ((prev?.second ?: 0) + 1)
                    }
                    is Beat.Decision -> beat.choices.forEach { (idx, c) ->
                        val n = s.copy(); n.choose(idx)
                        val key = "${n.state.sceneId}#c$idx#${s.state.sceneId}|" + n.state.stateKey()
                        if (seen.add(key)) queue.add(n to path + "[${s.state.sceneId}] ${c.text}")
                    }
                    is Beat.Reply -> {
                        beat.step.intents.forEachIndexed { i, it2 ->
                            val n = s.copy(); n.replyIntent(i)
                            val key = "${s.state.sceneId}#i$i#" + System.identityHashCode(beat.step) + "|" + n.state.stateKey()
                            if (seen.add(key)) queue.add(n to path + "[${s.state.sceneId}] typed:${it2.match.firstOrNull()}")
                        }
                        val n = s.copy(); n.replyFallback()
                        val key = "${s.state.sceneId}#fb#" + System.identityHashCode(beat.step) + "|" + n.state.stateKey()
                        if (seen.add(key)) queue.add(n to path + "[${s.state.sceneId}] typed:(fallback)")
                    }
                    else -> {}
                }
            }
        }
        val endings = game.endings.map { e -> val r = reach[e.id]; EndingReach(e, r != null, r?.first ?: emptyList(), r?.second ?: 0) }
        endings.filter { !it.reachable }.forEach { if (!truncated) err("ending ${it.ending.id}", "unreachable on every path") else warn("ending ${it.ending.id}", "not reached before search was truncated") }
        val unvisited = game.scenes.keys.filter { it !in visitedScenes }
        unvisited.forEach { warn("scene $it", "never reached by any path") }
        return Report(game.id, issues, endings, explored, truncated, unvisited, chars, voiced, missing)
    }

    companion object {
        /** Minimal launch filter (slurs/explicit terms, English + Roman Hindi). Real moderation is the Phase 2 queue. */
        val DEFAULT_BANNED = listOf("fuck", "bitch", "chutiya", "madarchod", "behenchod", "bhosdi", "randi", "rape", "nude", "porn", "suicide method")
    }
}
