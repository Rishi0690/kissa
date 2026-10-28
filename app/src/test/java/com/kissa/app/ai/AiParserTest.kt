package com.kissa.app.ai

import com.kissa.app.model.*
import org.junit.Assert.*
import org.junit.Test

class AiParserTest {

    private fun ok(raw: String, lang: Lang = Lang.HINGLISH): ParseResult.Ok {
        val r = AiParser.parse(raw, lang)
        assertTrue("expected Ok but was $r", r is ParseResult.Ok)
        return r as ParseResult.Ok
    }

    @Test fun parsesWellFormedReply() {
        val r = ok("""{"dialogue":"Arre, dekh ke chalo na!","action":"picks up her notebook","emotion":"amused","gesture":"hand_on_hip",
            "mood_delta":2,"relationship_delta":1,"scene_event":"NONE","suggested_replies":["Sorry yaar","Main help karun?","Ye poems tumhare hain?"],"memory_note":""}""")
        val t = r.turn
        assertEquals("Arre, dekh ke chalo na!", t.dialogue)
        assertEquals(Emotion.AMUSED, t.emotion)
        assertEquals(Gesture.HAND_ON_HIP, t.gesture)
        assertEquals(2, t.moodDelta)
        assertEquals(1, t.relationshipDelta)
        assertEquals(SceneEvent.NONE, t.sceneEvent)
        assertEquals(3, t.suggestedReplies.size)
        assertNull(t.memoryNote)
        assertEquals("picks up her notebook", t.action)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun stripsCodeFencesAndProse() {
        val r = ok("Sure! Here you go:\n```json\n{\"dialogue\":\"Hi\",\"emotion\":\"happy\",\"gesture\":\"wave\",\"mood_delta\":0,\"relationship_delta\":0,\"scene_event\":\"NONE\",\"suggested_replies\":[\"a\",\"b\",\"c\"],\"memory_note\":\"\"}\n```")
        assertEquals("Hi", r.turn.dialogue)
        assertEquals(Gesture.WAVE, r.turn.gesture)
    }

    @Test fun clampsDeltasAndAcceptsStringNumbers() {
        val r = ok("""{"dialogue":"Wow.","emotion":"surprised","gesture":"none","mood_delta":"+45","relationship_delta":-99,"scene_event":"","suggested_replies":[]}""")
        assertEquals(10, r.turn.moodDelta)
        assertEquals(-10, r.turn.relationshipDelta)
        assertTrue(r.warnings.any { it.contains("clamped") })
    }

    @Test fun mapsSynonymsAndUnknownsSafely() {
        val r = ok("""{"dialogue":"Hmm…","emotion":"Embarrassed","gesture":"dance wildly","mood_delta":1.6,"relationship_delta":0,"scene_event":"students walk by","suggested_replies":["x"]}""")
        assertEquals(Emotion.SHY, r.turn.emotion)
        assertEquals(Gesture.NONE, r.turn.gesture) // unknown -> idle, never crashes the rig
        assertEquals(SceneEvent.CROWD_PASSES, r.turn.sceneEvent)
        assertEquals(2, r.turn.moodDelta)
        assertEquals(3, r.turn.suggestedReplies.size) // padded
        assertTrue(r.warnings.contains("unknown gesture"))
    }

    @Test fun movesStageDirectionsOutOfSpokenText() {
        val r = ok("""{"dialogue":"*rolls her eyes* Seriously? *smiles* Fine.","emotion":"amused","gesture":"none","mood_delta":0,"relationship_delta":0,"scene_event":"NONE","suggested_replies":["a","b","c"],"memory_note":"none"}""")
        assertEquals("Seriously? Fine.", r.turn.dialogue)
        assertEquals("rolls her eyes; smiles", r.turn.action)
        assertNull(r.turn.memoryNote)
    }

    @Test fun keepsMemoryNoteAndTruncatesLongDialogue() {
        val long = "Yeh bahut lambi baat hai. ".repeat(60)
        val r = ok("""{"dialogue":"$long","emotion":"neutral","gesture":"nod","mood_delta":0,"relationship_delta":3,"scene_event":"chapter_complete","suggested_replies":["a","b","c"],"memory_note":"Player loves Gulzar's poetry"}""")
        assertTrue(r.turn.dialogue.length <= AiParser.MAX_DIALOGUE + 1)
        assertTrue(r.turn.dialogue.endsWith("."))
        assertEquals("Player loves Gulzar's poetry", r.turn.memoryNote)
        assertEquals(SceneEvent.CHAPTER_COMPLETE, r.turn.sceneEvent)
    }

    @Test fun dedupesRepliesAndHandlesBracesInsideStrings() {
        val r = ok("""{"dialogue":"Use {curly} braces? Okay }","emotion":"confused","gesture":"head tilt","mood_delta":0,"relationship_delta":0,"scene_event":"NONE","suggested_replies":["Haan","haan","Nahi","Shayad"]}""")
        assertEquals("Use {curly} braces? Okay }", r.turn.dialogue)
        assertEquals(listOf("Haan", "Nahi", "Shayad"), r.turn.suggestedReplies)
        assertEquals(Gesture.HEAD_TILT, r.turn.gesture)
    }

    @Test fun failsWithoutDialogueOrJson() {
        assertTrue(AiParser.parse("I can't do that") is ParseResult.Fail)
        assertTrue(AiParser.parse("""{"emotion":"happy"}""") is ParseResult.Fail)
        assertTrue(AiParser.parse("""{"dialogue":"   "}""") is ParseResult.Fail)
        assertTrue(AiParser.parse("""{"dialogue": "unterminated""") is ParseResult.Fail)
    }

    @Test fun defaultRepliesFollowLanguage() {
        val r = ok("""{"dialogue":"नमस्ते","emotion":"happy","gesture":"wave","mood_delta":0,"relationship_delta":0,"scene_event":"NONE","suggested_replies":[]}""", Lang.HINDI)
        assertTrue(r.turn.suggestedReplies.all { s -> s.any { it in '\u0900'..'\u097F' } })
    }

    @Test fun meterStaysInRange() {
        assertEquals(100, applyMeter(95, 10))
        assertEquals(0, applyMeter(3, -10))
        assertEquals(52, applyMeter(50, 2))
    }

    @Test fun parsesDeliveryAndVocalForNaturalTts() {
        val r = ok("""{"dialogue":"Acha? Tum poetry padhte ho?","emotion":"amused","gesture":"head_tilt","delivery":"teasing, *slightly* smug <laugh>","vocal":"Chuckles","mood_delta":1,"relationship_delta":1,"scene_event":"NONE","suggested_replies":["a","b","c"]}""")
        assertEquals("teasing, slightly smug laugh", r.turn.delivery)
        assertEquals("chuckle", r.turn.vocal)
        val n = ok("""{"dialogue":"Hmm.","emotion":"neutral","gesture":"none","delivery":"none","vocal":"dance","mood_delta":0,"relationship_delta":0,"scene_event":"NONE","suggested_replies":["a","b","c"]}""")
        assertEquals(null, n.turn.delivery)
        assertEquals(null, n.turn.vocal)
        assertTrue(n.warnings.contains("unknown vocal"))
        val long = ok("""{"dialogue":"Hi","emotion":"happy","gesture":"wave","delivery":"${"x".repeat(200)}","mood_delta":0,"relationship_delta":0,"scene_event":"NONE","suggested_replies":["a","b","c"]}""")
        assertEquals(AiParser.MAX_DELIVERY, long.turn.delivery!!.length)
    }

    @Test fun schemaDeclaresDeliveryAndVocal() {
        val props = ContextBuilder.responseSchema()["properties"].toString()
        assertTrue(props.contains("delivery"))
        assertTrue(props.contains("\"chuckle\""))
    }
}
