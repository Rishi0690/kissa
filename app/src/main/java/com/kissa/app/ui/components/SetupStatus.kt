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
import kotlinx.coroutines.launch

/**
 * Honest status of the AI services: story AI (your Gemini key, or the Kissa server in backend mode), natural voices
 * and voice input. Offers a retry when something isn't ready. Never claims a service works when it hasn't been checked.
 */
@Composable
fun SetupStatusCard(state: SetupState, onRetry: () -> Unit, modifier: Modifier = Modifier, voiceNote: String? = null) {
    val checking = state.phase == SetupState.Phase.CHECKING
    val direct = state.mode == SetupState.Mode.DIRECT
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checking) Spinner() else Icon(
                if (state.phase == SetupState.Phase.READY) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null,
                tint = if (state.phase == SetupState.Phase.READY) Color(0xFF6BF0B2) else Color(0xFFFFC46B))
            Spacer(Modifier.width(10.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        StatusRow(Icons.Rounded.AutoStories, "Story AI", state.chat, checking,
            note = when {
                !direct -> "Kissa server"
                !state.keySet -> "needs your Gemini API key"
                state.chat -> "Gemini (your key)${state.model?.let { " · $it" } ?: ""}"
                state.phase == SetupState.Phase.KEY_INVALID -> "key rejected"
                else -> "Gemini (your key) · not reachable yet"
            })
        StatusRow(Icons.Rounded.RecordVoiceOver, "Natural voices", state.voice, checking && !direct,
            note = voiceNote ?: if (direct) "Gemini voices with your key, Microsoft neural voices as free backup" else if (state.voice) "Gemini voices (server)" else "basic phone voice until ready")
        StatusRow(Icons.Rounded.Mic, "Voice input", true, false, note = if (state.listening) "phone recogniser + Gemini backup" else "phone recogniser")
        val needsRetry = !checking && state.phase != SetupState.Phase.READY && state.phase != SetupState.Phase.NEEDS_KEY
        if (needsRetry) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Try again", Modifier.fillMaxWidth(), Icons.Rounded.Refresh, onRetry)
        }
    }
}

/**
 * Paste-your-own Gemini key card (direct mode, the default). Tests the key with a real request before saving it;
 * the key is stored in EncryptedSharedPreferences and only ever sent to Google's Gemini API.
 */
@Composable
fun ApiKeyCard(modifier: Modifier = Modifier, compact: Boolean = false) {
    val app = com.kissa.app.KissaApp.instance
    val s by app.settings.state.collectAsState()
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var editing by remember(s.apiKeySet) { mutableStateOf(!s.apiKeySet) }

    fun explain(e: Throwable): String = when ((e as? com.kissa.app.ai.AiException)?.kind) {
        com.kissa.app.ai.AiException.Kind.AUTH, com.kissa.app.ai.AiException.Kind.NO_KEY -> "Google rejected this key. Copy it again from aistudio.google.com/apikey."
        com.kissa.app.ai.AiException.Kind.NETWORK, com.kissa.app.ai.AiException.Kind.TIMEOUT -> "Couldn't reach Google — check your internet and test again."
        com.kissa.app.ai.AiException.Kind.QUOTA -> "The key is valid, but its free limit is used up right now."
        com.kissa.app.ai.AiException.Kind.BAD_RESPONSE -> "Google answered oddly: ${e.message?.take(90)}"
        else -> "Test failed: ${e.message?.take(90) ?: "unknown error"}"
    }

    fun test(key: String, save: Boolean) {
        if (key.isBlank() || busy) return
        busy = true; result = null
        scope.launch {
            val r = app.ai.testKey(key)
            busy = false
            r.onSuccess { model ->
                if (save) { app.settings.setApiKey(key); input = ""; editing = false; app.speech.geminiTts.resetQuotaMarks() }
                result = true to "Connected — Gemini answered ($model)."
                app.ai.runSetup(force = true)
            }.onFailure { e ->
                val quota = (e as? com.kissa.app.ai.AiException)?.kind == com.kissa.app.ai.AiException.Kind.QUOTA
                if (save && quota) { app.settings.setApiKey(key); input = ""; editing = false; app.ai.runSetup(force = true) }
                result = false to explain(e) + if (save && quota) " Saved anyway." else ""
            }
        }
    }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = LocalAccent.current)
            Spacer(Modifier.width(10.dp))
            Text("Your Gemini API key", style = MaterialTheme.typography.titleSmall, color = pal.text, modifier = Modifier.weight(1f))
            if (s.apiKeySet) Text("•••• ${s.apiKeyHint}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
        }
        if (!compact || !s.apiKeySet) {
            Spacer(Modifier.height(6.dp))
            Text("Kissa talks to Google Gemini with your own free key. It is stored encrypted on this phone and sent only to Google.",
                style = MaterialTheme.typography.bodySmall, color = pal.textDim)
        }
        if (editing) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(input, { input = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (s.apiKeySet) "Paste a new key" else "Paste your key (starts with AIza…)") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton(if (busy) "Testing…" else "Test & save", Modifier.weight(1f), Icons.Rounded.Bolt, enabled = input.length >= 20 && !busy) { test(input, save = true) }
                if (s.apiKeySet) GhostButton("Cancel", Modifier.weight(0.7f)) { editing = false; input = "" }
            }
            TextButton(onClick = {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) }
            }) { Text("Get a free key at aistudio.google.com/apikey") }
        } else {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(if (busy) "Testing…" else "Test connection", Modifier.weight(1.2f), Icons.Rounded.NetworkCheck) { app.settings.apiKey()?.let { test(it, save = false) } }
                GhostButton("Replace", Modifier.weight(0.8f)) { editing = true; result = null }
            }
            if (!compact) TextButton(onClick = { app.settings.setApiKey(null); result = null; app.ai.runSetup(force = true) }) { Text("Remove key from this phone") }
        }
        result?.let { (ok, msg) ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = if (ok) Color(0xFF6BF0B2) else Color(0xFFFFC46B), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, label: String, ok: Boolean, checking: Boolean, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text)
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
