package com.kissa.app.game

import kotlinx.serialization.json.*

/**
 * Declarative game format (spec §6). A game is a JSON graph of scenes plus rules: player role, stats, rank ladder,
 * flags, items, objectives, characters with logical voice slots, and an ordered list of conditional endings.
 * Parsing is lenient about optional fields and strict about structure; the [Validator] reports the rest.
 */
data class Game(
    val id: String,
    val title: String,
    val premise: String,
    val tagline: String,
    val genre: String,
    val tags: List<String>,
    val minutes: Int,
    val creator: String,
    val cover: String?,
    val accent: String?,
    val language: String,
    val player: PlayerRole,
    val stats: Map<String, StatDef>,
    val flags: Map<String, V>,
    val items: Map<String, String>,
    val rank: RankDef,
    val characters: List<CharacterDef>,
    val objectives: List<ObjectiveDef>,
    val start: String,
    val scenes: Map<String, SceneDef>,
    val endings: List<EndingDef>,
    val raw: JsonObject,
) {
    fun character(id: String?) = characters.firstOrNull { it.id == id }
    fun objective(id: String?) = objectives.firstOrNull { it.id == id }
    fun ending(id: String?) = endings.firstOrNull { it.id == id }
}

data class PlayerRole(val role: String, val roleText: String, val rig: String, val name: String, val defaultName: String, val goal: String)
data class StatDef(val name: String, val start: Int, val min: Int, val max: Int, val label: String, val icon: String?, val hidden: Boolean = false)
data class RankLevel(val name: String, val at: Int)
data class RankDef(val score: Int, val levels: List<RankLevel>) {
    fun levelIndex(score: Int): Int = levels.indices.lastOrNull { score >= levels[it].at } ?: 0
    fun name(score: Int): String = levels.getOrNull(levelIndex(score))?.name ?: ""
    fun next(score: Int): RankLevel? = levels.getOrNull(levelIndex(score) + 1)
}

data class Look(
    val skin: Int = 2,
    val hair: String = "long_straight",
    val hairColor: String = "black",
    val face: String = "soft",
    val eyes: String = "dark",
    val outfit: String = "college_blazer",
    val outfitColor: String = "navy",
    val accessories: List<String> = emptyList(),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("skin", skin); put("hair", hair); put("hair_color", hairColor); put("face", face); put("eyes", eyes)
        put("outfit", outfit); put("outfit_color", outfitColor); put("accessories", JsonArray(accessories.map { JsonPrimitive(it) }))
    }
    companion object {
        fun parse(o: JsonObject?): Look {
            if (o == null) return Look()
            val (hair, hc) = Catalog.splitHair(o.str("hair") ?: "long_straight")
            val (outfit, oc) = Catalog.splitOutfit(o.str("outfit") ?: "college_blazer")
            return Look(
                skin = o.int("skin") ?: 2,
                hair = hair,
                hairColor = o.str("hair_color") ?: hc ?: "black",
                face = o.str("face") ?: "soft",
                eyes = o.str("eyes") ?: "dark",
                outfit = outfit,
                outfitColor = o.str("outfit_color") ?: oc ?: "navy",
                accessories = o.strList("accessories"),
            )
        }
    }
}

data class VoiceProfile(val slot: String, val pitch: Double, val speed: Double, val extras: JsonObject?)
data class CharacterDef(val id: String, val name: String, val nameHi: String?, val rig: String, val look: Look, val voice: VoiceProfile, val bio: String)

data class ObjectiveDef(
    val id: String, val text: String, val place: String?, val deadline: String?, val givenBy: String?,
    val acceptChoice: Boolean, val onComplete: List<Effect>, val onFail: List<Effect>, val unlocks: String?,
)

data class EndingDef(val id: String, val title: String, val condition: String, val scene: String, val teaser: String, val kind: String)

enum class SceneType { CINEMATIC, POV, CHAT }

data class Placement(val id: String, val at: Float, val facing: Int)

data class SceneDef(
    val id: String,
    val type: SceneType,
    val title: String?,
    val background: String?,
    val time: String,
    val weather: String,
    val music: String?,
    val cast: List<Placement>,
    val audience: Int,
    val chatWith: String?,
    val chatHeader: String?,
    val checkpoint: String?,
    val steps: List<Step>,
    val choicePrompt: String?,
    val choices: List<Choice>,
    val next: String?,
    val resolveEnding: Boolean,
    val endingId: String?,
)

data class Choice(
    val text: String,
    val style: String,
    val score: Int,
    val effects: List<Effect>,
    val goto: String?,
    val reaction: List<Step>,
    val condition: String?,
    val kind: String, // reply | ignore | block | delay | action
)

data class Intent(val id: String, val match: List<String>, val effects: List<Effect>, val goto: String?, val reaction: List<Step>, val style: String, val score: Int)
data class Fallback(val reaction: List<Step>, val effects: List<Effect>, val goto: String?)

sealed class Step {
    data class Say(val char: String, val text: String, val emotion: String?, val move: String?, val voiceFile: String?, val time: String?, val delayMs: Int, val prop: String?) : Step()
    data class Narrate(val text: String, val voiceFile: String?) : Step()
    data class PlayerSay(val text: String, val time: String?) : Step()
    data class SetFx(val effects: List<Effect>) : Step()
    data class Move(val char: String, val move: String, val to: Float?) : Step()
    data class Enter(val char: String, val at: Float, val from: String) : Step()
    data class Exit(val char: String, val to: String) : Step()
    data class Emote(val char: String, val emotion: String) : Step()
    data class Camera(val zoom: Float?, val focus: String?, val shake: Float, val pan: Float?) : Step()
    data class Fx(val time: String?, val weather: String?, val flash: Boolean, val sparkles: Boolean) : Step()
    data class Prop(val char: String, val prop: String?, val anchor: String) : Step()
    data class Audience(val react: String) : Step()
    data class Wait(val ms: Int) : Step()
    data class If(val condition: String, val then: List<Step>, val otherwise: List<Step>) : Step()
    data class Goto(val scene: String) : Step()
    data class Checkpoint(val label: String) : Step()
    data class ResolveEnding(val dummy: Boolean = true) : Step()
    data class Objective(val id: String, val action: String) : Step()
    data class OpenReply(val prompt: String, val intents: List<Intent>, val fallback: Fallback?, val presets: List<String>, val placeholder: String?) : Step()
    data class Music(val track: String?) : Step()
}

sealed class Effect {
    data class StatDelta(val stat: String, val delta: Int) : Effect()
    data class FlagSet(val flag: String, val value: V) : Effect()
    data class Rank(val delta: Int) : Effect()
    data class AddItem(val item: String) : Effect()
    data class RemoveItem(val item: String) : Effect()
    data class ObjectiveChange(val id: String, val action: String) : Effect() // start | complete | fail | decline | offer
}

/** A runtime value for flags and condition evaluation. */
sealed class V {
    data class Num(val v: Double) : V() { override fun toString() = if (v == Math.floor(v)) v.toLong().toString() else v.toString() }
    data class Bool(val v: Boolean) : V() { override fun toString() = v.toString() }
    data class Str(val v: String) : V() { override fun toString() = v }
    fun truthy(): Boolean = when (this) { is Num -> v != 0.0; is Bool -> v; is Str -> v.isNotEmpty() && v != "false" && v != "none" }
    fun toJson(): JsonPrimitive = when (this) { is Num -> JsonPrimitive(v); is Bool -> JsonPrimitive(v); is Str -> JsonPrimitive(v) }
    companion object {
        fun of(p: JsonPrimitive): V = when {
            p.isString -> Str(p.content)
            p.booleanOrNull != null -> Bool(p.boolean)
            p.doubleOrNull != null -> Num(p.double)
            else -> Str(p.content)
        }
    }
}

class GameFormatException(msg: String) : Exception(msg)

// ---------------- parsing ----------------

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.doubleOrNull?.toInt()
internal fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
internal fun JsonObject.strList(k: String): List<String> = when (val e = this[k]) {
    is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(e.contentOrNull)
    else -> emptyList()
}

object GameParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): Game = parse(json.parseToJsonElement(text).jsonObject)

    fun parse(o: JsonObject): Game {
        val id = o.str("id") ?: throw GameFormatException("game.id missing")
        val stats = LinkedHashMap<String, StatDef>()
        o.obj("stats")?.forEach { (k, v) ->
            val s = v as? JsonObject ?: throw GameFormatException("stat $k must be an object")
            stats[k] = StatDef(k, s.int("start") ?: 0, s.int("min") ?: -100, s.int("max") ?: 100, s.str("label") ?: k.replaceFirstChar { it.uppercase() }, s.str("icon"), s.bool("hidden") ?: false)
        }
        val flags = LinkedHashMap<String, V>()
        o.obj("flags")?.forEach { (k, v) -> flags[k] = (v as? JsonPrimitive)?.let { V.of(it) } ?: V.Bool(false) }
        val items = LinkedHashMap<String, String>()
        when (val it = o["items"]) {
            is JsonObject -> it.forEach { (k, v) -> items[k] = (v as? JsonPrimitive)?.contentOrNull ?: ((v as? JsonObject)?.str("name") ?: k) }
            is JsonArray -> it.forEach { e -> (e as? JsonPrimitive)?.contentOrNull?.let { n -> items[n] = n } }
            else -> {}
        }
        val rankO = o.obj("rank")
        val levels = rankO?.arr("levels")?.map { val l = it.jsonObject; RankLevel(l.str("name") ?: "?", l.int("at") ?: 0) }?.sortedBy { it.at }
            ?: listOf(RankLevel("Beginner", 0))
        val rank = RankDef(rankO?.int("score") ?: 0, levels)
        val pl = o.obj("player") ?: JsonObject(emptyMap())
        val player = PlayerRole(
            role = pl.str("role") ?: "player", roleText = pl.str("role_text") ?: pl.str("role") ?: "",
            rig = pl.str("rig") ?: "player_choice", name = pl.str("name") ?: "player_entered",
            defaultName = pl.str("default_name") ?: "Aarav", goal = pl.str("goal") ?: "",
        )
        val chars = o.arr("characters")?.map { e ->
            val c = e.jsonObject
            val v = c.obj("voice")
            CharacterDef(
                id = c.str("id") ?: throw GameFormatException("character without id"),
                name = c.str("name") ?: c.str("id")!!.replaceFirstChar { it.uppercase() },
                nameHi = c.str("name_hi"),
                rig = c.str("rig") ?: "female",
                look = Look.parse(c.obj("look")),
                voice = VoiceProfile(v?.str("slot") ?: "", v?.dbl("pitch") ?: 1.0, v?.dbl("speed") ?: 1.0, v?.obj("provider_extras")),
                bio = c.str("bio") ?: "",
            )
        } ?: emptyList()
        val objectives = o.arr("objectives")?.map { e ->
            val ob = e.jsonObject
            ObjectiveDef(
                id = ob.str("id") ?: throw GameFormatException("objective without id"), text = ob.str("text") ?: "",
                place = ob.str("place"), deadline = ob.str("deadline"), givenBy = ob.str("given_by"),
                acceptChoice = ob.bool("accept_choice") ?: false, onComplete = effects(ob.obj("on_complete"), stats),
                onFail = effects(ob.obj("on_fail"), stats), unlocks = ob.str("unlocks"),
            )
        } ?: emptyList()
        val scenesO = o.obj("scenes") ?: throw GameFormatException("scenes missing")
        val scenes = LinkedHashMap<String, SceneDef>()
        scenesO.forEach { (sid, se) -> scenes[sid] = scene(sid, se as? JsonObject ?: throw GameFormatException("scene $sid must be an object"), stats) }
        val endings = o.arr("endings")?.map { e ->
            val en = e.jsonObject
            EndingDef(en.str("id") ?: "?", en.str("title") ?: (en.str("id") ?: "Ending"), en.str("if") ?: "true", en.str("scene") ?: "", en.str("teaser") ?: "", en.str("kind") ?: (en.str("id") ?: ""))
        } ?: emptyList()
        return Game(
            id = id, title = o.str("title") ?: id, premise = o.str("premise") ?: "", tagline = o.str("tagline") ?: "",
            genre = o.str("genre") ?: "Drama", tags = o.strList("tags"), minutes = o.int("minutes") ?: 8,
            creator = o.str("creator") ?: "Kissa Studio", cover = o.str("cover"), accent = o.str("accent"), language = o.str("language") ?: "hi",
            player = player, stats = stats, flags = flags, items = items, rank = rank, characters = chars, objectives = objectives,
            start = o.str("start") ?: scenes.keys.firstOrNull() ?: throw GameFormatException("start missing"),
            scenes = scenes, endings = endings, raw = o,
        )
    }

    private fun scene(id: String, s: JsonObject, stats: Map<String, StatDef>): SceneDef {
        val type = when (s.str("type")?.lowercase()) { "chat" -> SceneType.CHAT; "pov" -> SceneType.POV; else -> SceneType.CINEMATIC }
        var bg = s.str("background"); var time = s.str("time"); val weather = s.str("weather") ?: "clear"
        Catalog.backgroundAliases[bg]?.let { (b, t) -> bg = b; if (time == null) time = t }
        val cast = s.arr("cast")?.mapNotNull { e ->
            when (e) {
                is JsonPrimitive -> Placement(e.content, 0.5f, -1)
                is JsonObject -> Placement(e.str("id") ?: return@mapNotNull null, (e.dbl("at") ?: 0.5).toFloat(), e.int("facing") ?: -1)
                else -> null
            }
        } ?: emptyList()
        val choiceEl = s["choice"] ?: s["choices"]
        val (prompt, choices) = when (choiceEl) {
            is JsonArray -> null to choiceEl.map { choice(it.jsonObject, stats) }
            is JsonObject -> choiceEl.str("prompt") to (choiceEl.arr("options") ?: JsonArray(emptyList())).map { choice(it.jsonObject, stats) }
            else -> null to emptyList()
        }
        return SceneDef(
            id = id, type = type, title = s.str("title"), background = bg, time = time ?: "day", weather = weather, music = s.str("music"),
            cast = cast, audience = s.int("audience") ?: 0, chatWith = s.str("with"), chatHeader = s.str("header"),
            checkpoint = s.str("checkpoint") ?: if (s.bool("checkpoint") == true) (s.str("title") ?: id) else null,
            steps = steps(s.arr("steps"), stats), choicePrompt = prompt ?: s.str("prompt"), choices = choices,
            next = s.str("next"), resolveEnding = s.bool("resolve_ending") ?: false, endingId = s.str("ending"),
        )
    }

    private fun choice(c: JsonObject, stats: Map<String, StatDef>) = Choice(
        text = c.str("text") ?: "…", style = c.str("style") ?: "neutral", score = c.int("score") ?: 0,
        effects = effects(c.obj("effects"), stats), goto = c.str("goto"), reaction = steps(c.arr("reaction"), stats),
        condition = c.str("if"), kind = c.str("kind") ?: "reply",
    )

    fun steps(a: JsonArray?, stats: Map<String, StatDef>): List<Step> = a?.map { step(it.jsonObject, stats) } ?: emptyList()

    private fun step(s: JsonObject, stats: Map<String, StatDef>): Step = when {
        s.containsKey("say") -> Step.Say(
            char = s.str("say")!!, text = s.str("text") ?: "", emotion = s.str("emotion"), move = s.str("move"),
            voiceFile = s.str("voice_file"), time = s.str("time"), delayMs = s.int("delay") ?: 0, prop = s.str("prop"),
        )
        s.containsKey("narrate") -> Step.Narrate(s.str("narrate") ?: "", s.str("voice_file"))
        s.containsKey("player") -> Step.PlayerSay(s.str("player") ?: "", s.str("time"))
        s.containsKey("set") -> Step.SetFx(effects(s.obj("set"), stats))
        s.containsKey("open_reply") -> openReply(s.obj("open_reply") ?: JsonObject(emptyMap()), stats)
        s.containsKey("if") -> Step.If(s.str("if") ?: "false", steps(s.arr("then"), stats), steps(s.arr("else"), stats))
        s.containsKey("goto") -> Step.Goto(s.str("goto")!!)
        s.containsKey("checkpoint") -> Step.Checkpoint(s.str("checkpoint") ?: "Checkpoint")
        s.containsKey("resolve_ending") -> Step.ResolveEnding()
        s.containsKey("enter") -> Step.Enter(s.str("enter")!!, (s.dbl("at") ?: 0.5).toFloat(), s.str("from") ?: "right")
        s.containsKey("exit") -> Step.Exit(s.str("exit")!!, s.str("to") ?: "right")
        s.containsKey("move") && s.containsKey("char") -> Step.Move(s.str("char")!!, s.str("move")!!, s.dbl("to")?.toFloat())
        s.containsKey("emote") -> Step.Emote(s.str("emote")!!, s.str("emotion") ?: "neutral")
        s.containsKey("camera") -> (s.obj("camera") ?: JsonObject(emptyMap())).let { c ->
            Step.Camera(c.dbl("zoom")?.toFloat(), c.str("focus"), (c.dbl("shake") ?: 0.0).toFloat(), c.dbl("pan")?.toFloat())
        }
        s.containsKey("fx") -> (s.obj("fx") ?: JsonObject(emptyMap())).let { f ->
            Step.Fx(f.str("time"), f.str("weather"), f.bool("flash") ?: false, f.bool("sparkles") ?: false)
        }
        s.containsKey("prop") -> Step.Prop(s.str("char") ?: "", s.str("prop")?.takeIf { it != "none" }, s.str("anchor") ?: Catalog.defaultAnchor(s.str("prop") ?: ""))
        s.containsKey("audience") -> Step.Audience(s.str("audience") ?: "murmur")
        s.containsKey("wait") -> Step.Wait(s.int("wait") ?: 500)
        s.containsKey("objective") -> Step.Objective(s.str("objective")!!, s.str("action") ?: "start")
        s.containsKey("music") -> Step.Music(s.str("music"))
        else -> throw GameFormatException("unknown step: ${s.keys}")
    }

    private fun openReply(o: JsonObject, stats: Map<String, StatDef>): Step.OpenReply {
        val intents = o.arr("intents")?.mapIndexed { i, e ->
            val it = e.jsonObject
            Intent(it.str("id") ?: "intent${i + 1}", it.strList("match"), effects(it.obj("effects"), stats), it.str("goto"), steps(it.arr("reaction"), stats), it.str("style") ?: "neutral", it.int("score") ?: 0)
        } ?: emptyList()
        val fb = o.obj("fallback")?.let { f ->
            val reaction = steps(f.arr("reaction"), stats).toMutableList()
            // spec shorthand: "fallback": {"say": "…"} -> spoken by the open reply's character
            if (f.containsKey("say") && f.str("text") == null) reaction.add(0, Step.Say(f.str("char") ?: o.str("char") ?: "", f.str("say")!!, f.str("emotion"), null, f.str("voice_file"), null, 0, null))
            Fallback(reaction, effects(f.obj("effects"), stats), f.str("goto"))
        }
        return Step.OpenReply(o.str("prompt") ?: "", intents, fb, o.strList("choices"), o.str("placeholder"))
    }

    fun effects(o: JsonObject?, stats: Map<String, StatDef>): List<Effect> {
        if (o == null) return emptyList()
        val out = ArrayList<Effect>()
        o.forEach { (k, v) ->
            val p = v as? JsonPrimitive
            when {
                k == "rank" || k == "score" -> out += Effect.Rank(p?.doubleOrNull?.toInt() ?: 0)
                k == "add_item" -> (if (v is JsonArray) v.map { it.jsonPrimitive.content } else listOfNotNull(p?.contentOrNull)).forEach { out += Effect.AddItem(it) }
                k == "remove_item" -> (if (v is JsonArray) v.map { it.jsonPrimitive.content } else listOfNotNull(p?.contentOrNull)).forEach { out += Effect.RemoveItem(it) }
                k == "start_objective" || k == "accept_objective" -> out += Effect.ObjectiveChange(p!!.content, "start")
                k == "complete_objective" -> out += Effect.ObjectiveChange(p!!.content, "complete")
                k == "fail_objective" -> out += Effect.ObjectiveChange(p!!.content, "fail")
                k == "decline_objective" -> out += Effect.ObjectiveChange(p!!.content, "decline")
                k == "offer_objective" -> out += Effect.ObjectiveChange(p!!.content, "offer")
                k in stats && p?.doubleOrNull != null && p.booleanOrNull == null -> out += Effect.StatDelta(k, p.double.toInt())
                p != null -> out += Effect.FlagSet(k, V.of(p))
            }
        }
        return out
    }
}
