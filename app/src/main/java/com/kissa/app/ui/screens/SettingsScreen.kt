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
import com.kissa.app.voice.NeuralVoices
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
    val edgeOk by app.speech.edgeReachable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!app.ai.backendMode && app.speech.edgeReachable.value != true) app.speech.checkMicrosoftVoice() }
    val setup by app.ai.setup.collectAsStateWithLifecycle()
    val voiceMode by app.speech.mode.collectAsStateWithLifecycle()
    var showNeural by remember { mutableStateOf(false) }
    var name by remember(s.playerName) { mutableStateOf(s.playerName) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var showPlayerVoices by remember { mutableStateOf(false) }
    val upd = app.settings::update

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
        TopBar("Settings", if (nav.previousBackStackEntry != null && nav.previousBackStackEntry?.destination?.route?.startsWith("play") == true) ({ nav.popBackStack() }) else null)
        Column(Modifier.padding(horizontal = 20.dp)) {
            // ------------------------------------------------ AI & voices status
            Group("Story AI & voices", Icons.Rounded.Psychology) {
                if (!app.ai.backendMode) { ApiKeyCard(); Spacer(Modifier.height(12.dp)) }
                SetupStatusCard(setup, onRetry = { app.ai.runSetup(force = true) }, voiceNote = voiceMode.engine?.let { "now: ${it.label}" })
            }

            // ------------------------------------------------ Voice & audio
            Group("Voice & audio", Icons.Rounded.GraphicEq) {
                Toggle("Characters speak aloud", s.characterVoicesOn) { v -> upd { putBoolean("characterVoicesOn", v) } }
                Toggle("Natural voices", s.naturalVoices, if (!s.naturalVoices) "Off — the phone's basic voice is used." else
                    "Expressive neural voices; if none can be reached the phone voice is used and labelled \"Basic voice\".") { v -> upd { putBoolean("naturalVoices", v) } }
                if (s.naturalVoices) VoiceEngines(s.voiceEngine, s.apiKeySet, voiceMode, edgeOk, app.ai.backendMode,
                    geminiFreeAt = if (app.speech.geminiTts.available()) null else app.speech.geminiTts.nextAvailableAt()) { v -> upd { putString("voiceEngine", v) } }
                Toggle("Speak my typed lines aloud", s.speakPlayerLines) { v -> upd { putBoolean("speakPlayerLines", v) } }
                Toggle("Play my real voice for voice messages", s.keepRealVoice, "Android 13+ records your voice while recognising it. On older phones your line is spoken with your chosen voice.") { v -> upd { putBoolean("keepRealVoice", v) } }
                Text("Volume ${(s.volume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                Slider(s.volume, { v -> upd { putFloat("volume", v) } }, valueRange = 0f..1f)
                Text("My character's natural voice", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                val line = when (s.language) { Lang.HINDI -> "नमस्ते, मैं ${s.playerName} हूँ।"; Lang.ENGLISH -> "Hey, I'm ${s.playerName}. Nice to meet you."; Lang.HINGLISH -> "Hey, main ${s.playerName} hoon. Kaise ho?" }
                if (app.ai.backendMode) {
                    val myNeural = NeuralVoices.forPlayer(s.playerNeuralVoice, s.playerBody)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Preview", Modifier.weight(1f), Icons.Rounded.VolumeUp) {
                            app.speech.stop()
                            scope.launch { app.speech.play(Utterance("player", line, if (s.language == Lang.HINDI) "hi-IN" else s.language.ttsTag, s.playerVoiceName, s.playerBody == BodyType.FEM, s.playerPitch, s.playerRate,
                                Emotion.HAPPY, neuralVoice = myNeural, lang = NeuralVoices.langCode(s.language))) }
                        }
                        GhostButton(if (showNeural) "Hide" else myNeural, Modifier.weight(1f), Icons.Rounded.RecordVoiceOver) { showNeural = !showNeural }
                    }
                    if (showNeural) NeuralVoices.ALL.sortedBy { if ((s.playerBody == BodyType.FEM) == it.feminine) 0 else 1 }.forEach { v ->
                        VoiceRow("${v.name} · ${v.trait}${if (v.feminine) " · F" else " · M"}", myNeural == v.name) { upd { putString("playerNeuralVoice", v.name) } }
                    }
                } else {
                    val mine = com.kissa.app.voice.engines.EdgeTts.playerVoice(s.playerEdgeVoice, s.playerBody == BodyType.FEM)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Preview", Modifier.weight(1f), Icons.Rounded.VolumeUp) {
                            app.speech.stop()
                            scope.launch { app.speech.play(Utterance("player", line, if (s.language == Lang.HINDI) "hi-IN" else s.language.ttsTag, s.playerVoiceName, s.playerBody == BodyType.FEM, s.playerPitch, s.playerRate,
                                Emotion.HAPPY, edgeVoice = mine, lang = NeuralVoices.langCode(s.language))) }
                        }
                        GhostButton(if (showNeural) "Hide" else mine.label.substringBefore(" ·"), Modifier.weight(1f), Icons.Rounded.RecordVoiceOver) { showNeural = !showNeural }
                    }
                    if (showNeural) com.kissa.app.voice.engines.EdgeTts.PLAYER_CHOICES.sortedBy { if ((s.playerBody == BodyType.FEM) == it.feminine) 0 else 1 }.forEach { v ->
                        VoiceRow("${v.label}${if (v.feminine) " · F" else " · M"}", mine.id == v.id) { upd { putString("playerEdgeVoice", v.id) } }
                    }
                    Text("Your typed lines use a Microsoft neural voice so the Gemini voice quota is saved for the characters.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                }
                Text("Basic phone voice (last fallback)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                Text("Pitch ${"%.2f".format(s.playerPitch)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Slider(s.playerPitch, { v -> upd { putFloat("playerPitch", v) } }, valueRange = 0.6f..1.6f)
                Text("Speed ${"%.2f".format(s.playerRate)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Slider(s.playerRate, { v -> upd { putFloat("playerRate", v) } }, valueRange = 0.6f..1.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Preview", Modifier.weight(1f), Icons.Rounded.VolumeUp) {
                        app.speech.stop()
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
                Text("Everything — stories, conversations, memories and voice recordings — is stored only on this device. ${if (app.ai.backendMode) "Lines are sent to Kissa's server (which uses Google Gemini) to generate replies and natural voices." else "Story lines are sent directly to Google Gemini with your own API key (stored encrypted on this phone) to generate replies and character voices; when Gemini's voice quota runs out, lines are voiced by Microsoft's free Edge neural voice service."} Speech recognition uses your phone's recogniser, with Gemini as a backup.",
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
                Text("Kissa ${BuildConfig.VERSION_NAME} — interactive animated stories. Original characters and art. Story AI and voices by Google Gemini (your key), backup natural voices by Microsoft Edge neural TTS, basic voice by your device's text-to-speech engine.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                TextButton(onClick = { nav.navigate("onboarding") }) { Text("Show onboarding again") }
            }
        }
    }

    confirm?.let { what ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(when (what) { "key" -> "Remove API key?"; "memories" -> "Clear conversations & memories?"; else -> "Erase everything?" }) },
            text = { Text(when (what) { "key" -> "Characters can't talk until you add a key again."; "memories" -> "All playthroughs, transcripts, checkpoints, memories and voice recordings will be deleted. Your own stories and settings stay."; else -> "Deletes all data including your created stories, favourites, and settings." }) },
            confirmButton = {
                TextButton(onClick = {
                    val w = what; confirm = null
                    scope.launch {
                        when (w) {
                            "key" -> { app.settings.setApiKey(null); app.ai.runSetup(force = true) }
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

@Composable
private fun VoiceEngines(engine: String, keySet: Boolean, mode: com.kissa.app.voice.VoiceMode, edgeOk: Boolean?, backend: Boolean, geminiFreeAt: Long?, onEngine: (String) -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp)) {
        Text("Voice engines", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
        val now = mode.engine?.label ?: "nothing played yet"
        Text("Now speaking with: $now", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        mode.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFC46B)) }
        if (backend) {
            Text("1. Gemini voices via the Kissa server  2. Microsoft neural  3. Phone voice", style = MaterialTheme.typography.bodySmall, color = pal.textDim, modifier = Modifier.padding(top = 4.dp))
        } else {
            val gem = when {
                engine == "microsoft" -> "off (you chose Microsoft only)"
                !keySet -> "needs your API key"
                geminiFreeAt != null -> "free quota used up — back around ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(geminiFreeAt))}"
                else -> "ready for character lines (free tier ≈ 10 lines/day per model)"
            }
            Text("1. Gemini voice (your key): $gem", style = MaterialTheme.typography.bodySmall, color = pal.textDim, modifier = Modifier.padding(top = 4.dp))
            Text("2. Microsoft neural (free, no key): " + when (edgeOk) { true -> "reachable"; false -> "not reachable right now"; null -> "checking…" },
                style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            Text("3. Phone voice — shown as \"Basic voice\"", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KChip("Best available", engine != "microsoft") { onEngine("auto") }
                KChip("Microsoft only (consistent)", engine == "microsoft") { onEngine("microsoft") }
            }
        }
    }
}

@Suppress("unused") private val keepEmotionImport = Emotion.NEUTRAL
