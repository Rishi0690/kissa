package com.kissa.app.ui.play

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kissa.app.KissaApp
import com.kissa.app.data.GameEntry
import com.kissa.app.game.*
import com.kissa.app.play.*
import com.kissa.app.play.StageRenderer.drawStage
import com.kissa.app.rig.Actor
import com.kissa.app.ui.common.RigView
import com.kissa.app.ui.common.rememberRigRenderer
import com.kissa.app.ui.theme.LocalAccent

/** Where play starts: a fresh run, the autosave, or a reached checkpoint (index into the save's checkpoint list). */
sealed class StartPoint { object New : StartPoint(); object Resume : StartPoint(); data class Checkpoint(val index: Int) : StartPoint() }

private val Ink = Color(0xFF0E0B16)
private val TextHi = Color(0xFFF7F4FF)
private val TextLo = Color(0xFFC9C2DA)

@Composable
fun PlayerScreen(gameId: String, start: StartPoint, onExit: () -> Unit, onReplay: (StartPoint) -> Unit) {
    val app = LocalContext.current.applicationContext as KissaApp
    val entries by app.games.entries.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == gameId }
    if (entry == null) { Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val controller = remember(entry, start) {
        val game = entry.game
        val save = app.saves.load(game.id) ?: app.saves.startNew(game.id, game.player.defaultName, "male", Look())
        val snapshot = when (start) {
            StartPoint.New -> null
            StartPoint.Resume -> save.autosave
            is StartPoint.Checkpoint -> save.checkpoints.getOrNull(start.index)?.state
        }
        if (start !is StartPoint.Resume) app.saves.startNew(game.id, save.playerName, save.playerRig, save.look)
        val session = if (snapshot != null) GameSession.resume(game, snapshot.copy(playerName = save.playerName)) else GameSession(game, GameState.initial(game, save.playerName))
        session.playerRig = save.playerRig
        session.onSceneEntered = { sc, snap -> app.saves.onSceneEntered(game.id, sc, snap) }
        session.onEnding = { e, st -> app.saves.onEnding(game.id, e, st) }
        app.voice.volume = settings.volume
        GameController(entry, session, app.voice, { app.settings.current.characterVoicesOn }, scope, Actor("player", save.playerRig, save.look))
    }
    DisposableEffect(controller) { controller.start(); onDispose { controller.stop() } }
    val ui by controller.ui.collectAsStateWithLifecycle()
    var showStatus by remember { mutableStateOf(false) }
    val renderer = rememberRigRenderer()
    val bgCache = remember { HashMap<String, BgLayers?>() }
    val bgMeta = remember { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(app.assets.open("bg/backgrounds.json").bufferedReader().readText()) as kotlinx.serialization.json.JsonObject }.getOrNull() }
    fun bg(id: String): BgLayers? = bgCache.getOrPut(id) {
        fun img(n: String) = runCatching { app.assets.open("bg/${id}_$n.webp").use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        val top = (bgMeta?.get(id) as? kotlinx.serialization.json.JsonObject)?.get("mid_top")?.toString()?.toFloatOrNull() ?: 400f
        BgLayers(img("back"), img("mid"), img("front"), top)
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        var last = 0L
        while (true) withFrameNanos { n ->
            val dt = if (last == 0L) 0.016f else ((n - last) / 1e9f).coerceAtMost(0.05f); last = n
            val st = controller.stage
            st.update(if (settings.reduceMotion) dt * 0.8f else dt)
            val amp = app.voice.amplitude()
            st.actors.values.forEach { it.mouth = if (it.id == st.speaking) amp else 0f }
            st.player?.mouth = 0f
            tick++
        }
    }
    val acc = LocalAccent.current
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (ui.sceneType == SceneType.CHAT) {
            ChatSurface(controller, ui, entry, onBack = onExit, onStatus = { showStatus = true })
        } else {
            Canvas(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { controller.tapSkip() }) {
                tick
                drawStage(controller.stage, bg(controller.stage.background), renderer)
            }
            TopHud(ui, onBack = onExit, onStatus = { showStatus = true })
            AnimatedVisibility(ui.showTitle && ui.sceneTitle != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                Text(ui.sceneTitle ?: "", color = TextHi, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 12.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()) {
                if (ui.reply == null && ui.decision == null) ui.caption?.let { CaptionBar(it, settings.subtitleScale) }
                ui.reply?.let { r -> TypingBar(r, controller, inScene = true) }
            }
            ui.decision?.let { DecisionOverlay(it, controller) }
        }
        ToastStack(ui, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
        if (ui.basicVoiceUsed) Text("Basic voice (fallback)", color = TextHi, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 60.dp, end = 12.dp).background(Color(0xAA6A3A00), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
        if (showStatus) StatusSheet(controller.game, controller.session, onClose = { showStatus = false })
        if (ui.ending != null || ui.endingNone) EndingOverlay(controller, app, onExit, onReplay)
    }
}

@Composable
private fun TopHud(ui: PlayUi, onBack: () -> Unit, onStatus: () -> Unit) {
    val acc = LocalAccent.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, Modifier.size(40.dp).background(Color(0x66000000), CircleShape)) { Icon(Icons.Rounded.Close, "Exit", tint = TextHi) }
        Spacer(Modifier.width(8.dp))
        RankBadge(ui.rankName, ui.rankUp != null)
        Spacer(Modifier.weight(1f))
        ui.objective?.let { o ->
            Row(Modifier.widthIn(max = 170.dp).clip(RoundedCornerShape(50)).background(Color(0x88000000)).clickable(onClick = onStatus).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = acc, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                Text(o.text, color = TextHi, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
        }
        IconButton(onStatus, Modifier.size(40.dp).background(Color(0x66000000), CircleShape)) { Icon(Icons.Rounded.BarChart, "Status", tint = TextHi) }
    }
}

@Composable
fun RankBadge(name: String, glow: Boolean) {
    val acc = LocalAccent.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xCC1A1428), Color(0x991A1428))))
        .border(1.dp, if (glow) acc else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.MilitaryTech, null, tint = acc, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
        Text("PLAYER ", color = TextLo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(name.uppercase(), color = TextHi, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CaptionBar(c: Caption, scale: Float) {
    val acc = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xD90E0B16)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        if (c.speaker != null) {
            Text(c.speaker, color = if (c.player) Color(0xFF8FE3C0) else acc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
        }
        Text(c.text, color = if (c.narration) TextLo else TextHi, fontSize = (17f * scale).sp, lineHeight = (25f * scale).sp,
            fontStyle = if (c.narration) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal)
    }
}

private fun styleTag(style: String) = when (style) { "confident" -> "😎 confident"; "timid" -> "😬 timid"; "rude" -> "😤 rude"; "funny" -> "😏 funny"; "kind" -> "🤝 kind"; "cold" -> "🧊 cold"; else -> "" }

@Composable
private fun DecisionOverlay(d: Beat.Decision, c: GameController) {
    val acc = LocalAccent.current
    Box(Modifier.fillMaxSize().background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            d.prompt?.let { Text(it, color = TextHi, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp)) }
            d.choices.forEachIndexed { n, (idx, ch) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF)))).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .clickable { c.choose(idx) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(acc, CircleShape), contentAlignment = Alignment.Center) { Text("${n + 1}", color = Ink, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.session.substitute(ch.text), color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (styleTag(ch.style).isNotEmpty()) Text(styleTag(ch.style), color = TextLo, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBar(r: Step.OpenReply, c: GameController, inScene: Boolean) {
    var text by remember(r) { mutableStateOf("") }
    val acc = LocalAccent.current
    val stt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = it }
    }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().background(if (inScene) Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0E0B16))) else Brush.verticalGradient(listOf(Color(0xFF15121F), Color(0xFF15121F)))).padding(12.dp)) {
        if (r.prompt.isNotBlank()) Text(r.prompt, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            r.presets.forEach { p ->
                val t = c.session.substitute(p)
                Text(t, color = TextHi, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))
                    .border(1.dp, acc.copy(alpha = 0.6f), RoundedCornerShape(50)).clickable { c.reply(t) }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(r.placeholder ?: "Apne shabdon mein likho…", color = TextLo) }, singleLine = true,
                shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { c.reply(text) }),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextHi, unfocusedTextColor = TextHi, focusedContainerColor = Color(0xFF221D30), unfocusedContainerColor = Color(0xFF221D30), focusedBorderColor = acc, unfocusedBorderColor = Color(0x44FFFFFF), cursorColor = acc))
            Spacer(Modifier.width(6.dp))
            IconButton({
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo…")
                runCatching { stt.launch(i) }.onFailure { android.widget.Toast.makeText(ctx, "Is phone par voice input uplabdh nahi — type karo", android.widget.Toast.LENGTH_SHORT).show() }
            }, Modifier.size(46.dp).background(Color(0xFF2A2438), CircleShape)) { Icon(Icons.Rounded.Mic, "Mic", tint = TextHi) }
            Spacer(Modifier.width(6.dp))
            IconButton({ c.reply(text) }, Modifier.size(46.dp).background(acc, CircleShape)) { Icon(Icons.Rounded.Send, "Send", tint = Ink) }
        }
    }
}

@Composable
private fun ToastStack(ui: PlayUi, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ui.rankUp?.let { Text("RANK UP → ${it.uppercase()}", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            modifier = Modifier.padding(4.dp).background(Brush.horizontalGradient(listOf(Color(0xFFFFD36B), Color(0xFFFF9A5C))), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) }
        ui.toasts.forEach { t ->
            val col = when (t.positive) { true -> Color(0xFF1F6B4A); false -> Color(0xFF7A2333); null -> Color(0xFF3A3450) }
            Text(t.text, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(3.dp).background(col.copy(alpha = 0.92f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
}

// ---------------------------------------------------------------- chat surface
@Composable
private fun ChatSurface(c: GameController, ui: PlayUi, entry: GameEntry, onBack: () -> Unit, onStatus: () -> Unit) {
    val acc = LocalAccent.current
    val partner = c.game.character(ui.chatWith)
    val list = rememberLazyListState()
    LaunchedEffect(ui.chat.size, ui.typing) { if (ui.chat.isNotEmpty()) list.animateScrollToItem(ui.chat.size + (if (ui.typing) 1 else 0)) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14111D), Color(0xFF0D0B14))))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1B1726)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = TextHi) }
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF2E2840))) { partner?.let { RigView(it.rig, it.look, Modifier.fillMaxSize(), portrait = true, animate = false) } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.chatHeader ?: partner?.name ?: "", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(when { ui.blocked -> "Blocked"; ui.typing -> "typing…"; else -> "online" }, color = if (ui.typing) Color(0xFF8FE3C0) else TextLo, fontSize = 12.sp)
            }
            RankBadge(ui.rankName, ui.rankUp != null)
            IconButton(onStatus) { Icon(Icons.Rounded.BarChart, "Status", tint = TextHi) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, contentPadding = PaddingValues(vertical = 12.dp)) {
            item { ui.caption?.takeIf { it.narration }?.let { Text(it.text, color = TextLo, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)) } }
            items(ui.chat, key = { it.id }) { m -> Bubble(m, ui.chat.lastOrNull { it.fromPlayer && !it.system } == m) }
            if (ui.typing) item { TypingDots() }
        }
        ui.decision?.let { d ->
            Column(Modifier.fillMaxWidth().background(Color(0xFF15121F)).navigationBarsPadding().padding(10.dp)) {
                d.prompt?.let { Text(it, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(4.dp)) }
                d.choices.forEach { (idx, ch) ->
                    val icon = when (ch.kind) { "ignore" -> Icons.Rounded.VisibilityOff; "block" -> Icons.Rounded.Block; "delay" -> Icons.Rounded.Schedule; else -> Icons.Rounded.Reply }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (ch.kind == "reply") Color(0xFF262036) else Color(0xFF1E1A2A))
                        .border(1.dp, if (ch.kind == "block") Color(0xFFB0414F) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).clickable { c.choose(idx) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = if (ch.kind == "block") Color(0xFFFF7A86) else acc, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                        Text(c.session.substitute(ch.text), color = TextHi, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (styleTag(ch.style).isNotEmpty()) Text(styleTag(ch.style).substringBefore(' '), fontSize = 14.sp)
                    }
                }
            }
        }
        ui.reply?.let { Box(Modifier.navigationBarsPadding().imePadding()) { TypingBar(it, c, inScene = false) } }
        if (ui.decision == null && ui.reply == null) Row(Modifier.fillMaxWidth().background(Color(0xFF15121F)).navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Message…", color = Color(0x66FFFFFF), modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF221D30)).padding(horizontal = 16.dp, vertical = 12.dp))
            Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Mic, null, tint = Color(0x66FFFFFF))
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg, lastPlayer: Boolean) {
    val acc = LocalAccent.current
    if (m.system) { Text(m.text, color = TextLo, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)); return }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (m.fromPlayer) Alignment.End else Alignment.Start) {
        Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.fromPlayer) 18.dp else 4.dp, bottomEnd = if (m.fromPlayer) 4.dp else 18.dp))
            .background(if (m.fromPlayer) Color(0xFF6B3550) else Color(0xFF2A2536)).padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(m.text, color = TextHi, fontSize = 16.sp, lineHeight = 22.sp)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                m.time?.let { Text(it, color = TextLo, fontSize = 10.sp) }
                if (m.fromPlayer) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.DoneAll, null, tint = if (m.seen) Color(0xFF7FD3FF) else TextLo, modifier = Modifier.size(14.dp)) }
            }
        }
        if (m.fromPlayer && lastPlayer && m.seen) Text("Seen", color = TextLo, fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp, top = 2.dp))
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransitionCompat()
    Row(Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2536)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        repeat(3) { i -> Box(Modifier.padding(horizontal = 2.dp).size(8.dp).background(TextLo.copy(alpha = 0.4f + 0.6f * (((t + i * 0.33f) % 1f))), CircleShape)) }
    }
}

@Composable
private fun rememberInfiniteTransitionCompat(): Float {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    val v by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900)), label = "d")
    return v
}

// ---------------------------------------------------------------- status + ending
@Composable
fun StatusSheet(game: Game, s: GameSession, onClose: () -> Unit) {
    val acc = LocalAccent.current
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF17131F)).clickable(enabled = false) {}
            .navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Status", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(12.dp))
            val score = s.state.rankScore; val next = game.rank.next(score); val cur = game.rank.levels.getOrNull(game.rank.levelIndex(score))
            Row(verticalAlignment = Alignment.CenterVertically) { RankBadge(s.rankName, false); Spacer(Modifier.width(10.dp)); Text("Score $score" + (next?.let { " / ${it.at} → ${it.name}" } ?: " · top rank"), color = TextLo, fontSize = 13.sp) }
            if (next != null && cur != null) LinearProgressIndicator(progress = { ((score - cur.at).toFloat() / (next.at - cur.at)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = acc, trackColor = Color(0x33FFFFFF))
            Spacer(Modifier.height(10.dp))
            game.stats.values.filter { !it.hidden }.forEach { d ->
                val v = s.state.stats[d.name] ?: d.start
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, color = TextHi, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                    LinearProgressIndicator(progress = { ((v - d.min).toFloat() / (d.max - d.min)).coerceIn(0f, 1f) }, Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (v >= 0) Color(0xFF6BD69A) else Color(0xFFE0606E), trackColor = Color(0x22FFFFFF))
                    Text("  $v", color = TextHi, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Objectives", color = TextHi, fontWeight = FontWeight.Bold)
            val shown = game.objectives.filter { (s.state.objectives[it.id] ?: ObjStatus.NONE) != ObjStatus.NONE }
            if (shown.isEmpty()) Text("Abhi koi objective nahi.", color = TextLo, fontSize = 13.sp)
            shown.forEach { o ->
                val st = s.state.objectives[o.id]!!
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(when (st) { ObjStatus.DONE -> Icons.Rounded.CheckCircle; ObjStatus.FAILED -> Icons.Rounded.Cancel; ObjStatus.DECLINED -> Icons.Rounded.DoNotDisturb; else -> Icons.Rounded.Flag }, null,
                        tint = when (st) { ObjStatus.DONE -> Color(0xFF6BD69A); ObjStatus.FAILED -> Color(0xFFE0606E); ObjStatus.ACTIVE -> acc; else -> TextLo }, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(o.text, color = TextHi, fontSize = 14.sp)
                        Text(listOfNotNull(o.place, o.deadline, st.name.lowercase()).joinToString(" · "), color = TextLo, fontSize = 12.sp)
                    }
                }
            }
            if (s.state.items.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text("Items", color = TextHi, fontWeight = FontWeight.Bold); s.state.items.forEach { Text("• ${game.items[it] ?: it}", color = TextLo, fontSize = 13.sp) } }
            Spacer(Modifier.height(16.dp))
            Text("Band karo", color = acc, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClose).padding(8.dp))
        }
    }
}

@Composable
private fun EndingOverlay(c: GameController, app: KissaApp, onExit: () -> Unit, onReplay: (StartPoint) -> Unit) {
    val acc = LocalAccent.current
    val save = remember { app.saves.load(c.game.id) }
    val ending = c.ui.value.ending
    var pickCheckpoint by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xE60E0B16), Color(0xFA0E0B16))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Text("ENDING", color = acc, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 4.sp)
            Text(ending?.title ?: "Kahani khatam", color = TextHi, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            ending?.kind?.let { Text(it.uppercase(), color = TextLo, fontSize = 11.sp, letterSpacing = 2.sp) }
            Spacer(Modifier.height(16.dp))
            RankBadge(c.session.rankName, true)
            Spacer(Modifier.height(16.dp))
            c.game.stats.values.filter { !it.hidden }.forEach { d ->
                val v = c.session.state.stats[d.name] ?: d.start
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, color = TextHi, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                    LinearProgressIndicator(progress = { ((v - d.min).toFloat() / (d.max - d.min)).coerceIn(0f, 1f) }, Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)), color = acc, trackColor = Color(0x22FFFFFF))
                    Text("  $v", color = TextHi, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Baaki endings (${(save?.endingsReached?.size ?: 1)}/${c.game.endings.size} mili)", color = TextHi, fontWeight = FontWeight.Bold)
            c.game.endings.forEach { e ->
                val got = e.id in (save?.endingsReached ?: emptySet()) || e.id == ending?.id
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (got) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = if (got) Color(0xFF6BD69A) else TextLo, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column { Text(if (got) e.title else "???", color = TextHi, fontWeight = FontWeight.SemiBold); Text(e.teaser, color = TextLo, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
            val cps = save?.checkpoints ?: emptyList()
            Button({ pickCheckpoint = !pickCheckpoint }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Ink), enabled = cps.isNotEmpty()) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(8.dp)); Text("Checkpoint se replay karo") }
            if (pickCheckpoint) cps.forEachIndexed { i, cp ->
                Text("↺ ${cp.label}", color = TextHi, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).clickable { onReplay(StartPoint.Checkpoint(i)) }.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ onReplay(StartPoint.New) }, Modifier.fillMaxWidth()) { Text("Shuru se dobara khelo", color = TextHi) }
            TextButton(onExit) { Text("Game page par wapas", color = TextLo) }
        }
    }
}
