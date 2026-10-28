package com.kissa.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kissa.app.ai.GeminiClient
import com.kissa.app.model.BodyType
import com.kissa.app.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val onboarded: Boolean = false,
    val playerName: String = "Aarav",
    val playerBody: BodyType = BodyType.MASC,
    val language: Lang = Lang.HINGLISH,
    val themeId: String = "midnight",
    val accent: Long = 0xFFFF7A90,
    val backdrop: String = "aurora",
    val subtitles: Boolean = true,
    val subtitleScale: Float = 1f,
    val volume: Float = 1f,
    val speakPlayerLines: Boolean = true,
    val playerVoiceName: String? = null,
    val playerPitch: Float = 0.92f,
    val playerRate: Float = 1.0f,
    val keepRealVoice: Boolean = true,
    val characterVoicesOn: Boolean = true,
    val showSuggestions: Boolean = true,
    val reduceMotion: Boolean = false,
    val model: String = GeminiClient.DEFAULT_MODEL,
    val proxyUrl: String = "",
    val hasApiKey: Boolean = false,
    val keyStorageSecure: Boolean = true,
    val voiceOverrides: Map<String, String> = emptyMap(),
)

class SettingsRepository(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("kissa_settings", Context.MODE_PRIVATE)
    private var secureOk = true
    private val secret: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "kissa_secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        secureOk = false
        ctx.getSharedPreferences("kissa_secret_fallback", Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun apiKey(): String? = secret.getString("gemini_key", null)?.takeIf { it.isNotBlank() }

    fun setApiKey(key: String?) {
        secret.edit().apply { if (key.isNullOrBlank()) remove("gemini_key") else putString("gemini_key", key.trim()) }.apply()
        _state.value = load()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val overrides = prefs.all.filterKeys { it.startsWith("voice_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("voice_") to it } }.toMap()
        return AppSettings(
            onboarded = prefs.getBoolean("onboarded", false),
            playerName = prefs.getString("playerName", d.playerName) ?: d.playerName,
            playerBody = runCatching { BodyType.valueOf(prefs.getString("playerBody", d.playerBody.name)!!) }.getOrDefault(d.playerBody),
            language = runCatching { Lang.valueOf(prefs.getString("language", d.language.name)!!) }.getOrDefault(d.language),
            themeId = prefs.getString("themeId", d.themeId) ?: d.themeId,
            accent = prefs.getLong("accent", d.accent),
            backdrop = prefs.getString("backdrop", d.backdrop) ?: d.backdrop,
            subtitles = prefs.getBoolean("subtitles", d.subtitles),
            subtitleScale = prefs.getFloat("subtitleScale", d.subtitleScale),
            volume = prefs.getFloat("volume", d.volume),
            speakPlayerLines = prefs.getBoolean("speakPlayerLines", d.speakPlayerLines),
            playerVoiceName = prefs.getString("playerVoiceName", null),
            playerPitch = prefs.getFloat("playerPitch", d.playerPitch),
            playerRate = prefs.getFloat("playerRate", d.playerRate),
            keepRealVoice = prefs.getBoolean("keepRealVoice", d.keepRealVoice),
            characterVoicesOn = prefs.getBoolean("characterVoicesOn", d.characterVoicesOn),
            showSuggestions = prefs.getBoolean("showSuggestions", d.showSuggestions),
            reduceMotion = prefs.getBoolean("reduceMotion", d.reduceMotion),
            model = prefs.getString("model", d.model) ?: d.model,
            proxyUrl = prefs.getString("proxyUrl", "") ?: "",
            hasApiKey = apiKey() != null,
            keyStorageSecure = secureOk,
            voiceOverrides = overrides,
        )
    }

    fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    fun setCharacterVoice(characterId: String, voiceName: String?) = update {
        if (voiceName == null) remove("voice_$characterId") else putString("voice_$characterId", voiceName)
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        secret.edit().clear().apply()
        _state.value = load()
    }
}
