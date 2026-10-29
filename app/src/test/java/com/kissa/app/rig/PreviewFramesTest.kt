package com.kissa.app.rig

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kissa.app.game.GameParser
import com.kissa.app.game.Look
import com.kissa.app.game.SceneType
import com.kissa.app.play.StageRenderer.drawStage
import com.kissa.app.play.StageState
import org.junit.Test
import java.io.File

/**
 * Renders the real stage/rig output for each play surface (no text: Compose text can't render headless on the JVM).
 * tools/preview/compose_previews.py then lays the real game text and the app's UI chrome over these frames.
 * Run: ./gradlew testDebugUnitTest --tests '*PreviewFramesTest*' -Dshots=true -DpreviewOut=/abs/out/previews-v3/raw
 */
class PreviewFramesTest {
    private fun game(id: String) = GameParser.parse(File(Preview.root, "src/main/assets/games/$id/game.json").readText())
    private val player = Actor("player", "male", Look(skin = 2, hair = "curly", hairColor = "black", face = "soft", outfit = "hoodie", outfitColor = "maroon", accessories = listOf("glasses_round")))

    private fun stage(gameId: String, sceneId: String, setup: (StageState) -> Unit): StageState {
        val g = game(gameId); val sc = g.scenes.getValue(sceneId)
        val st = StageState(); st.reset(sc.type, sc.background!!, sc.time, sc.weather)
        sc.cast.forEach { p -> val c = g.character(p.id)!!; st.actors[p.id] = Actor(p.id, c.rig, c.look).apply { x = p.at; targetX = x; facing = p.facing } }
        if (sc.type == SceneType.CINEMATIC) st.player = player.apply { x = (sc.cast.minOfOrNull { it.at } ?: 0.6f) - 0.24f; targetX = x; facing = 1 }
        st.setAudience(sc.audience, sc.id.hashCode())
        setup(st)
        repeat(45) { st.update(1 / 30f) }
        return st
    }

    @Test fun renderSurfaceFrames() {
        if (!Preview.enabled()) return
        val r = Preview.renderer
        val W = 720; val H = 1280
        // 1. cinematic: Aukaat s1 — Tanvi furious, Sahil smug, the player's own rig, audience
        val s1 = stage("aukaat", "s1") { st ->
            st.actors["tanvi"]!!.apply { expression = "furious"; play("point") }
            st.actors["sahil"]!!.apply { expression = "smug"; play("cross_arms") }
            player.expression = "worried"; player.play("look_down")
            st.react("laugh"); st.focusActor = "tanvi"; st.targetZoom = 1.12f; st.zoom = 1.12f
        }
        s1.speaking = "tanvi"; s1.actors["tanvi"]!!.mouth = 0.7f
        Preview.shot("cinematic", W, H) { drawStage(s1, Preview.bg("college_corridor"), r) }
        // 2. POV: Aukaat s7 canteen — Sahil addresses the camera with the phone, crowd behind, player never drawn
        val s7 = stage("aukaat", "s7_canteen") { st ->
            st.actors["sahil"]!!.apply { expression = "smug"; play("hold_phone"); props["hand_r"] = "phone" }
            st.react("murmur"); st.targetZoom = 1.1f; st.zoom = 1.1f
        }
        Preview.shot("pov", W, H) { drawStage(s7, Preview.bg("cafe"), r) }
        // 3. cinematic night + rain filter: Baarish r2_walk — Naina with the umbrella
        val r2 = stage("baarish-wali-raat", "r2_walk") { st ->
            st.actors["naina"]!!.apply { expression = "shy"; play("idle"); props["hand_r"] = "umbrella" }
            player.expression = "smile"; player.play("idle"); st.targetZoom = 1.15f; st.zoom = 1.15f
        }
        Preview.shot("cinematic_rain", W, H) { drawStage(r2, Preview.bg("street"), r) }
        // 4. ending frame: Aukaat e_good — library at dusk, sparkles
        val eg = stage("aukaat", "e_good") { st -> st.actors["tanvi"]!!.apply { expression = "blushing"; play("look_down") }; st.sparkles = 2f; st.targetZoom = 1.2f; st.zoom = 1.2f }
        Preview.shot("ending_bg", W, H) { drawStage(eg, Preview.bg("library"), r) }
        // 5. office cinematic for status sheet backdrop: Pehla Hafta w1
        val w1 = stage("pehla-hafta", "w1") { st -> st.actors["vikram"]!!.apply { expression = "angry"; play("point"); props["hand_l"] = "file_folder" }; st.actors["zoya"]!!.apply { expression = "smug"; play("cross_arms") } }
        Preview.shot("office", W, H) { drawStage(w1, Preview.bg("office"), r) }
        // 6. customiser: the player's rig full body + chat avatar head
        val pf = Actor("pf", "female", Look(skin = 1, hair = "ponytail", hairColor = "brown", face = "round", eyes = "hazel", outfit = "denim_jacket", outfitColor = "blue", accessories = listOf("earrings")))
        pf.expression = "smile"; repeat(20) { pf.update(0.03f) }
        Preview.shot("customiser_rig", 720, 560) { drawRect(Color(0xFF2A2240)); with(r) { drawActor(pf, Offset(360f, 300f), 0.30f) } }
        val tanvi = game("aukaat").character("tanvi")!!
        val av = Actor("t", tanvi.rig, tanvi.look).apply { expression = "worried"; update(0.1f) }
        Preview.shot("chat_avatar", 200, 200) { drawRect(Color(0xFF2E2840)); with(r) { drawActor(av, Offset(100f, 905f * 200f / 520f), 200f / 520f) } }
    }
}
