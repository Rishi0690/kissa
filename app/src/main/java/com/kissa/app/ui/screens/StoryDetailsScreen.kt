package com.kissa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kissa.app.KissaApp
import com.kissa.app.Routes
import com.kissa.app.model.Emotion
import com.kissa.app.model.Story
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@Composable
fun StoryDetailsScreen(nav: NavController, storyId: String) {
    val repo = KissaApp.instance.repo
    val settings by KissaApp.instance.settings.state.collectAsStateWithLifecycle()
    var story by remember { mutableStateOf<Story?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(storyId) { story = repo.story(storyId); loaded = true }
    val play by repo.dao.playthroughFlow(storyId).collectAsStateWithLifecycle(null)
    val favs by repo.dao.favorites().collectAsStateWithLifecycle(emptyList())
    val isFav = favs.any { it.storyId == storyId }
    val scope = rememberCoroutineScope()
    val pal = LocalPalette.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val s = story
    if (s == null) {
        Column { TopBar("Story", { nav.popBackStack() }); if (loaded) EmptyState(Icons.Rounded.ErrorOutline, "Story not found", "It may have been deleted.") }
        return
    }
    val started = (play?.turnCount ?: 0) > 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Box(Modifier.fillMaxWidth().height(460.dp)) {
            StoryCover(s, Modifier.matchParentSize(), portraitFraction = 0.78f)
            Row(Modifier.statusBarsPadding().padding(12.dp)) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", bg = Color.Black.copy(alpha = 0.35f)) { nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                IconCircle(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isFav) "Remove favourite" else "Add favourite",
                    tint = if (isFav) LocalAccent.current else Color.White, bg = Color.Black.copy(alpha = 0.35f)) { scope.launch { repo.toggleFavorite(storyId, !isFav) } }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(0.75f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.genres.take(3).forEach { Pill("${it.emoji} ${it.label}", Color(s.accent)) } }
                Spacer(Modifier.height(8.dp))
                Text(s.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(s.tagline, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            AccentButton(if (started) "Continue · Chapter ${(play?.chapter ?: 0) + 1}" else "Enter the scene", Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow) { nav.navigate(Routes.play(s.id)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("History", Modifier.weight(1f), Icons.Rounded.History) { nav.navigate(Routes.history(s.id)) }
                if (s.isCustom) GhostButton("Edit", Modifier.weight(1f), Icons.Rounded.Edit) { nav.navigate(Routes.edit(s.id)) }
                if (started) GhostButton("Restart", Modifier.weight(1f), Icons.Rounded.RestartAlt) { confirmReset = true }
            }
            Spacer(Modifier.height(22.dp))
            Text("Premise", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(s.premise, style = MaterialTheme.typography.bodyLarge, color = pal.textDim)
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.character(s.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterPortrait(s.character.appearance, Modifier.size(78.dp).clip(CircleShape).background(Color(s.accent).copy(alpha = 0.2f)), Emotion.HAPPY)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.character.name, style = MaterialTheme.typography.titleMedium)
                        Text(s.character.role, style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                        Spacer(Modifier.height(4.dp))
                        Text(s.character.personality, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = pal.textDim)
                    }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
            Spacer(Modifier.height(18.dp))
            InfoBlock("Setting", "${s.setting.label} — ${s.atmosphere}")
            InfoBlock("Opening scene", s.openingSituation)
            InfoBlock("You play", "${s.playerNameOverride?.takeIf { it.isNotBlank() } ?: settings.playerName}, ${s.playerRole}")
            InfoBlock("Language", if (s.isCustom) s.language.label else "${settings.language.label} (your default — changeable inside the scene)")
            Spacer(Modifier.height(8.dp))
            Text("Chapters", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            s.chapters.forEachIndexed { i, ch ->
                val done = (play?.chapter ?: 0) > i || play?.ended == true
                val current = (play?.chapter ?: 0) == i && started && play?.ended != true
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) LocalAccent.current else if (current) LocalAccent.current.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Rounded.Check, null, tint = Color(0xFF16101F), modifier = Modifier.size(16.dp)) else Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ch.title, style = MaterialTheme.typography.titleSmall)
                        // Goals of future chapters stay hidden to avoid spoilers
                        Text(if (done || current) ch.goal else "Locked — reach it through your choices", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                }
            }
            if (play?.ended == true) InfoBlock("Your ending", play?.endingTitle ?: "")
            if (s.isCustom) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete this story") }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Restart story?") },
        text = { Text("This erases the conversation, memories and checkpoints of this playthrough.") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.resetProgress(storyId) }; confirmReset = false }) { Text("Restart") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete story?") },
        text = { Text("The story, its character and all progress will be removed from this device.") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteCustom(storyId); nav.popBackStack() }; confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
fun InfoBlock(title: String, body: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = LocalAccent.current)
        Spacer(Modifier.height(3.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text)
    }
}
