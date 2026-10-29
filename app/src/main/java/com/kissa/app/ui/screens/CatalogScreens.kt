package com.kissa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kissa.app.KissaApp
import com.kissa.app.data.GameEntry
import com.kissa.app.game.Catalog
import com.kissa.app.game.Look
import com.kissa.app.ui.common.GameCover
import com.kissa.app.ui.common.RigView
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette

private val Hi = Color(0xFFF7F4FF)
private val Lo = Color(0xFFC9C2DA)

@Composable
private fun app() = LocalContext.current.applicationContext as KissaApp

@Composable
fun visibleGames(): List<GameEntry> {
    val a = app()
    val all by a.games.entries.collectAsStateWithLifecycle()
    val social by a.social.state.collectAsStateWithLifecycle()
    return all.filter { it.id !in social.hiddenGames && it.game.creator !in social.blockedCreators }
}

// ---------------------------------------------------------------- home (catalog of standalone games)
@Composable
fun HomeScreen(onGame: (String) -> Unit, onSearch: () -> Unit, onContinue: (String) -> Unit) {
    val a = app()
    val games = visibleGames()
    val saves = remember(games) { games.mapNotNull { g -> a.saves.load(g.id)?.takeIf { it.autosave != null }?.let { g to it } } }
    val acc = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kissa", color = Hi, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text("Story games jahan hero tum ho", color = Lo, fontSize = 13.sp)
                }
                IconCircle(Icons.Rounded.Search, "Search", onClick = onSearch)
            }
        }
        games.firstOrNull()?.let { f ->
            item {
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(0.82f).clickable { onGame(f.id) }) {
                    GameCover(f, Modifier.fillMaxSize(), showTitle = false)
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Pill(f.game.genre)
                        Spacer(Modifier.height(6.dp))
                        Text(f.game.title, color = Hi, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Text(f.game.tagline, color = Lo, fontSize = 14.sp, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        AccentButton("Khelo", icon = Icons.Rounded.PlayArrow) { onGame(f.id) }
                    }
                }
            }
        }
        if (saves.isNotEmpty()) {
            item { SectionHeader("Continue") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(saves) { (g, s) ->
                        Row(Modifier.width(260.dp).glass().clickable { onContinue(g.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameCover(g, Modifier.size(64.dp), showTitle = false)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.game.title, color = Hi, fontWeight = FontWeight.SemiBold)
                                Text("${s.playerName} · ${g.game.scenes[s.autosave!!.sceneId]?.title ?: "Jaari"}", color = Lo, fontSize = 12.sp, maxLines = 1)
                            }
                            Icon(Icons.Rounded.PlayCircle, null, tint = acc)
                        }
                    }
                }
            }
        }
        item { SectionHeader("Sab games") }
        items(games) { g -> GameRow(g) { onGame(g.id) } }
        item {
            Text("Har game ek alag kahani hai — apna naam, apna look, apne faisle. Endings tumhare stats tay karte hain.", color = Lo, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
fun GameRow(g: GameEntry, onClick: () -> Unit) {
    val a = app()
    val save = remember(g.id) { a.saves.load(g.id) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().glass().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GameCover(g, Modifier.width(92.dp).height(122.dp), showTitle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.game.title, color = Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text("${g.game.genre} · ${g.game.minutes} min · ${g.game.endings.size} endings", color = Lo, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(g.game.tagline, color = Hi.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            save?.let { if (it.endingsReached.isNotEmpty()) Text("Endings mili: ${it.endingsReached.size}/${g.game.endings.size}", color = LocalAccent.current, fontSize = 12.sp) }
        }
    }
}

// ---------------------------------------------------------------- search
@Composable
fun SearchScreen(onGame: (String) -> Unit, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val games = visibleGames()
    val acc = LocalAccent.current
    val res = games.filter { g -> q.isBlank() || listOf(g.game.title, g.game.genre, g.game.tagline, g.game.premise, g.game.tags.joinToString(" "), g.game.characters.joinToString(" ") { it.name }).any { it.contains(q, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("Search", onBack)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Game, genre, character… (jaise: college)", color = Lo) }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Lo) }, shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Hi, unfocusedTextColor = Hi, focusedBorderColor = acc, unfocusedBorderColor = Color(0x44FFFFFF), cursorColor = acc))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            games.map { it.game.genre }.distinct().forEach { KChip(it, q == it, Modifier.padding(end = 8.dp)) { q = if (q == it) "" else it } }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
            items(res) { g -> GameRow(g) { onGame(g.id) } }
            if (res.isEmpty()) item { Text("Kuch nahi mila.", color = Lo, modifier = Modifier.padding(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------- game detail
@Composable
fun GameDetailScreen(gameId: String, onBack: () -> Unit, onSetup: () -> Unit, onContinue: () -> Unit) {
    val a = app()
    val all by a.games.entries.collectAsStateWithLifecycle()
    val social by a.social.state.collectAsStateWithLifecycle()
    val g = all.firstOrNull { it.id == gameId } ?: return
    val save = remember(gameId) { a.saves.load(gameId) }
    val acc = LocalAccent.current
    var reporting by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.9f)) {
            GameCover(g, Modifier.fillMaxSize(), showTitle = false)
            IconCircle(Icons.Rounded.ArrowBack, "Back", Modifier.statusBarsPadding().padding(12.dp), onClick = onBack)
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Pill(g.game.genre)
                Text(g.game.title, color = Hi, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text("${g.game.minutes} min · ${g.game.endings.size} endings · by ${g.game.creator}", color = Lo, fontSize = 13.sp)
            }
        }
        Column(Modifier.padding(20.dp)) {
            Text(g.game.tagline, color = Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(g.game.premise, color = Lo, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (save?.autosave != null) { AccentButton("Continue", icon = Icons.Rounded.PlayArrow, onClick = onContinue); GhostButton("Naya game", onClick = onSetup) }
                else AccentButton(if (save?.endingsReached?.isNotEmpty() == true) "Phir se khelo" else "Khelo", icon = Icons.Rounded.PlayArrow, onClick = onSetup)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val liked = g.id in social.liked
                IconCircle(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) Color(0xFFFF6F8A) else Hi) { a.social.toggleLike(g.id) }
                val inLib = g.id in social.library
                IconCircle(if (inLib) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, "Library", tint = if (inLib) acc else Hi) { a.social.toggleLibrary(g.id) }
                val following = g.game.creator in social.following
                KChip(if (following) "Following ${g.game.creator}" else "Follow ${g.game.creator}", following) { a.social.toggleFollow(g.game.creator) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Tumhara role", color = Hi, fontWeight = FontWeight.Bold)
            Text(g.game.player.roleText + (if (g.game.player.goal.isNotBlank()) " — ${g.game.player.goal}" else ""), color = Lo, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Text("Cast", color = Hi, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                g.game.characters.forEach { c ->
                    Column(Modifier.padding(end = 12.dp).width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xFF2A2438))) { RigView(c.rig, c.look, Modifier.fillMaxSize(), portrait = true, animate = false) }
                        Text(c.name, color = Hi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.bio, color = Lo, fontSize = 10.sp, maxLines = 3, lineHeight = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Endings (${save?.endingsReached?.size ?: 0}/${g.game.endings.size})", color = Hi, fontWeight = FontWeight.Bold)
            g.game.endings.forEach { e ->
                val got = e.id in (save?.endingsReached ?: emptySet())
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (got) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = if (got) Color(0xFF6BD69A) else Lo, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (got) e.title else "??? — ${e.teaser}", color = if (got) Hi else Lo, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Size: game ${g.sizeBytes / 1024} KB · voice ${g.voiceBytes / 1024} KB · offline ready", color = Lo, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Report", icon = Icons.Rounded.Flag) { reporting = true }
                GhostButton("Block creator", icon = Icons.Rounded.Block) { blocking = true }
            }
        }
    }
    if (reporting) ReportDialog(onDismiss = { reporting = false }) { reason, note -> a.social.report(g.id, reason, note); reporting = false; onBack() }
    if (blocking) AlertDialog(onDismissRequest = { blocking = false }, containerColor = Color(0xFF1C1828), titleContentColor = Hi, textContentColor = Lo,
        title = { Text("Block ${g.game.creator}?") }, text = { Text("Is creator ke saare games tumhari feed se hat jayenge. Settings se unblock kar sakte ho.") },
        confirmButton = { TextButton({ a.social.blockCreator(g.game.creator); blocking = false; onBack() }) { Text("Block", color = Color(0xFFFF7A86)) } },
        dismissButton = { TextButton({ blocking = false }) { Text("Cancel", color = Hi) } })
}

@Composable
fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    val reasons = listOf("Copied content", "Hate / harassment", "Sexual content", "Violence", "Spam / misleading", "Other")
    var sel by remember { mutableStateOf(reasons.first()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1828), titleContentColor = Hi, textContentColor = Lo,
        title = { Text("Report game") },
        text = {
            Column {
                reasons.forEach { r -> Row(Modifier.fillMaxWidth().clickable { sel = r }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sel == r, { sel = r }); Text(r, color = Hi) } }
                OutlinedTextField(note, { note = it }, placeholder = { Text("Detail (optional)", color = Lo) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Hi, unfocusedTextColor = Hi))
                Text("Report is device par queue hota hai aur game turant tumhari feed se hat jata hai. Online moderation Phase 2 mein.", color = Lo, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton({ onSend(sel, note) }) { Text("Report", color = Color(0xFFFF7A86)) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Hi) } })
}

// ---------------------------------------------------------------- role setup (name + look from the same rig catalog)
private val femaleHairs = listOf("long_straight", "long_wavy", "ponytail", "bob", "bun", "braid")
private val maleHairs = listOf("short_messy", "side_part", "curly", "buzz", "undercut", "man_bun")

@Composable
fun RoleSetupScreen(gameId: String, onBack: () -> Unit, onStart: () -> Unit) {
    val a = app()
    val all by a.games.entries.collectAsStateWithLifecycle()
    val g = all.firstOrNull { it.id == gameId } ?: return
    val prev = remember { a.saves.load(gameId) }
    val acc = LocalAccent.current
    var name by remember { mutableStateOf(prev?.playerName ?: g.game.player.defaultName) }
    var rig by remember { mutableStateOf(prev?.playerRig ?: "male") }
    var look by remember { mutableStateOf(prev?.look ?: Look(skin = 2, hair = "short_messy", hairColor = "black", outfit = "hoodie", outfitColor = "navy")) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.42f).background(Brush.verticalGradient(listOf(Color(0xFF2A2240), Color(0xFF15121F))))) {
            RigView(rig, look, Modifier.fillMaxSize().padding(top = 40.dp), expression = "smile", move = "idle")
            IconCircle(Icons.Rounded.ArrowBack, "Back", Modifier.statusBarsPadding().padding(12.dp), onClick = onBack)
            Text("Tumhara character", color = Hi, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp))
        }
        Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text("Role: ${g.game.player.roleText}", color = Lo, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it.take(18) }, Modifier.fillMaxWidth(), label = { Text("Tumhara naam", color = Lo) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Hi, unfocusedTextColor = Hi, focusedBorderColor = acc, unfocusedBorderColor = Color(0x44FFFFFF), cursorColor = acc))
            Opt("Body") {
                listOf("male" to "Ladka", "female" to "Ladki").forEach { (r, l) -> KChip(l, rig == r, Modifier.padding(end = 8.dp)) {
                    rig = r; val hs = if (r == "female") femaleHairs else maleHairs
                    if (look.hair !in hs) look = look.copy(hair = hs.first())
                } }
            }
            Opt("Skin") { com.kissa.app.rig.RigKit.skinTones.forEachIndexed { i, c -> Swatch(c, look.skin == i) { look = look.copy(skin = i) } } }
            Opt("Face") { Catalog.faces.forEach { f -> KChip(f, look.face == f, Modifier.padding(end = 8.dp)) { look = look.copy(face = f) } } }
            Opt("Aankhein") { com.kissa.app.rig.RigKit.eyeColors.forEach { (k, c) -> Swatch(c, look.eyes == k) { look = look.copy(eyes = k) } } }
            Opt("Hair") { (femaleHairs + maleHairs).forEach { h -> KChip(h.replace('_', ' '), look.hair == h, Modifier.padding(end = 8.dp)) { look = look.copy(hair = h) } } }
            Opt("Hair colour") { com.kissa.app.rig.RigKit.hairColors.forEach { (k, c) -> Swatch(c, look.hairColor == k) { look = look.copy(hairColor = k) } } }
            Opt("Outfit") { Catalog.outfits.forEach { o -> KChip(o.replace('_', ' '), look.outfit == o, Modifier.padding(end = 8.dp)) { look = look.copy(outfit = o) } } }
            Opt("Outfit colour") { com.kissa.app.rig.RigKit.outfitColors.forEach { (k, c) -> Swatch(c, look.outfitColor == k) { look = look.copy(outfitColor = k) } } }
            Opt("Accessories") {
                Catalog.accessories.forEach { x -> val on = x in look.accessories
                    KChip(x.replace('_', ' '), on, Modifier.padding(end = 8.dp)) { look = look.copy(accessories = if (on) look.accessories - x else look.accessories + x) } }
            }
            Spacer(Modifier.height(16.dp))
            AccentButton("Game shuru karo", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, enabled = name.isNotBlank()) {
                a.saves.startNew(gameId, name.trim(), rig, look)
                onStart()
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Opt(label: String, content: @Composable RowScope.() -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(label, color = Hi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun Swatch(c: Color, sel: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(end = 10.dp).size(36.dp).clip(CircleShape).background(c).border(if (sel) 3.dp else 1.dp, if (sel) LocalAccent.current else Color(0x55FFFFFF), CircleShape).clickable(onClick = onClick))
}
