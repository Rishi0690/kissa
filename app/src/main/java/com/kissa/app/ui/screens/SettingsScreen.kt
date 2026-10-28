package com.kissa.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kissa.app.BuildConfig
import com.kissa.app.KissaApp
import com.kissa.app.ai.*
import com.kissa.app.model.BodyType
import com.kissa.app.model.Emotion
import com.kissa.app.model.Lang
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import com.kissa.app.ui.theme.Themes
import com.kissa.app.voice.Utterance
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val app = KissaApp.instance
    val s by app.settings.state.collectAsStateWithLifecycle()
    val voices by app.speech.voices.collectAsStateWithLifecycle()
    val reports by app.repo.dao.reportCount().collectAsStateWithLifecycle(0)
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var proxy by remember(s.proxyUrl) { mutableStateOf(s.proxyUrl) }
    var name by remember(s.playerName) { mutableStateOf(s.playerName) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var showPlayerVoices by remember { mutableStateOf(false) }
    val upd = app.settings::update

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
        TopBar("Settings", if (nav.previousBackStackEntry != null && nav.previousBackStackEntry?.destination?.route?.startsWith("play") == true) ({ nav.popBackStack() }) else null)
        Column(Modifier.padding(horizontal = 20.dp)) {
            // ------------------------------------------------ AI
            Group("AI · Google Gemini", Icons.Rounded.Psychology) {
                Text(if (s.hasApiKey) "✓ API key saved ${if (s.keyStorageSecure) "(encrypted on this device)" else "(device keystore unavailable — stored privately)"}" else "No API key yet. Get a free key at aistudio.google.com — sign in with Google, tap \"Create API key\", copy it here.",
                    style = MaterialTheme.typography.bodySmall, color = if (s.hasApiKey) Color(0xFF6BF0B2) else pal.textDim)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(keyInput, { keyInput = it.trim() }, Modifier.fillMaxWidth(), label = { Text(if (s.hasApiKey) "Replace API key" else "Paste Gemini API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(autoCorrect = false))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton("Save key", Modifier.weight(1f), enabled = keyInput.length > 10) { app.settings.setApiKey(keyInput); keyInput = ""; testResult = "Key saved." }
                    GhostButton("Get a key", Modifier.weight(1f), Icons.Rounded.OpenInNew) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
                }
                if (s.hasApiKey) TextButton(onClick = { confirm = "key" }) { Text("Remove saved key") }
                Spacer(Modifier.height(8.dp))
                Text("Model", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeminiClient.MODELS.forEach { m -> KChip(m, s.model == m) { upd { putString("model", m) } } }
                }
                Text("Flash-Lite is fastest and cheapest. If a model isn't available for your key, Kissa automatically tries the next one.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(proxy, { proxy = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Optional backend proxy URL") }, singleLine = true, placeholder = { Text("https://your-server.example.com") })
                Text("If set, requests go to <proxy>/generate?model=… with the same JSON body and your key is NOT sent from the phone — the server holds it.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    GhostButton("Save proxy", Modifier.weight(1f)) { upd { putString("proxyUrl", proxy) }; testResult = if (proxy.isBlank()) "Proxy cleared — calling Gemini directly." else "Proxy saved." }
                    GhostButton(if (testing) "Testing…" else "Test connection", Modifier.weight(1f), Icons.Rounded.NetworkCheck) {
                        if (testing) return@GhostButton
                        testing = true; testResult = null
                        scope.launch {
                            testResult = try {
                                val raw = app.gemini.generate(app.settings.apiKey(), s.proxyUrl, s.model,
                                    PromptPayload("Reply with a JSON object {\"ok\": true, \"line\": \"<a 5-word friendly greeting>\"}", listOf(PromptContent("user", "ping"))), null, 0.2, 200, attempts = 1)
                                "✓ Connected to ${s.model}: ${raw.take(80)}"
                            } catch (e: AiException) { "✗ ${e.message}" } catch (e: Exception) { "✗ ${e.message}" }
                            testing = false
                        }
                    }
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }

            // ------------------------------------------------ Voice & audio
            Group("Voice & audio", Icons.Rounded.GraphicEq) {
                Toggle("Characters speak aloud", s.characterVoicesOn) { v -> upd { putBoolean("characterVoicesOn", v) } }
                Toggle("Speak my typed lines aloud", s.speakPlayerLines) { v -> upd { putBoolean("speakPlayerLines", v) } }
                Toggle("Play my real voice for voice messages", s.keepRealVoice, "Android 13+ records your voice while recognising it. On older phones your line is spoken with your synthetic voice.") { v -> upd { putBoolean("keepRealVoice", v) } }
                Text("Volume ${(s.volume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Slider(s.volume, { v -> upd { putFloat("volume", v) } }, valueRange = 0f..1f)
                Text("My synthetic voice", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                Text("Pitch ${"%.2f".format(s.playerPitch)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Slider(s.playerPitch, { v -> upd { putFloat("playerPitch", v) } }, valueRange = 0.6f..1.6f)
                Text("Speed ${"%.2f".format(s.playerRate)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Slider(s.playerRate, { v -> upd { putFloat("playerRate", v) } }, valueRange = 0.6f..1.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Preview", Modifier.weight(1f), Icons.Rounded.VolumeUp) {
                        app.speech.stop()
                        val line = when (s.language) { Lang.HINDI -> "नमस्ते, मैं ${s.playerName} हूँ।"; Lang.ENGLISH -> "Hey, I'm ${s.playerName}. Nice to meet you."; Lang.HINGLISH -> "Hey, main ${s.playerName} hoon. Kaise ho?" }
                        scope.launch { app.speech.play(Utterance("player", line, if (s.language == Lang.HINDI) "hi-IN" else s.language.ttsTag, s.playerVoiceName, s.playerBody == BodyType.FEM, s.playerPitch, s.playerRate)) }
                    }
                    GhostButton(if (showPlayerVoices) "Hide voices" else "Choose voice", Modifier.weight(1f), Icons.Rounded.RecordVoiceOver) { showPlayerVoices = !showPlayerVoices; app.speech.refreshVoices() }
                }
                if (showPlayerVoices) {
                    VoiceRow("Automatic", s.playerVoiceName == null) { upd { remove("playerVoiceName") } }
                    if (voices.isEmpty()) Text("No English/Hindi TTS voices found. Install them in Android Settings → Accessibility → Text-to-speech.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    voices.forEach { v -> VoiceRow("${v.name} · ${v.locale}${v.feminine?.let { if (it) " · F" else " · M" } ?: ""}", s.playerVoiceName == v.name) { upd { putString("playerVoiceName", v.name) } } }
                }
                app.speech.initError?.let { Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall) }
            }

            // ------------------------------------------------ Story defaults
            Group("You & language", Icons.Rounded.Translate) {
                OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("Your character's name") }, singleLine = true,
                    trailingIcon = { if (name != s.playerName && name.isNotBlank()) TextButton(onClick = { upd { putString("playerName", name.trim()) } }) { Text("Save") } })
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip("He / him", s.playerBody == BodyType.MASC) { upd { putString("playerBody", BodyType.MASC.name) } }
                    KChip("She / her", s.playerBody == BodyType.FEM) { upd { putString("playerBody", BodyType.FEM.name) } }
                }
                Text("Default dialogue language", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Lang.entries.forEach { l -> KChip(l.label, s.language == l) { upd { putString("language", l.name) } } } }
                Text("Applies to built-in stories unless you change it inside a story's scene menu.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            }

            // ------------------------------------------------ Appearance
            Group("Look & feel", Icons.Rounded.Palette) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Themes.all.forEach { t ->
                        val sel = s.themeId == t.id
                        Column(Modifier.width(92.dp).clickable { upd { putString("themeId", t.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(t.glow1, t.bg2, t.bg, t.glow2.copy(alpha = 0.6f))))
                                .border(if (sel) 2.dp else 1.dp, if (sel) LocalAccent.current else Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)))
                            Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (sel) LocalAccent.current else pal.textDim, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text("Accent colour", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Themes.accents.forEach { a ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(a)).border(if (s.accent == a) 3.dp else 0.dp, Color.White, CircleShape).clickable { upd { putLong("accent", a) } })
                    }
                }
                Text("Background", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("aurora" to "Aurora", "nebula" to "Nebula", "plain" to "Plain").forEach { (id, l) -> KChip(l, s.backdrop == id) { upd { putString("backdrop", id) } } }
                }
                Toggle("Subtitles", s.subtitles) { v -> upd { putBoolean("subtitles", v) } }
                Text("Subtitle size ${(s.subtitleScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Slider(s.subtitleScale, { v -> upd { putFloat("subtitleScale", v) } }, valueRange = 0.8f..1.5f)
                Toggle("Suggested replies", s.showSuggestions) { v -> upd { putBoolean("showSuggestions", v) } }
                Toggle("Reduce motion", s.reduceMotion, "Calmer idle animation and no passers-by in scenes.") { v -> upd { putBoolean("reduceMotion", v) } }
            }

            // ------------------------------------------------ Privacy
            Group("Privacy & data", Icons.Rounded.Shield) {
                Text("Everything — stories, conversations, memories, voice recordings and your key — is stored only on this device. Your messages are sent to Google Gemini (or your proxy) to generate replies; speech recognition uses your phone's recogniser.",
                    style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                Spacer(Modifier.height(8.dp))
                Text("Reports filed: $reports (stored locally; reported lines are hidden from the story).", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                Spacer(Modifier.height(10.dp))
                GhostButton("Clear all conversations & memories", Modifier.fillMaxWidth(), Icons.Rounded.DeleteSweep) { confirm = "memories" }
                Spacer(Modifier.height(8.dp))
                GhostButton("Erase everything & reset app", Modifier.fillMaxWidth(), Icons.Rounded.DeleteForever) { confirm = "all" }
            }

            Group("Account & sync", Icons.Rounded.CloudOff) {
                Text("Not available in this version. There is no sign-in and nothing is synced to a cloud — your progress lives on this phone only (uninstalling the app deletes it).",
                    style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            }

            Group("About", Icons.Rounded.Info) {
                Text("Kissa ${BuildConfig.VERSION_NAME} — interactive animated stories. Original characters and art. AI by Google Gemini; voices by your device's text-to-speech engine.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                TextButton(onClick = { nav.navigate("onboarding") }) { Text("Show onboarding again") }
            }
        }
    }

    confirm?.let { what ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(when (what) { "key" -> "Remove API key?"; "memories" -> "Clear conversations & memories?"; else -> "Erase everything?" }) },
            text = { Text(when (what) { "key" -> "Characters won't be able to reply until you add a key again."; "memories" -> "All playthroughs, transcripts, checkpoints, memories and voice recordings will be deleted. Your own stories and settings stay."; else -> "Deletes all data including your created stories, favourites, settings and API key." }) },
            confirmButton = {
                TextButton(onClick = {
                    val w = what; confirm = null
                    scope.launch {
                        when (w) {
                            "key" -> app.settings.setApiKey(null)
                            "memories" -> app.repo.clearAllMemories()
                            else -> { app.repo.clearEverything(); app.settings.resetAll(); nav.navigate("onboarding") { popUpTo(nav.graph.id) { inclusive = true } } }
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun Group(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(14.dp))
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalAccent.current); Spacer(Modifier.width(10.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.textDim) }
        }
        Switch(value, onChange)
    }
}

@Suppress("unused") private val keepEmotionImport = Emotion.NEUTRAL
