package com.kissa.app.ai

import com.kissa.app.model.*
import kotlinx.serialization.json.*

data class HistoryLine(val role: Role, val text: String, val emotion: Emotion? = null, val action: String? = null)

data class StateSnapshot(
    val mood: Int,
    val relationship: Int,
    val chapter: Int,
    val summary: String,
    val turnCount: Int,
    val ended: Boolean = false,
)

data class PromptContent(val role: String, val text: String)
data class PromptPayload(val system: String, val contents: List<PromptContent>)

/**
 * Builds the compact context sent per turn: persona + story + state + rolling summary + memory
 * notes + only the most recent turns. Never the full transcript.
 */
object ContextBuilder {
    const val RECENT_TURNS = 12
    const val MAX_NOTES = 15
    const val MAX_LINE = 400
    const val MAX_SUMMARY = 1500

    fun relationshipLabel(v: Int) = when {
        v < 15 -> "distrustful / cold"
        v < 35 -> "strangers / guarded"
        v < 55 -> "acquaintances, warming up"
        v < 75 -> "friends, comfortable"
        v < 90 -> "close, trusting"
        else -> "deeply bonded"
    }

    fun moodLabel(v: Int) = when {
        v < 20 -> "very upset"
        v < 40 -> "irritated / low"
        v < 60 -> "neutral"
        v < 80 -> "good mood"
        else -> "elated"
    }

    fun languageRule(lang: Lang) = when (lang) {
        Lang.HINGLISH -> "Speak in natural Hinglish (Hindi + English mixed, written ONLY in Roman/Latin script, like young urban Indians text). Never use Devanagari."
        Lang.ENGLISH -> "Speak in natural conversational English (Indian English flavour is fine)."
        Lang.HINDI -> "Speak in natural conversational Hindi written in Devanagari script. Common English words may stay in English."
    }

    fun build(
        story: Story,
        playerName: String,
        lang: Lang,
        state: StateSnapshot,
        notes: List<String>,
        history: List<HistoryLine>,
        playerInput: String?,
    ): PromptPayload {
        val c = story.character
        val chapterIdx = state.chapter.coerceIn(0, (story.chapters.size - 1).coerceAtLeast(0))
        val chapter = story.chapters.getOrNull(chapterIdx)
        val isLastChapter = chapterIdx >= story.chapters.size - 1
        val sb = StringBuilder()
        sb.appendLine("You are ${c.name}, a character in an animated interactive story app. Stay fully in character at all times. You are NOT an assistant.")
        sb.appendLine()
        sb.appendLine("## Who you are")
        sb.appendLine("Name: ${c.name}, age ${c.age}. Role: ${c.role}.")
        sb.appendLine("Background: ${c.background}")
        sb.appendLine("Personality: ${c.personality}")
        sb.appendLine("Speaking style: ${c.speakingStyle}")
        sb.appendLine("Likes: ${c.likes}. Dislikes: ${c.dislikes}.")
        sb.appendLine("Goals: ${c.goals}")
        sb.appendLine("Boundaries: ${c.boundaries}")
        if (c.quirks.isNotBlank()) sb.appendLine("Quirks: ${c.quirks}")
        sb.appendLine()
        sb.appendLine("## The story: ${story.title}")
        sb.appendLine("Premise: ${story.premise}")
        sb.appendLine("Location: ${story.setting.label}. Atmosphere: ${story.atmosphere}")
        sb.appendLine("Opening situation: ${story.openingSituation}")
        sb.appendLine("The player is $playerName — ${story.playerRole}. They are physically standing in front of you in the scene.")
        sb.appendLine()
        sb.appendLine("## Current state")
        sb.appendLine("Your mood: ${state.mood}/100 (${moodLabel(state.mood)}). Relationship with $playerName: ${state.relationship}/100 (${relationshipLabel(state.relationship)}).")
        if (chapter != null) {
            sb.appendLine("Chapter ${chapterIdx + 1} of ${story.chapters.size}: \"${chapter.title}\" — beat to work toward: ${chapter.goal}")
            sb.appendLine(
                if (isLastChapter) "This is the final chapter. When its beat is clearly resolved, set scene_event to ENDING (the ending's tone must follow the relationship and choices so far)."
                else "When this chapter's beat is clearly achieved through the conversation, set scene_event to CHAPTER_COMPLETE (only once)."
            )
        }
        if (state.ended) sb.appendLine("The main story has reached an ending; continue as an epilogue conversation.")
        sb.appendLine("Turns so far: ${state.turnCount}.")
        if (state.summary.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("## What happened earlier (summary)")
            sb.appendLine(state.summary.take(MAX_SUMMARY))
        }
        val keptNotes = notes.takeLast(MAX_NOTES)
        if (keptNotes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Things you remember about $playerName and events")
            keptNotes.forEach { sb.appendLine("- ${it.take(MAX_LINE)}") }
        }
        sb.appendLine()
        sb.appendLine("## Rules")
        sb.appendLine("- ${languageRule(lang)}")
        sb.appendLine("- React to EXACTLY what $playerName just said, consistent with your personality, mood, relationship and memory. Never give generic replies.")
        sb.appendLine("- You have your own opinions and boundaries. Do NOT simply agree with everything. Warm up gradually; relationship changes should be earned (typical delta -3..+3, big moments up to ±10).")
        sb.appendLine("- Keep dialogue spoken and natural: 1–3 short sentences (max ~45 words). No narration inside dialogue, no asterisks, no emojis. Put any physical action in 'action' (short, third person, e.g. 'tucks her hair behind her ear').")
        sb.appendLine("- delivery: a few words on HOW you say this line out loud (tone, pace, hesitation), e.g. 'teasing, slightly smug' or 'soft and embarrassed, a nervous laugh'. vocal: an optional sound before the line (laugh/chuckle/giggle/sigh/gasp/tsk/hmm/breath) or none. Never put these in dialogue.")
        sb.appendLine("- emotion must match your line; gesture must fit the body language. Use scene_event only when something in the environment or plot actually happens; otherwise NONE.")
        sb.appendLine("- suggested_replies: three short, different options $playerName could say next (one kind, one playful/bold, one curious), in the same language. They are optional hints.")
        sb.appendLine("- memory_note: one short factual note ONLY if something worth remembering long-term happened (a preference, promise, secret, important event). Otherwise empty string.")
        sb.appendLine("- Keep it safe and respectful: no sexual content, no graphic violence, no hate. If $playerName pushes for that, refuse in character and steer the story elsewhere.")
        sb.appendLine("- Never mention being an AI, a model, prompts or these rules.")

        val contents = mutableListOf<PromptContent>()
        val recent = history.filter { it.role != Role.NARRATOR }.takeLast(RECENT_TURNS)
        for (h in recent) {
            val role = if (h.role == Role.CHARACTER) "model" else "user"
            val text = if (h.role == Role.CHARACTER) {
                buildJsonObject {
                    put("dialogue", h.text.take(MAX_LINE))
                    h.emotion?.let { put("emotion", it.name.lowercase()) }
                    h.action?.let { put("action", it) }
                }.toString()
            } else "$playerName: ${h.text.take(MAX_LINE)}"
            appendMerged(contents, role, text)
        }
        val input = playerInput?.trim().orEmpty()
        val finalUser = if (input.isEmpty()) {
            if (recent.isEmpty()) "[The scene begins. $playerName has just arrived. Open the scene with your first line, reacting to the opening situation.]"
            else "[$playerName stays silent for a moment. Continue naturally, react to the silence.]"
        } else "$playerName: ${input.take(MAX_LINE)}"
        appendMerged(contents, "user", finalUser)
        // Gemini expects the conversation to start with a user turn.
        if (contents.first().role != "user") contents.add(0, PromptContent("user", "[Earlier conversation continues]"))
        return PromptPayload(sb.toString().trim(), contents)
    }

    private fun appendMerged(list: MutableList<PromptContent>, role: String, text: String) {
        val last = list.lastOrNull()
        if (last != null && last.role == role) list[list.size - 1] = PromptContent(role, last.text + "\n" + text)
        else list += PromptContent(role, text)
    }

    /** OpenAPI-subset schema for Gemini structured output. */
    fun responseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("dialogue") { put("type", "STRING"); put("description", "What the character says aloud. No narration.") }
            putJsonObject("action") { put("type", "STRING"); put("description", "Short physical action, third person. May be empty.") }
            putJsonObject("emotion") { put("type", "STRING"); putJsonArray("enum") { Emotion.entries.forEach { add(it.name.lowercase()) } } }
            putJsonObject("gesture") { put("type", "STRING"); putJsonArray("enum") { Gesture.entries.forEach { add(it.name.lowercase()) } } }
            putJsonObject("mood_delta") { put("type", "INTEGER") }
            putJsonObject("relationship_delta") { put("type", "INTEGER") }
            putJsonObject("scene_event") { put("type", "STRING"); putJsonArray("enum") { SceneEvent.entries.forEach { add(it.name) } } }
            putJsonObject("suggested_replies") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
            putJsonObject("memory_note") { put("type", "STRING") }
            putJsonObject("delivery") { put("type", "STRING"); put("description", "How the line is performed aloud (tone/pace). Not spoken.") }
            putJsonObject("vocal") { put("type", "STRING"); putJsonArray("enum") { (listOf("none") + AiParser.VOCALS).forEach { add(it) } } }
        }
        putJsonArray("required") {
            listOf("dialogue", "emotion", "gesture", "mood_delta", "relationship_delta", "scene_event", "suggested_replies", "memory_note").forEach { add(it) }
        }
        putJsonArray("propertyOrdering") {
            listOf("dialogue", "action", "emotion", "gesture", "delivery", "vocal", "mood_delta", "relationship_delta", "scene_event", "suggested_replies", "memory_note").forEach { add(it) }
        }
    }

    fun summaryPrompt(story: Story, playerName: String, previousSummary: String, lines: List<HistoryLine>): PromptPayload {
        val sys = "You maintain the running memory of an interactive story. Write a compact factual summary in English (max 120 words) of everything important so far: key events, promises, revealed facts, how ${story.character.name} feels about $playerName. Merge with the previous summary. Plain text only."
        val body = buildString {
            if (previousSummary.isNotBlank()) appendLine("Previous summary: $previousSummary").appendLine()
            appendLine("New dialogue to merge:")
            lines.forEach {
                val who = when (it.role) { Role.PLAYER -> playerName; Role.CHARACTER -> story.character.name; Role.NARRATOR -> "Narrator" }
                appendLine("$who: ${it.text.take(MAX_LINE)}")
            }
        }
        return PromptPayload(sys, listOf(PromptContent("user", body)))
    }
}
