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
import com.kissa.app.ui.scene.SceneScreen
import com.kissa.app.ui.screens.*
import com.kissa.app.ui.theme.KissaTheme
import com.kissa.app.ui.theme.LocalAccent
import com.kissa.app.ui.theme.LocalPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as KissaApp
        app.speech // warm up TTS engine early so the first line isn't delayed
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
    Tab("home", "Home", Icons.Rounded.AutoAwesome),
    Tab("discover", "Discover", Icons.Rounded.Explore),
    Tab("create", "Create", Icons.Rounded.AddCircle),
    Tab("library", "Library", Icons.Rounded.CollectionsBookmark),
    Tab("settings", "Settings", Icons.Rounded.Tune),
)

object Routes {
    fun story(id: String) = "story/$id"
    fun play(id: String, replay: Long? = null) = "play/$id?replay=${replay ?: -1}"
    fun character(storyId: String) = "character/$storyId"
    fun history(storyId: String?) = "history/${storyId ?: "all"}"
    fun edit(storyId: String) = "create?edit=$storyId"
}

@Composable
fun AppNav(startOnboarding: Boolean) {
    val nav = rememberNavController()
    val start = remember { if (startOnboarding) "onboarding" else "home" }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { route?.substringBefore("?") == it.route } && !(route?.startsWith("create") == true && !entry?.arguments?.getString("edit").isNullOrEmpty())
    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = start,
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 10 } },
        ) {
            composable("onboarding") { OnboardingScreen(onDone = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }) }
            composable("home") { HomeScreen(nav) }
            composable("discover?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null })) { e -> DiscoverScreen(nav, e.arguments?.getString("tab")) }
            composable("create?edit={edit}", arguments = listOf(navArgument("edit") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                CreatorScreen(nav, e.arguments?.getString("edit"))
            }
            composable("library") { LibraryScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("story/{id}") { e -> StoryDetailsScreen(nav, e.arguments?.getString("id")!!) }
            composable("character/{id}") { e -> CharacterProfileScreen(nav, e.arguments?.getString("id")!!) }
            composable("history/{id}") { e -> HistoryScreen(nav, e.arguments?.getString("id")!!.takeIf { it != "all" }) }
            composable(
                "play/{id}?replay={replay}",
                arguments = listOf(navArgument("replay") { type = NavType.LongType; defaultValue = -1L }),
                enterTransition = { fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 1.06f) },
            ) { e ->
                val id = e.arguments?.getString("id")!!
                val replay = e.arguments?.getLong("replay")?.takeIf { it >= 0 }
                SceneScreen(id, replay, onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate("settings") },
                    onOpenHistory = { nav.navigate(Routes.history(it)) }, onOpenCharacter = { nav.navigate(Routes.character(it)) })
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
            .clip(RoundedCornerShape(28.dp)).background(pal.bg2.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { t ->
            val sel = route?.substringBefore("?") == t.route
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable {
                    if (!sel) nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (sel) acc else pal.textDim, modifier = Modifier.size(24.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (sel) acc else pal.textDim)
            }
        }
    }
}
