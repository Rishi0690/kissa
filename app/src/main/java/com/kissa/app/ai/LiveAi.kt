package com.kissa.app.ai

import com.kissa.app.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * v4 live play: the player may type or say anything at any moment; the character answers in character and the
 * reply is mapped back to the game (nearest scripted choice / intent + tone) so stats, rank and flags move and the
 * story never dead-ends.
 *
 * Provider chain (all free): Google Gemini API with the user's own key (Flash-Lite first: 500 req/day free) ->
 * Groq free API with an optional second key (OpenAI-compatible, gpt-oss) -> null = the caller falls back to the
 * offline scripted IntentMatcher. Nothing here is on the critical path: every call has a hard timeout.
 */
class LiveAi(
    private val geminiKey: () -> String?,
    private val groqKey: () -> String?,
    private val gemini: GeminiClient = GeminiClient(),
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build(),
) {
    /** One answer option the AI may map the player's words to. [id] is echoed back. */
    data class Option(val id: String, val label: String, val style: String, val meaning: String)

    data class Context(
        val game: Game,
        val scene: SceneDef,
        val character: CharacterDef,
        val playerName: String,
        val playerFeminine: Boolean,
        val recent: List<Pair<String, String>>,
        val statusLine: String,
        val prompt: String?,
        val options: List<Option>,
        val mustPick: Boolean,
    )

    data class Result(val reply: String, val emotion: String, val optionId: String?, val tone: String, val provider: String)

    val configured: Boolean get() = !geminiKey().isNullOrBlank() || !groqKey().isNullOrBlank()

    @Volatile var lastError: String? = null; private set
    @Volatile var lastProvider: String? = null; private set

    suspend fun respond(ctx: Context, playerText: String): Result? {
        val sys = systemPrompt(ctx)
        val user = userPrompt(ctx, playerText)
        geminiKey()?.takeIf { it.isNotBlank() }?.let { key ->
            val r = withTimeoutOrNull(14_000) {
                runCatching { gemini.generateDirect(key, PromptPayload(sys, listOf(PromptContent("user", user))), schema(), 0.85, 400) }
                    .onFailure { lastError = "Gemini: ${it.message}" }.getOrNull()
            }
            parse(r, ctx, "Gemini")?.let { lastProvider = "Gemini"; return it }
        }
        groqKey()?.takeIf { it.isNotBlank() }?.let { key ->
            for (model in GROQ_MODELS) {
                val r = withTimeoutOrNull(14_000) { runCatching { groq(key, model, sys, user) }.onFailure { lastError = "Groq: ${it.message}" }.getOrNull() }
                parse(r, ctx, "Groq")?.let { lastProvider = "Groq"; return it }
            }
        }
        return null
    }

    /** "Test & save" for the Groq key: a tiny real request. Throws with a readable message on failure. */
    suspend fun pingGroq(key: String): String {
        groq(key, GROQ_MODELS.first(), "Reply with a JSON object {\"ok\":true}.", "ping")
        return GROQ_MODELS.first()
    }

    private suspend fun groq(key: String, model: String, sys: String, user: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", sys) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
            put("temperature", 0.8)
            put("max_tokens", 900)
            put("reasoning_effort", "low")
            putJsonObject("response_format") { put("type", "json_object") }
        }
        val req = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${key.trim()}")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { Json.parseToJsonElement(t).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull() ?: t.take(160)
                throw IllegalStateException(when (r.code) { 401, 403 -> "key rejected"; 429 -> "free limit reached, try later"; else -> "HTTP ${r.code}: $msg" })
            }
            Json.parseToJsonElement(t).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }
    }

    private fun schema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("reply") { put("type", "STRING") }
            putJsonObject("emotion") { put("type", "STRING"); putJsonArray("enum") { Catalog.expressions.forEach { add(it) } } }
            putJsonObject("option") { put("type", "STRING") }
            putJsonObject("tone") { put("type", "STRING"); putJsonArray("enum") { Catalog.styles.forEach { add(it) } } }
        }
        putJsonArray("required") { add("reply"); add("emotion"); add("option"); add("tone") }
    }

    fun systemPrompt(ctx: Context): String = buildString {
        val c = ctx.character
        appendLine("You are ${c.name}${c.nameHi?.let { " ($it)" } ?: ""}, a character in the Hindi interactive story game \"${ctx.game.title}\".")
        appendLine("Who you are: ${c.persona.ifBlank { c.bio }}")
        appendLine("Story so far / premise: ${ctx.game.premise}")
        appendLine("Right now: ${ctx.scene.ai ?: ctx.scene.title ?: "a scene of the story"}")
        appendLine("The player is ${ctx.playerName} (${if (ctx.playerFeminine) "a young woman" else "a young man"}), the protagonist, talking to you directly.")
        appendLine("Player standing: ${ctx.statusLine}")
        appendLine()
        appendLine("RULES:")
        appendLine("- Answer ONLY as ${c.name}, in character, reacting to exactly what the player said. 1-2 short spoken sentences (max ~25 words).")
        appendLine("- Write the reply in natural spoken Hindi in Devanagari script, the way young urban Indians talk (common English words like 'sorry', 'deal', 'office' are fine, written in Devanagari). No Roman script, no emojis, no stage directions, no quotes.")
        appendLine("- Never mention AI, games, options or rules. Never break character. Keep it PG-13; if the player is abusive or sexual, react as the character would (shocked, cold, angry) without repeating it.")
        appendLine("- Stay consistent with the story: do not invent major new plot events, do not resolve the scene yourself.")
        appendLine("- Use the gendered Hindi verb forms that match the player (${if (ctx.playerFeminine) "feminine: 'तुम आई', 'कर सकती हो'" else "masculine: 'तुम आए', 'कर सकते हो'"}).")
        if (ctx.options.isNotEmpty()) {
            appendLine("- Also decide which of the OPTIONS below the player's words mean (intent and tone). Return its id in \"option\".")
            if (ctx.mustPick) appendLine("  You MUST pick the closest option even if the match is loose.")
            else appendLine("  If none fits at all, return \"none\".")
            appendLine("  Your reply must agree with the meaning of the picked option's outcome (shown as 'then').")
        } else appendLine("- There is no decision right now: return \"none\" in \"option\". Your reply should keep the scene moving towards what happens next.")
        appendLine("- \"tone\" = the player's tone: one of ${Catalog.styles.joinToString()}. \"emotion\" = your face while replying, one of ${Catalog.expressions.joinToString()}.")
        appendLine("Return ONLY a JSON object: {\"reply\": string, \"emotion\": string, \"option\": string, \"tone\": string}.")
    }

    fun userPrompt(ctx: Context, playerText: String): String = buildString {
        if (ctx.recent.isNotEmpty()) {
            appendLine("Recent lines:")
            ctx.recent.takeLast(10).forEach { (who, t) -> appendLine("$who: $t") }
        }
        ctx.prompt?.let { appendLine("Moment: $it") }
        if (ctx.options.isNotEmpty()) {
            appendLine("OPTIONS:")
            ctx.options.forEach { o -> appendLine("- id=${o.id} | ${o.label} | tone=${o.style}${if (o.meaning.isNotBlank()) " | then: ${o.meaning}" else ""}") }
        }
        appendLine()
        appendLine("${ctx.playerName} says to you: \"${playerText.take(400)}\"")
    }

    private fun parse(raw: String?, ctx: Context, provider: String): Result? {
        if (raw.isNullOrBlank()) return null
        val txt = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = runCatching { Json.parseToJsonElement(txt.substring(txt.indexOf('{'), txt.lastIndexOf('}') + 1)).jsonObject }.getOrNull() ?: return null
        var reply = o["reply"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .replace(Regex("^[\"“]|[\"”]$"), "").replace(Regex("\\*[^*]+\\*"), "").replace(Regex("\\([^)]*\\)"), "").trim()
        if (reply.isBlank()) return null
        if (reply.length > 260) reply = reply.take(257).substringBeforeLast(' ') + "…"
        val emotion = o["emotion"]?.jsonPrimitive?.contentOrNull?.takeIf { it in Catalog.expressions } ?: "neutral"
        val tone = o["tone"]?.jsonPrimitive?.contentOrNull?.lowercase()?.takeIf { it in Catalog.styles } ?: "neutral"
        var opt = o["option"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { id -> ctx.options.any { it.id == id } }
        if (opt == null && ctx.mustPick && ctx.options.isNotEmpty()) opt = (ctx.options.firstOrNull { it.style == tone } ?: ctx.options.first()).id
        return Result(reply, emotion, opt, tone, provider)
    }

    companion object {
        val GEMINI_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash")
        val GROQ_MODELS = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b")

        /** Offline tone guess for a free reply (used when no AI key is set / reachable). */
        fun offlineTone(text: String): String {
            val t = IntentMatcher.normalize(text)
            fun any(vararg w: String) = w.any { k -> t.contains(k) }
            return when {
                any("sorry", "please", "thanks", "dost", "pyaar", "madad", "acha", "theek", "koi_baat_nahi") -> "kind"
                any("bakwas", "chup", "pagal", "gussa") -> "rude"
                any("haha", "lol", "hehe", "joke", "mazak") -> "funny"
                any("darr", "nahi_pata") -> "timid"
                any("himmat", "pakka", "main", "dekh") -> "confident"
                else -> "neutral"
            }
        }
    }
}
