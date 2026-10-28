package com.kissa.app.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.kissa.app.model.*
import com.kissa.app.voice.SpeakingState
import kotlin.math.*
import kotlin.random.Random

/** One character on stage with its animation state. Positions are fractions of the stage width. */
class Actor(val id: String, var appearance: Appearance, val isPlayer: Boolean, var homeX: Float) {
    var x = homeX
    var targetX = homeX
    var baseFacing = if (isPlayer) 1f else -1f
    var emotion = Emotion.NEUTRAL
    var prevEmotion = Emotion.NEUTRAL
    var targetExpr = Expr()
    var gesture = Gesture.NONE
    var gestureStart = 0f
    var gestureHold = 2.8f
    var thinkingTarget = 0f
    var listening = false
    var visible = true
    var alphaTarget = 1f
    var turnAwayUntil = 0f
    var markStart = -10f
    internal var nextBlink = 2f
    internal var blinkStart = -1f
    internal var mouth = 0f
    internal val phase = Random.nextFloat() * 6f
    val pose = PoseFrame()
    var drawFacing = baseFacing
    private val range = if (isPlayer) 0.16f..0.38f else 0.62f..0.84f

    fun setEmotion(e: Emotion, now: Float) {
        if (e != emotion) { markStart = now; prevEmotion = emotion }
        emotion = e
        targetExpr = Expr.of(e)
    }

    fun setGesture(g: Gesture, now: Float) {
        gesture = g; gestureStart = now
        gestureHold = when (g) {
            Gesture.CROSS_ARMS, Gesture.HAND_ON_HIP -> 6f
            Gesture.NOD, Gesture.SHAKE_HEAD -> 1.4f
            Gesture.TURN_AWAY -> 2.6f
            else -> 3.0f
        }
        val toward = if (isPlayer) 1f else -1f
        when (g) {
            Gesture.STEP_CLOSER -> targetX = (homeX + toward * 0.05f).coerceIn(range).also { homeX = it }
            Gesture.STEP_BACK -> targetX = (homeX - toward * 0.05f).coerceIn(range).also { homeX = it }
            Gesture.TURN_AWAY -> turnAwayUntil = now + 2.6f
            else -> {}
        }
    }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var phase: Float, var life: Float = 1f)
class Silhouette(var x: Float, val speed: Float, val depth: Float, val tall: Float, val shade: Float, val umbrella: Boolean)

enum class Ambient { DUST, RAIN, FIREFLIES, STARS, PETALS, BOKEH }

/**
 * The reusable 2D scene engine: owns actors, camera, ambient life and environmental events, and advances them
 * every frame. Rendering is done by [SceneRenderer]; the AI/voice layer only calls the small public API below.
 */
class SceneEngine(val setting: SettingId, playerApp: Appearance, npcSpec: CharacterSpec) {
    var frame by mutableLongStateOf(0L)
        private set

    val player = Actor("player", playerApp, true, 0.27f)
    val npc = Actor(npcSpec.id, npcSpec.appearance, false, 0.73f)
    val actors = listOf(player, npc)

    var time = 0f; private set
    var camX = 0f; private set      // in stage-width fractions
    var zoom = 1f; private set
    var focusId: String? = null
    var reduceMotion = false

    /** Cinematic editor: which shot is on screen (close-up / OTS / medium / wide), cut by who speaks and events. */
    val director = ShotDirector(npcSpec.id)
    /** VHS rewind effect strength 0..1 (after the player rewinds a bad turn). */
    var vhs = 0f; private set
    private var vhsUntil = -1f

    // environment
    var rain = if (setting == SettingId.STREET) 1f else 0f; private set
    private var rainTarget = rain
    var dim = 0f; private set
    private var dimTarget = 0f
    var flicker = 0f; private set
    private var flickerUntil = 0f
    var wind = 0f; private set
    private var windUntil = 0f
    var flash = 0f; private set
    var fade = 1f; private set          // 1 = black
    private var fadeTarget = 0f
    var punch = 0f; private set

    val particles = mutableListOf<Particle>()
    val drops = mutableListOf<Particle>()
    val silhouettes = mutableListOf<Silhouette>()
    val ambients: Set<Ambient> = when (setting) {
        SettingId.CORRIDOR, SettingId.CLASSROOM, SettingId.LIBRARY -> setOf(Ambient.DUST)
        SettingId.CAFE -> setOf(Ambient.BOKEH, Ambient.DUST)
        SettingId.FOREST -> setOf(Ambient.FIREFLIES)
        SettingId.SPACESHIP -> setOf(Ambient.STARS)
        SettingId.STREET -> setOf(Ambient.BOKEH)
        SettingId.PARK -> setOf(Ambient.PETALS)
    }
    val hasCrowd = setting in setOf(SettingId.CORRIDOR, SettingId.STREET, SettingId.PARK, SettingId.CAFE, SettingId.CLASSROOM)
    private var nextSilhouette = 3f
    private val rnd = Random(42)

    init {
        repeat(when { Ambient.STARS in ambients -> 50; Ambient.FIREFLIES in ambients -> 34; Ambient.PETALS in ambients -> 26; Ambient.BOKEH in ambients -> 16; else -> 42 }) {
            particles += newParticle(initial = true)
        }
        repeat(140) { drops += Particle(rnd.nextFloat(), rnd.nextFloat(), 0f, 0f, 0.5f + rnd.nextFloat(), rnd.nextFloat()) }
        player.setEmotion(Emotion.NEUTRAL, 0f)
        this.npc.setEmotion(Emotion.NEUTRAL, 0f)
    }

    private fun newParticle(initial: Boolean): Particle {
        val r = rnd
        return when {
            Ambient.STARS in ambients -> Particle(r.nextFloat(), r.nextFloat() * 0.5f, 0f, 0f, 1f + r.nextFloat() * 2.5f, r.nextFloat() * 6f)
            Ambient.FIREFLIES in ambients -> Particle(r.nextFloat(), 0.25f + r.nextFloat() * 0.65f, (r.nextFloat() - 0.5f) * 0.02f, (r.nextFloat() - 0.5f) * 0.02f, 2f + r.nextFloat() * 3f, r.nextFloat() * 6f)
            Ambient.PETALS in ambients -> Particle(r.nextFloat() * 1.2f, if (initial) r.nextFloat() else -0.05f, -0.02f - r.nextFloat() * 0.03f, 0.04f + r.nextFloat() * 0.05f, 5f + r.nextFloat() * 6f, r.nextFloat() * 6f)
            Ambient.BOKEH in ambients -> Particle(r.nextFloat(), r.nextFloat() * 0.7f, (r.nextFloat() - 0.5f) * 0.01f, -0.004f - r.nextFloat() * 0.006f, 18f + r.nextFloat() * 40f, r.nextFloat() * 6f)
            else -> Particle(r.nextFloat(), r.nextFloat() * 0.9f, (r.nextFloat() - 0.3f) * 0.01f, (r.nextFloat() - 0.5f) * 0.006f, 1.5f + r.nextFloat() * 2.5f, r.nextFloat() * 6f)
        }
    }

    // ---------------------------------------------------------------- public API

    fun actor(id: String): Actor? = actors.firstOrNull { it.id == id }

    /** Cinematic opening: fade in, the girl looks away, the player walks in from the side. */
    fun startEntrance(withWalk: Boolean) {
        fade = 1f; fadeTarget = 0f
        if (withWalk) {
            player.x = -0.25f; player.targetX = player.homeX
            npc.turnAwayUntil = time + 2.4f
            director.intro(time)
        } else director.reset(time)
        focusId = null
    }

    fun react(id: String, emotion: Emotion, gesture: Gesture) {
        val a = actor(id) ?: return
        a.setEmotion(emotion, time)
        if (gesture != Gesture.NONE) a.setGesture(gesture, time)
    }

    fun setThinking(id: String, thinking: Boolean) { actor(id)?.thinkingTarget = if (thinking) 1f else 0f }

    fun fadeOutIn() { fade = 0f; fadeTarget = 1f; pendingFadeBack = true }
    private var pendingFadeBack = false

    /** Plays the VHS rewind (grayscale, scanlines, tracking jitter) and returns the camera to a calm close-up. */
    fun rewindFx(seconds: Float = 1.5f) {
        vhsUntil = time + seconds
        npc.visible = true; npc.x = npc.homeX; npc.targetX = npc.homeX
        director.reset(time + seconds)
    }

    fun event(e: SceneEvent) {
        director.onEvent(e, time)
        when (e) {
            SceneEvent.LIGHT_FLICKER -> flickerUntil = time + 1.6f
            SceneEvent.CROWD_PASSES -> repeat(4) { i -> spawnSilhouette(near = true, offset = i * 0.18f) }
            SceneEvent.WIND_GUST -> windUntil = time + 3f
            SceneEvent.RAIN_START -> rainTarget = 1f
            SceneEvent.RAIN_STOP -> rainTarget = 0f
            SceneEvent.LIGHTS_DIM -> dimTarget = 0.38f
            SceneEvent.LIGHTS_BRIGHT -> dimTarget = 0f
            SceneEvent.CHARACTER_EXITS -> { npc.targetX = 1.35f; npc.alphaTarget = 1f }
            SceneEvent.CHARACTER_ENTERS -> { npc.visible = true; if (npc.x > 1.1f) npc.targetX = npc.homeX else npc.targetX = npc.homeX }
            SceneEvent.PLOT_TWIST -> { flash = 1f; punch = 1f; flickerUntil = time + 0.8f }
            SceneEvent.CHAPTER_COMPLETE, SceneEvent.ENDING -> { flash = 0.6f }
            SceneEvent.NONE -> {}
        }
    }

    private fun spawnSilhouette(near: Boolean, offset: Float = 0f) {
        val dir = if (rnd.nextBoolean()) 1f else -1f
        val depth = if (near) 0.75f + rnd.nextFloat() * 0.15f else 0.35f + rnd.nextFloat() * 0.2f
        silhouettes += Silhouette(
            x = if (dir > 0) -0.2f - offset else 1.2f + offset,
            speed = dir * (0.05f + rnd.nextFloat() * 0.05f) * (if (near) 1.8f else 1f),
            depth = depth, tall = 0.9f + rnd.nextFloat() * 0.2f, shade = rnd.nextFloat(),
            umbrella = setting == SettingId.STREET && rain > 0.5f,
        )
    }

    // ---------------------------------------------------------------- per-frame update

    fun update(dtRaw: Float, speaking: SpeakingState?) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        val t = time
        val nowMs = System.currentTimeMillis()

        // camera
        val speaker = speaking?.speakerId
        val focus = speaker ?: focusId ?: if (npc.thinkingTarget > 0.5f) npc.id else null
        val fa = focus?.let { actor(it) }
        val camTarget = if (fa != null && fa.visible && fa.x in -0.1f..1.1f) (fa.x - 0.5f) * 0.32f else 0f
        val zoomTarget = (if (fa != null) 1.13f else 1.0f) + punch * 0.12f
        val kc = 1f - exp(-dt * 2.0f)
        camX += (camTarget - camX) * kc
        zoom += (zoomTarget - zoom) * kc
        punch = max(0f, punch - dt * 1.5f)

        // environment
        rain += (rainTarget - rain) * (1f - exp(-dt * 0.8f))
        dim += (dimTarget - dim) * (1f - exp(-dt * 1.5f))
        flicker = if (t < flickerUntil) (if (sin(t * 47f) + sin(t * 23f) > 0.6f) 0.5f else 0.05f)
            else if (setting == SettingId.CORRIDOR || setting == SettingId.CLASSROOM || setting == SettingId.STREET) {
                // the occasional tube-light / neon hiccup
                val n = sin(t * 0.37f) * sin(t * 1.73f) * sin(t * 13f)
                if (n > 0.93f) 0.18f else 0f
            } else 0f
        wind += ((if (t < windUntil) 1f else 0f) - wind) * (1f - exp(-dt * 2f))
        flash = max(0f, flash - dt * 1.4f)
        fade += (fadeTarget - fade) * (1f - exp(-dt * 3f))
        if (pendingFadeBack && fade > 0.97f) { pendingFadeBack = false; fadeTarget = 0f }

        vhs = if (t < vhsUntil) 1f else max(0f, vhs - dt * 3f)
        director.update(t, speaking?.speakerId ?: focusId, npc.thinkingTarget > 0.5f)

        updateParticles(dt, t)
        updateSilhouettes(dt)

        for (a in actors) updateActor(a, dt, t, nowMs, speaking)
        frame++
    }

    private fun updateParticles(dt: Float, t: Float) {
        val windPush = wind * 0.25f
        for (p in particles) {
            when {
                Ambient.STARS in ambients -> {}
                Ambient.PETALS in ambients -> {
                    p.x += (p.vx - windPush + sin(t + p.phase) * 0.01f) * dt
                    p.y += p.vy * dt
                    if (p.y > 1.05f || p.x < -0.1f) { val n = newParticle(false); p.x = n.x; p.y = n.y; p.vx = n.vx; p.vy = n.vy }
                }
                else -> {
                    p.x += (p.vx + sin(t * 0.5f + p.phase) * 0.006f - windPush * 0.3f) * dt
                    p.y += (p.vy + cos(t * 0.4f + p.phase) * 0.004f) * dt
                    if (p.x < -0.05f) p.x = 1.05f; if (p.x > 1.05f) p.x = -0.05f
                    if (p.y < -0.05f) p.y = 0.95f; if (p.y > 1.0f) p.y = 0.0f
                }
            }
        }
        if (rain > 0.02f) for (d in drops) {
            d.y += dt * (1.6f + d.size * 0.6f)
            d.x -= dt * (0.25f + wind * 0.4f)
            if (d.y > 1f) { d.y = -0.05f - rnd.nextFloat() * 0.1f; d.x = rnd.nextFloat() * 1.3f; d.phase = 0f }
        }
    }

    private fun updateSilhouettes(dt: Float) {
        if (hasCrowd && !reduceMotion) {
            nextSilhouette -= dt
            if (nextSilhouette <= 0f) { spawnSilhouette(near = false); nextSilhouette = 4f + rnd.nextFloat() * 7f }
        }
        silhouettes.forEach { it.x += it.speed * dt }
        silhouettes.removeAll { it.x < -0.4f || it.x > 1.4f }
    }

    private fun updateActor(a: Actor, dt: Float, t: Float, nowMs: Long, speaking: SpeakingState?) {
        val p = a.pose
        val other = if (a === player) npc else player
        val ke = 1f - exp(-dt * 9f)
        p.t = t
        p.emotion = a.emotion
        p.expr = p.expr.lerpTo(a.targetExpr, ke)

        // locomotion
        val dx = a.targetX - a.x
        val walking = abs(dx) > 0.004f
        if (walking) {
            val step = sign(dx) * min(abs(dx), 0.2f * dt)
            a.x += step
            p.legPhase += dt * 9f
        }
        p.walk += ((if (walking) 1f else 0f) - p.walk) * (1f - exp(-dt * 8f))
        if (!walking && a === npc && a.x > 1.25f) a.visible = false else if (a.x < 1.2f) a.visible = true

        // facing
        val turnedAway = t < a.turnAwayUntil
        a.drawFacing = when {
            walking -> sign(dx)
            turnedAway -> -a.baseFacing
            else -> a.baseFacing
        }

        // breathing, sway
        val m = if (reduceMotion) 0.4f else 1f
        p.breath = sin(t * 2f * PI.toFloat() / 3.8f + a.phase)
        p.lean = sin(t * 0.6f + a.phase) * 0.8f * m + (if (a.listening) 1.4f else 0f)
        p.bob = -abs(sin(p.legPhase)) * 12f * p.walk
        p.hairSway = (sin(t * 1.3f + a.phase) * 4f + wind * 16f * sin(t * 6f) + p.walk * 7f * sin(p.legPhase)) * m

        // blink
        if (t > a.nextBlink && a.blinkStart < 0f) a.blinkStart = t
        if (a.blinkStart >= 0f) {
            val bt = (t - a.blinkStart) / 0.14f
            p.blink = if (bt < 1f) sin(bt * PI.toFloat()) else 0f
            if (bt >= 1f) { a.blinkStart = -1f; a.nextBlink = t + if (Random.nextFloat() < 0.15f) 0.25f else 2.2f + Random.nextFloat() * 3.3f }
        }

        // gaze
        val thinking = a.thinkingTarget
        p.thinking += (thinking - p.thinking) * (1f - exp(-dt * 6f))
        val gazeTx = when { p.thinking > 0.5f -> 0.2f; a.emotion == Emotion.SHY -> -0.5f; turnedAway -> 0.8f; else -> 0.6f }
        val gazeTy = when { p.thinking > 0.5f -> -0.7f; a.emotion == Emotion.SHY -> 0.6f; a.emotion == Emotion.SAD -> 0.4f; else -> 0f }
        p.gazeX += (gazeTx - p.gazeX) * ke
        p.gazeY += (gazeTy - p.gazeY) * ke

        // gestures -> arm targets
        val gt = t - a.gestureStart
        val g = if (gt < a.gestureHold) a.gesture else Gesture.NONE
        val swayArm = sin(t * 0.9f + a.phase) * 2.5f * m
        var back = ArmPose(6f + swayArm, 10f)
        var front = ArmPose(6f - swayArm, 10f)
        var tilt = when (a.emotion) { Emotion.CONFUSED -> 7f; Emotion.SHY -> -6f; Emotion.SAD -> -3f; Emotion.AMUSED -> 3f; else -> 0f }
        var hx = 0f; var hy = 0f; var shoulder = 0f; var bounce = 0f
        val decay = (1f - (gt / a.gestureHold)).coerceIn(0f, 1f)
        when (g) {
            Gesture.WAVE -> front = ArmPose(150f, 20f + sin(gt * 11f) * 28f)
            Gesture.NOD -> hy = sin(gt * 11f) * 7f * decay
            Gesture.SHAKE_HEAD -> hx = sin(gt * 13f) * 8f * decay
            Gesture.SHRUG -> { back = ArmPose(30f, -75f); front = ArmPose(30f, -75f); shoulder = 14f; tilt += 5f }
            Gesture.CROSS_ARMS -> { back = ArmPose(-6f, 112f); front = ArmPose(-10f, 118f) }
            Gesture.HAND_ON_HIP -> { front = ArmPose(40f, 70f); back = ArmPose(40f, 70f) }
            Gesture.THINK_CHIN -> { front = ArmPose(-30f, 140f, 0.5f); back = ArmPose(-6f, 100f); tilt += 4f }
            Gesture.POINT -> front = ArmPose(78f, 8f)
            Gesture.COVER_FACE -> { back = ArmPose(-20f, 160f, 0.4f); front = ArmPose(-20f, 160f, 0.4f); tilt -= 6f; hy = 6f }
            Gesture.LAUGH -> { front = ArmPose(-30f, 140f, 0.5f); bounce = -abs(sin(gt * 13f)) * 8f * decay; tilt += 4f }
            Gesture.FACEPALM -> { front = ArmPose(-30f, 140f, 0.3f); tilt -= 6f; hy = 8f }
            Gesture.HEAD_TILT -> tilt += 11f
            Gesture.STEP_CLOSER, Gesture.STEP_BACK, Gesture.TURN_AWAY, Gesture.NONE -> {}
        }
        if (p.thinking > 0.5f && g == Gesture.NONE) { front = ArmPose(-30f, 140f, 0.5f); back = ArmPose(-6f, 100f); tilt += 4f }
        if (a.listening && g == Gesture.NONE) {
            // small acknowledging nods while the other person talks
            val nodPhase = (t + a.phase) % 3.2f
            if (nodPhase < 0.5f) hy += sin(nodPhase / 0.5f * PI.toFloat()) * 4f
            tilt += 3f
        }
        val ka = 1f - exp(-dt * 7f)
        p.backArm = p.backArm.lerpTo(back, ka)
        p.frontArm = p.frontArm.lerpTo(front, ka)
        p.headTilt += (tilt - p.headTilt) * ka
        p.headShiftX += (hx - p.headShiftX) * (1f - exp(-dt * 20f))
        p.headShiftY += (hy - p.headShiftY) * (1f - exp(-dt * 20f))
        p.shoulderRaise += (shoulder - p.shoulderRaise) * ka
        p.bob += bounce

        // mouth / lip-sync
        val target = if (speaking != null && speaking.speakerId == a.id) {
            val env = speaking.envelope
            when {
                env != null && env.isNotEmpty() -> {
                    val idx = ((nowMs - speaking.startedAt) / 50L).toInt()
                    if (idx in env.indices) (env[idx] * 1.2f).coerceIn(0f, 1f) else 0f
                }
                speaking.hasWordEvents -> if (nowMs - speaking.lastWordAt < 380) 0.2f + 0.65f * abs(sin(t * 17f + a.phase)) * (0.7f + 0.3f * sin(t * 4.3f)) else 0.04f
                else -> 0.15f + 0.6f * abs(sin(t * 15f)) * (0.6f + 0.4f * sin(t * 3.3f))
            }
        } else 0f
        a.mouth += (target - a.mouth) * (1f - exp(-dt * 28f))
        p.mouthOpen = a.mouth

        // comic marks
        val since = t - a.markStart
        val persistent = a.emotion == Emotion.SHY || a.emotion == Emotion.SAD
        p.markAlpha = when {
            since < 0.25f -> since / 0.25f
            since < 2.8f -> 1f
            since < 3.4f -> if (persistent) 1f - (since - 2.8f) / 0.6f * 0.5f else 1f - (since - 2.8f) / 0.6f
            else -> if (persistent) 0.5f else 0f
        }

        // fade for exits
        p.alpha = if (a.visible) 1f else 0f
        a.listening = speaking != null && speaking.speakerId == other.id
    }
}
