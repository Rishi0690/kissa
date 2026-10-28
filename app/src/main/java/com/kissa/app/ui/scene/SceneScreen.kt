package com.kissa.app.ui.scene

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissa.app.data.MessageEntity
import com.kissa.app.model.Lang
import com.kissa.app.model.Role
import com.kissa.app.scene.SceneStage
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.DisplayFont
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import com.kissa.app.voice.SpeechDirector
import com.kissa.app.voice.VoiceInputState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(
    storyId: String,
    replayCheckpoint: Long?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val vm: SceneViewModel = viewModel(key = "scene_$storyId", factory = SceneViewModel.Factory(storyId, replayCheckpoint))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ready by vm.engineReady.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val speaking by vm.speech.speaking.collectAsStateWithLifecycle()
    val voiceState by vm.voice.state.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val pal = LocalPalette.current

    var resumed by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { resumed = false; vm.onPause() }
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); vm.onPause() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.ui.value.let { /* explained below */ }
        if (!granted) android.widget.Toast.makeText(ctx, "Microphone permission is needed for voice. You can keep typing instead.", android.widget.Toast.LENGTH_LONG).show()
    }
    fun mic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    // continuous mode needs permission up-front
    LaunchedEffect(ui.continuousVoice) {
        if (ui.continuousVoice && ui.phase == Phase.IDLE && voiceState is VoiceInputState.Idle) mic()
    }

    var input by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(ui.toast) {
        ui.toast?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show(); vm.consumeToast() }
    }
    BackHandler(enabled = showLog || showMenu) { showLog = false; showMenu = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val engine = vm.engine
        if (ready && engine != null) {
            SceneStage(engine, vm.speech.speaking, Modifier.fillMaxSize(), paused = !resumed)
        }

        // ---------- top bar
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", bg = Color.Black.copy(alpha = 0.35f)) { onBack() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ui.story?.title ?: "", style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFont), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val ch = ui.story?.chapters?.getOrNull(ui.chapter)
                    Text(if (ui.ended) "Epilogue" else "Chapter ${ui.chapter + 1}${ch?.let { " · ${it.title}" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
                IconCircle(if (ui.continuousVoice) Icons.Rounded.RecordVoiceOver else Icons.Rounded.VoiceOverOff, "Voice conversation mode",
                    bg = if (ui.continuousVoice) accent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)) { vm.toggleContinuous() }
                Spacer(Modifier.width(8.dp))
                IconCircle(Icons.Rounded.MoreVert, "Scene menu", bg = Color.Black.copy(alpha = 0.35f)) { showMenu = true }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Meter("Mood", ui.mood, Color(0xFFFFC46B), Modifier.weight(1f))
                Spacer(Modifier.width(14.dp))
                Meter("Bond with ${ui.story?.character?.name?.substringBefore(' ') ?: ""}", ui.relationship, accent, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatusPill(ui, speaking?.speakerId, voiceState, Modifier.align(Alignment.CenterHorizontally))
            AnimatedVisibility(ui.lastNote != null, enter = fadeIn() + slideInVertically(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("✦ ${ui.story?.character?.name?.substringBefore(' ')} will remember that", Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFE3A0))
            }
        }

        // ---------- chapter banner
        AnimatedVisibility(ui.banner != null, enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.9f), exit = fadeOut(tween(600)), modifier = Modifier.align(Alignment.Center)) {
            Column(Modifier.padding(32.dp).glass(alpha = 0.10f).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 26.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", color = accent)
                Text(ui.banner ?: "", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
            }
        }

        // ---------- bottom stack
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            ui.error?.let { err ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xCC2A1520)).border(1.dp, Color(0x55FF7A90), RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
                    if (ui.errorNeedsKey) TextButton(onClick = onOpenSettings) { Text("Settings") }
                    if (ui.canRetry) TextButton(onClick = { vm.retry() }) { Text("Retry") }
                    IconButton(onClick = { vm.dismissError() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White) }
                }
            }
            DialoguePanel(ui, speaking?.let { if (it.text == ui.line?.text) it.highlightEnd to it.hasWordEvents else null }, settings.subtitles, settings.subtitleScale,
                onReplay = { vm.replayLine() }, onStop = { vm.stopAudio(); vm.stopReplay() }, onExpand = { showLog = true },
                onReport = { ui.line?.takeIf { !it.isPlayer }?.let { reportTarget = it.messageId } }, isPlaying = speaking != null || ui.phase == Phase.REPLAY)
            Spacer(Modifier.height(8.dp))
            if (settings.showSuggestions && ui.suggestions.isNotEmpty() && ui.phase == Phase.IDLE && voiceState !is VoiceInputState.Listening) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.suggestions.forEach { s ->
                        Text(s, Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .clickable { input = s }.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            when (val v = voiceState) {
                is VoiceInputState.Listening, VoiceInputState.Processing -> RecordingBar(v, onCancel = { vm.voice.cancel() }, onDone = { vm.voice.finish() })
                is VoiceInputState.Result -> if (!ui.continuousVoice) VoiceReview(v, onRetry = { vm.voice.consume(); mic() }, onSend = { text -> vm.voice.consume(); vm.send(text, if (text == v.text) v.audioPath else null) },
                    onDiscard = { vm.voice.consume() }, onPlay = { vm.voice.consume(); vm.send(v.text, v.audioPath) })
                is VoiceInputState.Error -> Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).glass().background(Color.Black.copy(alpha = 0.4f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MicOff, null, tint = accent); Spacer(Modifier.width(8.dp))
                    Text(v.message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    if (v.canRetry) TextButton(onClick = { vm.voice.consume(); mic() }) { Text("Try again") }
                    IconButton(onClick = { vm.voice.consume() }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                }
                else -> {}
            }
            if (voiceState !is VoiceInputState.Listening && voiceState !is VoiceInputState.Processing) {
                InputBar(input, { input = it },
                    enabled = ui.phase != Phase.THINKING && ui.phase != Phase.LOADING && ui.phase != Phase.REPLAY && ui.phase != Phase.INTRO && ui.phase != Phase.PLAYER_SPEAKING,
                    onSend = { if (input.isNotBlank()) { vm.send(input); input = "" } }, onMic = { mic() })
            }
        }

        // ---------- ending card
        AnimatedVisibility(ui.ending != null, enter = fadeIn(tween(700)), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp).glass(alpha = 0.09f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ENDING REACHED", style = MaterialTheme.typography.labelMedium, color = accent)
                    Spacer(Modifier.height(8.dp))
                    Text(ui.ending ?: "", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Bond ${ui.relationship}/100 · ${ui.turnCount} turns. Every choice you made shaped this ending — try another branch from a checkpoint.",
                        style = MaterialTheme.typography.bodyMedium, color = pal.textDim, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    AccentButton("Continue the epilogue", Modifier.fillMaxWidth()) { vm.dismissEnding() }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Branch from a checkpoint", Modifier.fillMaxWidth(), Icons.Rounded.History) { vm.dismissEnding(); onOpenHistory(storyId) }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Start over", Modifier.fillMaxWidth(), Icons.Rounded.Replay) { vm.restart() }
                }
            }
        }

        if (showLog) TranscriptSheet(messages, ui.playerName, onDismiss = { showLog = false }, onReport = { reportTarget = it })

        if (showMenu) ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = pal.bg2) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Scene", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.Rounded.BookmarkAdd, "Save checkpoint") { vm.saveCheckpoint(); showMenu = false }
                MenuRow(Icons.Rounded.History, "History & replay") { showMenu = false; onOpenHistory(storyId) }
                MenuRow(Icons.Rounded.PlayCircle, "Replay this chapter's dialogue") { showMenu = false; vm.startReplay(null) }
                MenuRow(Icons.Rounded.Forum, "Full transcript") { showMenu = false; showLog = true }
                MenuRow(Icons.Rounded.Face, "About ${ui.story?.character?.name ?: "character"}") { showMenu = false; onOpenCharacter(storyId) }
                Spacer(Modifier.height(8.dp))
                Text("Dialogue language", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lang.entries.forEach { l -> KChip(l.label, ui.language == l) { vm.setLanguage(l) } }
                }
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.Rounded.Flag, "Report the last reply") { showMenu = false; ui.line?.takeIf { !it.isPlayer }?.let { reportTarget = it.messageId } }
                MenuRow(Icons.Rounded.Settings, "Voice & app settings") { showMenu = false; onOpenSettings() }
                MenuRow(Icons.Rounded.RestartAlt, "Restart story (erases this playthrough)") { showMenu = false; vm.restart() }
            }
        }

        reportTarget?.let { mid ->
            var reason by remember { mutableStateOf("Inappropriate") }
            AlertDialog(
                onDismissRequest = { reportTarget = null },
                title = { Text("Report this reply") },
                text = {
                    Column {
                        Text("Reported lines are hidden and removed from the story's memory. Reports are stored on this device.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        listOf("Inappropriate", "Offensive", "Out of character", "Other").forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(reason == r, { reason = r }); Text(r)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.report(mid, reason); reportTarget = null }) { Text("Report") } },
                dismissButton = { TextButton(onClick = { reportTarget = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current); Spacer(Modifier.width(14.dp)); Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusPill(ui: SceneUi, speaker: String?, voice: VoiceInputState, modifier: Modifier) {
    val name = ui.story?.character?.name?.substringBefore(' ') ?: ""
    val (text, color) = when {
        voice is VoiceInputState.Listening -> "$name is listening…" to Color(0xFFFF6B6B)
        voice is VoiceInputState.Processing -> "Understanding you…" to Color(0xFFFFC46B)
        ui.phase == Phase.THINKING -> "$name is thinking…" to Color(0xFFA58BFF)
        speaker != null && speaker != "player" -> "$name is speaking" to LocalAccent.current
        speaker == "player" -> "You're speaking · $name is listening" to Color(0xFF6BF0B2)
        ui.phase == Phase.REPLAY -> "Replay" to Color(0xFF5FE0FF)
        ui.phase == Phase.INTRO -> "Entering the scene…" to Color.White
        else -> null to Color.White
    }
    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = modifier, label = "status") { t ->
        if (t != null) {
            val inf = rememberInfiniteTransition(label = "dot")
            val a by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
                Text(t, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun DialoguePanel(
    ui: SceneUi, progress: Pair<Int, Boolean>?, subtitles: Boolean, scale: Float,
    onReplay: () -> Unit, onStop: () -> Unit, onExpand: () -> Unit, onReport: () -> Unit, isPlaying: Boolean,
) {
    val line = ui.line ?: return
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xB0120E1E), Color(0xD80C0A14))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(22.dp))
            .clickable(onClick = onExpand)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.speaker, Modifier.clip(RoundedCornerShape(50)).background((if (line.isPlayer) Color(0xFF6BF0B2) else accent).copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 3.dp),
                color = if (line.isPlayer) Color(0xFF9CF7CF) else accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (isPlaying) IconButton(onClick = onStop, Modifier.size(34.dp)) { Icon(Icons.Rounded.StopCircle, "Stop audio", tint = Color.White) }
            else IconButton(onClick = onReplay, Modifier.size(34.dp)) { Icon(Icons.Rounded.Replay, "Replay line", tint = Color.White.copy(alpha = 0.85f)) }
            if (!line.isPlayer) IconButton(onClick = onReport, Modifier.size(34.dp)) { Icon(Icons.Rounded.OutlinedFlag, "Report", tint = Color.White.copy(alpha = 0.5f)) }
            IconButton(onClick = onExpand, Modifier.size(34.dp)) { Icon(Icons.Rounded.UnfoldMore, "Open transcript", tint = Color.White.copy(alpha = 0.7f)) }
        }
        line.action?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.6f)) }
        Spacer(Modifier.height(4.dp))
        if (subtitles) {
            val text = buildAnnotatedString {
                val end = progress?.takeIf { it.second }?.first?.coerceIn(0, line.text.length) ?: line.text.length
                withStyle(SpanStyle(color = Color.White)) { append(line.text.substring(0, end)) }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f))) { append(line.text.substring(end)) }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * scale).sp, lineHeight = (24 * scale).sp), maxLines = 5, overflow = TextOverflow.Ellipsis)
        } else {
            Text(if (isPlaying) "🔊 speaking… (subtitles off)" else "Subtitles are off · tap to read", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit, onMic: () -> Unit) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, Modifier.weight(1f).padding(vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                decorationBox = { inner -> Box { if (value.isEmpty()) Text("Say something as yourself…", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodyLarge); inner() } },
            )
            IconButton(onClick = onMic, enabled = enabled) { Icon(Icons.Rounded.Mic, "Record voice", tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)) }
        }
        Spacer(Modifier.width(8.dp))
        val canSend = enabled && value.isNotBlank()
        Box(Modifier.size(52.dp).clip(CircleShape).background(if (canSend) Brush.linearGradient(listOf(accent, lerpColor(accent, Color.White, 0.3f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))))
            .clickable(enabled = canSend, onClick = onSend), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (canSend) Color(0xFF16101F) else Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun RecordingBar(v: VoiceInputState, onCancel: () -> Unit, onDone: () -> Unit) {
    val listening = v as? VoiceInputState.Listening
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listening?.startedAt) { while (listening != null) { elapsed = System.currentTimeMillis() - listening.startedAt; delay(250) } }
    val inf = rememberInfiniteTransition(label = "rec")
    val pulse by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xE0140E1C)).border(1.dp, Color(0x66FF6B6B), RoundedCornerShape(24.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).scale(pulse).clip(CircleShape).background(Color(0xFFFF4D5E)))
            Spacer(Modifier.width(8.dp))
            Text(if (listening == null) "Processing…" else if (listening.keepingAudio) "Recording your voice · ${elapsed / 1000}s" else "Listening · ${elapsed / 1000}s",
                style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
            val lvl = listening?.level ?: 0f
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(7) { i ->
                    val h = (6 + 26 * (lvl * (0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin((elapsed / 120.0 + i).toFloat()))))).dp
                    Box(Modifier.width(4.dp).height(h).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFF8A8A)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(listening?.partial?.ifBlank { "Speak now…" } ?: "…", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", Modifier.weight(1f), Icons.Rounded.Close, onCancel)
            AccentButton("Done", Modifier.weight(1f), Icons.Rounded.Check, enabled = listening != null, onClick = onDone)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun VoiceReview(v: VoiceInputState.Result, onRetry: () -> Unit, onSend: (String) -> Unit, onDiscard: () -> Unit, onPlay: () -> Unit) {
    var text by remember(v) { mutableStateOf(v.text) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xE0140E1C)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(14.dp)) {
        Text(if (v.audioPath != null) "Your voice message · your real voice will be played" else "Your voice message", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.textDim)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge, maxLines = 4)
        Text("Edit the words if needed (editing switches your line to the synthetic voice).", style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.textDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.Delete, "Discard", size = 44.dp) { onDiscard() }
            GhostButton("Re-record", Modifier.weight(1f), Icons.Rounded.Mic, onRetry)
            AccentButton("Send", Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, enabled = text.isNotBlank()) { onSend(text) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptSheet(messages: List<MessageEntity>, playerName: String, onDismiss: () -> Unit, onReport: (Long) -> Unit) {
    val pal = LocalPalette.current
    val visible = messages.filter { !it.hidden }
    val state = rememberLazyListState()
    LaunchedEffect(visible.size) { if (visible.isNotEmpty()) state.scrollToItem(visible.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = pal.bg2, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Transcript", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        if (visible.isEmpty()) EmptyState(Icons.Rounded.Forum, "Nothing said yet", "The conversation will appear here.")
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp)) {
            items(visible, key = { it.id }) { m ->
                val mine = m.role == Role.PLAYER.name
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) playerName else m.speaker, style = MaterialTheme.typography.labelSmall, color = pal.textDim)
                    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(if (mine) LocalAccent.current.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)).padding(12.dp)) {
                        m.action?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = pal.textDim) }
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                        if (!mine) Text("Report", Modifier.align(Alignment.End).clickable { onReport(m.id) }.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = pal.textDim)
                        if (mine && m.audioPath != null) Text("🎙 your voice", style = MaterialTheme.typography.labelSmall, color = pal.textDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Suppress("unused") private fun devanagari(t: String) = SpeechDirector.hasDevanagari(t)
