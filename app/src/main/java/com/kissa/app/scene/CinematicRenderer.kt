package com.kissa.app.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kissa.app.scene.CharacterPainter.drawCharacter
import kotlin.math.*

/**
 * Everything the cinematic renderer draws from. [bgRev] is the reverse-angle plate (shots looking at the player);
 * when null the main plate is mirrored. Blurred plates are pre-blurred offline (cheap depth of field on every API).
 */
class SceneArt(
    val bg: ImageBitmap,
    val bgBlur: ImageBitmap?,
    val bgRev: ImageBitmap? = null,
    val bgRevBlur: ImageBitmap? = null,
    val npcCast: CastSet? = null,
    val playerCast: CastSet? = null,
)

/** Desaturation applied to every painted layer while the VHS rewind plays (colour matrix works on all API levels). */
private var vhsFilter: ColorFilter? = null

private fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

/** Where the back-of-head sits inside each OTS frame (fractions of the image), measured from the art. */
private val OTS_HEAD = mapOf("kabir" to Triple(0.50f, 0.30f, 0.50f), "riya" to Triple(0.52f, 0.28f, 0.52f))

/**
 * Cinematic shot renderer (full-bleed, any aspect): hard cuts chosen by [ShotDirector], slow push-in on every held
 * shot, depth of field (blurred plate + blurred foreground shoulder), and "alive" micro-motion on the painted
 * characters: breathing, idle head sway, emotion tilt, nod/shake from gestures, blinking and amplitude-driven lip-sync.
 */
fun DrawScope.drawCinematic(engine: SceneEngine, art: SceneArt) {
    val w = size.width; val h = size.height
    val shot = engine.director.current
    val t = engine.time
    val age = max(0f, t - shot.startedAt)
    val push = if (engine.reduceMotion) 0f else ease(age / 10f)
    val npc = engine.npc; val player = engine.player
    fun actor(id: String?) = if (id == player.id) player else if (id == npc.id) npc else null
    fun castOf(a: Actor?) = when (a) { player -> art.playerCast; npc -> art.npcCast; else -> null }
    val subj = actor(shot.subject) ?: npc
    val other = actor(shot.other)
    val subjCast = castOf(subj)
    val lookingAtPlayer = subj === player
    val side = if (subj === player) -1f else 1f
    vhsFilter = if (engine.vhs > 0.05f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - engine.vhs.coerceIn(0f, 1f)) }) else null
    val vhsShake = if (engine.vhs > 0f) sin(t * 90f) * 9f * engine.vhs + (if (sin(t * 13f) > 0.92f) 26f else 0f) * engine.vhs else 0f

    withTransform({ translate(vhsShake, 0f) }) {
        // wide shots without painted art: the classic full stage (it draws its own grade/fade)
        val proceduralWide = subjCast == null && shot.type in setOf(ShotType.ESTABLISHING, ShotType.MEDIUM, ShotType.TWO_SHOT, ShotType.EXIT_WALK)
        if (proceduralWide) {
            drawStage(engine, art.bg)
        } else {
            when (shot.type) {
                ShotType.CLOSE_UP -> {
                    drawPlate(art, lookingAtPlayer, blur = 1f, zoom = 1.10f + push * 0.05f, t = t, engine = engine)
                    val faceH = h * 0.27f * (1f + push * 0.045f)
                    val fx = w * 0.5f + side * w * 0.05f
                    val fy = h * 0.40f
                    drawActorPortrait(engine, subj, subjCast, fx, fy, faceH, if (subj === player) 1f else -1f)
                    // a soft out-of-focus hint of the listener's shoulder keeps the eyeline grounded
                    if (other != null && shot.tag != "alone") drawBlurShoulder(w, h, -side, 0.35f)
                }
                ShotType.OTS -> {
                    drawPlate(art, lookingAtPlayer, blur = 1f, zoom = 1.12f + push * 0.04f, t = t, engine = engine)
                    val faceH = h * 0.205f * (1f + push * 0.04f)
                    drawActorPortrait(engine, subj, subjCast, w * 0.5f + side * w * 0.15f, h * 0.38f, faceH, if (subj === player) 1f else -1f)
                    drawOtsForeground(other, castOf(other), w, h, -side, t)
                }
                ShotType.MEDIUM -> {
                    drawPlate(art, lookingAtPlayer, blur = 0.55f, zoom = 1.04f + push * 0.04f, t = t, engine = engine)
                    val name = if (subj === player) (if (shot.tag == "intro" && subjCast?.has("body") == true) "body" else "stand") else "body"
                    drawBodyShot(subjCast, if (subjCast?.has(name) == true) name else "body", w * 0.5f + side * w * 0.06f, h * 1.0f, h * 0.88f * (1f + push * 0.04f), t, subj)
                }
                ShotType.TWO_SHOT, ShotType.ESTABLISHING -> {
                    val wide = shot.type == ShotType.ESTABLISHING
                    drawPlate(art, false, blur = 0f, zoom = (if (wide) 1.0f else 1.03f) + push * 0.06f, t = t, engine = engine)
                    drawSilhouettes(engine, w, h)
                    if (!wide) {
                        // the establishing shot shows the place only; the two-shot puts both characters in it
                        drawBodyShot(art.playerCast, if (art.playerCast?.has("stand") == true) "stand" else "body", w * 0.29f, h * 1.01f, h * 0.64f, t, player)
                        if (engine.director.npcPresent) drawBodyShot(art.npcCast, "body", w * 0.72f, h * 1.01f, h * 0.62f, t, npc)
                    }
                }
                ShotType.EXIT_WALK -> {
                    drawPlate(art, false, blur = 0f, zoom = 1.04f, t = t, engine = engine)
                    val p = ease(age / 3.4f)
                    val img = art.npcCast?.shot("walk")
                    val fr = art.npcCast?.shotFrame("walk")
                    if (img != null && fr != null && fr.bbox.size == 4) {
                        val targetH = h * (0.80f - 0.50f * p)
                        val s = targetH / (fr.bbox[3] - fr.bbox[1])
                        val cx = w * (0.60f - 0.10f * p) + sin(age * 7f) * w * 0.004f
                        val bottom = h * (1.0f - 0.30f * p)
                        val a = (1f - ((p - 0.82f) / 0.18f).coerceIn(0f, 1f))
                        withTransform({ translate(cx - (fr.bbox[0] + fr.bbox[2]) / 2f * s, bottom - fr.bbox[3] * s); scale(s, s, Offset.Zero) }) {
                            drawImage(img, Offset.Zero, alpha = a, colorFilter = vhsFilter)
                        }
                    }
                }
            }
            drawParticles(engine, w, h, 0f)
            if (engine.rain > 0.02f) drawRain(engine, w, h)
            drawSettingLightSoft(engine, w, h)
            drawFinish(engine, w, h, vignette = 0.5f)
        }
    }
    if (engine.vhs > 0.01f) drawVhs(engine, w, h)
}

/** Background plate with Ken Burns drift; blurred plate cross-faded in for shallow depth of field. */
private fun DrawScope.drawPlate(art: SceneArt, reverse: Boolean, blur: Float, zoom: Float, t: Float, engine: SceneEngine) {
    val w = size.width; val h = size.height
    val sharp = if (reverse && art.bgRev != null) art.bgRev else art.bg
    val soft = if (reverse && art.bgRev != null) art.bgRevBlur else art.bgBlur
    val mirror = reverse && art.bgRev == null
    val drift = if (engine.reduceMotion) 0f else sin(t * 0.05f) * w * 0.01f
    // landscape plates in a portrait frame: look at a different part of the room for each side of the conversation
    val focusX = if (mirror) 0.40f else 0.58f
    fun plate(img: ImageBitmap, alpha: Float) {
        if (alpha <= 0.01f) return
        val cover = max(w / img.width, h / img.height) * zoom
        val dw = img.width * cover; val dh = img.height * cover
        val left = (w - dw) * focusX.coerceIn(0f, 1f) + drift
        val top = (h - dh) * 0.5f
        withTransform({
            if (mirror) scale(-1f, 1f, Offset(w / 2f, h / 2f))
            translate(left, top)
            scale(cover, cover, Offset.Zero)
        }) { drawImage(img, Offset.Zero, alpha = alpha, colorFilter = vhsFilter) }
    }
    if (soft == null || blur < 0.99f) plate(sharp, 1f)
    if (soft != null && blur > 0.01f) plate(soft, blur)
}

private fun DrawScope.drawSettingLightSoft(engine: SceneEngine, w: Float, h: Float) {
    // warm light wash from the windows/arches (lighter than the wide-stage god rays so faces stay clean)
    val t = engine.time
    val a = 0.06f + 0.02f * sin(t * 0.4f)
    drawRect(Brush.linearGradient(listOf(Color(0xFFFFD9A0).copy(alpha = a), Color.Transparent), Offset(w, 0f), Offset(w * 0.2f, h * 0.7f)))
}

private fun DrawScope.drawSilhouettes(engine: SceneEngine, w: Float, h: Float) {
    engine.silhouettes.filter { it.depth < 0.6f }.forEach { drawSilhouette(it, w, h * 0.78f, h * 0.26f, engine.time, 0.28f) }
}

/** Painted close-up with micro-motion, blink and lip-sync; procedural rig when the character has no painted set. */
private fun DrawScope.drawActorPortrait(engine: SceneEngine, a: Actor, cast: CastSet?, fx: Float, fy: Float, faceH: Float, facing: Float) {
    val p = a.pose
    val t = engine.time
    val m = if (engine.reduceMotion) 0.35f else 1f
    if (cast == null) {
        // procedural fallback framed as a close-up (face ~120 rig units tall, centred ~920 above the feet; measured from renders)
        val s = faceH / 120f
        withTransform({ translate(fx, fy); scale(s * facing, s, Offset.Zero); translate(0f, 920f) }) { drawCharacter(a.appearance, p) }
        return
    }
    val face = cast.entry.face
    val s = faceH / face.h
    val imgH = cast.frame(a.emotion)?.h?.toFloat() ?: (face.cy * 2f)
    // chest-up frames can end above the bottom of the screen in tighter OTS framings: continue the torso by
    // stretching the last rows of the frame down (darkened), instead of showing a hard edge
    val extendPx = max(0f, (size.height * 1.02f - (fy + (imgH - face.cy) * s)) / s)
    val fyAdj = fy
    val sway = (sin(t * 0.55f + a.phase) * 0.7f + sin(t * 0.21f + a.phase * 2f) * 0.5f) * m
    val tilt = p.headTilt * 0.28f + sway
    val unit = faceH / 300f
    val dx = p.headShiftX * unit * 0.9f + p.lean * unit * 1.5f
    val dy = p.headShiftY * unit * 0.9f + p.bob * unit * 0.6f
    val breathe = 1f + p.breath * 0.006f * m
    // expression change: quick cross-fade from the previous face (~0.18 s) so swaps never pop
    val since = t - a.markStart
    val xfade = (since / 0.18f).coerceIn(0f, 1f)
    val emo = a.emotion
    withTransform({
        translate(fx + dx, fyAdj + dy)
        rotate(tilt, Offset(0f, faceH * 0.95f))
        scale(s * breathe, s * breathe, Offset(0f, faceH * 1.4f))
        translate(-face.cx, -face.cy)
    }) {
        if (xfade < 1f && a.prevEmotion != emo) cast.base(a.prevEmotion)?.let { drawImage(it, Offset.Zero, colorFilter = vhsFilter) }
        val base = cast.base(emo) ?: return@withTransform
        if (extendPx > 0f) {
            val src = 6
            drawImage(base, androidx.compose.ui.unit.IntOffset(0, base.height - src), androidx.compose.ui.unit.IntSize(base.width, src),
                androidx.compose.ui.unit.IntOffset(0, base.height - 2), androidx.compose.ui.unit.IntSize(base.width, extendPx.toInt() + 4), colorFilter = vhsFilter)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E0810)), base.height.toFloat(), base.height + extendPx),
                Offset(0f, base.height.toFloat() - 1f), Size(base.width.toFloat(), extendPx + 4f))
        }
        drawImage(base, Offset.Zero, alpha = if (xfade < 1f && a.prevEmotion != emo) xfade else 1f, colorFilter = vhsFilter)
        val fr = cast.frame(emo)
        if (xfade >= 1f || a.prevEmotion == emo) {
            // lip-sync: the open-mouth patch is blended by the real speech amplitude
            val mo = ((p.mouthOpen - 0.06f) / 0.34f).coerceIn(0f, 1f)
            if (mo > 0.01f) fr?.mouth?.let { pt -> cast.mouth(emo)?.let { drawImage(it, Offset(pt.x.toFloat(), pt.y.toFloat()), alpha = mo, colorFilter = vhsFilter) } }
            if (p.blink > 0.45f) fr?.blink?.let { pt -> cast.blink(emo)?.let { drawImage(it, Offset(pt.x.toFloat(), pt.y.toFloat()), colorFilter = vhsFilter) } }
        }
    }
}

/** Medium / wide body shot from a painted set (or the procedural rig). */
private fun DrawScope.drawBodyShot(cast: CastSet?, name: String, cx: Float, bottom: Float, height: Float, t: Float, a: Actor) {
    val img = cast?.shot(name); val fr = cast?.shotFrame(name)
    if (img == null || fr == null || fr.bbox.size != 4) {
        val s = height / 1000f
        withTransform({ translate(cx, bottom); scale(s * a.drawFacing, s, Offset.Zero) }) { drawCharacter(a.appearance, a.pose) }
        return
    }
    val bw = (fr.bbox[2] - fr.bbox[0]).toFloat(); val bh = (fr.bbox[3] - fr.bbox[1]).toFloat()
    val s = height / bh
    val breathe = 1f + a.pose.breath * 0.004f
    val sway = sin(t * 0.5f + a.phase) * 0.35f
    withTransform({
        translate(cx - (fr.bbox[0] + bw / 2f) * s, bottom - fr.bbox[3] * s)
        rotate(sway, Offset((fr.bbox[0] + bw / 2f) * s, fr.bbox[3] * s))
        scale(s, s * breathe, Offset(0f, fr.bbox[3] * s))
    }) { drawImage(img, Offset.Zero, colorFilter = vhsFilter) }
}

/** Over-the-shoulder foreground: the other character's back of head, pre-blurred and darkened (shallow focus). */
private fun DrawScope.drawOtsForeground(other: Actor?, cast: CastSet?, w: Float, h: Float, side: Float, t: Float) {
    val img = cast?.shot("ots_blur") ?: cast?.shot("ots")
    val head = cast?.id?.let { OTS_HEAD[it] }
    if (img == null || head == null || other == null) { drawBlurShoulder(w, h, side, 1f); return }
    val headH = h * 0.62f
    val s = headH / (img.height * head.third)
    val cx = w * (0.5f + side * 0.40f) + sin(t * 0.4f) * w * 0.004f
    val cy = h * 0.80f
    withTransform({ translate(cx - img.width * head.first * s, cy - img.height * head.second * s); scale(s, s, Offset.Zero) }) {
        drawImage(img, Offset.Zero, colorFilter = vhsFilter ?: ColorFilter.tint(Color(0x55120A10), BlendMode.SrcAtop))
    }
}

/** Generic out-of-focus shoulder blob for characters without a painted back view. */
private fun DrawScope.drawBlurShoulder(w: Float, h: Float, side: Float, strength: Float) {
    val c = Offset(w * (0.5f + side * 0.62f), h * 1.02f)
    val r = w * 0.55f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1016).copy(alpha = 0.75f * strength), Color(0xFF1A1016).copy(alpha = 0.35f * strength), Color.Transparent), c, r), r, c)
}

/** VHS rewind look: grayscale, scanlines, a rolling tracking band and chroma fringes. */
private fun DrawScope.drawVhs(engine: SceneEngine, w: Float, h: Float) {
    val v = engine.vhs
    val t = engine.time
    drawRect(Color(0xFF8A8A92).copy(alpha = 0.18f * v))
    drawRect(Color(0xFF101018).copy(alpha = 0.22f * v))
    var y = 0f
    val step = 5f * density.coerceAtLeast(1f)
    while (y < h) { drawRect(Color.Black.copy(alpha = 0.22f * v), Offset(0f, y), Size(w, step * 0.4f)); y += step }
    val bandY = ((t * 0.9f) % 1.2f - 0.1f) * h
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f * v), Color.Transparent), bandY - h * 0.04f, bandY + h * 0.04f), Offset(0f, bandY - h * 0.04f), Size(w, h * 0.08f))
    drawRect(Color(0xFFFF2050).copy(alpha = 0.07f * v), Offset(-4f, 0f), Size(w, h), blendMode = BlendMode.Plus)
    drawRect(Color(0xFF20C0FF).copy(alpha = 0.06f * v), Offset(4f, 0f), Size(w, h), blendMode = BlendMode.Plus)
}
