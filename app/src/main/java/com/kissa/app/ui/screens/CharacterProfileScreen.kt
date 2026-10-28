package com.kissa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kissa.app.KissaApp
import com.kissa.app.Routes
import com.kissa.app.ai.ContextBuilder
import com.kissa.app.model.Emotion
import com.kissa.app.model.Lang
import com.kissa.app.model.Story
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import com.kissa.app.voice.Utterance
import kotlinx.coroutines.launch

@Composable
fun CharacterProfileScreen(nav: NavController, storyId: String) {
    val app = KissaApp.instance
    val repo = app.repo
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var story by remember { mutableStateOf<Story?>(null) }
    LaunchedEffect(storyId) { story = repo.story(storyId) }
    val play by repo.dao.playthroughFlow(storyId).collectAsStateWithLifecycle(null)
    val notes by repo.dao.notesFlow(storyId).collectAsStateWithLifecycle(emptyList())
    val voices by app.speech.voices.collectAsStateWithLifecycle()
    val speaking by app.speech.speaking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pal = LocalPalette.current
    var emotion by remember { mutableStateOf(Emotion.HAPPY) }
    var showVoices by remember { mutableStateOf(false) }
    var showNeural by remember { mutableStateOf(false) }
    val s = story ?: run { TopBar("Character", { nav.popBackStack() }); return }
    val c = s.character
    DisposableEffect(Unit) { onDispose { app.speech.stop() } }

    fun preview() {
        val sample = when (if (s.isCustom) s.language else settings.language) {
            Lang.HINDI -> "नमस्ते! मैं ${c.name.substringBefore(' ')} हूँ। चलो, कहानी शुरू करते हैं?"
            Lang.ENGLISH -> "Hi, I'm ${c.name.substringBefore(' ')}. Ready to see where this story goes?"
            Lang.HINGLISH -> "Hi, main ${c.name.substringBefore(' ')} hoon. Chalo, dekhte hain ye kahani kahan jaati hai?"
        }
        val loc = if (sample.any { it in '\u0900'..'\u097F' }) "hi-IN" else (if (s.isCustom) s.language else settings.language).ttsTag
        app.speech.stop()
        scope.launch {
            // natural preview with the character's fixed Microsoft neural voice (keeps the small Gemini voice quota for the story)
            app.speech.play(Utterance(c.id, sample, loc, settings.voiceOverrides[c.id] ?: c.voice.voiceName, c.voice.feminine, c.voice.pitch, c.voice.rate, emotion,
                neuralVoice = com.kissa.app.voice.NeuralVoices.forCharacter(c, settings.neuralOverrides[c.id]),
                edgeVoice = com.kissa.app.voice.NeuralVoices.edgeForCharacter(c, settings.neuralOverrides[c.id], settings.playerEdgeVoice, settings.playerBody),
                lang = com.kissa.app.voice.NeuralVoices.langCode(if (s.isCustom) s.language else settings.language)))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        TopBar(c.name.substringBefore(' '), { nav.popBackStack() })
        Box(Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(s.accent).copy(alpha = 0.35f), Color.Transparent))).clickable {
                emotion = Emotion.entries[(emotion.ordinal + 1) % Emotion.entries.size]
            }) {
            CharacterPortrait(c.appearance, Modifier.fillMaxHeight().aspectRatio(0.85f).align(Alignment.BottomCenter), emotion, animate = true)
            Text("Tap to see ${emotion.label.lowercase()} →", Modifier.align(Alignment.TopEnd).padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Emotion.entries.forEach { e -> KChip(e.label, e == emotion) { emotion = e } }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineMedium)
            Text("${c.role} · ${c.age}", style = MaterialTheme.typography.bodyMedium, color = pal.textDim)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentButton(if ((play?.turnCount ?: 0) > 0) "Continue story" else "Meet ${c.name.substringBefore(' ')}", Modifier.weight(1f), Icons.Rounded.PlayArrow) { nav.navigate(Routes.play(s.id)) }
                GhostButton(if (speaking != null) "Stop" else "Hear voice", Modifier.weight(1f), if (speaking != null) Icons.Rounded.Stop else Icons.Rounded.VolumeUp) { if (speaking != null) app.speech.stop() else preview() }
            }
            Spacer(Modifier.height(18.dp))
            play?.let { p ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Your relationship", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Meter("Bond · ${ContextBuilder.relationshipLabel(p.relationship)}", p.relationship, LocalAccent.current)
                    Spacer(Modifier.height(8.dp))
                    Meter("Current mood · ${ContextBuilder.moodLabel(p.mood)}", p.mood, Color(0xFFFFC46B))
                }
                Spacer(Modifier.height(16.dp))
            }
            InfoBlock("Background", c.background)
            InfoBlock("Personality", c.personality)
            InfoBlock("Speaking style", c.speakingStyle)
            InfoBlock("Likes", c.likes)
            InfoBlock("Dislikes", c.dislikes)
            InfoBlock("Goals", c.goals)
            InfoBlock("Boundaries", c.boundaries)
            if (c.quirks.isNotBlank()) InfoBlock("Quirks", c.quirks)
            val gem = com.kissa.app.voice.NeuralVoices.forCharacter(c, settings.neuralOverrides[c.id])
            val ms = com.kissa.app.voice.NeuralVoices.edgeForCharacter(c, settings.neuralOverrides[c.id], settings.playerEdgeVoice, settings.playerBody)
            GlassCard(Modifier.fillMaxWidth(), onClick = { showNeural = !showNeural }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = LocalAccent.current)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Natural voice", style = MaterialTheme.typography.titleSmall)
                        Text("Gemini: $gem · backup: Microsoft ${ms.label}", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                    IconButton(onClick = { preview() }) { Icon(Icons.Rounded.VolumeUp, "Preview voice") }
                    Icon(if (showNeural) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (showNeural) {
                    Spacer(Modifier.height(8.dp))
                    VoiceRow("Automatic", settings.neuralOverrides[c.id] == null) { app.settings.setCharacterNeuralVoice(c.id, null) }
                    com.kissa.app.voice.NeuralVoices.ALL.sortedBy { if (it.feminine == c.voice.feminine) 0 else 1 }.forEach { v ->
                        VoiceRow("${v.name} · ${v.trait}${if (v.feminine) " · F" else " · M"}", settings.neuralOverrides[c.id] == v.name) { app.settings.setCharacterNeuralVoice(c.id, v.name) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), onClick = { showVoices = !showVoices }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.RecordVoiceOver, null, tint = LocalAccent.current)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Basic phone voice (fallback)", style = MaterialTheme.typography.titleSmall)
                        Text(settings.voiceOverrides[c.id] ?: "Automatic (${if (c.voice.feminine) "feminine" else "masculine"}, pitch ${"%.2f".format(c.voice.pitch)})", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                    Icon(if (showVoices) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (showVoices) {
                    Spacer(Modifier.height(8.dp))
                    VoiceRow("Automatic", settings.voiceOverrides[c.id] == null) { app.settings.setCharacterVoice(c.id, null) }
                    if (voices.isEmpty()) Text("No English/Hindi voices found. Install voice data in Android Settings → Text-to-speech.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    voices.forEach { v ->
                        VoiceRow("${v.name} · ${v.locale}${v.feminine?.let { if (it) " · F" else " · M" } ?: ""}${if (!v.offline) " · online" else ""}", settings.voiceOverrides[c.id] == v.name) {
                            app.settings.setCharacterVoice(c.id, v.name); preview()
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memories (${notes.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (notes.isNotEmpty()) TextButton(onClick = { scope.launch { repo.dao.deleteNotes(storyId) } }) { Text("Forget all") }
            }
            if (notes.isEmpty()) Text("${c.name.substringBefore(' ')} will remember important things you share — they'll appear here, and you can delete any of them.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            notes.forEach { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", color = LocalAccent.current); Spacer(Modifier.width(10.dp))
                    Text(n.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { scope.launch { repo.dao.deleteNote(n.id) } }) { Icon(Icons.Rounded.Close, "Forget this", tint = pal.textDim) }
                }
            }
            if (!play?.summary.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                InfoBlock("Story so far (rolling summary)", play!!.summary)
            }
        }
    }
}

@Composable
fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
