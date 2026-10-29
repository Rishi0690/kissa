package com.kissa.app.rig

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kissa.app.game.Catalog
import com.kissa.app.game.Look
import kotlin.math.*

/** Runtime state of one rigged character on stage. Pure state; [RigRenderer] draws it. */
class Actor(val id: String, var rig: String, var look: Look) {
    var x = 0.5f            // stage position 0..1
    var targetX = 0.5f
    var facing = -1         // -1 faces left, +1 faces right (front view mirrored)
    var expression = "neutral"
    var move = "idle"
    var moveTime = 0f
    var prevPose = Pose()
    var blendT = 1f
    var blink = 0f
    private var blinkClock = 2.5f
    var mouth = 0f          // 0..1 driven by voice amplitude
    var visible = true
    var alpha = 1f
    var exiting = false
    var exitDir = 1
    val props = HashMap<String, String>() // anchor -> prop id
    var time = 0f
    var speed = 0.32f       // stage widths per second when walking

    fun play(m: String) {
        if (m !in Catalog.moves) return
        prevPose = currentPose(); blendT = 0f
        move = m; moveTime = 0f
        if (m == "turn" || m == "walk_away") facing = -facing
    }

    fun walkTo(tx: Float, clip: String = "walk") { targetX = tx; if (move !in Moves.locomotion) play(clip) }

    fun enterFrom(side: String, at: Float) {
        visible = true; exiting = false; alpha = 1f
        x = if (side == "left") -0.25f else 1.25f
        facing = if (side == "left") 1 else -1
        targetX = at; play("enter")
    }

    fun exitTo(side: String) {
        exiting = true; exitDir = if (side == "left") -1 else 1
        facing = exitDir; targetX = if (side == "left") -0.3f else 1.3f
        if (move !in Moves.locomotion) play("exit")
    }

    fun currentPose(): Pose {
        val clip = Moves.clip(move)
        var p = clip.sample(moveTime)
        if (clip.done(moveTime) && move !in Moves.holding) {
            val back = ((moveTime - clip.seconds) / 0.4f).coerceIn(0f, 1f)
            p = p.lerp(Moves.clip("idle").sample(time), back)
        }
        if (blendT < 1f) p = prevPose.lerp(p, blendT)
        return Moves.breathe(time, p)
    }

    fun update(dt: Float) {
        time += dt; moveTime += dt
        blendT = (blendT + dt / 0.18f).coerceAtMost(1f)
        // locomotion
        val dx = targetX - x
        if (abs(dx) > 0.004f) {
            val step = sign(dx) * min(abs(dx), speed * dt)
            x += step
            facing = if (dx > 0) 1 else -1
            if (move !in Moves.locomotion) play("walk")
        } else if (move in Moves.locomotion) {
            x = targetX
            if (exiting) visible = false
            play("idle")
        }
        // blink
        blinkClock -= dt
        if (blinkClock <= 0f) { blink += dt / 0.07f; if (blink >= 2f) { blink = 0f; blinkClock = 2.2f + ((time * 7.31f) % 2.6f) } }
        if (move == "idle" && Moves.clip("idle").done(moveTime)) moveTime = 0f
    }

    val blinkAmount: Float get() = if (blink <= 0f) 0f else if (blink < 1f) blink else 2f - blink
}

/** Expression parameters for the vector face layer (20 catalog expressions). */
data class Expr(
    val browTilt: Float = 0f, val browRaise: Float = 0f, val eyeOpen: Float = 1f, val eyeMode: Int = 0, val pupil: Float = 1f,
    val lookX: Float = 0f, val lookY: Float = 0f, val mouth: Int = 0, val mouthOpen: Float = 0f,
    val blush: Float = 0f, val tears: Float = 0f, val sweat: Boolean = false, val vein: Boolean = false, val gloom: Boolean = false,
    val asym: Float = 0f,
)

object Expressions {
    // mouth: 0 line, 1 smile, 2 open smile, 3 grin, 4 frown, 5 shout, 6 O, 7 small, 8 smirk, 9 wavy
    // eyeMode: 0 normal, 1 happy arc, 2 closed (sad), 3 wide
    val table: Map<String, Expr> = mapOf(
        "neutral" to Expr(),
        "smile" to Expr(browRaise = 2f, eyeOpen = 0.92f, mouth = 1),
        "happy" to Expr(browRaise = 4f, eyeOpen = 0.95f, mouth = 2, mouthOpen = 0.35f, blush = 0.25f),
        "laugh" to Expr(browRaise = 6f, eyeMode = 1, mouth = 3, mouthOpen = 0.7f, blush = 0.3f),
        "angry" to Expr(browTilt = 20f, browRaise = -5f, eyeOpen = 0.78f, mouth = 4, mouthOpen = 0.1f, vein = true),
        "furious" to Expr(browTilt = 28f, browRaise = -8f, eyeOpen = 0.86f, pupil = 0.8f, mouth = 5, mouthOpen = 0.8f, vein = true, blush = 0.2f),
        "sad" to Expr(browTilt = -18f, browRaise = 2f, eyeOpen = 0.72f, lookY = 0.3f, mouth = 4),
        "crying" to Expr(browTilt = -24f, browRaise = 4f, eyeOpen = 0.45f, mouth = 9, mouthOpen = 0.3f, tears = 1f, blush = 0.35f),
        "shocked" to Expr(browRaise = 15f, eyeOpen = 1.25f, eyeMode = 3, pupil = 0.6f, mouth = 6, mouthOpen = 0.7f, gloom = true),
        "surprised" to Expr(browRaise = 12f, eyeOpen = 1.15f, eyeMode = 3, pupil = 0.8f, mouth = 6, mouthOpen = 0.4f),
        "shy" to Expr(browTilt = -10f, browRaise = 2f, eyeOpen = 0.75f, lookX = 0.6f, lookY = 0.3f, mouth = 7, blush = 0.8f),
        "blushing" to Expr(browTilt = -6f, browRaise = 3f, eyeOpen = 0.88f, mouth = 1, blush = 1f),
        "smug" to Expr(browTilt = 8f, browRaise = -1f, eyeOpen = 0.62f, mouth = 8, asym = 1f),
        "worried" to Expr(browTilt = -16f, browRaise = 4f, eyeOpen = 0.95f, mouth = 9, sweat = true),
        "scared" to Expr(browTilt = -20f, browRaise = 9f, eyeOpen = 1.2f, eyeMode = 3, pupil = 0.55f, mouth = 9, mouthOpen = 0.3f, sweat = true, gloom = true),
        "disgusted" to Expr(browTilt = 12f, browRaise = -2f, eyeOpen = 0.6f, lookX = -0.4f, mouth = 4, asym = -1f),
        "bored" to Expr(browRaise = -2f, eyeOpen = 0.52f, lookX = 0.3f, mouth = 0),
        "thinking" to Expr(browTilt = 6f, eyeOpen = 0.85f, lookX = 0.5f, lookY = -0.5f, mouth = 7, asym = 1f),
        "sleepy" to Expr(browTilt = -6f, eyeOpen = 0.22f, mouth = 6, mouthOpen = 0.12f),
        "determined" to Expr(browTilt = 14f, browRaise = -2f, eyeOpen = 0.95f, mouth = 0),
    )
    fun of(name: String) = table[name] ?: table.getValue("neutral")
}

/** Draws [Actor]s from the rig kit: forward kinematics over the shared skeleton, tinted layers, vector face, props. */
class RigRenderer(val kit: RigKit) {
    /** Slightly enlarged heads for the anime proportion (applies to face, hair and head accessories alike). */
    val HEAD_SCALE = 1.14f
    private val ink = Color(0xFF2B2228)

    private fun DrawScope.part(rel: String, tint: Color? = null, alpha: Float = 1f, scaleY: Float = 1f) {
        val img = kit.image(rel) ?: return
        val info = kit.info(rel) ?: return
        withTransform({ if (scaleY != 1f) scale(1f, scaleY, Offset.Zero) }) {
            drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(round(-info.px).toInt(), round(-info.py).toInt()), dstSize = IntSize(info.w, info.h),
                alpha = alpha, colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) })
        }
    }

    private fun restAngle(rig: String, a: String, b: String): Float {
        val (ax, ay) = kit.joint(rig, a); val (bx, by) = kit.joint(rig, b)
        return Math.toDegrees(atan2(-(bx - ax).toDouble(), (by - ay).toDouble())).toFloat()
    }
    private fun len(rig: String, a: String, b: String): Float { val (ax, ay) = kit.joint(rig, a); val (bx, by) = kit.joint(rig, b); return hypot(bx - ax, by - ay) }

    /**
     * Draws the actor with its pelvis at [pelvis] (screen px) and [scale] screen px per rig unit.
     * [drawBody]=false draws only head+shoulders region (still full rig, used for tight POV crops — clipping is the caller's).
     */
    fun DrawScope.drawActor(a: Actor, pelvis: Offset, scale: Float, tintMul: Color? = null) {
        if (!a.visible) return
        val rig = a.rig; val look = a.look; val pose = a.currentPose()
        val skin = RigKit.skin(look).mul(tintMul); val hair = RigKit.hair(look).mul(tintMul); val cloth = RigKit.cloth(look).mul(tintMul)
        val o = "outfit/$rig/${look.outfit}"
        val alpha = a.alpha
        withTransform({
            translate(pelvis.x, pelvis.y)
            scale(scale * (if (a.facing > 0) -1f else 1f) * pose.squash, scale, Offset.Zero)
            translate(pose.rootX, pose.rootY)
        }) {
            // backpack behind everything
            if ("backpack" in look.accessories) withTransform({ rotate(pose.lean, Offset.Zero) }) { part("acc/backpack", null, alpha) }
            // hair back layer, attached to the head bone
            val (hx, hy) = kit.joint(rig, "head")
            headBone(pose, hx, hy) { part("hair/${look.hair}_back", hair, alpha) }
            // legs
            for (side in listOf("l", "r")) leg(a, side, pose, o, skin, alpha)
            // torso
            withTransform({ rotate(pose.lean, Offset.Zero) }) {
                part("skin/$rig/torso", skin, alpha)
                part("$o/torso_t", cloth, alpha); part("$o/torso_c", tintMul, alpha)
                a.props["back"]?.let { p -> withTransform({ translate(0f, -300f) }) { prop(p) } }
                if ("scarf" in look.accessories) { val (nx, ny) = kit.joint(rig, "neck"); withTransform({ translate(nx, ny) }) { part("acc/scarf", tintMul, alpha) } }
                // head
                headBone(pose, hx, hy) {
                    part("face/${look.face}", skin, alpha)
                    face(a, look, pose)
                    if ("beard" in look.accessories) part("acc/beard", hair, alpha)
                    part("hair/${look.hair}_front", hair, alpha)
                    part("hair/${look.hair}_shine", null, alpha * 0.8f)
                    for (acc in listOf("earrings", "bindi", "glasses_round", "glasses_square")) if (acc in look.accessories) part("acc/$acc", tintMul, alpha)
                    a.props["head"]?.let { p -> withTransform({ translate(0f, (kit.faces[look.face] ?: kit.faces.getValue("soft")).crown + 30f) }) { prop(p) } }
                }
                // arms over the torso
                for (side in listOf("l", "r")) arm(a, side, pose, o, skin, cloth, alpha, tintMul)
            }
        }
    }

    private fun Color.mul(m: Color?): Color = if (m == null) this else Color(red * m.red, green * m.green, blue * m.blue, alpha)

    private inline fun DrawScope.headBone(pose: Pose, hx: Float, hy: Float, block: DrawScope.() -> Unit) {
        withTransform({ translate(hx + pose.headTurn * 6f, hy + pose.headDy); rotate(pose.head, Offset.Zero); scale(HEAD_SCALE, HEAD_SCALE, Offset.Zero) }, block)
    }

    private fun DrawScope.arm(a: Actor, side: String, p: Pose, o: String, skin: Color, cloth: Color, alpha: Float, tintMul: Color?) {
        val rig = a.rig
        val sgn = if (side == "l") 1f else -1f
        val (sx, sy) = kit.joint(rig, "shoulder_$side")
        val up = restAngle(rig, "shoulder_$side", "elbow_$side") + sgn * ((if (side == "l") p.armOutL else p.armOutR) - 4f)
        val upLen = len(rig, "shoulder_$side", "elbow_$side")
        val upS = if (side == "l") p.upperScaleL else p.upperScaleR
        val fore = -sgn * ((if (side == "l") p.elbowL else p.elbowR) - 4f)
        val foreS = if (side == "l") p.foreScaleL else p.foreScaleR
        val foreLen = len(rig, "elbow_$side", "wrist_$side")
        val hand = sgn * (if (side == "l") p.handL else p.handR)
        withTransform({ translate(sx, sy); rotate(up, Offset.Zero) }) {
            part("skin/$rig/upper_arm_$side", skin, alpha, upS)
            part("$o/upper_arm_${side}_t", cloth, alpha, upS)
            withTransform({ translate(0f, upLen * upS); rotate(fore, Offset.Zero) }) {
                part("skin/$rig/forearm_$side", skin, alpha, foreS)
                part("$o/forearm_${side}_t", cloth, alpha, foreS)
                if (side == "l" && "watch" in a.look.accessories) withTransform({ translate(0f, foreLen * foreS - 10f) }) { part("acc/watch", tintMul, alpha) }
                withTransform({ translate(0f, foreLen * foreS); rotate(hand, Offset.Zero) }) {
                    part("skin/$rig/hand_$side", skin, alpha)
                    a.props["hand_$side"]?.let { pr -> withTransform({ translate(0f, 52f) }) { prop(pr) } }
                }
            }
        }
    }

    private fun DrawScope.leg(a: Actor, side: String, p: Pose, o: String, skin: Color, alpha: Float) {
        val rig = a.rig
        val sgn = if (side == "l") 1f else -1f
        val (hx, hy) = kit.joint(rig, "hip_$side")
        val th = restAngle(rig, "hip_$side", "knee_$side") + sgn * ((if (side == "l") p.hipOutL else p.hipOutR) - 1f)
        val thLen = len(rig, "hip_$side", "knee_$side"); val thS = if (side == "l") p.thighScaleL else p.thighScaleR
        val kn = sgn * (if (side == "l") p.kneeL else p.kneeR) * 0.3f
        val shLen = len(rig, "knee_$side", "ankle_$side"); val shS = if (side == "l") p.shinScaleL else p.shinScaleR
        withTransform({ translate(hx, hy); rotate(th, Offset.Zero) }) {
            part("skin/$rig/thigh_$side", skin, alpha, thS)
            part("$o/thigh_${side}_c", null, alpha, thS)
            withTransform({ translate(0f, thLen * thS); rotate(kn, Offset.Zero) }) {
                part("skin/$rig/shin_$side", skin, alpha, shS)
                part("$o/shin_${side}_c", null, alpha, shS)
                withTransform({ translate(0f, shLen * shS) }) {
                    part("skin/$rig/foot_$side", skin, alpha)
                    part("$o/foot_${side}_c", null, alpha)
                }
            }
        }
    }

    private fun DrawScope.prop(id: String) {
        val rel = "prop/$id"; val info = kit.info(rel) ?: return
        val g = info.extra["grip"]?.let { (it as? kotlinx.serialization.json.JsonArray)?.let { a -> a[0].toString().toFloat() to a[1].toString().toFloat() } } ?: (0f to 0f)
        withTransform({ translate(-g.first, -g.second) }) { part(rel) }
    }

    // ---------------------------------------------------------------- vector face
    private fun DrawScope.face(a: Actor, look: Look, pose: Pose) {
        val f = kit.faces[look.face] ?: kit.faces.getValue("soft")
        val e = Expressions.of(a.expression)
        val turn = pose.headTurn * 10f
        val eyeCol = RigKit.eyes(look)
        val hairCol = RigKit.hair(look)
        val blinkA = a.blinkAmount
        // blush
        if (e.blush > 0f) for (s in listOf(-1f, 1f)) {
            drawOval(Brush.radialGradient(listOf(Color(0xFFFF6F7D).copy(alpha = 0.45f * e.blush), Color.Transparent), center = Offset(s * (f.eyeDx + 8f) + turn, f.eyeY + 34f), radius = 30f),
                topLeft = Offset(s * (f.eyeDx + 8f) - 30f + turn, f.eyeY + 18f), size = Size(60f, 32f))
            for (k in 0..2) drawLine(Color(0xFFE0505E).copy(alpha = 0.5f * e.blush), Offset(s * (f.eyeDx - 4f) + k * 7f + turn, f.eyeY + 40f), Offset(s * (f.eyeDx - 10f) + k * 7f + turn, f.eyeY + 28f), 2.2f)
        }
        // eyes
        for (s in listOf(-1f, 1f)) {
            val cx = s * f.eyeDx + turn
            val cy = f.eyeY
            val asymOpen = if (e.asym != 0f && s * e.asym > 0) 0.85f else 1f
            val open = (e.eyeOpen * asymOpen * (1f - blinkA)).coerceIn(0f, 1.3f)
            val w = f.eyeW; val h = f.eyeH * open
            if (e.eyeMode == 1 || (open < 0.12f)) {
                // closed / happy arc
                val path = Path().apply {
                    if (e.eyeMode == 1) { moveTo(cx - w / 2, cy + 4f); quadraticBezierTo(cx, cy - 16f, cx + w / 2, cy + 4f) }
                    else { moveTo(cx - w / 2, cy); quadraticBezierTo(cx, cy + 8f, cx + w / 2, cy) }
                }
                drawPath(path, ink, style = Stroke(5f, cap = StrokeCap.Round))
            } else {
                val top = cy - h / 2; val bot = cy + h / 2
                val sclera = Path().apply {
                    moveTo(cx - w / 2, cy); quadraticBezierTo(cx - w * 0.2f, top - 4f, cx + w / 2, cy - h * 0.15f)
                    quadraticBezierTo(cx + w * 0.3f, bot + 2f, cx - w / 2, cy); close()
                }
                drawPath(sclera, Color.White)
                clipPath(sclera) {
                    val ir = (w * 0.34f).coerceAtMost(f.eyeH * 0.6f)
                    val ix = cx + e.lookX * w * 0.18f * (if (a.facing > 0) -1f else 1f)
                    val iy = cy + e.lookY * h * 0.25f + 2f
                    drawCircle(Brush.verticalGradient(listOf(eyeCol.copy(alpha = 1f).darken(0.55f), eyeCol, eyeCol.lighten(0.25f)), startY = iy - ir, endY = iy + ir), ir, Offset(ix, iy))
                    drawCircle(Color(0xFF140C10), ir * 0.48f * e.pupil, Offset(ix, iy + 1f))
                    drawCircle(Color.White.copy(alpha = 0.95f), ir * 0.28f, Offset(ix + ir * 0.35f, iy - ir * 0.38f))
                    drawCircle(Color.White.copy(alpha = 0.7f), ir * 0.13f, Offset(ix - ir * 0.35f, iy + ir * 0.4f))
                    // upper-lid shadow
                    drawRect(Color(0x33000000), Offset(cx - w, top - 6f), Size(w * 2, 7f))
                }
                // lash line
                val lash = Path().apply { moveTo(cx - w / 2 - 3f, cy + 1f); quadraticBezierTo(cx - w * 0.2f, top - 6f, cx + w / 2 + 2f, cy - h * 0.18f) }
                drawPath(lash, ink, style = Stroke(5.5f, cap = StrokeCap.Round))
                drawLine(ink, Offset(cx + s * w / 2 + 1f, cy - h * 0.18f), Offset(cx + s * (w / 2 + 9f), cy - h * 0.18f - 7f), 3.5f, StrokeCap.Round)
                val low = Path().apply { moveTo(cx - w * 0.3f, bot + 1f); quadraticBezierTo(cx, bot + 3f, cx + w * 0.32f, bot - 1f) }
                drawPath(low, ink.copy(alpha = 0.45f), style = Stroke(2f, cap = StrokeCap.Round))
                if (e.eyeMode == 3) drawCircle(ink.copy(alpha = 0.15f), w * 0.6f, Offset(cx, cy), style = Stroke(1.5f))
            }
            // brows (inner end at the centre side)
            val by = f.browY - e.browRaise
            val tilt = e.browTilt * (if (e.asym != 0f && s * e.asym < 0) 0.4f else 1f)
            val inner = Offset(s * (f.eyeDx - f.eyeW * 0.45f) + turn, by + tilt * 0.35f)
            val outer = Offset(s * (f.eyeDx + f.eyeW * 0.55f) + turn, by - tilt * 0.12f - 2f)
            val brow = Path().apply { moveTo(inner.x, inner.y); quadraticBezierTo((inner.x + outer.x) / 2, min(inner.y, outer.y) - 6f, outer.x, outer.y) }
            drawPath(brow, hairCol.darken(0.35f), style = Stroke(7f, cap = StrokeCap.Round))
            if (e.tears > 0f) {
                val tp = Path().apply { moveTo(cx - 4f, cy + f.eyeH * 0.4f); cubicTo(cx - 10f, cy + 40f, cx + 6f, cy + 52f, cx + 2f, cy + 70f) }
                drawPath(tp, Color(0xFF9FD4FF).copy(alpha = 0.85f), style = Stroke(5f, cap = StrokeCap.Round))
            }
        }
        // mouth
        val mx = turn * 0.8f; val my = f.mouthY; val mw = f.mouthW
        val open = max(e.mouthOpen, a.mouth * 0.85f)
        val mouthCol = Color(0xFF5A1E28)
        when {
            open > 0.12f -> {
                val h = 6f + open * 22f
                val wide = when (e.mouth) { 3, 2 -> 1.25f; 5 -> 1.1f; 6 -> 0.6f; 7 -> 0.55f; else -> 0.85f }
                val w2 = mw * wide
                val smile = when (e.mouth) { 1, 2, 3, 8 -> 6f; 4, 5, 9 -> -4f; else -> 0f }
                val m = Path().apply {
                    moveTo(mx - w2, my - smile * 0.4f)
                    quadraticBezierTo(mx, my - h * 0.3f + smile * 0.2f, mx + w2, my - smile * 0.4f)
                    quadraticBezierTo(mx, my + h + smile, mx - w2, my - smile * 0.4f); close()
                }
                drawPath(m, mouthCol)
                clipPath(m) {
                    drawOval(Color(0xFFE07A86), Offset(mx - w2 * 0.6f, my + h * 0.45f), Size(w2 * 1.2f, h))
                    if (e.mouth in listOf(2, 3, 5)) drawRect(Color.White, Offset(mx - w2, my - h * 0.4f), Size(w2 * 2, h * 0.32f))
                }
                drawPath(m, ink, style = Stroke(2.6f))
            }
            else -> {
                val p = Path()
                when (e.mouth) {
                    1 -> { p.moveTo(mx - mw, my - 4f); p.quadraticBezierTo(mx, my + 10f, mx + mw, my - 4f) }
                    4 -> { p.moveTo(mx - mw * 0.8f, my + 4f); p.quadraticBezierTo(mx, my - 7f, mx + mw * 0.8f, my + 4f) }
                    7 -> { p.moveTo(mx - mw * 0.35f, my); p.quadraticBezierTo(mx, my + 3f, mx + mw * 0.35f, my) }
                    8 -> { p.moveTo(mx - mw * 0.7f, my + 1f); p.quadraticBezierTo(mx + mw * 0.2f, my + 4f, mx + mw * 0.9f, my - 7f) }
                    9 -> { p.moveTo(mx - mw * 0.7f, my); p.cubicTo(mx - mw * 0.3f, my - 6f, mx + mw * 0.2f, my + 6f, mx + mw * 0.7f, my) }
                    6 -> { p.addOval(androidx.compose.ui.geometry.Rect(mx - 6f, my - 5f, mx + 6f, my + 7f)) }
                    else -> { p.moveTo(mx - mw * 0.6f, my); p.quadraticBezierTo(mx, my + 2f, mx + mw * 0.6f, my) }
                }
                drawPath(p, ink, style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
        // comic marks
        if (e.sweat) {
            val sx = f.w / 2 - 10f + turn; val sy = f.eyeY - 60f
            val d = Path().apply { moveTo(sx, sy); cubicTo(sx - 12f, sy + 18f, sx - 10f, sy + 30f, sx, sy + 30f); cubicTo(sx + 10f, sy + 30f, sx + 12f, sy + 18f, sx, sy); close() }
            drawPath(d, Color(0xFFBFE6FF)); drawPath(d, Color(0xFF4A90C8), style = Stroke(2.5f))
        }
        if (e.vein) {
            val vx = -f.w / 2 + 34f + turn; val vy = f.browY - 40f
            for (k in 0..3) rotate(k * 90f + 45f, Offset(vx, vy)) { drawLine(Color(0xFFD8323C), Offset(vx + 5f, vy), Offset(vx + 15f, vy), 4.5f, StrokeCap.Round) }
        }
        if (e.gloom) for (k in 0..4) drawLine(Color(0xFF3C3A6E).copy(alpha = 0.5f), Offset(-f.w * 0.35f + k * f.w * 0.17f + turn, f.crown + 50f), Offset(-f.w * 0.35f + k * f.w * 0.17f + turn, f.browY - 20f), 3f)
    }

    private fun Color.darken(k: Float) = Color(red * (1 - k), green * (1 - k), blue * (1 - k), alpha)
    private fun Color.lighten(k: Float) = Color(red + (1 - red) * k, green + (1 - green) * k, blue + (1 - blue) * k, alpha)
}
