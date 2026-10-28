package com.kissa.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kissa.app.KissaApp
import com.kissa.app.R
import com.kissa.app.content.SeedContent
import com.kissa.app.model.BodyType
import com.kissa.app.model.Emotion
import com.kissa.app.model.Lang
import com.kissa.app.ui.components.*
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = KissaApp.instance
    val s = app.settings.current
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(s.playerName) }
    var body by remember { mutableStateOf(s.playerBody) }
    var lang by remember { mutableStateOf(s.language) }
    val pal = LocalPalette.current
    val setup by app.ai.setup.collectAsState()

    fun finish() {
        app.settings.update {
            putBoolean("onboarded", true); putString("playerName", name.trim().ifEmpty { "Aarav" })
            putString("playerBody", body.name); putString("language", lang.name)
        }
        onDone()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = true) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                when (page) {
                    0 -> {
                        Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(30.dp))) {
                            Image(painterResource(R.drawable.bg_corridor), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
                            PaintedPortrait("cast/kabir/stand.webp", Modifier.fillMaxHeight(0.82f).aspectRatio(0.67f).align(Alignment.BottomStart), ContentScale.Fit)
                            PaintedPortrait("cast/riya/body.webp", Modifier.fillMaxHeight(0.86f).aspectRatio(0.75f).align(Alignment.BottomEnd), ContentScale.Fit)
                        }
                        Spacer(Modifier.height(26.dp))
                        Text("Kissa", style = MaterialTheme.typography.displaySmall)
                        Text("Step inside the story.", style = MaterialTheme.typography.headlineSmall, color = LocalAccent.current)
                        Spacer(Modifier.height(12.dp))
                        Text("Every story is a living animated scene. Speak or type as yourself, hear your line, and watch characters listen, react and answer in their own voices — and remember everything you've shared.",
                            style = MaterialTheme.typography.bodyLarge, color = pal.textDim)
                    }
                    1 -> {
                        Text("Who are you in the story?", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            val app2 = SeedContent.stories.first().playerAppearance.let { if (body == BodyType.FEM) it.copy(body = BodyType.FEM, hairStyle = com.kissa.app.model.HairStyle.PONYTAIL) else it }
                            CharacterPortrait(app2, Modifier.size(180.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)), Emotion.HAPPY, animate = true)
                        }
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("Your character's name") }, singleLine = true)
                        Spacer(Modifier.height(14.dp))
                        Text("Your character", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KChip("He / him", body == BodyType.MASC) { body = BodyType.MASC }
                            KChip("She / her", body == BodyType.FEM) { body = BodyType.FEM }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Dialogue language", style = MaterialTheme.typography.labelLarge, color = pal.textDim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Lang.entries.forEach { l -> KChip(l.label, lang == l) { lang = l } } }
                        Spacer(Modifier.height(8.dp))
                        Text("You can change all of this later in Settings, and pick a language per story.", style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                    else -> {
                        Text("Bring the characters to life", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(10.dp))
                        if (app.ai.backendMode) {
                            Text("Kissa sets up its story AI and natural voices for you in the background.", style = MaterialTheme.typography.bodyLarge, color = pal.textDim)
                        } else {
                            Text("Characters think and speak with Google Gemini. Paste your own free Gemini API key once — tap the link below to create one in a minute.",
                                style = MaterialTheme.typography.bodyLarge, color = pal.textDim)
                            Spacer(Modifier.height(16.dp))
                            ApiKeyCard(compact = true)
                        }
                        Spacer(Modifier.height(14.dp))
                        SetupStatusCard(setup, onRetry = { app.ai.runSetup(force = true) })
                        Spacer(Modifier.height(12.dp))
                        Text(if (app.ai.backendMode) "Your conversations stay on this phone. Lines are sent to Kissa's server only to generate replies and voices."
                            else "Your conversations stay on this phone. Lines go straight to Google Gemini with your key; backup voices use Microsoft's free neural voice service. You can add or change the key later in Settings.",
                            style = MaterialTheme.typography.bodySmall, color = pal.textDim)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.height(6.dp).width(if (pager.currentPage == i) 22.dp else 6.dp).clip(RoundedCornerShape(50))
                        .background(if (pager.currentPage == i) LocalAccent.current else Color.White.copy(alpha = 0.25f)))
                }
            }
            if (pager.currentPage < 2) AccentButton("Next") { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
            else AccentButton(when {
                setup.phase == com.kissa.app.ai.SetupState.Phase.NEEDS_KEY -> "Skip for now"
                setup.phase == com.kissa.app.ai.SetupState.Phase.CHECKING -> "Continue"
                else -> "Enter Kissa"
            }) { finish() }
        }
    }
}
