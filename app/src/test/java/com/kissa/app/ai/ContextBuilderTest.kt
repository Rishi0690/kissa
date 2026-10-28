package com.kissa.app.ai

import com.kissa.app.content.SeedContent
import com.kissa.app.model.*
import com.kissa.app.voice.SpeechDirector
import com.kissa.app.voice.WavUtil
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ContextBuilderTest {
    private val story = SeedContent.stories.first()
    private val state = StateSnapshot(mood = 60, relationship = 40, chapter = 1, summary = "Aarav bumped into Riya and apologised.", turnCount = 30)

    private fun history(n: Int) = (1..n).map { i ->
        if (i % 2 == 1) HistoryLine(Role.PLAYER, "player line $i") else HistoryLine(Role.CHARACTER, "riya line $i", Emotion.HAPPY, "smiles")
    }

    @Test fun sendsOnlyRecentWindowPlusSummaryAndNotes() {
        val notes = (1..30).map { "note $it" }
        val p = ContextBuilder.build(story, "Aarav", Lang.HINGLISH, state, notes, history(40), "Kal fest pe aaogi?")
        // window: last 12 history lines + new input, merged by role
        val all = p.contents.joinToString("\n") { it.text }
        assertFalse(all.contains("player line 27"))
        assertTrue(all.contains("player line 29"))
        assertTrue(all.contains("riya line 40"))
        assertTrue(p.contents.last().text.endsWith("Aarav: Kal fest pe aaogi?"))
        assertEquals("user", p.contents.first().role)
        assertEquals("user", p.contents.last().role)
        // roles alternate
        p.contents.zipWithNext().forEach { (a, b) -> assertNotEquals(a.role, b.role) }
        // summary + only the newest notes
        assertTrue(p.system.contains(state.summary))
        assertTrue(p.system.contains("note 30"))
        assertFalse(p.system.contains("- note 15\n"))
        assertTrue(p.system.contains("- note 16"))
    }

    @Test fun includesPersonaStateChapterAndLanguageRules() {
        val p = ContextBuilder.build(story, "Aarav", Lang.HINGLISH, state, emptyList(), emptyList(), "hi")
        assertTrue(p.system.contains("Riya Malhotra"))
        assertTrue(p.system.contains(story.character.boundaries))
        assertTrue(p.system.contains("Chapter 2 of 4"))
        assertTrue(p.system.contains("CHAPTER_COMPLETE"))
        assertTrue(p.system.contains("Roman/Latin script"))
        assertTrue(p.system.contains("40/100"))
        val hindi = ContextBuilder.build(story, "Aarav", Lang.HINDI, state.copy(chapter = 3), emptyList(), emptyList(), "hi")
        assertTrue(hindi.system.contains("Devanagari"))
        assertTrue(hindi.system.contains("final chapter"))
        assertTrue(hindi.system.contains("ENDING"))
    }

    @Test fun openingTurnHasSceneStartInstruction() {
        val p = ContextBuilder.build(story, "Aarav", Lang.ENGLISH, state.copy(turnCount = 0), emptyList(), emptyList(), null)
        assertEquals(1, p.contents.size)
        assertTrue(p.contents[0].text.contains("scene begins"))
    }

    @Test fun modelTurnsAreCompactJsonAndNarratorIsDropped() {
        val h = listOf(HistoryLine(Role.NARRATOR, "The bell rings."), HistoryLine(Role.CHARACTER, "Late again?", Emotion.AMUSED, "smirks"))
        val p = ContextBuilder.build(story, "Aarav", Lang.ENGLISH, state, emptyList(), h, "Sorry!")
        assertEquals("user", p.contents[0].role) // synthetic opener because history starts with the model
        val model = p.contents.first { it.role == "model" }
        val obj = Json.parseToJsonElement(model.text).jsonObject
        assertEquals("Late again?", obj["dialogue"]!!.jsonPrimitive.content)
        assertEquals("amused", obj["emotion"]!!.jsonPrimitive.content)
        assertFalse(p.contents.any { it.text.contains("bell rings") })
    }

    @Test fun longInputsAreTruncated() {
        val huge = "a".repeat(5000)
        val p = ContextBuilder.build(story, "Aarav", Lang.ENGLISH, state.copy(summary = "s".repeat(5000)), listOf(huge), listOf(HistoryLine(Role.PLAYER, huge)), huge)
        assertTrue(p.contents.sumOf { it.text.length } < 1200)
        assertTrue(p.system.length < 9000)
    }

    @Test fun schemaListsAllEnumsAndRequiredFields() {
        val s = ContextBuilder.responseSchema()
        val props = s["properties"]!!.jsonObject
        val emotions = props["emotion"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(Emotion.entries.size, emotions.size)
        val req = s["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(req.containsAll(listOf("dialogue", "emotion", "gesture", "mood_delta", "relationship_delta", "scene_event", "suggested_replies", "memory_note")))
    }

    @Test fun requestBodyHasSchemaAndSystemInstruction() {
        val p = ContextBuilder.build(story, "Aarav", Lang.ENGLISH, state, emptyList(), emptyList(), "hey")
        val body = GeminiClient().buildBody(p, ContextBuilder.responseSchema(), 0.9, 512)
        assertEquals("application/json", body["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
        assertNotNull(body["systemInstruction"])
        assertEquals("user", body["contents"]!!.jsonArray.last().jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test fun extractsCandidateTextAndSkipsThoughts() {
        val c = GeminiClient()
        val json = """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"{\"dialogue\":\"Hi\"}"}]},"finishReason":"STOP"}]}"""
        assertEquals("{\"dialogue\":\"Hi\"}", c.extractText(json))
        try { c.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}"""); fail() } catch (e: AiException) { assertEquals(AiException.Kind.BLOCKED, e.kind) }
    }

    @Test fun summaryPromptIncludesPreviousSummary() {
        val p = ContextBuilder.summaryPrompt(story, "Aarav", "Earlier stuff.", history(4))
        assertTrue(p.contents[0].text.contains("Earlier stuff."))
        assertTrue(p.contents[0].text.contains("Riya Malhotra: riya line 2"))
    }

    // ---- small pure helpers used by the voice layer and safety
    @Test fun sentenceSplittingAndVoiceGender() {
        assertEquals(listOf("Arre yaar! Kya kar rahe ho?", "Main toh bas notes utha rahi thi."),
            SpeechDirector.splitSentences("Arre yaar! Kya kar rahe ho? Main toh bas notes utha rahi thi."))
        assertEquals(true, SpeechDirector.guessFeminine("hi-in-x-hia-local"))
        assertEquals(false, SpeechDirector.guessFeminine("en-in-x-end-network"))
        assertEquals(null, SpeechDirector.guessFeminine("en-us-x-unknown"))
        assertTrue(SpeechDirector.hasDevanagari("नमस्ते yaar"))
    }

    @Test fun envelopeIsNormalised() {
        val pcm = ByteArray(16000 * 2) // 1 s silence
        for (i in 8000 until 16000) { val v = ((i % 40) - 20) * 800; pcm[2 * i] = (v and 0xff).toByte(); pcm[2 * i + 1] = (v shr 8).toByte() }
        val env = WavUtil.envelopeOf(pcm, 16000)
        assertEquals(20, env.size)
        assertEquals(0f, env[0], 0.001f)
        assertEquals(1f, env.max(), 0.001f)
    }

    @Test fun moderationBlocksObviousCases() {
        assertTrue(Moderation.check("Tumhari poems bahut acchi hain").allowed)
        assertFalse(Moderation.check("send nudes").allowed)
        assertTrue(Moderation.check("I want to kill myself").selfHarm)
    }
}
