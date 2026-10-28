package com.kissa.app.scene

import androidx.compose.ui.graphics.ImageBitmap
import com.kissa.app.model.Emotion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Painterly character art sets (generated illustration frames shipped in assets/cast/<id>/).
 * Each emotion has a full close-up frame plus two small feathered patches drawn on top of it at a fixed offset:
 * `_mouth` (mouth open, alpha-blended by speech amplitude) and `_blink` (eyes closed). Patches come from edits of
 * the same frame, so swapping them never shifts the face. Extra shots: body/stand (medium), ots (back of head for
 * over-the-shoulder), ots_blur (pre-blurred depth-of-field version), walk (walking away).
 */
@Serializable data class Patch(val x: Int, val y: Int, val w: Int, val h: Int)
@Serializable data class EmotionFrame(val w: Int, val h: Int, val mouth: Patch? = null, val blink: Patch? = null, val bbox: List<Int> = emptyList())
@Serializable data class ShotFrame(val w: Int, val h: Int, val bbox: List<Int> = emptyList())
@Serializable data class FaceAnchor(val cx: Float, val cy: Float, val h: Float)
@Serializable data class CastEntry(val emotions: Map<String, EmotionFrame>, val shots: Map<String, ShotFrame> = emptyMap(), val face: FaceAnchor)

object CastManifest {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): Map<String, CastEntry> = json.decodeFromString(text)

    /** App emotion -> art emotion name, with graceful substitutes when a set lacks one. */
    fun emotionKey(e: Emotion, available: Set<String>): String {
        val wanted = when (e) {
            Emotion.NEUTRAL -> listOf("neutral")
            Emotion.HAPPY -> listOf("happy", "amused", "neutral")
            Emotion.SHY -> listOf("shy", "happy", "neutral")
            Emotion.SURPRISED -> listOf("surprised", "neutral")
            Emotion.CONFUSED -> listOf("confused", "neutral")
            Emotion.SAD -> listOf("sad", "shy", "neutral")
            Emotion.ANGRY -> listOf("angry", "confused", "neutral")
            Emotion.AMUSED -> listOf("amused", "happy", "neutral")
        }
        return wanted.firstOrNull { it in available } ?: available.firstOrNull() ?: "neutral"
    }
}

/** One character's loaded art. Bitmaps are decoded lazily and kept (a set is ~25 frames, decoded on demand). */
class CastSet(val id: String, val entry: CastEntry, private val loader: (String) -> ImageBitmap?) {
    private val cache = HashMap<String, ImageBitmap?>()

    fun image(name: String): ImageBitmap? = synchronized(cache) { cache.getOrPut(name) { loader("cast/$id/$name.webp") } }

    fun key(e: Emotion) = CastManifest.emotionKey(e, entry.emotions.keys)
    fun frame(e: Emotion): EmotionFrame? = entry.emotions[key(e)]
    fun base(e: Emotion) = image(key(e))
    fun mouth(e: Emotion) = frame(e)?.mouth?.let { image(key(e) + "_mouth") }
    fun blink(e: Emotion) = frame(e)?.blink?.let { image(key(e) + "_blink") }
    fun shot(name: String) = if (entry.shots.containsKey(name)) image(name) else null
    fun shotFrame(name: String) = entry.shots[name]
    fun has(name: String) = entry.shots.containsKey(name)

    /** Decode every close-up frame up front (call off the main thread) so the first cuts don't hitch. */
    fun warm() { entry.emotions.keys.forEach { k -> image(k); if (entry.emotions[k]?.mouth != null) image(k + "_mouth"); if (entry.emotions[k]?.blink != null) image(k + "_blink") } }
}

/** Which characters have painterly sets. Others fall back to the procedural vector rig inside the same shot system. */
object CastRegistry {
    fun castIdFor(characterId: String): String? = when (characterId) {
        "riya" -> "riya"
        else -> null
    }

    /** The painterly player set is used where the scene's character also has one, so art styles never mix. */
    fun playerCastId(npcCastId: String?, feminine: Boolean): String? = if (npcCastId != null && !feminine) "kabir" else null
}
