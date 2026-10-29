package com.kissa.app.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kissa.app.game.SceneType
import kotlin.math.*
import kotlin.random.Random

/**
 * One painted character on stage. Pure animation state (no Android): smooth position/alpha, idle breathing + sway,
 * expression crossfade, natural blinking, lip-sync smoothing and short "move" gestures expressed as gentle whole-body
 * motion (nod, laugh bounce, step back, shake…). Only the face patches ever change, so nothing can pop or misalign.
 */
class Performer(val id: String, val cast: CastSet?) {
    var x = 0.5f; var targetX = 0.5f
    var alpha = 0f; var targetAlpha = 1f
    var enterDx = 0f
    var facing = 1 // 1 = as painted, -1 = mirrored
    var expr = "neutral"; private set
    var prevExpr = "neutral"; private set
    var exprBlend = 1f; private set
    var mouth = 0f; private set
    var blink = 0f; private set
    var dim = 0f; var targetDim = 0f
    var scale = 1f; var targetScale = 1f
    private var phase = Random(id.hashCode()).nextFloat() * 10f
    private var t = 0f
    private var nextBlink = 1.5f + Random(id.hashCode() + 7).nextFloat() * 2f
    private var blinkT = -1f
    private var move: String? = null
    private var moveT = 0f
    var visible: Boolean get() = targetAlpha > 0f || alpha > 0.01f
        set(v) { targetAlpha = if (v) 1f else 0f }

    fun setEmotion(emotion: String?) {
        val e = cast?.exprFor(emotion) ?: "neutral"
        if (e == expr) return
        prevExpr = if (exprBlend < 0.5f) prevExpr else expr
        expr = e; exprBlend = 0f
    }

    fun play(m: String?) { if (m == null || m == "idle") return; move = m; moveT = 0f }

    fun enterFrom(side: String, at: Float) {
        x = at; targetX = at; alpha = 0f; targetAlpha = 1f
        enterDx = when (side) { "left" -> -0.10f; "right" -> 0.10f; else -> 0f }
    }

    fun exitTo(side: String) { targetAlpha = 0f; enterDx = 0f; exitDx = when (side) { "left" -> -0.10f; else -> 0.10f } }
    private var exitDx = 0f

    /** [amp] = current voice amplitude 0..1 when this performer is the one speaking. */
    fun update(dt: Float, amp: Float) {
        t += dt
        x += (targetX - x) * min(1f, dt * 3.2f)
        alpha += (targetAlpha - alpha) * min(1f, dt * 4.5f)
        enterDx *= max(0f, 1f - dt * 4.2f)
        if (targetAlpha == 0f) enterDx += (exitDx - enterDx) * min(1f, dt * 3f)
        dim += (targetDim - dim) * min(1f, dt * 5f)
        scale += (targetScale - scale) * min(1f, dt * 4f)
        exprBlend = min(1f, exprBlend + dt / 0.22f)
        // lip-sync: fast attack, slower release, gated so tiny noise never flickers the mouth
        val target = ((amp - 0.10f) / 0.45f).coerceIn(0f, 1f)
        mouth += (target - mouth) * min(1f, dt * (if (target > mouth) 26f else 14f))
        // blink: close 70 ms, hold 40 ms, open 90 ms; occasional double blink
        if (blinkT < 0f) {
            nextBlink -= dt
            if (nextBlink <= 0f) { blinkT = 0f; nextBlink = 2.4f + Random.nextFloat() * 3.4f; if (Random.nextFloat() < 0.15f) nextBlink = 0.35f }
        } else {
            blinkT += dt
            blink = when {
                blinkT < 0.07f -> blinkT / 0.07f
                blinkT < 0.11f -> 1f
                blinkT < 0.20f -> 1f - (blinkT - 0.11f) / 0.09f
                else -> { blinkT = -1f; 0f }
            }
        }
        if (move != null) { moveT += dt; if (moveT > moveDuration(move!!)) move = null }
    }

    private fun moveDuration(m: String) = when (m) { "laugh", "cry", "shake_head" -> 0.9f; "nod" -> 0.6f; "step_back", "turn", "shrug" -> 0.7f; else -> 0.8f }

    /** Body transform for this frame: dx/dy in sprite-height units, rotation in degrees, extra scale. */
    data class Pose(val dx: Float, val dy: Float, val rot: Float, val sx: Float, val sy: Float)

    fun pose(): Pose {
        val breathe = sin((t + phase) * 2f * PI.toFloat() / 3.8f)
        val sway = sin((t + phase) * 2f * PI.toFloat() / 6.4f)
        var dx = enterDx + sway * 0.0025f
        var dy = breathe * 0.0018f
        var rot = sway * 0.35f
        var sx = 1f; var sy = 1f + breathe * 0.004f
        val m = move
        if (m != null) {
            val k = (moveT / moveDuration(m)).coerceIn(0f, 1f)
            val env = sin(k * PI.toFloat())
            when (m) {
                "nod" -> dy += sin(k * 2 * PI.toFloat()) * 0.010f
                "shake_head", "cry" -> dx += sin(k * 6 * PI.toFloat()) * 0.006f * env
                "laugh" -> dy -= abs(sin(k * 3 * PI.toFloat())) * 0.012f * env
                "step_back" -> { dx += 0.025f * env * facing; sx -= 0.02f * env; sy -= 0.02f * env }
                "point", "hands_on_hips", "cross_arms", "wave" -> { rot += -1.2f * env * facing; dy -= 0.004f * env }
                "look_down", "kneel", "sit" -> { dy += 0.012f * env; rot += 0.8f * env }
                "shrug" -> dy -= 0.010f * env
                "turn", "walk_away", "walk", "exit", "enter" -> dx += 0.015f * env
                "hold_phone" -> rot += 0.6f * env
            }
        }
        return Pose(dx, dy, rot, sx, sy)
    }
}

/** Floating crowd reaction bubble (😂 😮 👏 …) for scenes with an audience. */
class Reaction(val glyph: String, val x: Float, var age: Float = 0f)

/**
 * Everything a cinematic / POV frame needs: painted background (with time-of-day grade when no dedicated painting
 * exists), performers, the player's own painted avatar (slides in when the player speaks in cinematic scenes),
 * camera (zoom toward the speaker, shake), rain, flash, sparkles and crowd reactions. Pure state + DrawScope renderer.
 */
class StageState {
    var type = SceneType.CINEMATIC
    var background = "college_corridor"
    var time = "day"
    var weather = "clear"
    val actors = LinkedHashMap<String, Performer>()
    var player: Performer? = null
    var audience = 0
    var zoom = 1f; var targetZoom = 1f
    var focus = 0.5f
    var focusActor: String? = null
    var targetFocus: Float? = null
    var shake = 0f
    var flash = 0f
    var sparkles = 0f
    var clock = 0f
    var speaking: String? = null
    val reactions = ArrayList<Reaction>()
    private val rain = List(120) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f) }

    fun reset(type: SceneType, bg: String, time: String, weather: String) {
        this.type = type; background = bg; this.time = time; this.weather = weather
        actors.clear(); zoom = 1f; targetZoom = 1f; shake = 0f; flash = 0f; sparkles = 0f
        focusActor = null; targetFocus = null; reactions.clear(); speaking = null
        player?.visible = false
    }

    fun react(kind: String) {
        if (audience <= 0) return
        val glyphs = when (kind) { "laugh" -> listOf("😂", "🤣", "😆"); "gasp" -> listOf("😮", "😱", "🫢"); "cheer" -> listOf("👏", "🔥", "🙌"); "boo" -> listOf("👎", "😤"); else -> listOf("👀", "🤔") }
        repeat(min(6, 2 + audience / 3)) { i -> reactions += Reaction(glyphs[i % glyphs.size], if (i % 2 == 0) 0.06f + Random.nextFloat() * 0.18f else 0.76f + Random.nextFloat() * 0.18f, -i * 0.12f) }
    }

    fun update(dt: Float, amp: Float) {
        clock += dt
        actors.values.forEach { it.update(dt, if (it.id == speaking) amp else 0f) }
        player?.update(dt, 0f)
        // the speaker is lit; others step back into soft shadow
        val anySpeaker = speaking != null && actors.containsKey(speaking)
        actors.values.forEach { a -> val lit = !anySpeaker || a.id == speaking; a.targetDim = if (lit) 0f else 0.38f; a.targetScale = if (lit) 1f else 0.965f }
        zoom += (targetZoom - zoom) * min(1f, dt * 2.4f)
        val f = targetFocus ?: focusActor?.let { actors[it]?.x } ?: actors.values.filter { it.targetAlpha > 0f }.map { it.x }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0.5f
        focus += (f - focus) * min(1f, dt * 1.8f)
        shake = max(0f, shake - dt * 1.6f)
        flash = max(0f, flash - dt * 2.2f)
        sparkles = max(0f, sparkles - dt * 0.3f)
        reactions.forEach { it.age += dt }; reactions.removeAll { it.age > 2.2f }
    }

    fun rainDrops() = rain
}

object StageRenderer {
    private val paint = Paint()

    /** Draws one frame. [bg] painted background (may be null), [tinted] = true when it needs the time-of-day grade. */
    fun DrawScope.drawStage(st: StageState, bg: ImageBitmap?, tinted: Boolean) {
        val W = size.width; val H = size.height
        val pov = st.type == SceneType.POV
        val sx = if (st.shake > 0f) sin(st.clock * 70f) * st.shake * 16f else 0f
        val sy = if (st.shake > 0f) cos(st.clock * 53f) * st.shake * 10f else 0f
        val zoom = st.zoom * (if (pov) 1.06f else 1f)
        withTransform({ translate(sx, sy) }) {
            // ---- background: cover-fit, gentle drift + parallax opposite to the camera focus
            if (bg != null) {
                val s = max(W / bg.width, H / bg.height) * (1.06f + (zoom - 1f) * 0.35f)
                val dw = bg.width * s; val dh = bg.height * s
                val drift = sin(st.clock * 0.05f) * 0.01f
                val left = (W - dw) / 2 - (st.focus - 0.5f) * W * 0.06f + drift * W
                val top = (H - dh) / 2 + (zoom - 1f) * H * 0.05f
                drawImage(bg, IntOffset.Zero, IntSize(bg.width, bg.height), IntOffset(left.roundToInt(), top.roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.Medium)
            } else drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1830), Color(0xFF3A2E48))))
            if (tinted) when (st.time) {
                "dusk" -> drawRect(Color(0xFFFF8A4C), alpha = 0.18f, blendMode = BlendMode.Multiply)
                "night" -> { drawRect(Color(0xFF1B2A55), alpha = 0.55f, blendMode = BlendMode.Multiply); drawRect(Color(0xFF000010), alpha = 0.18f) }
            }
            if (st.weather == "rain") drawRect(Color(0xFF0A1424), alpha = 0.18f)
            // soft vignette + floor shadow so characters sit in the scene
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), center = Offset(W / 2, H * 0.45f), radius = max(W, H) * 0.75f))
            drawRect(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color(0xB0000000)))

            // ---- characters: half-body paintings anchored to the bottom edge, camera zooms toward the speaker's head
            val spriteH = H * (if (pov) 0.86f else 0.78f)
            fun drawPerformer(p: Performer, xFrac: Float, extraScale: Float) {
                val c = p.cast ?: return
                val base = c.base ?: return
                if (p.alpha < 0.01f) return
                val pose = p.pose()
                val sc = spriteH / c.h * extraScale * p.scale
                val cx = W * (0.5f + (xFrac + pose.dx - 0.5f) * (if (pov) 0.9f else 1.18f))
                val bottom = H + spriteH * 0.03f + pose.dy * spriteH
                val dw = c.w * sc; val dh = c.h * sc
                val left = cx - dw / 2; val top = bottom - dh
                val cf = if (p.dim > 0.01f) ColorFilter.tint(Color(0f, 0f, 0.05f, p.dim), BlendMode.SrcAtop) else null
                withTransform({
                    rotate(pose.rot, Offset(cx, bottom))
                    scale(pose.sx * p.facing, pose.sy, Offset(cx, bottom))
                }) {
                    fun img(b: ImageBitmap, r: Rect4?, a: Float) {
                        val dx = left + (r?.x ?: 0) * sc; val dy = top + (r?.y ?: 0) * sc
                        drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset(dx.roundToInt(), dy.roundToInt()),
                            IntSize((b.width * sc).roundToInt(), (b.height * sc).roundToInt()), alpha = a * p.alpha, colorFilter = cf, filterQuality = FilterQuality.Medium)
                    }
                    img(base, null, 1f)
                    // expression crossfade: previous face under, new face fading in (both over the unchanging body)
                    val cur = p.expr; val prev = p.prevExpr
                    if (p.exprBlend < 1f && prev != "neutral") c.face(prev)?.let { img(it, c.face, 1f) }
                    if (cur != "neutral") c.face(cur)?.let { img(it, c.face, p.exprBlend) } else if (p.exprBlend < 1f && prev != "neutral") c.face(prev)?.let { img(it, c.face, 1f - p.exprBlend) }
                    if (p.mouth > 0.02f) c.mouth(cur)?.let { m -> img(m, c.exprs[cur]?.mouth, p.mouth * p.exprBlend) }
                    if (p.blink > 0.02f) c.blink(cur)?.let { b -> img(b, c.exprs[cur]?.blink, p.blink * p.exprBlend) }
                }
            }
            // camera: scale around the focused performer's head
            val fp = st.focusActor?.let { st.actors[it] }
            val headY = fp?.cast?.let { H + spriteH * 0.03f - spriteH + it.headY / it.h * spriteH } ?: H * 0.42f
            val pivotX = W * (0.5f + (st.focus - 0.5f) * 1.18f)
            withTransform({ scale(zoom, zoom, Offset(pivotX, headY)) }) {
                st.actors.values.filter { it.id != st.speaking }.forEach { drawPerformer(it, it.x, 1f) }
                st.actors[st.speaking]?.let { drawPerformer(it, it.x, 1f) }
            }
            st.player?.let { p -> if (!pov) drawPerformer(p, p.x, 0.92f) }
            // ---- weather / fx
            if (st.weather == "rain") {
                val t = st.clock
                st.rainDrops().forEach { d ->
                    val x = ((d[0] + t * 0.08f * d[2]) % 1f) * W
                    val y = ((d[1] + t * 1.25f * d[2]) % 1f) * H
                    drawLine(Color(0x88C8DCFF), Offset(x, y), Offset(x - 6f * d[2], y + 34f * d[2]), strokeWidth = 1.6f * d[2])
                }
            }
            if (st.sparkles > 0f) repeat(26) { i ->
                val r = Random(i * 31)
                val x = r.nextFloat() * W; val y = ((r.nextFloat() - st.clock * 0.05f * (0.5f + r.nextFloat())) % 1f + 1f) % 1f * H
                drawCircle(Color(0xFFFFE6A8), radius = 2f + 3f * abs(sin(st.clock * 3f + i)), center = Offset(x, y), alpha = min(1f, st.sparkles) * 0.8f)
            }
        }
        if (st.flash > 0f) drawRect(Color.White, alpha = min(1f, st.flash))
    }
}
