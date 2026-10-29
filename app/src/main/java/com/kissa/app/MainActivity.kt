package com.kissa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kissa.app.ui.components.AppBackdrop
import com.kissa.app.ui.play.PlayerScreen
import com.kissa.app.ui.play.StartPoint
import com.kissa.app.ui.screens.*
import com.kissa.app.ui.theme.KissaTheme
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as KissaApp
        setContent {
            val s by app.settings.state.collectAsStateWithLifecycle()
            KissaTheme(s.themeId, s.accent, s.backdrop) {
                AppBackdrop { AppNav(startOnboarding = !s.onboarded) }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", "Games", Icons.Rounded.SportsEsports),
    Tab("search", "Search", Icons.Rounded.Search),
    Tab("library", "Library", Icons.Rounded.CollectionsBookmark),
    Tab("settings", "Settings", Icons.Rounded.Tune),
)

object Routes {
    fun game(id: String) = "game/$id"
    fun setup(id: String) = "setup/$id"
    /** from: -2 = new run, -1 = autosave, >= 0 = checkpoint index */
    fun play(id: String, from: Int) = "play/$id?from=$from"
}

private fun StartPoint.code() = when (this) { StartPoint.New -> -2; StartPoint.Resume -> -1; is StartPoint.Checkpoint -> index }

@Composable
fun AppNav(startOnboarding: Boolean) {
    val nav = rememberNavController()
    val start = remember { if (startOnboarding) "onboarding" else "home" }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { route == it.route }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = start,
            enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(300)) { it / 12 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(280)) { it / 12 } },
        ) {
            composable("onboarding") { OnboardingScreen(onDone = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }) }
            composable("home") { HomeScreen(onGame = { nav.navigate(Routes.game(it)) }, onSearch = { nav.navigate("search") }, onContinue = { nav.navigate(Routes.play(it, -1)) }) }
            composable("search") { SearchScreen(onGame = { nav.navigate(Routes.game(it)) }, onBack = { nav.popBackStack() }) }
            composable("library") { LibraryScreen(onGame = { nav.navigate(Routes.game(it)) }) }
            composable("settings") { SettingsScreen() }
            composable("game/{id}") { e ->
                val id = e.arguments?.getString("id")!!
                GameDetailScreen(id, onBack = { nav.popBackStack() }, onSetup = { nav.navigate(Routes.setup(id)) }, onContinue = { nav.navigate(Routes.play(id, -1)) })
            }
            composable("setup/{id}") { e ->
                val id = e.arguments?.getString("id")!!
                RoleSetupScreen(id, onBack = { nav.popBackStack() }, onStart = { nav.navigate(Routes.play(id, -2)) { popUpTo(Routes.setup(id)) { inclusive = true } } })
            }
            composable("play/{id}?from={from}", arguments = listOf(navArgument("from") { type = NavType.IntType; defaultValue = -2 }),
                enterTransition = { fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.05f) }) { e ->
                val id = e.arguments?.getString("id")!!
                val from = e.arguments?.getInt("from") ?: -2
                val sp = when { from == -2 -> StartPoint.New; from == -1 -> StartPoint.Resume; else -> StartPoint.Checkpoint(from) }
                PlayerScreen(id, sp, onExit = { nav.popBackStack() }, onReplay = { p ->
                    nav.navigate(Routes.play(id, p.code())) { popUpTo("play/{id}?from={from}") { inclusive = true } }
                })
            }
        }
        AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(nav, route)
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    val acc = LocalAccent.current
    val pal = LocalPalette.current
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(pal.bg2.copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { t ->
            val sel = route == t.route
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable {
                    if (!sel) nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (sel) acc else pal.textDim, modifier = Modifier.size(24.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (sel) acc else pal.textDim)
            }
        }
    }
}
