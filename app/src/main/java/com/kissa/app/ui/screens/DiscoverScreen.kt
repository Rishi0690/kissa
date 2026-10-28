package com.kissa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kissa.app.Routes
import com.kissa.app.model.Emotion
import com.kissa.app.model.Genre
import com.kissa.app.model.SettingId
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette

@Composable
fun DiscoverScreen(nav: NavController, initialTab: String? = null) {
    val (stories, plays, favs) = rememberLibraryState()
    val pal = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var setting by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf("Featured") }
    var tab by rememberSaveable { mutableStateOf(if (initialTab == "characters") 1 else 0) }
    var onlyNew by rememberSaveable { mutableStateOf(false) }
    val played = plays.filter { it.turnCount > 0 }.map { it.storyId }.toSet()

    val q = query.trim().lowercase()
    val filtered = stories.filter { s ->
        (genre == null || s.genres.any { it.name == genre }) &&
            (setting == null || s.setting.name == setting) &&
            (!onlyNew || s.id !in played) &&
            (q.isEmpty() || listOf(s.title, s.tagline, s.premise, s.character.name, s.character.role, s.character.personality, s.setting.label).any { it.lowercase().contains(q) } ||
                s.genres.any { it.label.lowercase().contains(q) })
    }.let { list ->
        when (sort) {
            "A–Z" -> list.sortedBy { it.title }
            "Newest" -> list.sortedByDescending { it.isCustom }
            else -> list.sortedByDescending { (if (it.featured) 2 else 0) + (if (it.id in favs) 1 else 0) }
        }
    }

    LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 14.dp)) {
                Text("Discover", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = pal.textDim)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = pal.text), cursorBrush = SolidColor(LocalAccent.current),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text("Stories, characters, places, moods…", color = pal.textDim, style = MaterialTheme.typography.bodyLarge); inner() } })
                    if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", Modifier.clickable { query = "" }, tint = pal.textDim)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip("Stories", tab == 0) { tab = 0 }
                    KChip("Characters", tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(12.dp))
                Text("Genres", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip("All", genre == null) { genre = null }
                    Genre.entries.forEach { g -> KChip("${g.emoji} ${g.label}", genre == g.name) { genre = if (genre == g.name) null else g.name } }
                }
                Spacer(Modifier.height(10.dp))
                Text("Places", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip("Anywhere", setting == null) { setting = null }
                    SettingId.entries.forEach { st -> KChip(st.label, setting == st.name) { setting = if (setting == st.name) null else st.name } }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sort, null, tint = pal.textDim)
                    listOf("Featured", "A–Z", "Newest").forEach { s -> KChip(s, sort == s) { sort = s } }
                    KChip("Not played yet", onlyNew) { onlyNew = !onlyNew }
                }
                Spacer(Modifier.height(6.dp))
                Text("${filtered.size} result${if (filtered.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = pal.textDim)
            }
        }
        if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(Icons.Rounded.TravelExplore, "No stories here yet", "Nothing matches these filters. Write one yourself — it takes a minute.", "Create a story") { nav.navigate("create") }
        }
        if (tab == 0) items(filtered, key = { it.id }) { s ->
            Column(Modifier.clickable { nav.navigate(Routes.story(s.id)) }) {
                Box {
                    StoryCover(s, Modifier.fillMaxWidth().aspectRatio(0.78f), portraitFraction = 0.85f)
                    if (s.isCustom) Box(Modifier.padding(8.dp)) { Pill("YOURS") }
                    if (s.id in favs) Icon(Icons.Rounded.Favorite, "Favourite", tint = LocalAccent.current, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.genres.first().emoji} ${s.genres.first().label} · ${s.character.name.substringBefore(' ')}", style = MaterialTheme.typography.bodySmall, color = pal.textDim, maxLines = 1)
            }
        } else items(filtered, key = { "c" + it.id }) { s ->
            Column(Modifier.glass().clickable { nav.navigate(Routes.character(s.id)) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CharacterPortrait(s.character.appearance, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(s.accent).copy(alpha = 0.2f)), Emotion.HAPPY)
                Spacer(Modifier.height(8.dp))
                Text(s.character.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.character.role, style = MaterialTheme.typography.bodySmall, color = pal.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
