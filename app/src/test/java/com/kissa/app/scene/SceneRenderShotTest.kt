package com.kissa.app.scene

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kissa.app.content.SeedContent
import com.kissa.app.model.Emotion
import com.kissa.app.model.Gesture
import com.kissa.app.model.SceneEvent
import com.kissa.app.voice.SpeakingState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders real cinematic frames (shot director + painted art + micro-motion) to PNG on the JVM — android.graphics is
 * backed by Java2D test doubles (testshim/) — so shots can be inspected without a device. Run with -Dshots=true.
 * Art is read from the PNG mirror in ../../art/v2/png (ImageIO can't decode WebP).
 */
class SceneRenderShotTest {
    private val root = File(System.getProperty("user.dir"))
    private val out = File(root, "build/shots").apply { mkdirs() }
    private val art = File(root, "../../art/v2/png")

    private fun load(rel: String): ImageBitmap? = File(art, rel).takeIf { it.exists() }?.let { com.kissa.shim.ShotIO.load(it.absolutePath) }

    private fun shot(name: String, w: Int, h: Int, block: DrawScope.() -> Unit) {
        val img = com.kissa.shim.ShotIO.create(w, h)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
        com.kissa.shim.ShotIO.save(img, File(out, "$name.png").absolutePath)
    }

    private fun sceneArt(story: com.kissa.app.model.Story, engine: SceneEngine): SceneArt {
        val manifest = CastManifest.parse(File(art, "cast/manifest.json").readText())
        val loader: (String) -> ImageBitmap? = { p -> load(p.removeSuffix(".webp") + ".png") }
        val npcId = CastRegistry.castIdFor(story.character.id)?.takeIf { manifest.containsKey(it) }
        val playerId = CastRegistry.playerCastId(npcId, engine.player.appearance.body == com.kissa.app.model.BodyType.FEM)
        val s = story.setting.name.lowercase()
        return SceneArt(
            bg = load("bg_$s.png")!!, bgBlur = load("bg_${s}_blur.png"),
            bgRev = load("bg_${s}_rev.png"), bgRevBlur = load("bg_${s}_rev_blur.png"),
            npcCast = npcId?.let { CastSet(it, manifest.getValue(it), loader) },
            playerCast = playerId?.let { CastSet(it, manifest.getValue(it), loader) },
        )
    }

    /** Builds a scene, runs [frames] engine frames, locks the camera on [lock] (if given) and renders it. */
    private fun cine(name: String, storyIdx: Int = 0, frames: Int = 90, w: Int = 540, h: Int = 960, lock: ((SceneEngine) -> Shot)? = null, post: ((SceneEngine) -> Unit)? = null, setup: (SceneEngine) -> SpeakingState?) {
        val story = SeedContent.stories[storyIdx]
        val engine = SceneEngine(story.setting, story.playerAppearance, story.character)
        engine.startEntrance(false)
        val sp = setup(engine)
        lock?.let { engine.director.locked = it(engine) }
        repeat(frames) { engine.update(1f / 60f, sp) }
        post?.invoke(engine)
        val a = sceneArt(story, engine)
        shot(name, w, h) { drawCinematic(engine, a) }
    }

    private val loud = FloatArray(400) { 0.85f }

    @Test fun renderScenes() {
        if (System.getProperty("shots") != "true" && System.getenv("SHOTS") != "true") return
        // corridor story with the painted cast
        cine("c01_establishing", frames = 40) { e -> e.startEntrance(true); null }
        cine("c02_intro_player_medium", frames = 170) { e -> e.startEntrance(true); null }
        cine("c03_riya_closeup_amused_talking") { e -> e.react(e.npc.id, Emotion.AMUSED, Gesture.NONE); SpeakingState(e.npc.id, "Arre!", envelope = loud) }
        cine("c04_riya_closeup_angry_blink", lock = { Shot(ShotType.CLOSE_UP, it.npc.id, "player", 0f) }, post = { it.npc.pose.blink = 1f }) { e -> e.react(e.npc.id, Emotion.ANGRY, Gesture.NONE); null }
        cine("c05_ots_on_riya_shy", lock = { Shot(ShotType.OTS, it.npc.id, "player", 0f) }) { e -> e.react(e.npc.id, Emotion.SHY, Gesture.NONE); SpeakingState(e.npc.id, "Hmm", envelope = loud) }
        cine("c06_player_closeup_talking_shy") { e -> e.react("player", Emotion.SHY, Gesture.NONE); SpeakingState("player", "Sorry!", envelope = loud) }
        cine("c07_ots_on_player", lock = { Shot(ShotType.OTS, "player", it.npc.id, 0f) }) { e -> e.react("player", Emotion.HAPPY, Gesture.NONE); SpeakingState("player", "Hi", envelope = loud) }
        cine("c08_two_shot", lock = { Shot(ShotType.TWO_SHOT, it.npc.id, "player", 0f) }) { e -> null }
        cine("c09_exit_walk", frames = 100) { e -> e.event(SceneEvent.CHARACTER_EXITS); null }
        cine("c10_vhs_rewind", frames = 30) { e -> e.rewindFx(2f); e.react(e.npc.id, Emotion.SURPRISED, Gesture.NONE); null }
        cine("c11_riya_sad_landscape", w = 960, h = 540, lock = { Shot(ShotType.CLOSE_UP, it.npc.id, "player", 0f) }) { e -> e.react(e.npc.id, Emotion.SAD, Gesture.NONE); null }
        // other stories: same shot system with the procedural rig
        cine("p01_cafe_closeup", storyIdx = 1, lock = { Shot(ShotType.CLOSE_UP, it.npc.id, "player", 0f) }) { e -> e.react(e.npc.id, Emotion.AMUSED, Gesture.NONE); SpeakingState(e.npc.id, "Hmm", envelope = loud) }
        cine("p02_library_ots", storyIdx = 2, lock = { Shot(ShotType.OTS, it.npc.id, "player", 0f) }) { e -> e.react(e.npc.id, Emotion.CONFUSED, Gesture.NONE); null }
        cine("p03_forest_two_shot", storyIdx = 3, lock = { Shot(ShotType.TWO_SHOT, it.npc.id, "player", 0f) }) { e -> e.react(e.npc.id, Emotion.HAPPY, Gesture.WAVE); null }
        assertTrue(out.listFiles()!!.isNotEmpty())
    }
}
