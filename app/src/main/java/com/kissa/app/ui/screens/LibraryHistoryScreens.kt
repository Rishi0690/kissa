package com.kissa.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kissa.app.KissaApp
import com.kissa.app.Routes
import com.kissa.app.data.CheckpointEntity
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(nav: NavController) {
    val (stories, plays, favs) = rememberLibraryState()
    val repo = KissaApp.instance.repo
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val byId = stories.associateBy { it.id }
    val pal = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconCircle(Icons.Rounded.History, "All history") { nav.navigate(Routes.history(null)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip("In progress", tab == 0) { tab = 0 }
                    KChip("Favourites", tab == 1) { tab = 1 }
                    KChip("My stories", tab == 2) { tab = 2 }
                }
            }
        }
        when (tab) {
            0 -> {
                val list = plays.filter { it.turnCount > 0 }.mapNotNull { p -> byId[p.storyId]?.let { it to p } }
                if (list.isEmpty()) item { EmptyState(Icons.Rounded.AutoStories, "No stories in progress", "Start any story and your progress is saved automatically.", "Discover stories") { nav.navigate("discover") } }
                items(list, key = { it.first.id }) { (s, p) ->
                    StoryRowCard(s, "${if (p.ended) "Ended · ${p.endingTitle}" else "Chapter ${p.chapter + 1}/${s.chapters.size}"} · ${p.turnCount} turns · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.lastPlayed))}",
                        onClick = { nav.navigate(Routes.play(s.id)) }) {
                        IconButton(onClick = { nav.navigate(Routes.story(s.id)) }) { Icon(Icons.Rounded.Info, "Details", tint = pal.textDim) }
                    }
                }
            }
            1 -> {
                val list = stories.filter { it.id in favs }
                if (list.isEmpty()) item { EmptyState(Icons.Rounded.FavoriteBorder, "No favourites yet", "Tap the heart on any story to keep it here.") }
                items(list, key = { "f" + it.id }) { s ->
                    StoryRowCard(s, s.tagline, onClick = { nav.navigate(Routes.story(s.id)) }) {
                        IconButton(onClick = { scope.launch { repo.toggleFavorite(s.id, false) } }) { Icon(Icons.Rounded.Favorite, "Remove favourite", tint = LocalAccent.current) }
                    }
                }
            }
            else -> {
                val list = stories.filter { it.isCustom }
                if (list.isEmpty()) item { EmptyState(Icons.Rounded.EditNote, "You haven't written a story yet", "Create your own characters and scenes — they're saved on this device.", "Create a story") { nav.navigate("create") } }
                items(list, key = { "m" + it.id }) { s ->
                    StoryRowCard(s, s.premise, onClick = { nav.navigate(Routes.story(s.id)) }) {
                        IconButton(onClick = { nav.navigate(Routes.edit(s.id)) }) { Icon(Icons.Rounded.Edit, "Edit", tint = pal.textDim) }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(nav: NavController, storyId: String?) {
    val repo = KissaApp.instance.repo
    val (stories, _, _) = rememberLibraryState()
    val byId = stories.associateBy { it.id }
    val flow = remember(storyId) { if (storyId != null) repo.dao.checkpointsFlow(storyId) else repo.dao.allCheckpoints() }
    val checkpoints by flow.collectAsStateWithLifecycle(emptyList())
    val messages by remember(storyId) { storyId?.let { repo.dao.messagesFlow(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<CheckpointEntity?>(null) }
    val pal = LocalPalette.current
    val title = storyId?.let { byId[it]?.title } ?: "History & Replay"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar(title, { nav.popBackStack() }) }
        item {
            Text("Checkpoints are saved at every new chapter, at endings, and whenever you save from the scene menu. Replay re-performs the scene with voices; Branch rewinds the story to that moment so you can choose differently.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = pal.textDim)
        }
        if (storyId != null && messages.isNotEmpty()) item {
            GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
                Text("Whole conversation", style = MaterialTheme.typography.titleSmall)
                Text("${messages.count { !it.hidden }} lines", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Replay recent", Modifier.weight(1f), Icons.Rounded.PlayCircle) { nav.navigate(Routes.play(storyId, 0L)) }
                    AccentButton("Continue", Modifier.weight(1f), Icons.Rounded.PlayArrow) { nav.navigate(Routes.play(storyId)) }
                }
            }
        }
        if (checkpoints.isEmpty()) item { EmptyState(Icons.Rounded.BookmarkBorder, "No checkpoints yet", "Play on — a checkpoint is created when a chapter completes. You can also save one from the scene menu.") }
        items(checkpoints, key = { it.id }) { c ->
            val s = byId[c.storyId]
            GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (storyId == null && s != null) { StoryCover(s, Modifier.size(56.dp), portraitFraction = 1f); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        if (storyId == null) Text(s?.title ?: c.storyId, style = MaterialTheme.typography.labelMedium, color = LocalAccent.current)
                        Text(c.label, style = MaterialTheme.typography.titleSmall)
                        Text("Bond ${c.relationship} · Mood ${c.mood} · ${c.turnCount} turns · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(c.createdAt))}",
                            style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Replay from here", Modifier.weight(1f), Icons.Rounded.PlayCircle) { nav.navigate(Routes.play(c.storyId, c.id)) }
                    GhostButton("Branch", Modifier.weight(1f), Icons.Rounded.CallSplit) { confirm = c }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Branch from \"${c.label}\"?") },
            text = { Text("Everything said after this checkpoint will be removed and the story's mood, bond, chapter and memories return to that moment. This can't be undone.") },
            confirmButton = { TextButton(onClick = { val cp = c; confirm = null; scope.launch { repo.restoreCheckpoint(cp.id)?.let { id -> nav.navigate(Routes.play(id)) } } }) { Text("Branch") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}
