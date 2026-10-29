package com.kissa.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

data class Report(val gameId: String, val reason: String, val note: String, val at: Long, val status: String = "queued")

data class SocialState(
    val liked: Set<String> = emptySet(),
    val following: Set<String> = emptySet(),
    val library: Set<String> = emptySet(),
    val blockedCreators: Set<String> = emptySet(),
    val hiddenGames: Set<String> = emptySet(),
    val reports: List<Report> = emptyList(),
)

/**
 * Likes, follows, library, blocks and reports — stored on the device for V1 (no accounts yet).
 * Reports are queued locally ("queued") and the reported game is hidden from this user's catalog immediately;
 * the queue is what the Phase 2 backend moderation endpoint will receive.
 */
class SocialStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("kissa_social", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SocialState> = _state

    private fun set(key: String) = prefs.getStringSet(key, emptySet())!!.toSet()
    private fun load(): SocialState {
        val reports = runCatching {
            Json.parseToJsonElement(prefs.getString("reports", "[]")!!).jsonArray.map {
                val o = it.jsonObject
                Report(o["game"]!!.jsonPrimitive.content, o["reason"]!!.jsonPrimitive.content, o["note"]?.jsonPrimitive?.content ?: "", o["at"]!!.jsonPrimitive.long, o["status"]?.jsonPrimitive?.content ?: "queued")
            }
        }.getOrDefault(emptyList())
        return SocialState(set("liked"), set("following"), set("library"), set("blocked"), set("hidden"), reports)
    }
    private fun toggle(key: String, id: String) {
        val s = set(key).toMutableSet(); if (!s.add(id)) s.remove(id)
        prefs.edit().putStringSet(key, s).apply(); _state.value = load()
    }
    fun toggleLike(gameId: String) = toggle("liked", gameId)
    fun toggleFollow(creator: String) = toggle("following", creator)
    fun toggleLibrary(gameId: String) = toggle("library", gameId)
    fun blockCreator(creator: String) { val s = set("blocked") + creator; prefs.edit().putStringSet("blocked", s).apply(); _state.value = load() }
    fun unblockCreator(creator: String) { val s = set("blocked") - creator; prefs.edit().putStringSet("blocked", s).apply(); _state.value = load() }
    fun report(gameId: String, reason: String, note: String) {
        val list = _state.value.reports + Report(gameId, reason, note, System.currentTimeMillis())
        val arr = JsonArray(list.map { r -> buildJsonObject { put("game", r.gameId); put("reason", r.reason); put("note", r.note); put("at", r.at); put("status", r.status) } })
        prefs.edit().putString("reports", arr.toString()).putStringSet("hidden", set("hidden") + gameId).apply()
        _state.value = load()
    }
    fun unhide(gameId: String) { prefs.edit().putStringSet("hidden", set("hidden") - gameId).apply(); _state.value = load() }
    fun visible(gameId: String, creator: String) = gameId !in _state.value.hiddenGames && creator !in _state.value.blockedCreators
}
