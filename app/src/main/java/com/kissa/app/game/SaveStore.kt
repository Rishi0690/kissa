package com.kissa.app.game

import kotlinx.serialization.json.*
import java.io.File

/**
 * Per-player, per-game saves as JSON files (spec §2.6): `<dir>/<gameId>.json` holds the player's name and look,
 * the autosave (state at the latest scene entry), every reached checkpoint (label + snapshot), and the set of endings
 * ever reached. Works offline; pure JVM so it is unit-tested.
 */
class SaveStore(private val dir: File) {
    data class CheckpointSave(val label: String, val sceneId: String, val state: GameState, val at: Long)
    data class GameSave(
        val gameId: String,
        val playerName: String,
        val playerRig: String,
        val look: Look,
        val autosave: GameState?,
        val checkpoints: List<CheckpointSave>,
        val endingsReached: Set<String>,
        val lastEnding: String?,
        val finalState: GameState?,
        val updatedAt: Long,
        val plays: Int,
    )

    init { dir.mkdirs() }
    private fun file(gameId: String) = File(dir, "${gameId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.json")

    fun load(gameId: String): GameSave? = runCatching {
        val f = file(gameId); if (!f.exists()) return null
        val o = Json.parseToJsonElement(f.readText()).jsonObject
        GameSave(
            gameId = gameId,
            playerName = o.str("player_name") ?: "",
            playerRig = o.str("player_rig") ?: "male",
            look = Look.parse(o.obj("look")),
            autosave = o.obj("autosave")?.let { GameState.fromJson(it) },
            checkpoints = o.arr("checkpoints")?.map { val c = it.jsonObject; CheckpointSave(c.str("label") ?: "", c.str("scene") ?: "", GameState.fromJson(c.obj("state")!!), c["at"]?.jsonPrimitive?.longOrNull ?: 0) } ?: emptyList(),
            endingsReached = o.strList("endings").toSet(),
            lastEnding = o.str("last_ending"),
            finalState = o.obj("final_state")?.let { GameState.fromJson(it) },
            updatedAt = o["updated_at"]?.jsonPrimitive?.longOrNull ?: 0,
            plays = o.int("plays") ?: 0,
        )
    }.getOrNull()

    fun save(s: GameSave) {
        val o = buildJsonObject {
            put("game", s.gameId); put("player_name", s.playerName); put("player_rig", s.playerRig); put("look", s.look.toJson())
            s.autosave?.let { put("autosave", it.toJson()) }
            put("checkpoints", JsonArray(s.checkpoints.map { c -> buildJsonObject { put("label", c.label); put("scene", c.sceneId); put("state", c.state.toJson()); put("at", c.at) } }))
            put("endings", JsonArray(s.endingsReached.map { JsonPrimitive(it) }))
            s.lastEnding?.let { put("last_ending", it) }
            s.finalState?.let { put("final_state", it.toJson()) }
            put("updated_at", s.updatedAt); put("plays", s.plays)
        }
        val f = file(s.gameId); val tmp = File(f.path + ".tmp")
        tmp.writeText(o.toString()); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun all(): List<GameSave> = dir.listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { load(it.nameWithoutExtension) } ?: emptyList()

    fun delete(gameId: String) { file(gameId).delete() }
    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    /** New playthrough (keeps endings reached and checkpoints from earlier runs). */
    fun startNew(gameId: String, name: String, rig: String, look: Look): GameSave {
        val prev = load(gameId)
        val s = GameSave(gameId, name, rig, look, null, prev?.checkpoints ?: emptyList(), prev?.endingsReached ?: emptySet(), prev?.lastEnding, prev?.finalState, System.currentTimeMillis(), (prev?.plays ?: 0) + 1)
        save(s); return s
    }

    /** Autosave at every scene entry; record a checkpoint when the scene is one (replacing an earlier save of the same label). */
    fun onSceneEntered(gameId: String, scene: SceneDef, snapshot: GameState) {
        val cur = load(gameId) ?: return
        val cps = if (scene.checkpoint != null) cur.checkpoints.filter { it.label != scene.checkpoint } + CheckpointSave(scene.checkpoint, scene.id, snapshot, System.currentTimeMillis()) else cur.checkpoints
        save(cur.copy(autosave = snapshot, checkpoints = cps, updatedAt = System.currentTimeMillis()))
    }

    fun onEnding(gameId: String, ending: EndingDef?, finalState: GameState) {
        val cur = load(gameId) ?: return
        save(cur.copy(autosave = null, endingsReached = cur.endingsReached + listOfNotNull(ending?.id), lastEnding = ending?.id, finalState = finalState, updatedAt = System.currentTimeMillis()))
    }
}
