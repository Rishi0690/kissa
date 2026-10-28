package com.kissa.app.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.kissa.app.model.*
import kotlin.math.*

/** Facial parameter set. Expressions are blended continuously between these targets. */
data class Expr(
    val browTilt: Float = 0f,   // + angry (inner down), - sad (inner up)
    val browRaise: Float = 0f,
    val browAsym: Float = 0f,
    val eyeOpen: Float = 1f,
    val eyeSmile: Float = 0f,   // lower lid raise -> closed happy arcs at 1
    val mouthCurve: Float = 0.15f,
    val mouthWidth: Float = 1f,
    val mouthBase: Float = 0f,
    val mouthAsym: Float = 0f,
    val blush: Float = 0f,
    val pupil: Float = 1f,
) {
    fun lerpTo(o: Expr, k: Float) = Expr(
        lerp(browTilt, o.browTilt, k), lerp(browRaise, o.browRaise, k), lerp(browAsym, o.browAsym, k),
        lerp(eyeOpen, o.eyeOpen, k), lerp(eyeSmile, o.eyeSmile, k), lerp(mouthCurve, o.mouthCurve, k),
        lerp(mouthWidth, o.mouthWidth, k), lerp(mouthBase, o.mouthBase, k), lerp(mouthAsym, o.mouthAsym, k),
        lerp(blush, o.blush, k), lerp(pupil, o.pupil, k),
    )

    companion object {
        fun of(e: Emotion): Expr = when (e) {
            Emotion.NEUTRAL -> Expr()
            Emotion.HAPPY -> Expr(browRaise = 0.35f, eyeOpen = 0.9f, eyeSmile = 0.7f, mouthCurve = 0.95f, mouthWidth = 1.15f, mouthBase = 0.18f, blush = 0.3f)
            Emotion.SHY -> Expr(browTilt = -0.45f, browRaise = 0.2f, eyeOpen = 0.72f, eyeSmile = 0.25f, mouthCurve = 0.35f, mouthWidth = 0.75f, blush = 1f)
            Emotion.SURPRISED -> Expr(browRaise = 1f, eyeOpen = 1.22f, mouthCurve = 0f, mouthWidth = 0.6f, mouthBase = 0.45f, pupil = 0.72f)
            Emotion.CONFUSED -> Expr(browTilt = 0.15f, browAsym = 0.9f, eyeOpen = 0.95f, mouthCurve = -0.25f, mouthWidth = 0.8f, mouthAsym = 0.6f)
            Emotion.SAD -> Expr(browTilt = -0.9f, browRaise = 0.15f, eyeOpen = 0.78f, mouthCurve = -0.7f, mouthWidth = 0.85f, pupil = 1.1f)
            Emotion.ANGRY -> Expr(browTilt = 1f, browRaise = -0.45f, eyeOpen = 0.82f, mouthCurve = -0.55f, mouthWidth = 0.9f)
            Emotion.AMUSED -> Expr(browTilt = 0.05f, browAsym = 0.45f, eyeOpen = 0.82f, eyeSmile = 0.4f, mouthCurve = 0.75f, mouthWidth = 1.0f, mouthAsym = 0.55f, blush = 0.15f)
        }
    }
}

/** a = upper-arm angle from hanging (outward +), e = elbow bend (inward +), fs = upper-arm foreshortening (elbow toward camera). */
data class ArmPose(val a: Float = 6f, val e: Float = 8f, val fs: Float = 1f) {
    fun lerpTo(o: ArmPose, k: Float) = ArmPose(lerp(a, o.a, k), lerp(e, o.e, k), lerp(fs, o.fs, k))
}

/** Everything the painter needs for one frame of one character. Units: character is 1000 tall, feet at origin. */
class PoseFrame {
    var t = 0f
    var breath = 0f
    var lean = 0f
    var bob = 0f
    var headTilt = 0f
    var headShiftX = 0f
    var headShiftY = 0f
    var expr = Expr()
    var blink = 0f
    var gazeX = 0.6f
    var gazeY = 0f
    var mouthOpen = 0f
    var backArm = ArmPose()
    var frontArm = ArmPose()
    var legPhase = 0f
    var walk = 0f
    var hairSway = 0f
    var shoulderRaise = 0f
    var emotion = Emotion.NEUTRAL
    var markAlpha = 0f
    var thinking = 0f
    var alpha = 1f
    var turnedAway = 0f
}

fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

private fun Long.c() = Color(this)
private fun Color.shade(f: Float): Color = if (f < 0) Color(red * (1 + f), green * (1 + f), blue * (1 + f), alpha)
    else Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

private val LINE = Color(0xFF2A1A22)
private val MOUTH_IN = Color(0xFF6B2232)

object CharacterPainter {
    const val HEAD_DROP = 26f
    const val HEAD_SCALE = 1.08f


    fun DrawScope.drawCharacter(app: Appearance, p: PoseFrame) {
        val fem = app.body == BodyType.FEM
        val skin = app.skin.c()
        val skinShade = skin.shade(-0.14f)
        val hair = app.hair.c()
        val outfit = app.outfit.c()
        val accent = app.outfitAccent.c()
        val bottom = app.bottom.c()

        // ground shadow
        drawOval(Color.Black.copy(alpha = 0.28f * p.alpha), Offset(-120f, -18f), Size(240f, 36f))

        val sw = if (fem) 78f else 96f
        val shoulderY = -760f - p.shoulderRaise + p.breath * 3f
        val hipY = -470f

        rotate(p.lean, Offset(0f, 0f)) {
            translate(0f, p.bob) {
                drawLegs(app, p, fem, bottom, skin)
                drawBackHair(app, p, hair, fem)
                drawTorso(app, p, fem, sw, shoulderY, hipY, outfit, accent, bottom)
                // neck
                drawRoundRect(skinShade, Offset(-(if (fem) 15f else 19f), shoulderY - 40f), Size(if (fem) 30f else 38f, 52f), CornerRadius(10f))
                translate(p.headShiftX, p.headShiftY + p.breath * 2f + HEAD_DROP) {
                    rotate(p.headTilt, Offset(0f, -800f)) {
                        scale(HEAD_SCALE, HEAD_SCALE, Offset(0f, -810f)) { drawHead(app, p, fem, skin, skinShade, hair, accent) }
                    }
                }
                drawArm(app, p, fem, -1f, p.backArm, sw, shoulderY, outfit, accent, skin)
                drawArm(app, p, fem, 1f, p.frontArm, sw, shoulderY, outfit, accent, skin)
                if (p.thinking > 0.01f) drawThought(p)
            }
        }
    }

    private fun DrawScope.drawLegs(app: Appearance, p: PoseFrame, fem: Boolean, bottom: Color, skin: Color) {
        val skirt = app.outfitStyle == OutfitStyle.DRESS
        val legColor = if (skirt) skin else bottom
        val hx = if (fem) 34f else 40f
        for (side in listOf(-1f, 1f)) {
            val swing = sin(p.legPhase + if (side > 0) 0f else PI.toFloat()) * 24f * p.walk
            val knee = max(0f, -sin(p.legPhase + if (side > 0) 0f else PI.toFloat())) * 30f * p.walk
            val hip = Offset(side * hx, -470f)
            val a1 = Math.toRadians(swing.toDouble()).toFloat()
            val kneeP = hip + Offset(sin(a1) * 235f, cos(a1) * 235f)
            val a2 = a1 - Math.toRadians(knee.toDouble()).toFloat()
            val ankle = kneeP + Offset(sin(a2) * 215f, cos(a2) * 215f)
            drawLine(legColor, hip, kneeP, if (fem) 60f else 68f, StrokeCap.Round)
            drawLine(legColor.shade(-0.05f), kneeP, ankle, if (fem) 46f else 54f, StrokeCap.Round)
            // shoe
            val shoe = Color(0xFF26222B)
            drawOval(shoe, Offset(ankle.x - 30f + 10f, ankle.y - 18f), Size(64f, 34f))
            drawOval(Color.White.copy(alpha = 0.18f), Offset(ankle.x - 12f, ankle.y - 14f), Size(30f, 8f))
        }
    }

    private fun DrawScope.drawBackHair(app: Appearance, p: PoseFrame, hair: Color, fem: Boolean) {
        val s = p.hairSway
        val dark = hair.shade(-0.18f)
        val hx = p.headShiftX; val hy = p.headShiftY
        translate(hx, hy + HEAD_DROP) {
            rotate(p.headTilt * 0.6f, Offset(0f, -800f)) { scale(HEAD_SCALE, HEAD_SCALE, Offset(0f, -810f)) {
                when (app.hairStyle) {
                    HairStyle.LONG -> {
                        val path = Path().apply {
                            moveTo(-72f, -920f)
                            cubicTo(-98f, -840f, -112f + s * 0.5f, -720f, -104f + s, -600f)
                            quadraticBezierTo(-60f + s, -575f, -10f + s, -592f)
                            quadraticBezierTo(50f + s, -572f, 104f + s, -600f)
                            cubicTo(112f + s * 0.5f, -720f, 98f, -840f, 72f, -920f)
                            close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(hair, dark), -990f, -580f))
                    }
                    HairStyle.PONYTAIL -> {
                        val path = Path().apply {
                            moveTo(-40f, -960f)
                            cubicTo(-120f, -950f, -112f + s, -800f, -86f + s * 1.4f, -690f)
                            quadraticBezierTo(-70f + s * 1.4f, -660f, -58f + s, -700f)
                            cubicTo(-70f, -800f, -60f, -900f, -20f, -950f)
                            close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(hair, dark), -980f, -660f))
                        drawCircle(dark, 14f, Offset(-46f, -950f))
                    }
                    HairStyle.BOB -> {
                        val path = Path().apply {
                            moveTo(-74f, -920f)
                            cubicTo(-90f, -880f, -88f, -830f, -84f + s * 0.3f, -800f)
                            quadraticBezierTo(0f, -785f, 84f + s * 0.3f, -800f)
                            cubicTo(88f, -830f, 90f, -880f, 74f, -920f)
                            close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(hair, dark), -980f, -790f))
                    }
                    HairStyle.BUN -> {
                        drawCircle(hair, 38f, Offset(0f + s * 0.1f, -1000f))
                        drawCircle(dark.copy(alpha = 0.5f), 38f, Offset(0f, -1000f), style = Stroke(4f))
                    }
                    HairStyle.CURLY -> {
                        val pts = listOf(-82f to -930f, -92f to -880f, -88f to -830f, -70f to -795f, 70f to -795f, 88f to -830f, 92f to -880f, 82f to -930f, -40f to -985f, 40f to -985f, 0f to -1000f)
                        pts.forEach { (x, y) -> drawCircle(hair, 34f, Offset(x + s * 0.2f * (if (y > -850f) 1 else 0), y)) }
                    }
                    HairStyle.SHORT, HairStyle.SPIKY -> {}
                }
            } }
        }
    }

    private fun DrawScope.drawTorso(app: Appearance, p: PoseFrame, fem: Boolean, sw: Float, sy: Float, hipY: Float, outfit: Color, accent: Color, bottom: Color) {
        val waist = if (fem) 56f else 74f
        val hip = if (fem) 74f else 76f
        val chestScale = 1f + p.breath * 0.012f
        val body = Path().apply {
            moveTo(-sw + 16f, sy - 10f)
            quadraticBezierTo(-sw - 4f, sy, -sw, sy + 30f)
            cubicTo(-sw + 2f, sy + 90f, -waist - 4f, sy + 120f, -waist, hipY - 120f)
            quadraticBezierTo(-hip - 2f, hipY - 40f, -hip, hipY + 22f)
            lineTo(hip, hipY + 22f)
            quadraticBezierTo(hip + 2f, hipY - 40f, waist, hipY - 120f)
            cubicTo(waist + 4f, sy + 120f, sw - 2f, sy + 90f, sw, sy + 30f)
            quadraticBezierTo(sw + 4f, sy, sw - 16f, sy - 10f)
            close()
        }
        // pants/waistband under tunic styles
        drawRect(bottom, Offset(-hip, hipY - 10f), Size(hip * 2, 60f))
        scale(1f, chestScale, Offset(0f, hipY)) {
            val bodyBrush = Brush.horizontalGradient(listOf(outfit.shade(0.06f), outfit, outfit.shade(-0.2f)), -sw, sw)
            when (app.outfitStyle) {
                OutfitStyle.KURTI -> {
                    val tunic = Path().apply {
                        moveTo(-waist, hipY - 120f)
                        quadraticBezierTo(-hip - 30f, hipY + 60f, -108f + p.hairSway * 0.15f, -250f)
                        lineTo(108f + p.hairSway * 0.15f, -250f)
                        quadraticBezierTo(hip + 30f, hipY + 60f, waist, hipY - 120f)
                        close()
                    }
                    drawPath(tunic, bodyBrush)
                    drawPath(body, bodyBrush)
                    // embroidered neckline and hem
                    val neck = Path().apply { moveTo(-30f, sy - 8f); quadraticBezierTo(0f, sy + 70f, 30f, sy - 8f) }
                    drawPath(neck, accent, style = Stroke(7f))
                    for (i in -4..4) drawCircle(accent.copy(alpha = 0.85f), 4f, Offset(i * 7f, sy + 30f + (16 - i * i).toFloat() * 1.6f))
                    drawLine(accent.copy(alpha = 0.7f), Offset(-106f, -262f), Offset(106f, -262f), 6f)
                    for (i in -6..6) drawCircle(accent.copy(alpha = 0.6f), 3.5f, Offset(i * 16f, -280f))
                }
                OutfitStyle.DRESS -> {
                    val skirt = Path().apply {
                        moveTo(-waist, hipY - 120f)
                        cubicTo(-hip - 20f, hipY - 40f, -130f + p.hairSway * 0.3f, -340f, -136f + p.hairSway * 0.4f, -300f)
                        quadraticBezierTo(-68f, -284f, 0f, -300f)
                        quadraticBezierTo(68f, -284f, 136f + p.hairSway * 0.4f, -300f)
                        cubicTo(130f + p.hairSway * 0.3f, -340f, hip + 20f, hipY - 40f, waist, hipY - 120f)
                        close()
                    }
                    drawPath(skirt, bodyBrush)
                    drawPath(body, bodyBrush)
                    drawLine(accent, Offset(-waist, hipY - 118f), Offset(waist, hipY - 118f), 12f)
                    val neck = Path().apply { moveTo(-36f, sy - 6f); quadraticBezierTo(0f, sy + 50f, 36f, sy - 6f) }
                    drawPath(neck, accent, style = Stroke(6f))
                    // little leaf pattern
                    for (i in 0..5) drawCircle(accent.copy(alpha = 0.35f), 6f, Offset(-90f + i * 36f, -330f))
                }
                else -> {
                    drawPath(body, bodyBrush)
                    when (app.outfitStyle) {
                        OutfitStyle.HOODIE -> {
                            val hood = Path().apply {
                                moveTo(-56f, sy - 8f); quadraticBezierTo(0f, sy - 40f, 56f, sy - 8f)
                                quadraticBezierTo(0f, sy + 46f, -56f, sy - 8f); close()
                            }
                            drawPath(hood, outfit.shade(-0.25f))
                            drawLine(accent, Offset(-14f, sy + 20f), Offset(-18f, sy + 110f), 4f, StrokeCap.Round)
                            drawLine(accent, Offset(14f, sy + 20f), Offset(18f, sy + 110f), 4f, StrokeCap.Round)
                            drawRoundRect(outfit.shade(-0.12f), Offset(-62f, hipY - 110f), Size(124f, 70f), CornerRadius(18f))
                            drawRect(outfit.shade(-0.22f), Offset(-hip, hipY - 4f), Size(hip * 2, 24f))
                        }
                        OutfitStyle.BLAZER, OutfitStyle.JACKET -> {
                            val inner = Path().apply {
                                moveTo(-34f, sy - 8f); lineTo(34f, sy - 8f)
                                lineTo(22f, hipY + 20f); lineTo(-22f, hipY + 20f); close()
                            }
                            drawPath(inner, accent)
                            val lapelL = Path().apply { moveTo(-36f, sy - 8f); lineTo(-6f, sy + 150f); lineTo(-48f, sy + 60f); close() }
                            val lapelR = Path().apply { moveTo(36f, sy - 8f); lineTo(6f, sy + 150f); lineTo(48f, sy + 60f); close() }
                            drawPath(lapelL, outfit.shade(-0.18f)); drawPath(lapelR, outfit.shade(-0.18f))
                            drawLine(outfit.shade(-0.3f), Offset(-22f, sy + 150f), Offset(-22f, hipY + 20f), 4f)
                            drawLine(outfit.shade(-0.3f), Offset(22f, sy + 150f), Offset(22f, hipY + 20f), 4f)
                            if (app.outfitStyle == OutfitStyle.BLAZER) {
                                drawCircle(accent.shade(-0.3f), 5f, Offset(-30f, hipY - 90f))
                            } else {
                                drawLine(Color(0xFFBFC3CC), Offset(-24f, sy + 160f), Offset(-24f, hipY + 10f), 2.5f)
                            }
                        }
                        OutfitStyle.UNIFORM -> {
                            val collar = Path().apply { moveTo(-40f, sy - 8f); lineTo(0f, sy + 46f); lineTo(40f, sy - 8f); lineTo(24f, sy - 12f); lineTo(0f, sy + 22f); lineTo(-24f, sy - 12f); close() }
                            drawPath(collar, accent)
                            drawLine(accent.copy(alpha = 0.9f), Offset(-sw + 10f, sy + 60f), Offset(sw - 30f, hipY - 60f), 9f)
                            drawCircle(accent, 9f, Offset(-38f, sy + 70f))
                        }
                        OutfitStyle.TEE -> {
                            val neck = Path().apply { moveTo(-34f, sy - 8f); quadraticBezierTo(0f, sy + 34f, 34f, sy - 8f) }
                            drawPath(neck, outfit.shade(-0.3f), style = Stroke(7f))
                            drawCircle(accent.copy(alpha = 0.85f), 30f, Offset(0f, sy + 120f))
                            drawCircle(outfit, 16f, Offset(0f, sy + 120f))
                        }
                        else -> {}
                    }
                }
            }
        }
        // soft shading under the chest/arms
        drawPath(body, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)), sy, hipY + 20f))
    }

    private fun DrawScope.drawArm(app: Appearance, p: PoseFrame, fem: Boolean, side: Float, pose: ArmPose, sw: Float, sy: Float, outfit: Color, accent: Color, skin: Color) {
        val shoulder = Offset(side * (sw - 14f), sy + 22f)
        val a = Math.toRadians(pose.a.toDouble())
        val c = Math.toRadians((pose.a - pose.e).toDouble())
        val upper = (if (fem) 170f else 180f) * pose.fs
        val fore = if (fem) 158f else 166f
        val elbow = shoulder + Offset((side * sin(a) * upper).toFloat(), (cos(a) * upper).toFloat())
        val hand = elbow + Offset((side * sin(c) * fore).toFloat(), (cos(c) * fore).toFloat())
        val shortSleeve = app.outfitStyle == OutfitStyle.TEE || app.outfitStyle == OutfitStyle.DRESS
        val w1 = if (fem) 33f else 41f
        val w2 = if (fem) 28f else 35f
        val sleeve = outfit.shade(if (side < 0) -0.12f else 0f)
        val outline = outfit.shade(-0.45f).copy(alpha = 0.55f)
        drawLine(outline, shoulder, elbow, w1 + 5f, StrokeCap.Round)
        drawLine(outline, elbow, hand, w2 + 5f, StrokeCap.Round)
        if (shortSleeve) {
            val mid = Offset(lerp(shoulder.x, elbow.x, 0.45f), lerp(shoulder.y, elbow.y, 0.45f))
            drawLine(skin.shade(if (side < 0) -0.08f else 0f), mid, elbow, w1 - 6f, StrokeCap.Round)
            drawLine(sleeve, shoulder, mid, w1 + 4f, StrokeCap.Round)
            drawLine(skin.shade(if (side < 0) -0.08f else 0f), elbow, hand, w2, StrokeCap.Round)
        } else {
            val kurti = app.outfitStyle == OutfitStyle.KURTI
            drawLine(sleeve, shoulder, elbow, w1, StrokeCap.Round)
            if (kurti) {
                val mid = Offset(lerp(elbow.x, hand.x, 0.5f), lerp(elbow.y, hand.y, 0.5f))
                drawLine(skin.shade(if (side < 0) -0.08f else 0f), mid, hand, w2 - 4f, StrokeCap.Round)
                drawLine(sleeve, elbow, mid, w2 + 2f, StrokeCap.Round)
                drawCircle(accent, (w2 + 2f) / 2f, mid, style = Stroke(4f))
            } else {
                drawLine(sleeve, elbow, hand, w2, StrokeCap.Round)
                val cuff = Offset(lerp(elbow.x, hand.x, 0.86f), lerp(elbow.y, hand.y, 0.86f))
                drawCircle(accent.copy(alpha = 0.9f), w2 / 2f, cuff)
            }
        }
        drawCircle(skin.shade(if (side < 0) -0.08f else 0f), if (fem) 17f else 21f, hand)
        drawCircle(skin.shade(-0.22f), if (fem) 17f else 21f, hand, style = Stroke(2f))
    }

    private fun DrawScope.drawHead(app: Appearance, p: PoseFrame, fem: Boolean, skin: Color, skinShade: Color, hair: Color, accent: Color) {
        val jaw = if (fem) 62f else 67f
        // ears
        if (app.accessory == Accessory.ELF_EARS) {
            for (s in listOf(-1f, 1f)) {
                val ear = Path().apply { moveTo(s * 56f, -900f); lineTo(s * 112f, -950f); lineTo(s * 60f, -860f); close() }
                drawPath(ear, skin); drawPath(ear, skinShade, style = Stroke(3f))
            }
        } else {
            drawOval(skin, Offset(-jaw - 9f, -896f), Size(20f, 34f))
            drawOval(skin, Offset(jaw - 11f, -896f), Size(20f, 34f))
        }
        val face = Path().apply {
            moveTo(0f, -968f)
            cubicTo(42f, -968f, jaw + 2f, -942f, jaw + 2f, -896f)
            cubicTo(jaw + 2f, -862f, if (fem) 48f else 56f, -834f, if (fem) 20f else 26f, -816f)
            quadraticBezierTo(0f, if (fem) -804f else -806f, if (fem) -20f else -26f, -816f)
            cubicTo(if (fem) -48f else -56f, -834f, -jaw - 2f, -862f, -jaw - 2f, -896f)
            cubicTo(-jaw - 2f, -942f, -42f, -968f, 0f, -968f)
            close()
        }
        drawPath(face, Brush.verticalGradient(listOf(skin.shade(0.04f), skin, skin.shade(-0.04f)), -968f, -806f))
        // face features
        val e = p.expr
        // blush
        if (e.blush > 0.02f) {
            val b = Color(0xFFFF6F8A).copy(alpha = (0.5f * e.blush).coerceIn(0f, 0.55f))
            drawOval(b, Offset(-62f, -862f), Size(36f, 16f))
            drawOval(b, Offset(26f, -862f), Size(36f, 16f))
            if (e.blush > 0.7f) for (s in listOf(-1f, 1f)) for (i in 0..2) {
                val x = s * 44f + (i - 1) * 8f
                drawLine(Color(0xFFE0506E).copy(alpha = 0.6f), Offset(x + 3f, -862f), Offset(x - 3f, -850f), 2.5f, StrokeCap.Round)
            }
        }
        drawEyes(p, fem, app)
        drawBrows(p, fem, hair)
        // nose
        drawLine(skinShade.shade(-0.1f), Offset(3f, -856f), Offset(-1f, -848f), 3f, StrokeCap.Round)
        drawMouth(p, fem)
        drawFrontHair(app, p, hair, fem, accent)
        // accessories
        when (app.accessory) {
            Accessory.GLASSES -> {
                val gc = Color(0xFF2B2430)
                drawRoundRect(gc, Offset(-56f, -900f), Size(46f, 36f), CornerRadius(10f), style = Stroke(4.5f))
                drawRoundRect(gc, Offset(10f, -900f), Size(46f, 36f), CornerRadius(10f), style = Stroke(4.5f))
                drawLine(gc, Offset(-10f, -886f), Offset(10f, -886f), 4f)
                drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(-54f, -898f), Size(20f, 12f), CornerRadius(6f))
            }
            Accessory.EARRINGS -> {
                val sway = sin(p.t * 2.2f) * 3f
                for (s in listOf(-1f, 1f)) {
                    drawLine(Color(0xFFE8C26A), Offset(s * (jaw + 1f), -866f), Offset(s * (jaw + 1f) + sway, -846f), 2f)
                    drawCircle(Color(0xFFF2CF73), 6f, Offset(s * (jaw + 1f) + sway, -842f))
                }
            }
            Accessory.HEADBAND -> {
                val band = Path().apply { moveTo(-74f, -925f); quadraticBezierTo(0f, -1000f, 74f, -925f) }
                drawPath(band, accent.shade(-0.1f), style = Stroke(12f, cap = StrokeCap.Round))
            }
            Accessory.VISOR -> {
                val band = Path().apply { moveTo(-78f, -935f); quadraticBezierTo(0f, -985f, 78f, -935f) }
                drawPath(band, Color(0xFF7FE3FF).copy(alpha = 0.75f + 0.2f * sin(p.t * 3f)), style = Stroke(10f, cap = StrokeCap.Round))
                drawCircle(Color(0xFF7FE3FF), 6f, Offset(70f, -935f))
            }
            else -> {}
        }
        drawMarks(p)
    }

    private fun DrawScope.drawEyes(p: PoseFrame, fem: Boolean, app: Appearance) {
        val e = p.expr
        val open = (e.eyeOpen * (1f - p.blink)).coerceIn(0f, 1.3f)
        val hw = if (fem) 19f else 17f
        val hh = (if (fem) 22f else 17f)
        val eyeY = -878f
        val iris = Color(app.eyes)
        for (s in listOf(-1f, 1f)) {
            val cx = s * 31f
            val smiling = e.eyeSmile > 0.62f
            if (smiling || open < 0.14f) {
                val path = Path()
                if (smiling && p.blink < 0.5f) {
                    path.moveTo(cx - hw, eyeY + 4f); path.quadraticBezierTo(cx, eyeY - hh * 1.1f, cx + hw, eyeY + 4f)
                } else {
                    path.moveTo(cx - hw, eyeY + 2f); path.quadraticBezierTo(cx, eyeY + 9f, cx + hw, eyeY + 2f)
                }
                drawPath(path, LINE, style = Stroke(if (fem) 5.5f else 5f, cap = StrokeCap.Round))
                if (fem) drawLine(LINE, Offset(cx + s * hw, eyeY + 3f), Offset(cx + s * (hw + 7f), eyeY - 3f), 3.5f, StrokeCap.Round)
                continue
            }
            val h = hh * open
            val lowerLift = e.eyeSmile * h * 0.7f
            val rect = Rect(cx - hw, eyeY - h, cx + hw, eyeY + h - lowerLift)
            val clip = Path().apply { addOval(rect) }
            clipPath(clip) {
                drawRect(Color.White, rect.topLeft, rect.size)
                val gy = p.gazeY * 4f
                val ir = Rect(cx - hw * 0.72f + p.gazeX * 5f, eyeY - hh * 0.82f + gy, cx + hw * 0.72f + p.gazeX * 5f, eyeY + hh * 0.82f + gy)
                drawOval(Brush.verticalGradient(listOf(iris.shade(-0.45f), iris, iris.shade(0.35f)), ir.top, ir.bottom), ir.topLeft, ir.size)
                val pr = 7f * e.pupil
                drawOval(Color(0xFF140A10), Offset(ir.center.x - pr * 0.8f, ir.center.y - pr), Size(pr * 1.6f, pr * 2f))
                drawCircle(Color.White, 5.5f, Offset(ir.center.x - 5f, ir.center.y - 8f))
                drawCircle(Color.White.copy(alpha = 0.8f), 2.5f, Offset(ir.center.x + 5f, ir.center.y + 6f))
                // upper lid shadow
                drawRect(Color.Black.copy(alpha = 0.10f), Offset(rect.left, rect.top), Size(rect.width, 6f))
            }
            // lashes
            drawArc(LINE, 195f, 150f, false, rect.topLeft - Offset(1f, 1f), Size(rect.width + 2f, rect.height + 2f), style = Stroke(if (fem) 6f else 4.5f, cap = StrokeCap.Round))
            if (fem) drawLine(LINE, Offset(cx + s * (hw - 1f), rect.center.y - h * 0.45f), Offset(cx + s * (hw + 8f), rect.center.y - h * 0.8f), 4f, StrokeCap.Round)
            drawArc(LINE.copy(alpha = 0.55f), 30f, 120f, false, rect.topLeft, rect.size, style = Stroke(2f))
        }
    }

    private fun DrawScope.drawBrows(p: PoseFrame, fem: Boolean, hair: Color) {
        val e = p.expr
        val baseY = -914f - e.browRaise * 9f
        val color = hair.shade(-0.25f)
        for (s in listOf(-1f, 1f)) {
            val asym = if (s > 0) e.browAsym * 9f else -e.browAsym * 2f
            val inner = Offset(s * 13f, baseY + e.browTilt * 9f - asym)
            val outer = Offset(s * 47f, baseY - e.browTilt * 3f - asym + 2f)
            val path = Path().apply {
                moveTo(inner.x, inner.y)
                quadraticBezierTo((inner.x + outer.x) / 2f, min(inner.y, outer.y) - 5f, outer.x, outer.y)
            }
            drawPath(path, color, style = Stroke(if (fem) 5f else 7.5f, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawMouth(p: PoseFrame, fem: Boolean) {
        val e = p.expr
        val open = max(e.mouthBase, p.mouthOpen).coerceIn(0f, 1f)
        val w = 15f * e.mouthWidth + open * 3f
        val cy = -830f
        val c = e.mouthCurve
        val asym = e.mouthAsym * 4f
        if (open < 0.07f) {
            val path = Path().apply {
                moveTo(-w, cy - c * 5f + asym)
                quadraticBezierTo(0f, cy + c * 6f, w, cy - c * 5f - asym)
            }
            drawPath(path, Color(0xFF7A3340), style = Stroke(3.6f, cap = StrokeCap.Round))
            return
        }
        val top = cy - c * 3f
        val upperMid = top + open * 2f - c * 1f
        val lowerMid = top + 4f + open * 20f + max(c, 0f) * 5f
        val path = Path().apply {
            moveTo(-w, top + asym)
            quadraticBezierTo(0f, upperMid, w, top - asym)
            quadraticBezierTo(w * 0.6f, lowerMid, 0f, lowerMid)
            quadraticBezierTo(-w * 0.6f, lowerMid, -w, top + asym)
            close()
        }
        drawPath(path, MOUTH_IN)
        clipPath(path) {
            if (open > 0.3f && c > 0.2f) drawRect(Color.White, Offset(-w, top - 4f), Size(w * 2, 6f + open * 2f))
            drawOval(Color(0xFFE77A8C), Offset(-w * 0.6f, lowerMid - 9f), Size(w * 1.2f, 12f))
        }
        drawPath(path, Color(0xFF5C1A28), style = Stroke(2.2f))
    }

    private fun DrawScope.drawFrontHair(app: Appearance, p: PoseFrame, hair: Color, fem: Boolean, accent: Color) {
        val shine = hair.shade(0.28f)
        val cap = Path().apply {
            moveTo(-72f, -872f)
            cubicTo(-80f, -960f, -44f, -1000f, 0f, -1000f)
            cubicTo(44f, -1000f, 80f, -960f, 72f, -872f)
        }
        when (app.hairStyle) {
            HairStyle.SPIKY -> {
                cap.apply {
                    lineTo(60f, -905f); lineTo(46f, -925f); lineTo(30f, -906f); lineTo(14f, -930f); lineTo(-4f, -908f)
                    lineTo(-20f, -932f); lineTo(-36f, -908f); lineTo(-52f, -926f); lineTo(-62f, -898f); close()
                }
                val spikes = Path().apply {
                    moveTo(-70f, -950f); lineTo(-92f, -1000f); lineTo(-40f, -985f); lineTo(-30f, -1035f); lineTo(5f, -998f)
                    lineTo(30f, -1040f); lineTo(42f, -992f); lineTo(90f, -1010f); lineTo(70f, -950f); close()
                }
                drawPath(spikes, hair)
            }
            HairStyle.SHORT -> {
                cap.apply {
                    lineTo(62f, -912f); quadraticBezierTo(30f, -940f, 8f, -924f); quadraticBezierTo(-20f, -948f, -40f, -918f)
                    quadraticBezierTo(-56f, -926f, -64f, -900f); close()
                }
            }
            else -> {
                cap.apply {
                    lineTo(64f, -888f); lineTo(50f, -916f); lineTo(38f, -900f); lineTo(24f, -926f); lineTo(10f, -904f)
                    lineTo(-6f, -930f); lineTo(-20f, -906f); lineTo(-34f, -926f); lineTo(-48f, -902f); lineTo(-60f, -916f); lineTo(-66f, -880f); close()
                }
            }
        }
        drawPath(cap, Brush.verticalGradient(listOf(hair.shade(0.08f), hair), -1000f, -880f))
        // side locks for longer styles
        if (app.hairStyle in listOf(HairStyle.LONG, HairStyle.BOB, HairStyle.PONYTAIL, HairStyle.CURLY, HairStyle.BUN) && fem) {
            val len = when (app.hairStyle) { HairStyle.LONG -> -770f; HairStyle.BOB -> -812f; HairStyle.CURLY -> -800f; else -> -840f }
            for (s in listOf(-1f, 1f)) {
                val lock = Path().apply {
                    moveTo(s * 72f, -930f)
                    cubicTo(s * 82f, -880f, s * (78f + p.hairSway * 0.1f), -840f, s * (70f + p.hairSway * 0.15f), len)
                    lineTo(s * (60f + p.hairSway * 0.15f), len + 18f)
                    cubicTo(s * 64f, -850f, s * 62f, -900f, s * 54f, -930f)
                    close()
                }
                drawPath(lock, hair)
            }
        }
        // shine band
        val band = Path().apply { moveTo(-46f, -972f); quadraticBezierTo(0f, -992f, 46f, -972f) }
        drawPath(band, shine.copy(alpha = 0.55f), style = Stroke(7f, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawMarks(p: PoseFrame) {
        val a = p.markAlpha.coerceIn(0f, 1f)
        if (a < 0.02f) return
        when (p.emotion) {
            Emotion.CONFUSED, Emotion.SHY -> { // sweat drop
                val y = -950f + (1f - a) * 10f
                val drop = Path().apply { moveTo(84f, y); quadraticBezierTo(98f, y + 26f, 84f, y + 32f); quadraticBezierTo(70f, y + 26f, 84f, y); close() }
                drawPath(drop, Color(0xFF9FD8FF).copy(alpha = 0.9f * a))
                drawPath(drop, Color(0xFF4A90C8).copy(alpha = a), style = Stroke(2.5f))
            }
            Emotion.ANGRY -> {
                val c = Offset(62f, -962f); val col = Color(0xFFE53950).copy(alpha = a)
                for (r in 0 until 4) {
                    rotate(r * 90f + 45f, c) {
                        val arc = Path().apply { moveTo(c.x + 5f, c.y - 5f); quadraticBezierTo(c.x + 8f, c.y - 16f, c.x + 18f, c.y - 14f) }
                        drawPath(arc, col, style = Stroke(5f, cap = StrokeCap.Round))
                    }
                }
            }
            Emotion.HAPPY, Emotion.AMUSED -> {
                listOf(Offset(96f, -975f), Offset(-102f, -930f), Offset(86f, -900f)).forEachIndexed { i, o ->
                    val s = (8f + 5f * sin(p.t * 5f + i * 2f)) * a
                    val star = Path().apply {
                        moveTo(o.x, o.y - s * 1.6f); lineTo(o.x + s * 0.35f, o.y - s * 0.35f); lineTo(o.x + s * 1.6f, o.y)
                        lineTo(o.x + s * 0.35f, o.y + s * 0.35f); lineTo(o.x, o.y + s * 1.6f); lineTo(o.x - s * 0.35f, o.y + s * 0.35f)
                        lineTo(o.x - s * 1.6f, o.y); lineTo(o.x - s * 0.35f, o.y - s * 0.35f); close()
                    }
                    drawPath(star, Color(0xFFFFE08A).copy(alpha = a))
                }
            }
            Emotion.SURPRISED -> {
                val col = Color(0xFFFFFFFF).copy(alpha = 0.9f * a)
                listOf(-30f, 0f, 30f).forEach { ang ->
                    rotate(ang, Offset(0f, -890f)) { drawLine(col, Offset(0f, -1030f), Offset(0f, -1060f), 6f, StrokeCap.Round) }
                }
            }
            Emotion.SAD -> {
                val y = -868f + (1f - a) * 40f + (p.t % 2f) * 6f
                val tear = Path().apply { moveTo(-44f, y); quadraticBezierTo(-36f, y + 14f, -44f, y + 18f); quadraticBezierTo(-52f, y + 14f, -44f, y); close() }
                drawPath(tear, Color(0xFF9FD8FF).copy(alpha = 0.85f * a))
            }
            Emotion.NEUTRAL -> {}
        }
    }

    private fun DrawScope.drawThought(p: PoseFrame) {
        val a = p.thinking
        val base = Offset(70f, -1010f)
        drawCircle(Color.White.copy(alpha = 0.75f * a), 8f, base)
        drawCircle(Color.White.copy(alpha = 0.8f * a), 13f, base + Offset(22f, -30f))
        val bubble = Offset(78f, -1110f)
        drawRoundRect(Color.White.copy(alpha = 0.9f * a), bubble, Size(120f, 64f), CornerRadius(32f))
        for (i in 0..2) {
            val ph = ((p.t * 2.4f - i * 0.35f) % 1.2f).let { if (it < 0) it + 1.2f else it }
            val up = if (ph < 0.4f) sin(ph / 0.4f * PI.toFloat()) * 8f else 0f
            drawCircle(Color(0xFF6B5B7B).copy(alpha = a), 8f, bubble + Offset(32f + i * 28f, 32f - up))
        }
    }
}
