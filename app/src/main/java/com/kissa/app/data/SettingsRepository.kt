package com.kissa.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kissa.app.BuildConfig
import com.kissa.app.ai.DeviceTokenStore
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
    /** Gemini prebuilt voice for the player's typed lines (null = default for the chosen body type). */
    val playerNeuralVoice: String? = null,
    /** Use the backend's natural neural voices when available (falls back to the phone voice, clearly labelled). */
    val naturalVoices: Boolean = true,
    /** Debug builds only: a developer Gemini key that bypasses the backend. Always false in release. */
    val devKeySet: Boolean = false,
    val voiceOverrides: Map<String, String> = emptyMap(),
    val neuralOverrides: Map<String, String> = emptyMap(),
)

class SettingsRepository(ctx: Context) : DeviceTokenStore {
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

    /** Developer override, honoured only in debug builds. Release builds never use a client-side provider key. */
    fun devApiKey(): String? = if (!BuildConfig.DEBUG) null else secret.getString("dev_gemini_key", null)?.takeIf { it.isNotBlank() }

    fun setDevApiKey(key: String?) {
        if (!BuildConfig.DEBUG) return
        secret.edit().apply { if (key.isNullOrBlank()) remove("dev_gemini_key") else putString("dev_gemini_key", key.trim()) }.apply()
        _state.value = load()
    }

    // ---- DeviceTokenStore (anonymous backend registration)
    override fun installId(): String {
        prefs.getString("install_id", null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString().replace("-", "") + java.lang.Long.toHexString(System.nanoTime())
        prefs.edit().putString("install_id", id.take(40)).apply()
        return id.take(40)
    }
    override fun token(): String? = secret.getString("device_token", null)
    override fun tokenExpiry(): Long = secret.getLong("device_token_exp", 0L)
    override fun saveToken(token: String?, expiresAtSec: Long) {
        secret.edit().apply {
            if (token == null) { remove("device_token"); remove("device_token_exp") } else { putString("device_token", token); putLong("device_token_exp", expiresAtSec) }
        }.apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val overrides = prefs.all.filterKeys { it.startsWith("voice_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("voice_") to it } }.toMap()
        val neural = prefs.all.filterKeys { it.startsWith("neural_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("neural_") to it } }.toMap()
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
            playerNeuralVoice = prefs.getString("playerNeuralVoice", null),
            naturalVoices = prefs.getBoolean("naturalVoices", d.naturalVoices),
            devKeySet = devApiKey() != null,
            voiceOverrides = overrides,
            neuralOverrides = neural,
        )
    }

    fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    fun setCharacterVoice(characterId: String, voiceName: String?) = update {
        if (voiceName == null) remove("voice_$characterId") else putString("voice_$characterId", voiceName)
    }

    fun setCharacterNeuralVoice(characterId: String, voice: String?) = update {
        if (voice == null) remove("neural_$characterId") else putString("neural_$characterId", voice)
    }

    fun resetAll() {
        val install = prefs.getString("install_id", null)
        prefs.edit().clear().apply()
        // keep the anonymous install id + device token: they are not personal data and re-registering is pointless
        install?.let { prefs.edit().putString("install_id", it).apply() }
        secret.edit().remove("dev_gemini_key").apply()
        _state.value = load()
    }
}
