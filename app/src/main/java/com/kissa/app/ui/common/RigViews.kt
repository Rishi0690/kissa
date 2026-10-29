package com.kissa.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.kissa.app.KissaApp
import com.kissa.app.data.GameEntry
import com.kissa.app.game.Look
import com.kissa.app.rig.Actor
import com.kissa.app.rig.RigRenderer

@Composable
fun rememberRigRenderer(): RigRenderer {
    val app = LocalContext.current.applicationContext as KissaApp
    return remember { RigRenderer(app.rigKit) }
}

/** A live rig drawing: full body or head-and-shoulders portrait, with idle animation (breathing, blinking). */
@Composable
fun RigView(rig: String, look: Look, modifier: Modifier = Modifier, expression: String = "smile", portrait: Boolean = false, animate: Boolean = true, move: String? = null) {
    val r = rememberRigRenderer()
    val actor = remember(rig, look) { Actor("view", rig, look).apply { facing = -1 } }
    actor.expression = expression
    LaunchedEffect(move) { move?.let { actor.play(it) } }
    var tick by remember { mutableIntStateOf(0) }
    if (animate) LaunchedEffect(actor) {
        var last = 0L
        while (true) { withFrameNanos { n -> if (last != 0L) actor.update(((n - last) / 1e9f).coerceAtMost(0.05f)); last = n; tick++ } }
    }
    Canvas(modifier) {
        tick
        val w = size.width; val h = size.height
        if (portrait) {
            val scale = h / 520f
            clipRect { with(r) { drawActor(actor, Offset(w / 2, 905f * scale), scale) } }
        } else {
            val scale = h / 1720f
            with(r) { drawActor(actor, Offset(w / 2, h * 0.52f), scale) }
        }
    }
}

/** Cover art for a game (bundled cover.webp), with a gradient + title fallback. */
@Composable
fun GameCover(entry: GameEntry, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val app = LocalContext.current.applicationContext as KissaApp
    val cover = remember(entry.id) { app.games.cover(entry) }
    val accent = entry.game.accent?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color(0xFFE8475F)
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), Color(0xFF120E1C))))) {
        if (cover != null) Image(cover, entry.game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else entry.game.characters.firstOrNull()?.let { c -> RigView(c.rig, c.look, Modifier.fillMaxSize(), portrait = true, animate = false) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xE6100C18)))))
        if (showTitle) Text(entry.game.title, Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(12.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, textAlign = TextAlign.Start)
    }
}
