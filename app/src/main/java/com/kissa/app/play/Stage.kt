package com.kissa.app.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kissa.app.game.Catalog
import com.kissa.app.game.Look
import com.kissa.app.game.SceneType
import com.kissa.app.rig.Actor
import com.kissa.app.rig.RigRenderer
import kotlin.math.*
import kotlin.random.Random

/** Background layer images for one catalog background (back / mid / front). */
class BgLayers(val back: ImageBitmap?, val mid: ImageBitmap?, val front: ImageBitmap?, val midTop: Float)

/**
 * Everything a cinematic or POV frame needs: layered background, actors (with the player's own rig in cinematic
 * scenes), audience extras, camera (zoom / focus / shake), time-of-day and weather filters, flash and sparkles.
 * Pure state + a DrawScope renderer so frames can be rendered headless in JVM tests.
 */
class StageState {
    var type = SceneType.CINEMATIC
    var background = "college_corridor"
    var time = "day"
    var weather = "clear"
    val actors = LinkedHashMap<String, Actor>()
    var player: Actor? = null
    val extras = ArrayList<Actor>()
    var zoom = 1f; var targetZoom = 1f
    var focus = 0.5f; var targetFocus: Float? = null
    var focusActor: String? = null
    var shake = 0f
    var flash = 0f
    var sparkles = 0f
    var clock = 0f
    var speaking: String? = null

    fun reset(type: SceneType, bg: String, time: String, weather: String) {
        this.type = type; background = bg; this.time = time; this.weather = weather
        actors.clear(); extras.clear(); zoom = if (type == SceneType.POV) 1f else 1f; targetZoom = zoom; shake = 0f; flash = 0f; sparkles = 0f
        focusActor = null; targetFocus = null
    }

    fun setAudience(n: Int, seed: Int) {
        extras.clear()
        val r = Random(seed)
        repeat(n.coerceAtMost(10)) { i ->
            val rig = if (r.nextBoolean()) "female" else "male"
            val hairs = if (rig == "female") Catalog.hairs.take(6) else Catalog.hairs.drop(6)
            val look = Look(skin = r.nextInt(5), hair = hairs[r.nextInt(hairs.size)], hairColor = Catalog.hairColors[r.nextInt(4)],
                face = Catalog.faces[r.nextInt(3)], outfit = Catalog.outfits[r.nextInt(7)], outfitColor = Catalog.outfitColors[r.nextInt(Catalog.outfitColors.size)])
            extras += Actor("extra$i", rig, look).apply {
                x = 0.08f + (i.toFloat() / max(1, n - 1)) * 0.84f + (r.nextFloat() - 0.5f) * 0.05f
                targetX = x; facing = if (x < 0.5f) 1 else -1; time = r.nextFloat() * 5f
            }
        }
    }

    fun react(kind: String) {
        val (expr, move) = when (kind) {
            "laugh" -> "laugh" to "laugh"; "gasp" -> "shocked" to "step_back"; "cheer" -> "happy" to "wave"
            "murmur" -> "thinking" to "look_down"; else -> "surprised" to "idle"
        }
        extras.forEachIndexed { i, e -> if (i % 2 == 0 || kind != "murmur") { e.expression = expr; e.play(move) } }
    }

    fun update(dt: Float) {
        clock += dt
        actors.values.forEach { it.update(dt) }
        player?.update(dt)
        extras.forEach { it.update(dt) }
        zoom += (targetZoom - zoom) * min(1f, dt * 3f)
        val f = targetFocus ?: focusActor?.let { actors[it]?.x } ?: actors.values.filter { it.visible }.map { it.x }.takeIf { it.isNotEmpty() }?.let { xs ->
            val all = xs + listOfNotNull(player?.takeIf { type == SceneType.CINEMATIC }?.x); (all.minOrNull()!! + all.maxOrNull()!!) / 2
        } ?: 0.5f
        focus += (f - focus) * min(1f, dt * 2.2f)
        shake = max(0f, shake - dt * 1.6f)
        flash = max(0f, flash - dt * 2.5f)
        sparkles = max(0f, sparkles - dt * 0.25f)
    }
}

object StageRenderer {
    /** Draws one frame. [bg] is the background's layers; [rig] draws actors. */
    fun DrawScope.drawStage(st: StageState, bg: BgLayers?, rig: RigRenderer) {
        val W = size.width; val H = size.height
        val pov = st.type == SceneType.POV
        val sx = if (st.shake > 0f) sin(st.clock * 70f) * st.shake * 18f else 0f
        val sy = if (st.shake > 0f) cos(st.clock * 53f) * st.shake * 12f else 0f
        val zoom = st.zoom
        // background geometry: cover height, camera centred on focus
        val imgW = 1280f; val imgH = 714f
        val s = H / imgH * zoom * (if (pov) 1.08f else 1f)
        val bgW = imgW * s; val bgH = imgH * s
        val cam = st.focus.coerceIn(0f, 1f)
        fun leftFor(par: Float): Float {
            val visibleFrac = W / bgW
            val c = cam.coerceIn(visibleFrac / 2, 1 - visibleFrac / 2)
            return W / 2 - c * bgW * par + (par - 1f) * (bgW - W) * 0.5f * 0f
        }
        val top = H - bgH + (if (zoom > 1f) (bgH - H) * 0.35f else 0f)
        withTransform({ translate(sx, sy) }) {
            bg?.back?.let { drawImg(it, Offset(leftFor(1f) + (cam - 0.5f) * 30f, top), Size(bgW, bgH)) }
            bg?.mid?.let { m -> val mh = m.height * s; drawImg(m, Offset(leftFor(1f) - (cam - 0.5f) * 30f, top + bg.midTop * s), Size(bgW, mh)) }
            if (bg?.back == null) drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1830), Color(0xFF3A2E48))))
            // depth haze so actors pop
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x22000000), Color(0x55000000)), startY = H * 0.35f, endY = H))
            val floorY = H * (if (pov) 0.80f else 0.95f)
            // actors live in camera space: 1.0 stage unit = 1.5 screen widths at zoom 1, centred on the camera focus
            fun stageX(x: Float) = W / 2 + (x - cam) * W * 1.75f * zoom
            // audience extras (smaller, dimmed, behind)
            val exScale = H * (if (pov) 0.40f else 0.30f) * zoom / 1600f
            val dim = Color(0.66f, 0.64f, 0.74f, 1f)
            with(rig) {
                st.extras.sortedBy { it.x }.forEach { e ->
                    val px = if (pov) W * (0.04f + e.x * 0.92f) else W * (0.02f + e.x * 0.96f) + (0.5f - cam) * W * 0.4f
                    drawActor(e, Offset(px, (if (pov) H * 0.62f else floorY - H * 0.47f)), exScale, dim)
                }
                if (pov) {
                    // the facing character addresses the camera; the player is never drawn
                    val a = st.actors.values.firstOrNull { it.visible }
                    if (a != null) {
                        val scale = H * 1.55f * zoom / 1600f
                        val px = W / 2 + (a.x - 0.5f) * W * 0.6f
                        drawActor(a, Offset(px, H * 1.22f + (zoom - 1f) * H * 0.25f), scale)
                    }
                } else {
                    val scale = H * 0.66f * zoom / 1600f
                    val pelvisY = floorY - 780f * scale
                    st.player?.let { p -> drawActor(p, Offset(stageX(p.x), pelvisY), scale) }
                    st.actors.values.sortedBy { if (it.id == st.speaking) 1 else 0 }.forEach { a -> drawActor(a, Offset(stageX(a.x), pelvisY), scale) }
                }
            }
            bg?.front?.let { drawImg(it, Offset(leftFor(1f) - (cam - 0.5f) * 90f - 40f, top + 10f), Size(bgW + 80f, bgH)) }
        }
        // time-of-day / weather filters (no new artwork)
        when (st.time) {
            "night" -> { drawRect(Color(0xFF0A1030).copy(alpha = 0.42f)); drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA05060F)), Offset(W / 2, H * 0.45f), max(W, H) * 0.75f)) }
            "dusk" -> drawRect(Brush.verticalGradient(listOf(Color(0x55FF8A4C), Color(0x22B04AA0), Color(0x33201040))))
        }
        if (st.weather == "rain") {
            drawRect(Color(0x33203048))
            val r = Random(7)
            repeat(140) {
                val x0 = r.nextFloat() * W * 1.2f
                val speed = 900f + r.nextFloat() * 700f
                val y0 = ((r.nextFloat() * H + st.clock * speed) % (H + 80f)) - 40f
                drawLine(Color(0x88C8D8F0), Offset(x0 - y0 * 0.18f, y0), Offset(x0 - y0 * 0.18f - 6f, y0 + 34f), 1.6f)
            }
        }
        if (st.sparkles > 0f) {
            val r = Random(3)
            repeat(26) {
                val x = r.nextFloat() * W; val y = r.nextFloat() * H * 0.8f
                val tw = (sin(st.clock * 4f + it) * 0.5f + 0.5f) * st.sparkles.coerceAtMost(1f)
                drawCircle(Color(0xFFFFF2C2).copy(alpha = tw), 3f + 5f * tw, Offset(x, y))
            }
        }
        // cinematic vignette
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), Offset(W / 2, H * 0.42f), max(W, H) * 0.85f))
        if (st.flash > 0f) drawRect(Color.White.copy(alpha = st.flash.coerceAtMost(1f) * 0.8f))
    }

    private fun DrawScope.drawImg(img: ImageBitmap, at: Offset, sz: Size) {
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(at.x.roundToInt(), at.y.roundToInt()), IntSize(sz.width.roundToInt(), sz.height.roundToInt()), filterQuality = FilterQuality.Medium)
    }
}
