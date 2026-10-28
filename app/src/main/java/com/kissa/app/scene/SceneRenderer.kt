package com.kissa.app.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kissa.app.R
import com.kissa.app.model.SettingId
import com.kissa.app.scene.CharacterPainter.drawCharacter
import com.kissa.app.voice.SpeakingState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

fun SettingId.backgroundRes(): Int = when (this) {
    SettingId.CORRIDOR -> R.drawable.bg_corridor
    SettingId.CAFE -> R.drawable.bg_cafe
    SettingId.LIBRARY -> R.drawable.bg_library
    SettingId.FOREST -> R.drawable.bg_forest
    SettingId.SPACESHIP -> R.drawable.bg_spaceship
    SettingId.STREET -> R.drawable.bg_street
    SettingId.PARK -> R.drawable.bg_park
    SettingId.CLASSROOM -> R.drawable.bg_classroom
}

/** Colour grade applied over the whole stage so the characters sit inside the painted lighting. */
private fun SettingId.grade(): Pair<Color, Float> = when (this) {
    SettingId.CORRIDOR -> Color(0xFFFFB46B) to 0.10f
    SettingId.CAFE -> Color(0xFFFF9D4D) to 0.12f
    SettingId.LIBRARY -> Color(0xFF2B4A8A) to 0.22f
    SettingId.FOREST -> Color(0xFF1F7A8C) to 0.20f
    SettingId.SPACESHIP -> Color(0xFF3D6BD9) to 0.16f
    SettingId.STREET -> Color(0xFF6E3DA8) to 0.20f
    SettingId.PARK -> Color(0xFFFF7A45) to 0.14f
    SettingId.CLASSROOM -> Color(0xFFFFD39A) to 0.07f
}

/** Drives the frame loop (only while composed/visible) and renders the stage. */
@Composable
fun SceneStage(engine: SceneEngine, speaking: StateFlow<SpeakingState?>, modifier: Modifier = Modifier, paused: Boolean = false) {
    val bg = ImageBitmap.imageResource(engine.setting.backgroundRes())
    LaunchedEffect(engine, paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else (now - last) / 1_000_000_000f
                last = now
                engine.update(dt, speaking.value)
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = engine.frame // subscribe to frame ticks
        drawStage(engine, bg)
    }
}

fun DrawScope.drawStage(engine: SceneEngine, bg: ImageBitmap) {
    val w = size.width; val h = size.height
    val portrait = h > w
    val t = engine.time
    val camPx = engine.camX * w
    val zoom = engine.zoom

    // ---- background, parallax 0.45, half the camera zoom, slow drift
    run {
        val cover = max(w / bg.width, h / bg.height) * 1.14f * (1f + (zoom - 1f) * 0.5f)
        val dw = bg.width * cover; val dh = bg.height * cover
        val drift = if (engine.reduceMotion) 0f else sin(t * 0.07f) * w * 0.012f
        val left = (w - dw) / 2f - camPx * 0.45f + drift
        val top = (h - dh) * 0.62f
        drawImage(bg, IntOffset.Zero, IntSize(bg.width, bg.height), IntOffset(left.roundToInt(), top.roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.Medium)
    }
    drawSettingLight(engine, w, h)

    // ---- stage transform (characters + mid layer)
    val floorY = if (portrait) h * 0.985f else h * 1.10f
    val charH = if (portrait) h * 0.64f else h * 0.84f
    val focusY = floorY - charH * 0.88f
    withTransform({
        translate(-camPx, 0f)
        scale(zoom, zoom, Offset(w / 2f + camPx, focusY))
    }) {
        // far silhouettes walking behind
        engine.silhouettes.filter { it.depth < 0.6f }.forEach { drawSilhouette(it, w, floorY - charH * 0.12f, charH * 0.62f * it.depth / 0.5f, t, 0.35f) }
        val stageW = if (portrait) w else min(w, h * 1.5f)
        val stageLeft = (w - stageW) / 2f
        engine.actors.sortedBy { if (it.isPlayer) 0 else 1 }.forEach { a ->
            if (!a.visible) return@forEach
            val px = stageLeft + a.x * stageW
            val s = charH / 1000f
            withTransform({
                translate(px, floorY)
                scale(s * a.drawFacing, s, Offset.Zero)
            }) { drawCharacter(a.appearance, a.pose) }
        }
        engine.silhouettes.filter { it.depth >= 0.6f }.forEach { drawSilhouette(it, w, floorY + charH * 0.05f, charH * 1.05f, t, 0.75f) }
    }

    // ---- foreground particles (parallax 1.25)
    drawParticles(engine, w, h, camPx * 1.25f)
    if (engine.rain > 0.02f) drawRain(engine, w, h)

    // ---- grade, lighting events, vignette
    val (gc, ga) = engine.setting.grade()
    drawRect(gc.copy(alpha = ga), blendMode = BlendMode.SrcOver)
    if (engine.dim > 0.01f) drawRect(Color(0xFF05040C).copy(alpha = engine.dim))
    if (engine.flicker > 0.01f) drawRect(Color.Black.copy(alpha = engine.flicker))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2f, h * 0.45f), max(w, h) * 0.75f))
    if (engine.flash > 0.01f) drawRect(Color.White.copy(alpha = engine.flash * 0.55f))
    if (engine.fade > 0.005f) drawRect(Color.Black.copy(alpha = engine.fade.coerceIn(0f, 1f)))
}

private fun DrawScope.drawSettingLight(engine: SceneEngine, w: Float, h: Float) {
    val t = engine.time
    when (engine.setting) {
        SettingId.CORRIDOR, SettingId.CLASSROOM -> {
            // shifting god-rays from the windows
            for (i in 0..2) {
                val x0 = w * (0.55f + i * 0.18f) + sin(t * 0.2f + i) * w * 0.02f - engine.camX * w * 0.5f
                val ray = Path().apply {
                    moveTo(x0, 0f); lineTo(x0 + w * 0.12f, 0f); lineTo(x0 - w * 0.35f, h); lineTo(x0 - w * 0.55f, h); close()
                }
                val a = 0.07f + 0.03f * sin(t * 0.5f + i * 2f)
                drawPath(ray, Brush.verticalGradient(listOf(Color(0xFFFFE2A8).copy(alpha = a), Color.Transparent), 0f, h))
            }
        }
        SettingId.LIBRARY -> {
            val pulse = 0.10f + 0.03f * sin(t * 1.7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB8FF9A).copy(alpha = pulse), Color.Transparent), Offset(w * 0.6f, h * 0.55f), w * 0.6f), w * 0.6f, Offset(w * 0.6f, h * 0.55f))
        }
        SettingId.FOREST -> {
            val mist = 0.12f + 0.04f * sin(t * 0.4f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFBFEFFF).copy(alpha = mist), Color.Transparent), h * 0.55f, h * 0.95f))
        }
        SettingId.SPACESHIP -> {
            val holo = if (sin(t * 9f) * sin(t * 2.3f) > 0.85f) 0.08f else 0.03f
            drawRect(Color(0xFF7FE3FF).copy(alpha = holo), Offset(0f, h * 0.45f), Size(w, h * 0.3f))
        }
        SettingId.STREET -> {
            val neon = 0.06f + (if (sin(t * 3f) * sin(t * 7.1f) > 0.8f) 0.06f else 0f)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFFFF3FA0).copy(alpha = neon), Color.Transparent, Color(0xFF3FE0FF).copy(alpha = neon))))
        }
        SettingId.CAFE -> {
            val warm = 0.08f + 0.02f * sin(t * 2.1f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = warm), Color.Transparent), Offset(w * 0.4f, h * 0.15f), w * 0.8f), w * 0.8f, Offset(w * 0.4f, h * 0.15f))
        }
        SettingId.PARK -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFA36B).copy(alpha = 0.10f), Color.Transparent), 0f, h * 0.6f))
            // distant birds
            for (i in 0..2) {
                val bx = ((t * 0.02f + i * 0.31f) % 1.3f) * w - 0.15f * w
                val by = h * (0.12f + i * 0.04f) + sin(t * 2f + i) * 6f
                val wing = sin(t * 8f + i) * 6f
                val b = Path().apply { moveTo(bx - 10f, by - wing); quadraticBezierTo(bx - 4f, by - 2f, bx, by); quadraticBezierTo(bx + 4f, by - 2f, bx + 10f, by - wing) }
                drawPath(b, Color(0xFF3A2433).copy(alpha = 0.7f), style = Stroke(2.5f))
            }
        }
    }
}

private fun DrawScope.drawSilhouette(s: Silhouette, w: Float, floor: Float, height: Float, t: Float, alpha: Float) {
    val x = s.x * w
    val hh = height * s.tall
    val bob = abs(sin(t * 6f + s.shade * 5f)) * hh * 0.012f
    val col = Color(0xFF120E1C).copy(alpha = alpha * (0.75f + 0.25f * s.shade))
    val top = floor - hh - bob
    drawCircle(col, hh * 0.07f, Offset(x, top + hh * 0.08f))
    drawRoundRect(col, Offset(x - hh * 0.09f, top + hh * 0.16f), Size(hh * 0.18f, hh * 0.42f), CornerRadius(hh * 0.06f))
    val swing = sin(t * 6f + s.shade * 5f) * hh * 0.05f
    drawLine(col, Offset(x - hh * 0.04f, top + hh * 0.55f), Offset(x - hh * 0.04f + swing, floor - bob), hh * 0.07f, StrokeCap.Round)
    drawLine(col, Offset(x + hh * 0.04f, top + hh * 0.55f), Offset(x + hh * 0.04f - swing, floor - bob), hh * 0.07f, StrokeCap.Round)
    if (s.umbrella) {
        drawArc(col, 180f, 180f, true, Offset(x - hh * 0.22f, top - hh * 0.08f), Size(hh * 0.44f, hh * 0.25f))
        drawLine(col, Offset(x, top + hh * 0.04f), Offset(x, top + hh * 0.3f), hh * 0.015f)
    }
}

private fun DrawScope.drawParticles(engine: SceneEngine, w: Float, h: Float, shift: Float) {
    val t = engine.time
    val dens = density
    for (p in engine.particles) {
        val x = ((p.x * w - shift * 0.15f) % w + w) % w
        val y = p.y * h
        when {
            Ambient.STARS in engine.ambients -> {
                val a = 0.35f + 0.65f * abs(sin(t * 1.5f + p.phase))
                drawCircle(Color.White.copy(alpha = a * 0.8f), p.size * dens * 0.6f, Offset(x, y))
            }
            Ambient.FIREFLIES in engine.ambients -> {
                val a = 0.3f + 0.7f * abs(sin(t * 1.2f + p.phase))
                val fx = x + sin(t * 0.8f + p.phase) * 18f; val fy = y + cos(t * 0.6f + p.phase) * 14f
                drawCircle(Brush.radialGradient(listOf(Color(0xFFD9FF8A).copy(alpha = a * 0.55f), Color.Transparent), Offset(fx, fy), p.size * dens * 4f), p.size * dens * 4f, Offset(fx, fy))
                drawCircle(Color(0xFFF4FFD0).copy(alpha = a), p.size * dens * 0.7f, Offset(fx, fy))
            }
            Ambient.PETALS in engine.ambients -> {
                rotate((t * 90f + p.phase * 60f) % 360f, Offset(x, y)) {
                    drawOval(Color(0xFFFFB7CF).copy(alpha = 0.9f), Offset(x - p.size * dens * 0.5f, y - p.size * dens * 0.3f), Size(p.size * dens, p.size * dens * 0.6f))
                }
            }
            Ambient.BOKEH in engine.ambients -> {
                val a = 0.05f + 0.05f * abs(sin(t * 0.7f + p.phase))
                val col = if (engine.setting == SettingId.STREET) (if (p.phase > 3f) Color(0xFFFF5FB0) else Color(0xFF5FE0FF)) else Color(0xFFFFD08A)
                drawCircle(col.copy(alpha = a), p.size * dens * 0.6f, Offset(x, y))
            }
            else -> {
                val a = 0.25f + 0.35f * abs(sin(t * 0.9f + p.phase))
                drawCircle(Color(0xFFFFF3D6).copy(alpha = a), p.size * dens * 0.55f, Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawRain(engine: SceneEngine, w: Float, h: Float) {
    val a = engine.rain
    val col = Color(0xFFCFE6FF).copy(alpha = 0.35f * a)
    val len = h * 0.035f
    for (d in engine.drops) {
        val x = d.x * w; val y = d.y * h
        drawLine(col, Offset(x, y), Offset(x - len * 0.25f, y + len * d.size), 1.3f * density * 0.6f)
        if (d.y > 0.9f) drawOval(Color(0xFFCFE6FF).copy(alpha = 0.18f * a), Offset(x - 8f, h * 0.97f), Size(16f, 4f))
    }
    drawRect(Color(0xFF0A1428).copy(alpha = 0.10f * a))
}
