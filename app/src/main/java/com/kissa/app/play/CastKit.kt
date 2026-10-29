package com.kissa.app.play

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.json.*

/**
 * v4 painted cast (assets/cast/<id>/, built by tools/artgen/castgen.py).
 * One full painted body (`base.webp`, never changes) + per-expression face patches at one fixed rect + small
 * feathered mouth-open / eyes-closed patches. All patches come from pixel-aligned edits of the same painting, so
 * crossfading expressions, blinking and lip-sync can never misalign or pop.
 */
data class Rect4(val x: Int, val y: Int, val r: Int, val b: Int) {
    val w get() = r - x
    val h get() = b - y
    companion object { fun of(a: JsonArray?) = a?.let { Rect4(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int, it[2].jsonPrimitive.int, it[3].jsonPrimitive.int) } }
}

data class ExprPatches(val mouth: Rect4?, val blink: Rect4?)

class CastSet(val id: String, val w: Int, val h: Int, val body: Rect4, val face: Rect4, val headX: Float, val headY: Float,
              val exprs: Map<String, ExprPatches>, private val loader: (String) -> ImageBitmap?) {
    private val cache = HashMap<String, ImageBitmap?>()
    fun image(name: String): ImageBitmap? = synchronized(cache) { cache.getOrPut(name) { loader("cast/$id/$name.webp") } }
    val base: ImageBitmap? get() = image("base")
    fun face(expr: String): ImageBitmap? = if (expr == "neutral") null else image(expr)
    fun mouth(expr: String): ImageBitmap? = exprs[expr]?.mouth?.let { image("${expr}_mouth") }
    fun blink(expr: String): ImageBitmap? = exprs[expr]?.blink?.let { image("${expr}_blink") }

    /** Decode everything up front (call off the main thread) so the first expression change never hitches. */
    fun warm() { base; exprs.forEach { (e, p) -> face(e); if (p.mouth != null) mouth(e); if (p.blink != null) blink(e) } }

    /** Game emotion (20 catalog expressions) -> the closest painted expression this character has. */
    fun exprFor(emotion: String?): String = CastKit.mapEmotion(emotion, exprs.keys)

    companion object {
        fun parse(id: String, text: String, loader: (String) -> ImageBitmap?): CastSet {
            val o = Json.parseToJsonElement(text).jsonObject
            val ex = o["expr"]!!.jsonObject.mapValues { (_, v) -> val e = v.jsonObject; ExprPatches(Rect4.of(e["mouth"] as? JsonArray), Rect4.of(e["blink"] as? JsonArray)) }
            val head = o["head"]?.jsonArray
            return CastSet(id, o["w"]!!.jsonPrimitive.int, o["h"]!!.jsonPrimitive.int, Rect4.of(o["body"]!!.jsonArray)!!, Rect4.of(o["face"]!!.jsonArray)!!,
                head?.get(0)?.jsonPrimitive?.float ?: 384f, head?.get(1)?.jsonPrimitive?.float ?: 400f, ex, loader)
        }
    }
}

object CastKit {
    /** Painted expression preference order for each of the 20 catalog expressions. */
    private val prefs: Map<String, List<String>> = mapOf(
        "neutral" to listOf("neutral"),
        "smile" to listOf("happy", "neutral"), "happy" to listOf("happy", "neutral"), "laugh" to listOf("happy", "neutral"),
        "angry" to listOf("angry", "neutral"), "furious" to listOf("angry", "neutral"), "disgusted" to listOf("angry", "smug", "neutral"),
        "sad" to listOf("sad", "neutral"), "crying" to listOf("sad", "neutral"), "worried" to listOf("sad", "scared", "neutral"),
        "scared" to listOf("scared", "surprised", "sad", "neutral"), "shocked" to listOf("surprised", "scared", "neutral"),
        "surprised" to listOf("surprised", "neutral"), "shy" to listOf("shy", "happy", "neutral"), "blushing" to listOf("shy", "happy", "neutral"),
        "smug" to listOf("smug", "happy", "neutral"), "bored" to listOf("smug", "neutral"), "thinking" to listOf("neutral"),
        "sleepy" to listOf("sad", "neutral"), "determined" to listOf("neutral"),
    )

    fun mapEmotion(emotion: String?, available: Set<String>): String =
        (prefs[emotion ?: "neutral"] ?: listOf(emotion ?: "neutral", "neutral")).firstOrNull { it in available } ?: "neutral"

    /** Character id -> painted cast id (game JSON `art`, default = character id). Player looks: player_m / player_f. */
    fun playerCast(rig: String) = if (rig == "female") "player_f" else "player_m"
}
