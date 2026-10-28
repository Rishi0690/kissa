package com.kissa.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kissa.app.KissaApp
import com.kissa.app.Routes
import com.kissa.app.data.PlaythroughEntity
import com.kissa.app.model.Emotion
import com.kissa.app.model.Story
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.absoluteValue

@Composable
fun rememberLibraryState(): Triple<List<Story>, List<PlaythroughEntity>, Set<String>> {
    val repo = KissaApp.instance.repo
    val stories by repo.allStories.collectAsStateWithLifecycle(emptyList())
    val plays by repo.dao.playthroughs().collectAsStateWithLifecycle(emptyList())
    val favs by repo.dao.favorites().collectAsStateWithLifecycle(emptyList())
    return Triple(stories, plays, favs.map { it.storyId }.toSet())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: NavController) {
    val (stories, plays, favs) = rememberLibraryState()
    val settings by KissaApp.instance.settings.state.collectAsStateWithLifecycle()
    val pal = LocalPalette.current
    val featured = stories.filter { it.featured } + stories.filter { !it.featured && !it.isCustom }.take(3)
    val byId = stories.associateBy { it.id }
    val continuing = plays.filter { it.turnCount > 0 }.mapNotNull { p -> byId[p.storyId]?.let { it to p } }
    val playedGenres = continuing.flatMap { it.first.genres }.toSet()
    val recommended = stories.filter { s -> continuing.none { it.first.id == s.id } }
        .sortedByDescending { s -> s.genres.count { it in playedGenres } }.take(6)
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$greeting, ${settings.playerName}", style = MaterialTheme.typography.bodyMedium, color = pal.textDim)
                    Text("Where to tonight?", style = MaterialTheme.typography.headlineMedium)
                }
                IconCircle(Icons.Rounded.Search, "Search") { nav.navigate("discover") }
            }
        }
        if (!settings.hasApiKey && settings.proxyUrl.isBlank()) item {
            GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth(), onClick = { nav.navigate("settings") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = LocalAccent.current)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Add your free Gemini key", style = MaterialTheme.typography.titleSmall)
                        Text("Characters need it to think and reply. Tap to set it up.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        if (featured.isNotEmpty()) item {
            val pager = rememberPagerState { featured.size }
            LaunchedEffect(pager) { while (true) { delay(6000); if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % featured.size) } }
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, modifier = Modifier.padding(top = 8.dp)) { page ->
                val s = featured[page]
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                Box(Modifier.fillMaxWidth().height(400.dp).graphicsScale(lerp(1f, 0.93f, off.coerceIn(0f, 1f))).clickable { nav.navigate(Routes.story(s.id)) }) {
                    StoryCover(s, Modifier.matchParentSize(), portraitFraction = 0.82f)
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(0.72f)) {
                        Pill(if (page == 0) "FEATURED" else s.genres.first().label.uppercase(), Color(s.accent))
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(s.tagline, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        AccentButton("Enter scene", icon = Icons.Rounded.PlayArrow) { nav.navigate(Routes.play(s.id)) }
                    }
                }
            }
        }
        if (continuing.isNotEmpty()) {
            item { SectionHeader("Continue playing", "Library") { nav.navigate("library") } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continuing, key = { it.first.id }) { (s, p) ->
                        Column(Modifier.width(230.dp).glass().clickable { nav.navigate(Routes.play(s.id)) }.padding(10.dp)) {
                            StoryCover(s, Modifier.fillMaxWidth().height(130.dp), portraitFraction = 1f)
                            Spacer(Modifier.height(10.dp))
                            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (p.ended) "Ending · ${p.endingTitle ?: ""}" else "Chapter ${p.chapter + 1} · ${s.chapters.getOrNull(p.chapter)?.title ?: ""}",
                                style = MaterialTheme.typography.bodySmall, color = pal.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Meter("Bond", p.relationship, Color(s.accent))
                        }
                    }
                }
            }
        }
        item { SectionHeader("Characters", "See all") { nav.navigate("discover?tab=characters") } }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(stories, key = { "c_" + it.id }) { s ->
                    Column(Modifier.width(92.dp).clickable { nav.navigate(Routes.character(s.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        CharacterPortrait(s.character.appearance, Modifier.size(84.dp).clip(CircleShape).background(Color(s.accent).copy(alpha = 0.22f)), Emotion.HAPPY)
                        Spacer(Modifier.height(6.dp))
                        Text(s.character.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(s.genres.first().label, style = MaterialTheme.typography.labelSmall, color = pal.textDim, maxLines = 1)
                    }
                }
            }
        }
        if (recommended.isNotEmpty()) {
            item { SectionHeader(if (playedGenres.isEmpty()) "Start here" else "Recommended for you", "Discover") { nav.navigate("discover") } }
            items(recommended, key = { "r_" + it.id }) { s ->
                StoryRowCard(s, s.tagline, onClick = { nav.navigate(Routes.story(s.id)) }) {
                    if (s.id in favs) Icon(Icons.Rounded.Favorite, "Favourite", tint = LocalAccent.current, modifier = Modifier.padding(6.dp))
                }
            }
        }
        val favStories = stories.filter { it.id in favs }
        if (favStories.isNotEmpty()) {
            item { SectionHeader("Favourites") }
            items(favStories, key = { "f_" + it.id }) { s -> StoryRowCard(s, s.tagline, onClick = { nav.navigate(Routes.story(s.id)) }) }
        }
        item {
            GlassCard(Modifier.padding(20.dp).fillMaxWidth(), onClick = { nav.navigate("create") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(LocalAccent.current.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = LocalAccent.current)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Write your own story", style = MaterialTheme.typography.titleMedium)
                        Text("Design a character, pick a place, and play it.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                }
            }
        }
    }
}

fun Modifier.graphicsScale(s: Float) = this.graphicsLayer { scaleX = s; scaleY = s }
