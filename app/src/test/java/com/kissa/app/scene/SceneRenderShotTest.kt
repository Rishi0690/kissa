package com.kissa.app.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kissa.app.content.SeedContent
import com.kissa.app.model.Emotion
import com.kissa.app.model.Gesture
import com.kissa.app.scene.CharacterPainter.drawCharacter
import com.kissa.app.voice.SpeakingState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders real scene-engine frames to PNG on the JVM (android.graphics is backed by Java2D test doubles in
 * src/test/java/android/graphics), so animation states can be inspected without a device. Run with -Dshots=true.
 */
class SceneRenderShotTest {
    private val root = File(System.getProperty("user.dir"))
    private val out = File(root, "build/shots").apply { mkdirs() }
    private val art = File(root, "../../art")

    private fun shot(name: String, w: Int, h: Int, block: DrawScope.() -> Unit) {
        val img = com.kissa.shim.ShotIO.create(w, h)
        CanvasDrawScope().draw(Density(2.75f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
        com.kissa.shim.ShotIO.save(img, File(out, "$name.png").absolutePath)
    }

    private fun scene(name: String, idx: Int, w: Int = 540, h: Int = 1170, frames: Int = 150, setup: (SceneEngine) -> SpeakingState?) {
        val story = SeedContent.stories[idx]
        val engine = SceneEngine(story.setting, story.playerAppearance, story.character)
        engine.startEntrance(false)
        val sp = setup(engine)
        repeat(frames) { engine.update(1f / 60f, sp) }
        val bg = com.kissa.shim.ShotIO.load(File(art, "bg_" + story.setting.name.lowercase() + ".png").absolutePath)
        shot(name, w, h) { drawStage(engine, bg) }
    }

    @Test fun renderScenes() {
        if (System.getProperty("shots") != "true" && System.getenv("SHOTS") != "true") return
        scene("s1_corridor_speaking_amused", 0) { e -> e.react(e.npc.id, Emotion.AMUSED, Gesture.HAND_ON_HIP); SpeakingState(e.npc.id, "Arre!") }
        scene("s2_corridor_shy_cover", 0) { e -> e.react(e.npc.id, Emotion.SHY, Gesture.COVER_FACE); null }
        scene("s3_cafe_angry_cross", 1) { e -> e.react(e.npc.id, Emotion.ANGRY, Gesture.CROSS_ARMS); null }
        scene("s4_library_thinking", 2) { e -> e.setThinking(e.npc.id, true); e.react(e.npc.id, Emotion.CONFUSED, Gesture.NONE); null }
        scene("s5_forest_happy_wave", 3) { e -> e.react(e.npc.id, Emotion.HAPPY, Gesture.WAVE); null }
        scene("s6_street_surprised_land", 5, 1170, 540) { e -> e.react(e.npc.id, Emotion.SURPRISED, Gesture.SHRUG); null }
        scene("s7_park_sad", 6) { e -> e.react(e.npc.id, Emotion.SAD, Gesture.NONE); null }
        scene("s8_ship_point", 4) { e -> e.react(e.npc.id, Emotion.NEUTRAL, Gesture.POINT); null }
        scene("s9_class_laugh", 7) { e -> e.react(e.npc.id, Emotion.AMUSED, Gesture.LAUGH); null }
        scene("s10_entrance_walk", 0, frames = 40) { e -> e.startEntrance(true); null }
        // close-up expression sheet of every character
        SeedContent.stories.forEachIndexed { i, st ->
            shot("face_${i}_${st.character.id}", 1600, 420) {
                drawRect(Color(0xFF1A1730))
                Emotion.entries.forEachIndexed { k, emo ->
                    val p = PoseFrame().apply { expr = Expr.of(emo); emotion = emo; markAlpha = 1f; gazeX = 0.3f; mouthOpen = if (k % 3 == 1) 0.6f else 0f }
                    val s = 420f / 330f
                    withTransform({ translate(100f + k * 200f, 420f + 690f * s); scale(s, s, Offset.Zero) }) { drawCharacter(st.character.appearance, p) }
                }
            }
        }
        assertTrue(out.listFiles()!!.isNotEmpty())
    }
}
