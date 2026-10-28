package com.kissa.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kissa.app.ai.SetupState
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Friendly status of the automatic setup (no keys, no provider names): story AI, natural voices, voice input.
 * Honest about what is and isn't working; offers a retry when something isn't ready.
 */
@Composable
fun SetupStatusCard(state: SetupState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val checking = state.phase == SetupState.Phase.CHECKING
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checking) Spinner() else Icon(
                if (state.phase == SetupState.Phase.READY) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null,
                tint = if (state.phase == SetupState.Phase.READY) Color(0xFF6BF0B2) else Color(0xFFFFC46B))
            Spacer(Modifier.width(10.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        StatusRow(Icons.Rounded.AutoStories, "Story AI", state.chat, checking)
        StatusRow(Icons.Rounded.RecordVoiceOver, if (state.voice) "Natural voices" else "Natural voices (basic phone voice until ready)", state.voice, checking)
        StatusRow(Icons.Rounded.Mic, "Voice input", true, false, note = if (state.listening) "phone + cloud backup" else "phone recogniser")
        if (!checking && state.phase != SetupState.Phase.READY && state.phase != SetupState.Phase.DEV_OVERRIDE) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Try again", Modifier.fillMaxWidth(), Icons.Rounded.Refresh, onRetry)
        }
        if (state.phase == SetupState.Phase.NOT_SET_UP) {
            Spacer(Modifier.height(8.dp))
            Text("Nothing for you to configure — no keys or accounts needed. The Kissa team switches the AI service on from their side.",
                style = MaterialTheme.typography.bodySmall, color = pal.textDim)
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, label: String, ok: Boolean, checking: Boolean, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.textDim) }
        }
        when {
            checking -> Text("…", color = LocalPalette.current.textDim)
            ok -> Icon(Icons.Rounded.Check, "ready", tint = Color(0xFF6BF0B2), modifier = Modifier.size(18.dp))
            else -> Icon(Icons.Rounded.HourglassEmpty, "not ready", tint = Color(0xFFFFC46B), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Spinner() {
    val inf = rememberInfiniteTransition(label = "spin")
    val r by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Icon(Icons.Rounded.Autorenew, null, tint = LocalAccent.current, modifier = Modifier.rotate(r))
}

/** A painted character frame from assets/cast (decoded off the main thread). */
@Composable
fun PaintedPortrait(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    var img by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        img = withContext(Dispatchers.IO) { runCatching { ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    }
    img?.let { Image(it, null, modifier, contentScale = contentScale) } ?: Box(modifier)
}

@Suppress("unused") private val dotShape = CircleShape
