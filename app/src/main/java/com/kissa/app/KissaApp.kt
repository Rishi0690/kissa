package com.kissa.app

import android.app.Application
import com.kissa.app.ai.AiService
import com.kissa.app.ai.BackendClient
import com.kissa.app.ai.GeminiClient
import com.kissa.app.data.AppDatabase
import com.kissa.app.data.SettingsRepository
import com.kissa.app.data.StoryRepository
import com.kissa.app.voice.SpeechDirector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (small app, no DI framework needed). */
class KissaApp : Application() {
    lateinit var settings: SettingsRepository; private set
    lateinit var repo: StoryRepository; private set
    lateinit var ai: AiService; private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val gemini by lazy { GeminiClient() }
    val backend by lazy { BackendClient(BuildConfig.KISSA_BACKEND_URL, settings) }
    val speech by lazy { SpeechDirector(this, backend, ai) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        repo = StoryRepository(this, AppDatabase.create(this))
        ai = AiService(backend, gemini, settings, appScope)
        // automatic, silent first-launch setup: anonymous device registration + real service health check
        ai.runSetup()
    }

    companion object { lateinit var instance: KissaApp; private set }
}
