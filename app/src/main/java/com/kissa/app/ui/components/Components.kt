package com.kissa.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kissa.app.model.Appearance
import com.kissa.app.model.Emotion
import com.kissa.app.model.Story
import com.kissa.app.scene.CharacterPainter.drawCharacter
import com.kissa.app.scene.Expr
import com.kissa.app.scene.PoseFrame
import com.kissa.app.scene.backgroundRes
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalBackdrop
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    val style = LocalBackdrop.current
    val inf = rememberInfiniteTransition(label = "bd")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.bg2, p.bg, p.bg)))
            .drawBehind {
                if (style == "plain") return@drawBehind
                val w = size.width; val h = size.height
                val a = if (style == "nebula") 0.30f else 0.22f
                drawCircle(Brush.radialGradient(listOf(p.glow1.copy(alpha = a), Color.Transparent), Offset(w * (0.15f + 0.2f * drift), h * 0.08f), w * 0.9f), w * 0.9f, Offset(w * (0.15f + 0.2f * drift), h * 0.08f))
                drawCircle(Brush.radialGradient(listOf(p.glow2.copy(alpha = a * 0.7f), Color.Transparent), Offset(w * (0.95f - 0.15f * drift), h * 0.35f), w * 0.7f), w * 0.7f, Offset(w * (0.95f - 0.15f * drift), h * 0.35f))
                if (style == "nebula") {
                    val r = Random(7)
                    repeat(60) {
                        drawCircle(Color.White.copy(alpha = 0.15f + r.nextFloat() * 0.4f), 0.6f + r.nextFloat() * 1.6f, Offset(r.nextFloat() * w, r.nextFloat() * h))
                    }
                }
            },
        content = content,
    )
}

fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(22.dp), alpha: Float = 0.07f): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha + 0.03f), Color.White.copy(alpha = alpha * 0.5f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))), shape)

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Column(
        modifier
            .scale(sc)
            .glass()
            .then(if (onClick != null) Modifier.clickable(src, LocalIndication.current, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Row(
        modifier
            .scale(sc)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Brush.horizontalGradient(listOf(acc, lerpColor(acc, Color.White, 0.25f))) else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))))
            .clickable(src, LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color(0xFF16101F), modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color(0xFF16101F), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.glass(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = LocalPalette.current.text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = LocalPalette.current.text)
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

@Composable
fun KChip(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) acc.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) acc.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) LocalPalette.current.text else LocalPalette.current.textDim,
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, color = LocalAccent.current, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun Meter(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(value / 100f, tween(700), label = "m")
    Column(modifier) {
        Row { Text(label, style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.textDim, modifier = Modifier.weight(1f)); Text("$value", style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).glass(RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(16.dp)); AccentButton(action, onClick = onAction) }
    }
}

/** Head-and-shoulders render of a character using the same rig as the scene. */
@Composable
fun CharacterPortrait(appearance: Appearance, modifier: Modifier = Modifier, emotion: Emotion = Emotion.HAPPY, animate: Boolean = false, facing: Float = 1f) {
    val pose = remember(appearance, emotion) { PoseFrame().apply { expr = Expr.of(emotion); this.emotion = emotion; gazeX = 0.3f } }
    var tick by remember { mutableIntStateOf(0) }
    if (animate) LaunchedEffect(Unit) {
        while (true) {
            delay(2600L + Random.nextLong(2000)); pose.blink = 1f; tick++; delay(120); pose.blink = 0f; tick++
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val s = size.height / 560f
        val tx = size.width / 2f; val ty = size.height + 520f * s
        withTransform({ translate(tx, ty); scale(s * facing, s, Offset.Zero) }) { drawCharacter(appearance, pose) }
    }
}

/** Story cover: painted location + the character's portrait, gradient for legibility. */
@Composable
fun StoryCover(story: Story, modifier: Modifier = Modifier, showCharacter: Boolean = true, portraitFraction: Float = 0.9f) {
    Box(modifier.clip(RoundedCornerShape(22.dp))) {
        Image(painterResource(story.setting.backgroundRes()), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.78f)))))
        if (showCharacter) CharacterPortrait(story.character.appearance, Modifier.fillMaxHeight(portraitFraction).aspectRatio(0.8f).align(Alignment.BottomEnd).padding(end = 6.dp), facing = -1f)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
    }
}

@Composable
fun StoryRowCard(story: Story, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).glass().clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCover(story, Modifier.size(76.dp), portraitFraction = 1f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(story.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun Pill(text: String, color: Color = LocalAccent.current) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
fun IconCircle(icon: ImageVector, desc: String, modifier: Modifier = Modifier, tint: Color = LocalPalette.current.text, bg: Color = Color.White.copy(alpha = 0.08f), size: Dp = 44.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.48f)) }
}
