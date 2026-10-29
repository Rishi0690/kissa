package com.kissa.app.game

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ValidatorTest {
    private val specText = javaClass.getResource("/spec_example.json")!!.readText()
    private fun mutate(block: MutableMap<String, JsonElement>.() -> Unit): Game {
        val o = Json.parseToJsonElement(specText).jsonObject.toMutableMap(); o.block(); return GameParser.parse(JsonObject(o))
    }
    private fun scenes(o: MutableMap<String, JsonElement>) = o["scenes"]!!.jsonObject.toMutableMap()
    private fun v() = Validator(voiceExists = { true })

    /** The spec §6 example itself: good + revenge are reachable; "neutral" is NOT, because an unset helped_isha counts as
     *  false, so the revenge condition (checked first) catches every path that would otherwise fall through. */
    @Test fun specExampleReachability() {
        val r = v().validate(GameParser.parse(specText))
        println(r.render())
        assertTrue(r.endings.first { it.ending.id == "good" }.reachable)
        assertTrue(r.endings.first { it.ending.id == "revenge" }.reachable)
        assertFalse(r.endings.first { it.ending.id == "neutral" }.reachable)
        assertEquals(listOf("ending neutral"), r.errors.map { it.where })
    }

    @Test fun rejectsUnknownAssetIds() {
        val g = mutate {
            val sc = scenes(this)
            sc["s1"] = JsonObject(sc["s1"]!!.jsonObject.toMutableMap().apply { put("background", JsonPrimitive("moon_base")) })
            put("scenes", JsonObject(sc))
        }
        assertTrue(v().validate(g).errors.any { it.message.contains("unknown background 'moon_base'") })
        val g2 = GameParser.parse(specText.replace("\"emotion\": \"angry\"", "\"emotion\": \"rage_face\"").replace("\"move\": \"walk_away\"", "\"move\": \"moonwalk\""))
        val e2 = v().validate(g2).errors.map { it.message }
        assertTrue(e2.any { it.contains("rage_face") }); assertTrue(e2.any { it.contains("moonwalk") })
    }

    @Test fun rejectsMissingTargetsAndNoFallback() {
        val g = GameParser.parse(specText.replace("\"goto\": \"s3_cold\"", "\"goto\": \"s9_nowhere\"").replace("\"fallback\": { \"effects\": { \"trust\": 1 }, \"goto\": \"s3_neutral\" },", ""))
        val r = v().validate(g)
        assertTrue(r.errors.any { it.message.contains("s9_nowhere") })
        assertTrue(r.errors.any { it.message.contains("no fallback") })
    }

    @Test fun rejectsUnreachableEnding() {
        val g = GameParser.parse(specText.replace("\"if\": \"affection >= 10 and trust >= 5\"", "\"if\": \"affection >= 50\""))
        val r = v().validate(g)
        assertTrue(r.render(), r.errors.any { it.where == "ending good" && it.message.contains("unreachable") })
    }

    @Test fun rejectsFilteredWordsAndWarnsEffectFreeAndMissingVoice() {
        val g = GameParser.parse(specText.replace("Whatever.", "Whatever, chutiya.").replace("\"effects\": { \"affection\": 1 }, ", ""))
        val r = Validator(voiceExists = { it != "audio/e1.opus" }).validate(g)
        assertTrue(r.errors.any { it.message.contains("content filter") })
        assertTrue(r.warnings.any { it.message.contains("effect-free") })
        assertTrue(r.warnings.any { it.message.contains("audio/e1.opus") })
        assertEquals(1, r.missingVoice)
    }
}

/** Validates the three bundled launch games and writes the reachability report (also printed). */
class LaunchGamesTest {
    private val root = File(System.getProperty("user.dir")) // app/app
    private val gamesDir = File(root, "src/main/assets/games")

    @Test fun launchGamesValidateWithReachableEndings() {
        val dirs = gamesDir.listFiles { f -> File(f, "game.json").exists() }!!.sortedBy { it.name }
        assertTrue("expected 3 launch games, found ${dirs.size}", dirs.size >= 3)
        val out = StringBuilder()
        var totalChars = 0
        for (d in dirs) {
            val g = GameParser.parse(File(d, "game.json").readText())
            val r = Validator(voiceExists = { File(d, it).exists() }).validate(g)
            out.append(r.render()).append('\n')
            totalChars += r.dialogueChars
            assertTrue("${g.id}: ${r.errors}", r.ok)
            assertTrue("${g.id} needs >= 2 reachable endings", r.endings.count { it.reachable } >= 2)
            assertTrue(g.scenes.values.any { it.type == SceneType.CHAT } && g.scenes.values.any { it.type == SceneType.POV } && g.scenes.values.any { it.type == SceneType.CINEMATIC })
        }
        out.append("Total dialogue characters across launch games: $totalChars\n")
        println(out)
        File(root, "build/reports").mkdirs(); File(root, "build/reports/reachability.txt").writeText(out.toString())
    }

    /** Spec §14: a confident-tagged choice produces a visibly different in-scene reaction from a timid one in the same encounter. */
    @Test fun toneTaggedChoicesReactDifferently() {
        val g = GameParser.parse(File(gamesDir, "aukaat/game.json").readText())
        val first = g.scenes.getValue(g.start).choices
        val timid = first.first { it.style == "timid" }; val confident = first.first { it.style == "confident" }
        assertNotEquals(timid.reaction, confident.reaction)
        assertTrue(confident.score > timid.score)
    }
}
