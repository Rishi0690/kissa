package com.kissa.app.rig

import kotlin.math.PI
import kotlin.math.sin

/**
 * A pose of the shared skeleton. Angles in degrees. Arm/leg angles use a mirrored "outward" convention so one pose
 * works for both sides: armOut > 0 lifts the arm away from the body, elbow > 0 bends the forearm in front of the body,
 * hipOut > 0 spreads the leg. Scale fields foreshorten a bone (pointing at the camera, kneeling, sitting).
 */
data class Pose(
    val rootX: Float = 0f, val rootY: Float = 0f, val lean: Float = 0f,
    val head: Float = 0f, val headDy: Float = 0f, val headTurn: Float = 0f,
    val armOutL: Float = 4f, val armOutR: Float = 4f, val elbowL: Float = 4f, val elbowR: Float = 4f,
    val handL: Float = 0f, val handR: Float = 0f, val upperScaleL: Float = 1f, val upperScaleR: Float = 1f,
    val foreScaleL: Float = 1f, val foreScaleR: Float = 1f,
    val hipOutL: Float = 1f, val hipOutR: Float = 1f, val kneeL: Float = 0f, val kneeR: Float = 0f,
    val thighScaleL: Float = 1f, val thighScaleR: Float = 1f, val shinScaleL: Float = 1f, val shinScaleR: Float = 1f,
    val squash: Float = 1f,
) {
    fun lerp(o: Pose, t: Float): Pose {
        fun l(a: Float, b: Float) = a + (b - a) * t
        return Pose(l(rootX, o.rootX), l(rootY, o.rootY), l(lean, o.lean), l(head, o.head), l(headDy, o.headDy), l(headTurn, o.headTurn),
            l(armOutL, o.armOutL), l(armOutR, o.armOutR), l(elbowL, o.elbowL), l(elbowR, o.elbowR), l(handL, o.handL), l(handR, o.handR),
            l(upperScaleL, o.upperScaleL), l(upperScaleR, o.upperScaleR), l(foreScaleL, o.foreScaleL), l(foreScaleR, o.foreScaleR),
            l(hipOutL, o.hipOutL), l(hipOutR, o.hipOutR), l(kneeL, o.kneeL), l(kneeR, o.kneeR),
            l(thighScaleL, o.thighScaleL), l(thighScaleR, o.thighScaleR), l(shinScaleL, o.shinScaleL), l(shinScaleR, o.shinScaleR), l(squash, o.squash))
    }
}

/** A named move clip: keyframes over normalised time, played once (holding the last key) or looped. */
data class Clip(val name: String, val seconds: Float, val loop: Boolean, val keys: List<Pair<Float, Pose>>, val hold: Boolean = true) {
    fun sample(time: Float): Pose {
        val t = if (loop) ((time / seconds) % 1f + 1f) % 1f else (time / seconds).coerceIn(0f, 1f)
        val i = keys.indexOfLast { it.first <= t }.coerceAtLeast(0)
        val a = keys[i]; val b = keys.getOrNull(i + 1) ?: return a.second
        val u = ((t - a.first) / (b.first - a.first)).coerceIn(0f, 1f)
        val s = u * u * (3 - 2 * u)
        return a.second.lerp(b.second, s)
    }
    fun done(time: Float) = !loop && time >= seconds
}

object Moves {
    private val rest = Pose()

    private val walkKeys = listOf(
        0f to Pose(armOutL = 6f, armOutR = 2f, elbowL = 10f, elbowR = 2f, kneeL = 0f, kneeR = 14f, shinScaleR = 0.94f, rootY = 0f, hipOutL = -3f, hipOutR = 5f),
        0.25f to Pose(armOutL = 4f, armOutR = 4f, elbowL = 6f, elbowR = 6f, rootY = -10f, hipOutL = 1f, hipOutR = 1f),
        0.5f to Pose(armOutL = 2f, armOutR = 6f, elbowL = 2f, elbowR = 10f, kneeL = 14f, shinScaleL = 0.94f, kneeR = 0f, rootY = 0f, hipOutL = 5f, hipOutR = -3f),
        0.75f to Pose(armOutL = 4f, armOutR = 4f, elbowL = 6f, elbowR = 6f, rootY = -10f, hipOutL = 1f, hipOutR = 1f),
        1f to Pose(armOutL = 6f, armOutR = 2f, elbowL = 10f, elbowR = 2f, kneeL = 0f, kneeR = 14f, shinScaleR = 0.94f, rootY = 0f, hipOutL = -3f, hipOutR = 5f),
    )

    /** The 20 catalog moves. Locomotion (enter/exit/walk/walk_away) loops the walk cycle while the actor travels. */
    val clips: Map<String, Clip> = listOf(
        Clip("idle", 3.2f, true, listOf(0f to rest, 0.5f to Pose(rootY = -3f, armOutL = 5f, armOutR = 5f, head = 1.5f), 1f to rest)),
        Clip("walk", 0.9f, true, walkKeys),
        Clip("enter", 0.9f, true, walkKeys),
        Clip("exit", 0.9f, true, walkKeys),
        Clip("walk_away", 0.9f, true, walkKeys),
        Clip("kneel", 0.8f, false, listOf(0f to rest, 1f to Pose(rootY = 300f, thighScaleL = 0.45f, thighScaleR = 0.2f, shinScaleL = 0.9f, shinScaleR = 0.5f, kneeL = -10f, kneeR = 10f, hipOutL = 14f, hipOutR = 8f, lean = 4f, head = 8f, headDy = 6f, armOutR = 18f, elbowR = 40f, armOutL = 8f, elbowL = 30f))),
        Clip("hold_phone", 0.6f, false, listOf(0f to rest, 1f to Pose(armOutR = 12f, elbowR = 150f, foreScaleR = 0.85f, handR = 20f, head = 6f, headDy = 6f))),
        Clip("look_down", 0.6f, false, listOf(0f to rest, 1f to Pose(head = 4f, headDy = 14f, lean = 1f))),
        Clip("nod", 0.9f, false, listOf(0f to rest, 0.25f to Pose(headDy = 14f), 0.5f to Pose(headDy = 0f), 0.75f to Pose(headDy = 12f), 1f to rest)),
        Clip("shake_head", 1.0f, false, listOf(0f to rest, 0.2f to Pose(headTurn = -0.7f, head = -3f), 0.4f to Pose(headTurn = 0.7f, head = 3f), 0.6f to Pose(headTurn = -0.6f, head = -3f), 0.8f to Pose(headTurn = 0.5f, head = 2f), 1f to rest)),
        Clip("point", 0.5f, false, listOf(0f to rest, 1f to Pose(armOutR = 78f, elbowR = -6f, upperScaleR = 0.92f, foreScaleR = 0.9f, handR = -10f, lean = -3f, head = -2f))),
        Clip("cross_arms", 0.6f, false, listOf(0f to rest, 1f to Pose(armOutL = 14f, armOutR = 14f, elbowL = 118f, elbowR = 124f, foreScaleL = 0.92f, foreScaleR = 0.92f, head = -3f, lean = -1f))),
        Clip("wave", 1.4f, false, listOf(0f to rest, 0.2f to Pose(armOutR = 150f, elbowR = 30f, handR = 0f), 0.4f to Pose(armOutR = 150f, elbowR = 0f, handR = -14f), 0.6f to Pose(armOutR = 150f, elbowR = 30f), 0.8f to Pose(armOutR = 150f, elbowR = 0f, handR = -14f), 1f to rest)),
        Clip("sit", 0.8f, false, listOf(0f to rest, 1f to Pose(rootY = 210f, thighScaleL = 0.28f, thighScaleR = 0.28f, hipOutL = 8f, hipOutR = 8f, kneeL = -6f, kneeR = -6f, armOutL = 10f, armOutR = 10f, elbowL = 40f, elbowR = 40f))),
        Clip("turn", 0.5f, false, listOf(0f to rest, 0.5f to Pose(squash = 0.6f, headTurn = 1f), 1f to rest)),
        Clip("laugh", 1.2f, false, listOf(0f to rest, 0.15f to Pose(head = -8f, headDy = -6f, rootY = -6f, armOutL = 10f, armOutR = 10f, elbowL = 20f, elbowR = 20f), 0.3f to Pose(head = -4f, rootY = 0f), 0.45f to Pose(head = -8f, headDy = -6f, rootY = -6f), 0.6f to Pose(head = -3f), 0.8f to Pose(head = 4f, lean = 3f, elbowR = 70f), 1f to rest)),
        Clip("cry", 1.0f, false, listOf(0f to rest, 1f to Pose(armOutL = 18f, armOutR = 18f, upperScaleL = 0.6f, upperScaleR = 0.6f, elbowL = 168f, elbowR = 168f, foreScaleL = 0.95f, foreScaleR = 0.95f, head = 6f, headDy = 18f, lean = 2f))),
        Clip("shrug", 0.9f, false, listOf(0f to rest, 0.4f to Pose(armOutL = 26f, armOutR = 26f, elbowL = -50f, elbowR = -50f, handL = -30f, handR = -30f, head = 8f, rootY = -6f), 0.7f to Pose(armOutL = 26f, armOutR = 26f, elbowL = -50f, elbowR = -50f, head = 8f, rootY = -6f), 1f to rest)),
        Clip("hands_on_hips", 0.6f, false, listOf(0f to rest, 1f to Pose(armOutL = 40f, armOutR = 40f, elbowL = -82f, elbowR = -82f, foreScaleL = 0.9f, foreScaleR = 0.9f, lean = 2f, head = -4f))),
        Clip("step_back", 0.6f, false, listOf(0f to rest, 0.5f to Pose(lean = -8f, head = -8f, rootY = -8f, armOutL = 16f, armOutR = 16f, elbowL = 30f, elbowR = 30f, kneeR = 10f), 1f to Pose(lean = -4f, head = -4f, armOutL = 10f, armOutR = 10f, elbowL = 20f, elbowR = 20f))),
    ).associateBy { it.name }

    val locomotion = setOf("walk", "enter", "exit", "walk_away")

    /** Clips that keep their final pose until another move plays (otherwise the actor eases back to idle). */
    val holding = setOf("kneel", "hold_phone", "look_down", "cross_arms", "sit", "hands_on_hips", "point", "cry", "step_back")

    fun clip(name: String?): Clip = clips[name] ?: clips.getValue("idle")

    /** Always-on life: breathing and a slow sway layered on whatever clip plays. */
    fun breathe(t: Float, base: Pose): Pose {
        val b = sin(t * 2f * PI.toFloat() / 3.4f)
        return base.copy(rootY = base.rootY + b * 2.5f, head = base.head + sin(t * 0.9f) * 0.8f, armOutL = base.armOutL + b * 0.8f, armOutR = base.armOutR + b * 0.8f)
    }
}
