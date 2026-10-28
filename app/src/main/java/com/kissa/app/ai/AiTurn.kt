package com.kissa.app.ai

import com.kissa.app.model.Emotion
import com.kissa.app.model.Gesture
import com.kissa.app.model.Lang
import com.kissa.app.model.SceneEvent
import kotlinx.serialization.json.*

/** A validated AI reply. Dialogue is kept separate from animation-control data. */
data class AiTurn(
    val dialogue: String,
    val action: String?,
    val emotion: Emotion,
    val gesture: Gesture,
    val moodDelta: Int,
    val relationshipDelta: Int,
    val sceneEvent: SceneEvent,
    val suggestedReplies: List<String>,
    val memoryNote: String?,
)

sealed class ParseResult {
    data class Ok(val turn: AiTurn, val warnings: List<String>) : ParseResult()
    data class Fail(val reason: String) : ParseResult()
}

/**
 * Parses and validates the model's JSON. Tolerates code fences, extra prose around the JSON,
 * synonyms for enum values, numbers sent as strings and out-of-range deltas (clamped).
 */
object AiParser {
    const val MAX_DIALOGUE = 700
    const val MAX_DELTA = 10
    const val MAX_REPLY_LEN = 90
    const val MAX_NOTE = 220

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extractJsonObject(raw: String): String? {
        val text = raw.trim().removePrefix("\uFEFF")
        val start = text.indexOf('{')
        if (start < 0) return null
        // walk to the matching brace, honouring strings
        var depth = 0; var inStr = false; var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    fun parse(raw: String, lang: Lang = Lang.HINGLISH): ParseResult {
        val obj = extractJsonObject(raw) ?: return ParseResult.Fail("no JSON object in reply")
        val el = try { json.parseToJsonElement(obj) } catch (e: Exception) {
            return ParseResult.Fail("malformed JSON: ${e.message?.take(80)}")
        }
        val o = el as? JsonObject ?: return ParseResult.Fail("reply is not an object")
        val warnings = mutableListOf<String>()

        var dialogue = o.str("dialogue") ?: o.str("text") ?: o.str("reply")
            ?: return ParseResult.Fail("missing dialogue")
        var action = o.str("action")?.trim()?.takeIf { it.isNotEmpty() }
        // Pull *stage directions* out of the spoken line so TTS doesn't read them aloud.
        val stage = Regex("\\*([^*]{1,120})\\*")
        val found = stage.findAll(dialogue).map { it.groupValues[1].trim() }.toList()
        if (found.isNotEmpty()) {
            dialogue = stage.replace(dialogue, " ")
            if (action == null) action = found.joinToString("; ")
            warnings += "moved stage directions to action"
        }
        dialogue = dialogue.replace(Regex("\\s+"), " ").trim().trim('"')
        if (dialogue.isEmpty()) return ParseResult.Fail("empty dialogue")
        if (dialogue.length > MAX_DIALOGUE) {
            val cut = dialogue.take(MAX_DIALOGUE)
            val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '।'))
            dialogue = if (lastStop > MAX_DIALOGUE / 2) cut.substring(0, lastStop + 1) else "$cut…"
            warnings += "dialogue truncated"
        }

        val emotion = Emotion.fromAny(o.str("emotion")) ?: Emotion.NEUTRAL.also { warnings += "unknown emotion" }
        val gesture = Gesture.fromAny(o.str("gesture")) ?: Gesture.NONE.also { warnings += "unknown gesture" }
        val scene = SceneEvent.fromAny(o.str("scene_event") ?: o.str("sceneEvent")) ?: SceneEvent.NONE.also { warnings += "unknown scene_event" }
        val mood = clampDelta(o.num("mood_delta") ?: o.num("moodDelta"), warnings, "mood_delta")
        val rel = clampDelta(o.num("relationship_delta") ?: o.num("relationshipDelta"), warnings, "relationship_delta")

        val repliesEl = o["suggested_replies"] ?: o["suggestedReplies"]
        val replies = (repliesEl as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.map { it.replace(Regex("\\s+"), " ").trim().trim('"').take(MAX_REPLY_LEN) }
            ?.filter { it.isNotEmpty() }
            ?.distinctBy { it.lowercase() }
            ?.take(3)
            .orEmpty()
        val filled = if (replies.size < 3) {
            warnings += "suggested_replies padded"
            (replies + defaultReplies(lang)).distinctBy { it.lowercase() }.take(3)
        } else replies

        val note = (o.str("memory_note") ?: o.str("memoryNote"))?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("none", true) && !it.equals("null", true) && it != "-" }
            ?.take(MAX_NOTE)

        return ParseResult.Ok(
            AiTurn(dialogue, action?.take(160), emotion, gesture, mood, rel, scene, filled, note),
            warnings,
        )
    }

    private fun clampDelta(v: Double?, warnings: MutableList<String>, name: String): Int {
        if (v == null || v.isNaN()) return 0
        val r = Math.round(v).toInt()
        if (r > MAX_DELTA || r < -MAX_DELTA) warnings += "$name clamped"
        return r.coerceIn(-MAX_DELTA, MAX_DELTA)
    }

    fun defaultReplies(lang: Lang): List<String> = when (lang) {
        Lang.HINDI -> listOf("और बताओ…", "सच में?", "मुझे माफ़ करना")
        Lang.ENGLISH -> listOf("Tell me more…", "Really?", "Sorry about that")
        Lang.HINGLISH -> listOf("Aur batao…", "Sach mein?", "Sorry yaar")
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.let {
        if (it is JsonNull) null else it.contentOrNull
    }

    private fun JsonObject.num(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.doubleOrNull ?: p.contentOrNull?.trim()?.removePrefix("+")?.toDoubleOrNull()
    }
}

/** Applies a delta to a 0..100 meter. */
fun applyMeter(current: Int, delta: Int): Int = (current + delta).coerceIn(0, 100)
