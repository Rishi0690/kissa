package com.kissa.app

import android.app.Application
import com.kissa.app.data.GameRepository
import com.kissa.app.data.SettingsRepository
import com.kissa.app.data.SocialStore
import com.kissa.app.game.SaveStore
import com.kissa.app.play.VoicePlayer
import com.kissa.app.rig.RigKit
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency container. v3 is play-only and fully offline: games come from the bundled catalog (or a CDN pack
 * download when BuildConfig.CATALOG_URL is set), the engine runs on-device, voices are pre-generated files.
 * The v2 live-AI code (ai/, voice/engines) stays in the source tree but is never constructed on the play path.
 */
class KissaApp : Application() {
    lateinit var settings: SettingsRepository; private set
    lateinit var games: GameRepository; private set
    lateinit var social: SocialStore; private set
    lateinit var saves: SaveStore; private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val rigKit: RigKit by lazy {
        RigKit(assets.open("rig/rig.json").bufferedReader().readText()) { rel ->
            runCatching { assets.open("rig/$rel.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val voice: VoicePlayer by lazy { VoicePlayer(this, games) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        games = GameRepository(this, BuildConfig.CATALOG_URL)
        social = SocialStore(this)
        saves = SaveStore(File(filesDir, "saves"))
        appScope.launch { games.load(); games.refreshRemote() }
    }

    companion object { lateinit var instance: KissaApp; private set }
}
