package com.kissa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kissa.app.BuildConfig
import com.kissa.app.KissaApp
import com.kissa.app.game.Look
import com.kissa.app.ui.common.RigView
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.Themes

private val Hi = Color(0xFFF7F4FF)
private val Lo = Color(0xFFC9C2DA)

// ---------------------------------------------------------------- library
@Composable
fun LibraryScreen(onGame: (String) -> Unit) {
    val a = LocalContext.current.applicationContext as KissaApp
    val games = visibleGames()
    val social by a.social.state.collectAsStateWithLifecycle()
    val saves = remember(games) { games.associate { it.id to a.saves.load(it.id) } }
    val saved = games.filter { it.id in social.library }
    val played = games.filter { saves[it.id] != null }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { Text("Library", color = Hi, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, modifier = Modifier.padding(20.dp)) }
        item {
            val total = games.sumOf { it.game.endings.size }; val got = saves.values.sumOf { it?.endingsReached?.size ?: 0 }
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = LocalAccent.current, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(12.dp))
                Column { Text("Endings collected: $got / $total", color = Hi, fontWeight = FontWeight.Bold); Text("Alag faisle → alag endings. Har game ke checkpoints se replay karo.", color = Lo, fontSize = 12.sp) }
            }
        }
        item { SectionHeader("Khele hue") }
        if (played.isEmpty()) item { Text("Abhi tak koi game nahi khela.", color = Lo, modifier = Modifier.padding(horizontal = 20.dp)) }
        items(played) { g -> GameRow(g) { onGame(g.id) } }
        item { SectionHeader("Saved") }
        if (saved.isEmpty()) item { Text("Game page par bookmark dabao, yahan dikhega.", color = Lo, modifier = Modifier.padding(horizontal = 20.dp)) }
        items(saved) { g -> GameRow(g) { onGame(g.id) } }
        if (social.following.isNotEmpty()) item {
            SectionHeader("Following")
            Text(social.following.joinToString(" · "), color = Lo, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

// ---------------------------------------------------------------- settings
@Composable
fun SettingsScreen() {
    val a = LocalContext.current.applicationContext as KissaApp
    val s by a.settings.state.collectAsStateWithLifecycle()
    val social by a.social.state.collectAsStateWithLifecycle()
    val acc = LocalAccent.current
    var cleared by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 120.dp)) {
        Text("Settings", color = Hi, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp))
        GlassCard {
            Text("Voice", color = Hi, fontWeight = FontWeight.Bold)
            ToggleRow("Character voice", "Har line ki pehle se bani awaaz. Band karne par captions aur chat text poore dikhte rahenge.", s.characterVoicesOn) { v -> a.settings.update { putBoolean("characterVoicesOn", v) } }
            Text("Volume", color = Lo, fontSize = 13.sp)
            Slider(s.volume, { v -> a.settings.update { putFloat("volume", v) }; a.voice.volume = v }, colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc))
            Text("Voices: Microsoft Edge neural (free), ek baar generate, game ke saath offline. Jo line tumhara naam leti hai woh phone ki basic awaaz mein bolti hai (fallback).", color = Lo, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Text("Display", color = Hi, fontWeight = FontWeight.Bold)
            Text("Caption size", color = Lo, fontSize = 13.sp)
            Slider(s.subtitleScale, { v -> a.settings.update { putFloat("subtitleScale", v) } }, valueRange = 0.85f..1.4f, colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc))
            ToggleRow("Reduce motion", "Camera aur animation thoda dheema.", s.reduceMotion) { v -> a.settings.update { putBoolean("reduceMotion", v) } }
            Text("Theme", color = Lo, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                Themes.all.forEach { t -> KChip(t.name, s.themeId == t.id, Modifier.padding(end = 8.dp)) { a.settings.update { putString("themeId", t.id) } } }
            }
            Row(Modifier.padding(vertical = 6.dp)) {
                Themes.accents.forEach { c -> Box(Modifier.padding(end = 10.dp).size(30.dp).clip(CircleShape).background(Color(c)).clickable { a.settings.update { putLong("accent", c) } }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Text("Storage", color = Hi, fontWeight = FontWeight.Bold)
            Text("Downloaded game packs aur temporary files hatao. Bundled launch games offline chalte rahenge.", color = Lo, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Clear cache", icon = Icons.Rounded.CleaningServices) {
                    val n = a.games.clearDownloads() + a.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    a.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    cleared = "${n / 1024} KB saaf"
                }
                GhostButton("Reset progress", icon = Icons.Rounded.RestartAlt) { confirmReset = true }
            }
            cleared?.let { Text(it, color = acc, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Text("Safety", color = Hi, fontWeight = FontWeight.Bold)
            Text("Reports queued on this device: ${social.reports.size}", color = Lo, fontSize = 13.sp)
            if (social.blockedCreators.isEmpty()) Text("Koi creator blocked nahi.", color = Lo, fontSize = 13.sp)
            social.blockedCreators.forEach { c -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Blocked: $c", color = Hi, modifier = Modifier.weight(1f)); TextButton({ a.social.unblockCreator(c) }) { Text("Unblock", color = acc) } } }
            social.hiddenGames.forEach { g -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Reported (hidden): $g", color = Hi, modifier = Modifier.weight(1f)); TextButton({ a.social.unhide(g) }) { Text("Show", color = acc) } } }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Text("About", color = Hi, fontWeight = FontWeight.Bold)
            Text("Kissa ${BuildConfig.VERSION_NAME} · play-only, offline. Koi account ya API key nahi chahiye. Gameplay mein live AI nahi (spec V1).", color = Lo, fontSize = 12.sp)
            Text("Experimental: v2 live AI chat (apni Gemini key ke saath) is build mein band hai — V4 roadmap.", color = Lo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, containerColor = Color(0xFF1C1828), titleContentColor = Hi, textContentColor = Lo,
        title = { Text("Saari progress hatayein?") }, text = { Text("Saves, checkpoints aur mili hui endings delete ho jayengi.") },
        confirmButton = { TextButton({ a.saves.clear(); confirmReset = false; cleared = "Progress reset" }) { Text("Delete", color = Color(0xFFFF7A86)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel", color = Hi) } })
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Hi, fontSize = 15.sp); Text(sub, color = Lo, fontSize = 12.sp) }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF16101F), checkedTrackColor = LocalAccent.current, uncheckedThumbColor = Lo, uncheckedTrackColor = Color(0x33FFFFFF)))
    }
}

// ---------------------------------------------------------------- onboarding (no key, no account)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val a = LocalContext.current.applicationContext as KissaApp
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.Center) {
            RigView("female", Look(skin = 1, hair = "long_wavy", hairColor = "black", outfit = "college_blazer", outfitColor = "navy", accessories = listOf("earrings")), Modifier.weight(1f).fillMaxHeight(), expression = "smug", move = "hands_on_hips")
            RigView("male", Look(skin = 2, hair = "curly", hairColor = "black", outfit = "hoodie", outfitColor = "maroon", accessories = listOf("glasses_round")), Modifier.weight(1f).fillMaxHeight(), expression = "happy", move = "wave")
        }
        Spacer(Modifier.height(18.dp))
        Text("Kahani mein hero tum ho.", color = Hi, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Apna naam aur look chuno, chat karo, faisle lo. Tumhare stats aur rank tay karenge ki kaunsi ending milegi — phir replay karke doosri ending dhoondo.",
            color = Lo, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text("Offline chalta hai. Koi account, koi API key nahi.", color = LocalAccent.current, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        AccentButton("Shuru karein", Modifier.fillMaxWidth(), icon = Icons.Rounded.ArrowForward) { a.settings.update { putBoolean("onboarded", true) }; onDone() }
    }
}
