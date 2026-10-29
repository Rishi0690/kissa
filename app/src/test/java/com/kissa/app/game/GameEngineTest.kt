package com.kissa.app.game

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameEngineTest {
    private fun spec(): Game = GameParser.parse(javaClass.getResource("/spec_example.json")!!.readText())

    /** Runs until a decision/reply/ending, collecting beats. */
    private fun run(s: GameSession): Pair<Beat, List<Beat>> {
        val seen = ArrayList<Beat>()
        while (true) { val b = s.next(); seen += b; if (b is Beat.Decision || b is Beat.Reply || b is Beat.Ending) return b to seen }
    }

    @Test fun parsesSpecExampleExactly() {
        val g = spec()
        assertEquals("first-day", g.id)
        assertEquals(listOf("affection", "trust", "power"), g.stats.keys.toList())
        assertEquals(listOf("Beginner", "Bold", "Pro"), g.rank.levels.map { it.name })
        val isha = g.character("isha")!!
        assertEquals("long_straight", isha.look.hair); assertEquals("black", isha.look.hairColor)
        assertEquals("college_blazer", isha.look.outfit); assertEquals("blue", isha.look.outfitColor)
        assertEquals("female_young_hindi_1", isha.voice.slot); assertEquals(1.05, isha.voice.pitch, 1e-9)
        assertEquals("college_corridor", g.scenes["s1"]!!.background); assertEquals("day", g.scenes["s1"]!!.time)
        assertEquals(SceneType.CHAT, g.scenes["s2"]!!.type)
        assertEquals(3, g.endings.size)
    }

    @Test fun timidVsConfidentBranchAndRank() {
        val g = spec()
        val a = GameSession(g, GameState.initial(g, "Riya"))
        val (d, beats) = run(a)
        assertTrue(beats.any { it is Beat.Line && it.text == "Do not talk to me again." })
        assertTrue(d is Beat.Decision)
        assertEquals(0, a.state.stats["power"]) // -2 clamped at min 0
        val b = a.copy()
        val o1 = a.choose(0)
        assertEquals("timid", o1.style); assertEquals(1, a.state.stats["affection"]); assertFalse(o1.rankChanged)
        run(a); assertEquals("s2", a.state.sceneId); assertTrue("s1_dismissed" in a.state.visited)
        val o2 = b.choose(1)
        assertEquals(3, b.state.stats["power"]); assertEquals(10, b.state.rankScore); assertEquals("Beginner", o2.rankAfter)
        assertTrue(o2.deltas.any { it.text == "Power +3" })
    }

    @Test fun openReplyIntentsAndFallbackReachEndings() {
        val g = spec()
        fun toReply(): GameSession { val s = GameSession(g, GameState.initial(g, "Riya")); run(s); s.choose(1); val (b, _) = run(s); assertTrue(b is Beat.Reply); return s }
        // typed Hinglish "haan batao" -> help -> good? affection 10 + trust 5 -> good
        val s1 = toReply(); val o = s1.reply("haan haan, batao kya hua")
        assertEquals("intent1", o.intentId); val (e1, _) = run(s1); assertEquals("good", (e1 as Beat.Ending).ending!!.id)
        // "aukaat yaad hai?" -> cold -> revenge
        val s2 = toReply(); s2.reply("aukat yaad hai?"); val (e2, _) = run(s2); assertEquals("revenge", (e2 as Beat.Ending).ending!!.id)
        // gibberish -> fallback -> neutral? helped_isha undefined == false -> revenge comes first in the ordered list
        val s3 = toReply(); val o3 = s3.reply("asdf qwerty"); assertTrue(o3.fallback); val (e3, _) = run(s3)
        assertEquals("revenge", (e3 as Beat.Ending).ending!!.id)
        assertEquals("s3_neutral", s3.state.visited.elementAt(s3.state.visited.size - 2))
    }

    @Test fun objectivesCompleteUnlockAndScoreRank() {
        val g = spec()
        val s = GameSession(g, GameState.initial(g, "Riya")); run(s); s.choose(1); run(s); s.reply("ok"); run(s)
        assertEquals(ObjStatus.DONE, s.state.objectives["o1"]); assertEquals(ObjStatus.AVAILABLE, s.state.objectives["o2"])
        assertEquals(20, s.state.rankScore) // 10 from confident choice + 10 on_complete
    }

    @Test fun playerNameAndGenderSubstitution() {
        val g = spec()
        val s = GameSession(g, GameState.initial(g, "Kavya")); s.playerRig = "female"
        assertEquals("Main Kavya, aa rahi hoon", s.substitute("Main {player}, {m:aa raha|f:aa rahi} hoon"))
        s.playerRig = "male"; assertEquals("aa raha", s.substitute("{m:aa raha|f:aa rahi}"))
    }

    @Test fun savesCheckpointsAndReplayFromCheckpoint() {
        val g = spec()
        val dir = Files.createTempDirectory("saves").toFile()
        val store = SaveStore(dir)
        store.startNew(g.id, "Riya", "female", Look(hair = "bob"))
        val s = GameSession(g, GameState.initial(g, "Riya"))
        s.onSceneEntered = { sc, snap -> store.onSceneEntered(g.id, sc.copy(checkpoint = sc.checkpoint ?: sc.id), snap) }
        s.onEnding = { e, st -> store.onEnding(g.id, e, st) }
        run(s); s.choose(1); run(s); s.reply("help"); run(s)
        val save = store.load(g.id)!!
        assertEquals(setOf("good"), save.endingsReached); assertNull(save.autosave)
        assertEquals("bob", save.look.hair)
        val cpS2 = save.checkpoints.first { it.sceneId == "s2" }
        assertEquals(3, cpS2.state.stats["power"]); assertEquals(10, cpS2.state.rankScore)
        // replay from the s2 checkpoint and choose differently -> different ending
        val r = GameSession.resume(g, cpS2.state)
        val (b, _) = run(r); assertTrue(b is Beat.Reply); r.reply("nahi, revenge"); val (e, _) = run(r)
        assertEquals("revenge", (e as Beat.Ending).ending!!.id)
        store.onEnding(g.id, e.ending, r.state)
        assertEquals(setOf("good", "revenge"), store.load(g.id)!!.endingsReached)
        // state round-trips exactly through JSON
        assertEquals(cpS2.state.stateKey(), GameState.fromJson(cpS2.state.toJson()).stateKey())
        dir.deleteRecursively()
    }
}
