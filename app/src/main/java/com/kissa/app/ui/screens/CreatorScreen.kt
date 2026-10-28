package com.kissa.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kissa.app.KissaApp
import com.kissa.app.Routes
import com.kissa.app.ai.*
import com.kissa.app.model.*
import com.kissa.app.scene.backgroundRes
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import com.kissa.app.voice.Utterance
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID

private val SKINS = listOf(0xFFF6D7C0, 0xFFF0C6A2, 0xFFE2B48C, 0xFFD39B72, 0xFFB97A55, 0xFF8D5A3B)
private val HAIRS = listOf(0xFF151217, 0xFF2B1A1E, 0xFF5A2E1E, 0xFF8A5A2E, 0xFFD8B26A, 0xFFB9E4F2, 0xFF6B1E3A, 0xFF5B3FD1)
private val EYES = listOf(0xFF2E1E14, 0xFF6B3F25, 0xFF2BB8A6, 0xFF3C6FD1, 0xFF6A4FC2, 0xFF7A8A3A)
private val CLOTHES = listOf(0xFFB4586E, 0xFF3D4E8A, 0xFF2F6E62, 0xFFE0A63A, 0xFF2D2A33, 0xFF6B4A3A, 0xFF7E3FA0, 0xFFE8E2D6, 0xFF2C3E57, 0xFFC0392B)
private val ACCENTS = listOf(0xFFF6EEE6, 0xFFEADBC8, 0xFF7FE3FF, 0xFFF2CF73, 0xFF2A2A2A, 0xFFFF9EC4)

private class Draft {
    var id by mutableStateOf("custom_" + UUID.randomUUID().toString().take(8))
    var title by mutableStateOf(""); var tagline by mutableStateOf(""); var premise by mutableStateOf("")
    var genres by mutableStateOf(setOf(Genre.SLICE_OF_LIFE)); var setting by mutableStateOf(SettingId.CORRIDOR)
    var atmosphere by mutableStateOf(""); var opening by mutableStateOf("")
    var playerName by mutableStateOf(""); var playerRole by mutableStateOf(""); var playerBody by mutableStateOf(BodyType.MASC)
    var cName by mutableStateOf(""); var cAge by mutableStateOf("21"); var cRole by mutableStateOf("")
    var personality by mutableStateOf(""); var style by mutableStateOf(""); var background by mutableStateOf("")
    var likes by mutableStateOf(""); var dislikes by mutableStateOf(""); var goals by mutableStateOf(""); var boundaries by mutableStateOf(""); var quirks by mutableStateOf("")
    var look by mutableStateOf(Appearance())
    var feminine by mutableStateOf(true); var pitch by mutableFloatStateOf(1.15f); var rate by mutableFloatStateOf(1.0f)
    var chapters by mutableStateOf(listOf(
        Chapter("First Impressions", "Break the ice and find out what the character wants right now."),
        Chapter("Complications", "Something unexpected happens that tests your relationship."),
        Chapter("The Decision", "A meaningful choice decides how the story ends."),
    ))
    var lang by mutableStateOf(Lang.HINGLISH); var startRel by mutableFloatStateOf(30f)
    var accent by mutableStateOf(0xFFFF7A90)
    var createdNew = true

    fun load(s: Story) {
        id = s.id; title = s.title; tagline = s.tagline; premise = s.premise; genres = s.genres.toSet(); setting = s.setting
        atmosphere = s.atmosphere; opening = s.openingSituation; playerName = s.playerNameOverride ?: ""; playerRole = s.playerRole; playerBody = s.playerAppearance.body
        val c = s.character
        cName = c.name; cAge = c.age.toString(); cRole = c.role; personality = c.personality; style = c.speakingStyle; background = c.background
        likes = c.likes; dislikes = c.dislikes; goals = c.goals; boundaries = c.boundaries; quirks = c.quirks; look = c.appearance
        feminine = c.voice.feminine; pitch = c.voice.pitch; rate = c.voice.rate; chapters = s.chapters; lang = s.language
        startRel = s.startingRelationship.toFloat(); accent = s.accent; createdNew = false
    }

    fun build(): Story {
        val playerLook = if (playerBody == BodyType.FEM) Appearance(BodyType.FEM, 0xFFE8BC98, 0xFF241A1C, HairStyle.PONYTAIL, 0xFF3B2618, 0xFF27324F, 0xFFE9E6DF, OutfitStyle.JACKET, Accessory.NONE, 0xFF33384A)
            else Appearance(BodyType.MASC, 0xFFE2B48C, 0xFF1C1414, HairStyle.SHORT, 0xFF3B2618, 0xFF27324F, 0xFFE9E6DF, OutfitStyle.JACKET, Accessory.NONE, 0xFF33384A)
        return Story(
            id = id, title = title.trim(), tagline = tagline.trim().ifEmpty { premise.trim().take(80) }, premise = premise.trim(),
            genres = genres.toList().ifEmpty { listOf(Genre.SLICE_OF_LIFE) }, setting = setting,
            atmosphere = atmosphere.trim().ifEmpty { setting.label }, openingSituation = opening.trim().ifEmpty { "You meet ${cName.trim()} for the first time." },
            playerRole = playerRole.trim().ifEmpty { "Yourself" }, playerAppearance = playerLook,
            character = CharacterSpec(
                id = "c_$id", name = cName.trim(), age = cAge.toIntOrNull()?.coerceIn(1, 999) ?: 21, role = cRole.trim().ifEmpty { "Story character" },
                appearance = look, background = background.trim().ifEmpty { "Unknown — reveals it slowly." },
                personality = personality.trim().ifEmpty { "Thoughtful, curious, has their own opinions." },
                speakingStyle = style.trim().ifEmpty { "Natural and conversational." }, likes = likes.trim().ifEmpty { "Honesty" },
                dislikes = dislikes.trim().ifEmpty { "Rudeness" }, goals = goals.trim().ifEmpty { "Find out who the player really is." },
                boundaries = boundaries.trim().ifEmpty { "Respects themselves; doesn't tolerate disrespect; trust is earned slowly." },
                quirks = quirks.trim(), voice = VoiceSpec(pitch, rate, null, feminine),
            ),
            chapters = chapters.filter { it.title.isNotBlank() }.ifEmpty { listOf(Chapter("The Story", "Let the story unfold.")) },
            language = lang, startingRelationship = startRel.toInt(), isCustom = true, accent = accent,
            playerNameOverride = playerName.trim().ifEmpty { null },
        )
    }
}

@Composable
fun CreatorScreen(nav: NavController, editId: String?) {
    val app = KissaApp.instance
    val draft = remember(editId) { Draft().apply { lang = app.settings.current.language; playerBody = app.settings.current.playerBody } }
    var loaded by remember(editId) { mutableStateOf(editId == null) }
    LaunchedEffect(editId) { if (editId != null) { app.repo.story(editId)?.let { draft.load(it) }; loaded = true } }
    val scope = rememberCoroutineScope()
    val pal = LocalPalette.current
    var error by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var previewEmotion by remember { mutableStateOf(Emotion.HAPPY) }
    if (!loaded) { TopBar("Edit story", { nav.popBackStack() }); return }

    fun save() {
        if (draft.title.isBlank() || draft.premise.isBlank() || draft.cName.isBlank()) { error = "Give your story a title, a premise and a character name."; return }
        val s = draft.build()
        val all = listOf(s.title, s.premise, s.openingSituation, s.character.personality, s.character.background, s.character.speakingStyle, s.character.goals).joinToString(" ")
        val v = Moderation.check(all)
        if (!v.allowed) { error = "Some of this text isn't allowed in Kissa stories (${v.reason}). Please revise it."; return }
        scope.launch {
            app.repo.saveCustom(s)
            nav.navigate(Routes.story(s.id)) { popUpTo("home") }
        }
    }

    fun aiFill() {
        if (aiPrompt.isBlank()) { error = "Describe the character in a line first."; return }
        if (!Moderation.check(aiPrompt).allowed) { error = "That description isn't allowed."; return }
        aiBusy = true; error = null
        scope.launch {
            try {
                val sys = "You design original characters for an interactive story app. Return ONLY a JSON object with string fields: name, age, role, personality, speaking_style, background, likes, dislikes, goals, boundaries, quirks, feminine (\"true\"/\"false\"). Keep each field under 220 characters. Characters must be original, safe for teens, with clear boundaries and their own opinions. Story context: title='${draft.title}', premise='${draft.premise}', setting='${draft.setting.label}'."
                val raw = app.ai.generate(PromptPayload(sys, listOf(PromptContent("user", aiPrompt))), null, temperature = 0.9, maxTokens = 1200)
                val obj = Json { isLenient = true }.parseToJsonElement(AiParser.extractJsonObject(raw) ?: error("no JSON")).jsonObject
                fun f(k: String) = obj[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (f("name").isNotEmpty()) draft.cName = f("name")
                f("age").filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.let { draft.cAge = it.take(3) }
                draft.cRole = f("role").ifEmpty { draft.cRole }; draft.personality = f("personality").ifEmpty { draft.personality }
                draft.style = f("speaking_style").ifEmpty { draft.style }; draft.background = f("background").ifEmpty { draft.background }
                draft.likes = f("likes").ifEmpty { draft.likes }; draft.dislikes = f("dislikes").ifEmpty { draft.dislikes }
                draft.goals = f("goals").ifEmpty { draft.goals }; draft.boundaries = f("boundaries").ifEmpty { draft.boundaries }
                draft.quirks = f("quirks").ifEmpty { draft.quirks }
                if (f("feminine").isNotEmpty()) {
                    val fem = f("feminine").startsWith("t", true)
                    draft.feminine = fem; draft.pitch = if (fem) 1.15f else 0.9f
                    draft.look = draft.look.copy(body = if (fem) BodyType.FEM else BodyType.MASC, hairStyle = if (fem) draft.look.hairStyle.takeIf { it != HairStyle.SHORT && it != HairStyle.SPIKY } ?: HairStyle.LONG else HairStyle.SHORT)
                }
            } catch (e: AiException) { error = e.message } catch (e: Exception) { error = "Couldn't generate details: ${e.message?.take(80)}" }
            aiBusy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
        TopBar(if (editId != null) "Edit story" else "Create a story", if (editId != null) ({ nav.popBackStack() }) else null) {
            TextButton(onClick = { save() }) { Text("Save") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (editId != null) Text("Edits keep your progress; changes apply from the next reply.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            Section("The story")
            Field("Title *", draft.title) { draft.title = it.take(60) }
            Field("One-line hook", draft.tagline) { draft.tagline = it.take(100) }
            Field("Premise *", draft.premise, lines = 3) { draft.premise = it.take(600) }
            Text("Genres", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Genre.entries.forEach { g -> KChip("${g.emoji} ${g.label}", g in draft.genres) { draft.genres = if (g in draft.genres) draft.genres - g else draft.genres + g } }
            }
            Text("Location", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingId.entries.forEach { st ->
                    val sel = st == draft.setting
                    Column(Modifier.width(120.dp).clickable { draft.setting = st }) {
                        Image(painterResource(st.backgroundRes()), st.label, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(14.dp)).border(if (sel) 2.dp else 0.dp, if (sel) LocalAccent.current else Color.Transparent, RoundedCornerShape(14.dp)))
                        Text(st.label, style = MaterialTheme.typography.labelSmall, color = if (sel) LocalAccent.current else pal.textDim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Field("Atmosphere", draft.atmosphere) { draft.atmosphere = it.take(160) }
            Field("Opening situation", draft.opening, lines = 2) { draft.opening = it.take(300) }
            Text("Dialogue language", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Lang.entries.forEach { l -> KChip(l.label, draft.lang == l) { draft.lang = l } } }

            Section("You")
            Field("Your name in this story (blank = ${app.settings.current.playerName})", draft.playerName) { draft.playerName = it.take(24) }
            Field("Your role", draft.playerRole) { draft.playerRole = it.take(100) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KChip("He / him", draft.playerBody == BodyType.MASC) { draft.playerBody = BodyType.MASC }
                KChip("She / her", draft.playerBody == BodyType.FEM) { draft.playerBody = BodyType.FEM }
            }

            Section("The character")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("✨ Describe them in a line and let AI fill the details", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(aiPrompt, { aiPrompt = it.take(300) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. a shy astronomy nerd who runs the college telescope club") }, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                if (aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth()) else GhostButton("Fill details with AI", Modifier.fillMaxWidth(), Icons.Rounded.AutoAwesome) { aiFill() }
            }
            Spacer(Modifier.height(10.dp))
            Field("Name *", draft.cName) { draft.cName = it.take(40) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(0.3f)) { Field("Age", draft.cAge) { draft.cAge = it.filter { c -> c.isDigit() }.take(3) } }
                Box(Modifier.weight(0.7f)) { Field("Role / occupation", draft.cRole) { draft.cRole = it.take(80) } }
            }
            Field("Personality", draft.personality, lines = 2) { draft.personality = it.take(400) }
            Field("Speaking style", draft.style, lines = 2) { draft.style = it.take(300) }
            Field("Background", draft.background, lines = 2) { draft.background = it.take(400) }
            Field("Likes", draft.likes) { draft.likes = it.take(200) }
            Field("Dislikes", draft.dislikes) { draft.dislikes = it.take(200) }
            Field("Goals", draft.goals) { draft.goals = it.take(200) }
            Field("Boundaries", draft.boundaries, lines = 2) { draft.boundaries = it.take(300) }
            Field("Quirks", draft.quirks) { draft.quirks = it.take(200) }
            Text("Starting bond: ${draft.startRel.toInt()} (${ContextBuilder.relationshipLabel(draft.startRel.toInt())})", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
            Slider(draft.startRel, { draft.startRel = it }, valueRange = 0f..100f)

            Section("Look")
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(150.dp).height(210.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f)).clickable {
                    previewEmotion = Emotion.entries[(previewEmotion.ordinal + 1) % Emotion.entries.size]
                }) {
                    Image(painterResource(draft.setting.backgroundRes()), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(), alpha = 0.5f)
                    CharacterPortrait(draft.look, Modifier.fillMaxSize(), previewEmotion, animate = true)
                    Text(previewEmotion.label, Modifier.align(Alignment.BottomCenter).padding(6.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Body", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        KChip("Feminine", draft.look.body == BodyType.FEM) { draft.look = draft.look.copy(body = BodyType.FEM); draft.feminine = true; draft.pitch = 1.15f }
                        KChip("Masculine", draft.look.body == BodyType.MASC) { draft.look = draft.look.copy(body = BodyType.MASC); draft.feminine = false; draft.pitch = 0.9f }
                    }
                    Text("Skin", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                    Swatches(SKINS, draft.look.skin) { draft.look = draft.look.copy(skin = it) }
                    Text("Eyes", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
                    Swatches(EYES, draft.look.eyes) { draft.look = draft.look.copy(eyes = it) }
                }
            }
            Text("Hair", style = MaterialTheme.typography.labelMedium, color = pal.textDim, modifier = Modifier.padding(top = 10.dp))
            Swatches(HAIRS, draft.look.hair) { draft.look = draft.look.copy(hair = it) }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HairStyle.entries.forEach { h -> KChip(h.name.lowercase().replaceFirstChar { it.uppercase() }, draft.look.hairStyle == h) { draft.look = draft.look.copy(hairStyle = h) } }
            }
            Text("Outfit", style = MaterialTheme.typography.labelMedium, color = pal.textDim, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutfitStyle.entries.forEach { o -> KChip(o.name.lowercase().replaceFirstChar { it.uppercase() }, draft.look.outfitStyle == o) { draft.look = draft.look.copy(outfitStyle = o) } }
            }
            Swatches(CLOTHES, draft.look.outfit) { draft.look = draft.look.copy(outfit = it) }
            Text("Detail colour", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
            Swatches(ACCENTS, draft.look.outfitAccent) { draft.look = draft.look.copy(outfitAccent = it) }
            Text("Accessory", style = MaterialTheme.typography.labelMedium, color = pal.textDim, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Accessory.entries.forEach { a -> KChip(a.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, draft.look.accessory == a) { draft.look = draft.look.copy(accessory = a) } }
            }

            Section("Voice")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KChip("Feminine voice", draft.feminine) { draft.feminine = true }
                KChip("Masculine voice", !draft.feminine) { draft.feminine = false }
            }
            Text("Pitch ${"%.2f".format(draft.pitch)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
            Slider(draft.pitch, { draft.pitch = it }, valueRange = 0.6f..1.6f)
            Text("Speed ${"%.2f".format(draft.rate)}", style = MaterialTheme.typography.labelMedium, color = pal.textDim)
            Slider(draft.rate, { draft.rate = it }, valueRange = 0.6f..1.5f)
            GhostButton("Preview voice", Modifier.fillMaxWidth(), Icons.Rounded.VolumeUp) {
                val line = when (draft.lang) { Lang.HINDI -> "नमस्ते, मैं ${draft.cName.ifBlank { "आपकी कहानी" }} हूँ।"; Lang.ENGLISH -> "Hi, I'm ${draft.cName.ifBlank { "your character" }}."; Lang.HINGLISH -> "Hi, main ${draft.cName.ifBlank { "tumhara character" }} hoon. Kaisa laga?" }
                app.speech.stop()
                scope.launch { app.speech.play(Utterance("preview", line, if (draft.lang == Lang.HINDI) "hi-IN" else draft.lang.ttsTag, null, draft.feminine, draft.pitch, draft.rate)) }
            }

            Section("Chapters")
            Text("Each chapter is a story beat the character steers towards. The AI moves on when a beat is reached; the last one leads to an ending.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
            draft.chapters.forEachIndexed { i, ch ->
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Chapter ${i + 1}", style = MaterialTheme.typography.labelLarge, color = LocalAccent.current, modifier = Modifier.weight(1f))
                        if (draft.chapters.size > 1) IconButton(onClick = { draft.chapters = draft.chapters.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Rounded.Delete, "Remove chapter") }
                    }
                    Field("Title", ch.title) { v -> draft.chapters = draft.chapters.toMutableList().also { it[i] = ch.copy(title = v.take(50)) } }
                    Field("Beat / goal", ch.goal, lines = 2) { v -> draft.chapters = draft.chapters.toMutableList().also { it[i] = ch.copy(goal = v.take(240)) } }
                }
            }
            if (draft.chapters.size < 8) GhostButton("Add chapter", Modifier.fillMaxWidth(), Icons.Rounded.Add) { draft.chapters = draft.chapters + Chapter("Chapter ${draft.chapters.size + 1}", "") }

            Spacer(Modifier.height(18.dp))
            error?.let { Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)) }
            AccentButton(if (editId != null) "Save changes" else "Save & open story", Modifier.fillMaxWidth(), Icons.Rounded.Check) { save() }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = lines == 1, minLines = lines, maxLines = lines + 2)
}

@Composable
private fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (c == selected) 3.dp else 1.dp, if (c == selected) LocalAccent.current else Color.White.copy(alpha = 0.2f), CircleShape).clickable { onPick(c) })
        }
    }
}
