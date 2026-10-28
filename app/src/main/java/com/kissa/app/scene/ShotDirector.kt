package com.kissa.app.scene

import com.kissa.app.model.SceneEvent

enum class ShotType { ESTABLISHING, CLOSE_UP, OTS, MEDIUM, TWO_SHOT, EXIT_WALK }

/**
 * One camera setup. [subject] is the actor in focus ("player" or the character id), [other] the actor whose
 * shoulder is in the foreground (OTS) or who shares the frame. [startedAt] drives the slow push-in.
 */
data class Shot(
    val type: ShotType,
    val subject: String,
    val other: String? = null,
    val startedAt: Float = 0f,
    val tag: String = "",
)

/**
 * The editor/cinematographer: decides hard cuts between close-ups, over-the-shoulder shots, reaction inserts, medium
 * and wide shots — like the reference's shot/reverse-shot cutting — from what is happening (who speaks, who thinks,
 * scene events, idle time). Pure logic with no Android dependencies so it is unit-tested on the JVM.
 */
class ShotDirector(val npcId: String, val playerId: String = "player") {
    var current: Shot = Shot(ShotType.ESTABLISHING, npcId, playerId, 0f, "start"); private set
    var cutCount = 0; private set

    private val queue = ArrayDeque<Pair<Shot, Float>>() // shot + duration (scripted sequences)
    private var queuedUntil = -1f
    private var lastSpeaker: String? = null
    private var speakingSince = 0f
    private var lineIndex = 0
    private var insertDone = false
    private var insertUntil = -1f
    private var returnShot: Shot? = null
    private var idleSince = 0f
    private var idleStep = 0
    var npcPresent = true; private set
    private var exitUntil = -1f

    companion object {
        const val MIN_SHOT = 0.9f
        const val INSERT_AFTER = 6.5f
        const val INSERT_LEN = 1.7f
        const val IDLE_HOLD = 9f
    }

    /** Freeze on a specific shot (scene previews / tests). Null releases it. */
    var locked: Shot? = null
        set(v) { field = v; if (v != null) current = v }

    private fun cut(s: Shot) {
        if (s.type == current.type && s.subject == current.subject && s.other == current.other && s.tag == current.tag) return
        current = s; cutCount++
    }

    /** Opening sequence: wide establishing shot, a medium on the player, then the character's close-up. */
    fun intro(t: Float, playerMediumTag: String = "intro") {
        queue.clear()
        queue += Shot(ShotType.ESTABLISHING, npcId, playerId, t, "establish") to 2.4f
        queue += Shot(ShotType.MEDIUM, playerId, npcId, t, playerMediumTag) to 2.2f
        queue += Shot(ShotType.CLOSE_UP, npcId, playerId, t, "intro_cu") to 0f
        startQueued(t)
    }

    private fun startQueued(t: Float) {
        val (s, d) = queue.removeFirstOrNull() ?: return
        cut(s.copy(startedAt = t))
        queuedUntil = if (d > 0f) t + d else -1f
        if (d <= 0f) queue.clear()
    }

    fun onEvent(e: SceneEvent, t: Float) {
        when (e) {
            SceneEvent.CHARACTER_EXITS -> { npcPresent = false; exitUntil = t + 3.6f; queue.clear(); queuedUntil = -1f; cut(Shot(ShotType.EXIT_WALK, npcId, playerId, t)) }
            SceneEvent.CHARACTER_ENTERS -> { npcPresent = true; exitUntil = -1f; cut(Shot(ShotType.MEDIUM, npcId, playerId, t, "enter")); queuedUntil = t + 2.4f }
            SceneEvent.CROWD_PASSES -> if (npcPresent) { cut(Shot(ShotType.TWO_SHOT, npcId, playerId, t, "crowd")); queuedUntil = t + 3f }
            SceneEvent.PLOT_TWIST -> if (npcPresent) cut(Shot(ShotType.CLOSE_UP, npcId, playerId, t, "twist"))
            SceneEvent.CHAPTER_COMPLETE, SceneEvent.ENDING -> if (npcPresent) { cut(Shot(ShotType.TWO_SHOT, npcId, playerId, t, "chapter")); queuedUntil = t + 3.5f }
            else -> {}
        }
    }

    /** Called when the story is rewound: back to a calm close-up of the character. */
    fun reset(t: Float) {
        queue.clear(); queuedUntil = -1f; npcPresent = true; exitUntil = -1f; lastSpeaker = null; insertUntil = -1f
        cut(Shot(ShotType.CLOSE_UP, npcId, playerId, t, "reset"))
        idleSince = t
    }

    /**
     * @param speaker id of whoever is audibly speaking now (null = silence)
     * @param npcThinking the character is waiting for the AI reply
     */
    fun update(t: Float, speaker: String?, npcThinking: Boolean) {
        if (locked != null) return
        val age = t - current.startedAt
        // scripted sequences (intro, event holds) run to completion unless someone starts talking
        if (queuedUntil > 0f && t < queuedUntil && speaker == null) return
        if (queuedUntil > 0f && t >= queuedUntil) {
            queuedUntil = -1f
            if (queue.isNotEmpty()) { startQueued(t); return }
        }
        if (!npcPresent && t < exitUntil) return

        if (speaker != null) {
            idleSince = t; idleStep = 0
            if (speaker != lastSpeaker) {
                lastSpeaker = speaker; speakingSince = t; insertDone = false; insertUntil = -1f; lineIndex++
                if (age < 0.35f && current.subject == speaker) return
                cut(speakerShot(speaker, t))
                return
            }
            // reaction insert on long lines: cut to the listener briefly, then back to the speaker from a new angle
            if (insertUntil > 0f) {
                if (t >= insertUntil) { insertUntil = -1f; returnShot?.let { cut(it.copy(startedAt = t)) } }
                return
            }
            val listener = if (speaker == playerId) npcId else playerId
            if (!insertDone && t - speakingSince > INSERT_AFTER && (listener != npcId || npcPresent)) {
                insertDone = true
                returnShot = if (current.type == ShotType.CLOSE_UP) Shot(ShotType.OTS, speaker, listener, t, "return") else Shot(ShotType.CLOSE_UP, speaker, listener, t, "return")
                cut(Shot(ShotType.CLOSE_UP, listener, speaker, t, "reaction"))
                insertUntil = t + INSERT_LEN
            }
            return
        }

        // silence
        if (lastSpeaker != null) { lastSpeaker = null; idleSince = t; idleStep = 0 }
        if (!npcPresent) {
            if (current.subject != playerId || current.type != ShotType.CLOSE_UP) cut(Shot(ShotType.CLOSE_UP, playerId, null, t, "alone"))
            return
        }
        if (npcThinking) {
            if (age > MIN_SHOT && !(current.type == ShotType.CLOSE_UP && current.subject == npcId)) cut(Shot(ShotType.CLOSE_UP, npcId, playerId, t, "thinking"))
            return
        }
        // waiting for the player: hold on the character listening, occasionally widen
        if (t - idleSince > IDLE_HOLD && age > IDLE_HOLD) {
            idleStep++
            idleSince = t
            cut(when (idleStep % 3) {
                1 -> Shot(ShotType.TWO_SHOT, npcId, playerId, t, "idle")
                2 -> Shot(ShotType.OTS, npcId, playerId, t, "idle")
                else -> Shot(ShotType.CLOSE_UP, npcId, playerId, t, "idle")
            })
        }
    }

    private fun speakerShot(speaker: String, t: Float): Shot {
        val other = if (speaker == playerId) (if (npcPresent) npcId else null) else playerId
        // alternate clean close-ups with over-the-shoulder angles so consecutive lines don't look identical
        return if (other != null && lineIndex % 3 == 2) Shot(ShotType.OTS, speaker, other, t, "line")
        else Shot(ShotType.CLOSE_UP, speaker, other, t, "line")
    }
}
