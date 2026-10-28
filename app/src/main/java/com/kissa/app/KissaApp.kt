package com.kissa.app

import android.app.Application
import com.kissa.app.ai.GeminiClient
import com.kissa.app.data.AppDatabase
import com.kissa.app.data.SettingsRepository
import com.kissa.app.data.StoryRepository
import com.kissa.app.voice.SpeechDirector

/** Manual dependency container (small app, no DI framework needed). */
class KissaApp : Application() {
    lateinit var settings: SettingsRepository; private set
    lateinit var repo: StoryRepository; private set
    val gemini by lazy { GeminiClient() }
    val speech by lazy { SpeechDirector(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        repo = StoryRepository(this, AppDatabase.create(this))
    }

    companion object { lateinit var instance: KissaApp; private set }
}
