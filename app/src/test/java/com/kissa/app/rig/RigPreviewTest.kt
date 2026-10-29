package com.kissa.app.rig

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kissa.app.game.Catalog
import com.kissa.app.game.GameParser
import com.kissa.app.game.Look
import com.kissa.app.game.SceneType
import com.kissa.app.play.BgLayers
import com.kissa.app.play.StageRenderer.drawStage
import com.kissa.app.play.StageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Shared JVM preview helpers (PNG mirror of the WebP kit, rendered through the Java2D android.graphics shim). */
object Preview {
    val root = File(System.getProperty("user.dir"))                       // app/app
    val png = File(root, "../../art/v3/png")
    val out = File(System.getProperty("previewOut") ?: File(root, "build/previews").path).apply { mkdirs() }
    fun load(f: File): ImageBitmap? = f.takeIf { it.exists() }?.let { com.kissa.shim.ShotIO.load(it.absolutePath) }
    val kit by lazy { RigKit(File(root, "src/main/assets/rig/rig.json").readText()) { rel -> load(File(png, "rig/$rel.png")) } }
    val renderer by lazy { RigRenderer(kit) }
    fun bg(id: String): BgLayers {
        val meta = kotlinx.serialization.json.Json.parseToJsonElement(File(root, "src/main/assets/bg/backgrounds.json").readText()).let { it as kotlinx.serialization.json.JsonObject }
        val top = (meta[id] as? kotlinx.serialization.json.JsonObject)?.get("mid_top")?.toString()?.toFloat() ?: 400f
        return BgLayers(load(File(png, "bg/${id}_back.png")), load(File(png, "bg/${id}_mid.png")), load(File(png, "bg/${id}_front.png")), top)
    }
    fun shot(name: String, w: Int, h: Int, block: DrawScope.() -> Unit): File {
        val img = com.kissa.shim.ShotIO.create(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
        val f = File(out, "$name.png"); com.kissa.shim.ShotIO.save(img, f.absolutePath); return f
    }
    fun enabled() = System.getProperty("shots") == "true" || System.getenv("SHOTS") == "true"
}

class RigPreviewTest {
    @Test fun catalogCounts() {
        val kit = Preview.kit
        assertEquals(20, Catalog.expressions.size); assertEquals(20, Catalog.moves.size); assertEquals(20, Catalog.props.size); assertEquals(10, Catalog.backgrounds.size)
        assertEquals(20, Expressions.table.size); assertTrue(Catalog.moves.all { it in Moves.clips })
        Catalog.props.forEach { assertTrue("prop $it", kit.info("prop/$it") != null) }
        Catalog.hairs.forEach { h -> assertTrue("hair $h", kit.info("hair/${h}_front") != null && kit.info("hair/${h}_back") != null) }
        for (rig in Catalog.rigs) for (o in Catalog.outfits) assertTrue("$rig/$o", kit.info("outfit/$rig/$o/torso_t") != null)
        Catalog.backgrounds.forEach { assertTrue("bg $it", File(Preview.root, "src/main/assets/bg/${it}_back.webp").exists()) }
    }

    @Test fun renderRigSheets() {
        if (!Preview.enabled()) return
        val r = Preview.renderer
        val games = File(Preview.root, "src/main/assets/games").listFiles()!!.filter { File(it, "game.json").exists() }.sortedBy { it.name }
        val cast = games.flatMap { GameParser.parse(File(it, "game.json").readText()).characters }
        // lineup of every launch character + two player looks
        val all = cast.map { Actor(it.id, it.rig, it.look) } + listOf(
            Actor("pf", "female", Look(skin = 2, hair = "ponytail", hairColor = "brown", outfit = "hoodie", outfitColor = "pink")),
            Actor("pm", "male", Look(skin = 1, hair = "curly", hairColor = "black", outfit = "tshirt_jeans", outfitColor = "olive", accessories = listOf("glasses_round"))))
        Preview.shot("rig_lineup", 220 * all.size, 760) {
            drawRect(Color(0xFF2A2638))
            all.forEachIndexed { i, a -> a.expression = listOf("smile", "neutral", "happy", "smug", "determined")[i % 5]; a.update(0.5f)
                with(r) { drawActor(a, Offset(110f + i * 220f, 310f), 0.42f) } }
        }
        // 20 expressions on a female and a male head
        for ((n, a) in listOf("female" to Actor("t", "female", cast.first().look), "male" to Actor("s", "male", cast.first { it.rig == "male" }.look))) {
            Preview.shot("expressions_$n", 5 * 260, 4 * 300) {
                drawRect(Color(0xFF3A3448))
                Catalog.expressions.forEachIndexed { i, e ->
                    a.expression = e; a.update(0.016f)
                    with(r) { drawActor(a, Offset(130f + (i % 5) * 260f, 300f * (i / 5) + 620f), 0.95f) }
                }
            }
        }
        // 20 moves (mid-clip pose)
        val m = Actor("m", "female", cast.first().look)
        Preview.shot("moves", 10 * 200, 2 * 520) {
            drawRect(Color(0xFF263040))
            Catalog.moves.forEachIndexed { i, mv ->
                m.play(mv); repeat(30) { m.update(0.02f) }
                if (mv == "hold_phone") m.props["hand_r"] = "phone" else m.props.remove("hand_r")
                with(r) { drawActor(m, Offset(100f + (i % 10) * 200f, 230f + (i / 10) * 520f), 0.3f) }
            }
        }
        // cinematic stage frame
        val st = StageState(); st.reset(SceneType.CINEMATIC, "college_corridor", "day", "clear")
        cast.take(2).forEachIndexed { i, c -> st.actors[c.id] = Actor(c.id, c.rig, c.look).apply { x = 0.62f + i * 0.2f; targetX = x; facing = -1 } }
        st.player = all.last().apply { x = 0.4f; targetX = x; facing = 1 }
        st.setAudience(5, 1); repeat(60) { st.update(1 / 30f) }
        val bg = Preview.bg("college_corridor")
        Preview.shot("stage_cinematic", 540, 960) { drawStage(st, bg, r) }
    }
}
