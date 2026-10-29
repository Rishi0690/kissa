package com.kissa.app.rig

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.kissa.app.game.Look
import kotlinx.serialization.json.*

/** One layer image of the rig kit with its pivot (pixels) and optional metadata (limb length, grip point…). */
data class PartInfo(val w: Int, val h: Int, val px: Float, val py: Float, val extra: JsonObject)

data class FaceAnchors(val w: Float, val chin: Float, val crown: Float, val eyeY: Float, val eyeDx: Float, val eyeW: Float, val eyeH: Float, val browY: Float, val noseY: Float, val mouthY: Float, val mouthW: Float)

/**
 * The shared rig kit (spec §5): skeleton joint positions for the two base rigs, face anchors per head shape,
 * and every layer image (skin, faces, hair, outfits, accessories, props) with its pivot — read from assets/rig/rig.json.
 * Images are loaded lazily through [loader] (Android assets on device, PNG mirror in JVM tests) and cached.
 */
class RigKit(manifestJson: String, private val loader: (String) -> ImageBitmap?) {
    val skeleton: Map<String, Map<String, Pair<Float, Float>>>
    val faces: Map<String, FaceAnchors>
    val parts: Map<String, PartInfo>
    private val cache = HashMap<String, ImageBitmap?>()

    init {
        val o = Json.parseToJsonElement(manifestJson).jsonObject
        skeleton = o["skeleton"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonObject.mapValues { (_, p) -> val a = p.jsonArray; a[0].jsonPrimitive.float to a[1].jsonPrimitive.float }
        }
        faces = o["face"]!!.jsonObject.mapValues { (_, v) ->
            val f = v.jsonObject
            fun g(k: String) = f[k]!!.jsonPrimitive.float
            FaceAnchors(g("w"), g("chin"), g("crown"), g("eye_y"), g("eye_dx"), g("eye_w"), g("eye_h"), g("brow_y"), g("nose_y"), g("mouth_y"), g("mouth_w"))
        }
        parts = o["parts"]!!.jsonObject.mapValues { (_, v) ->
            val p = v.jsonObject
            PartInfo(p["w"]!!.jsonPrimitive.int, p["h"]!!.jsonPrimitive.int, p["px"]!!.jsonPrimitive.float, p["py"]!!.jsonPrimitive.float, p)
        }
    }

    fun image(rel: String): ImageBitmap? = synchronized(cache) { cache.getOrPut(rel) { if (parts.containsKey(rel)) loader(rel) else null } }
    fun info(rel: String): PartInfo? = parts[rel]
    fun joint(rig: String, name: String): Pair<Float, Float> = skeleton[rig]?.get(name) ?: skeleton["female"]!![name] ?: (0f to 0f)

    /** Number of distinct catalog layers available (for honest asset counts). */
    fun count(prefix: String) = parts.keys.count { it.startsWith(prefix) }

    companion object {
        val skinTones = listOf(Color(0xFFFFE3CC), Color(0xFFF3C9A4), Color(0xFFE0AE84), Color(0xFFBC8760), Color(0xFF8E5E42))
        val hairColors = mapOf(
            "black" to Color(0xFF4A4552), "dark_brown" to Color(0xFF6B4A3A), "brown" to Color(0xFF9A6A48),
            "auburn" to Color(0xFFB2593A), "burgundy" to Color(0xFF8A3346), "ash" to Color(0xFFB8B8C0),
        )
        val outfitColors = mapOf(
            "navy" to Color(0xFF3A4878), "blue" to Color(0xFF5A86D0), "maroon" to Color(0xFF8E3040), "black" to Color(0xFF45454E),
            "white" to Color(0xFFF4F4F4), "olive" to Color(0xFF7C8650), "mustard" to Color(0xFFE0AE48), "pink" to Color(0xFFE79AB8),
            "teal" to Color(0xFF3C9494), "grey" to Color(0xFF8E929C),
        )
        val eyeColors = mapOf("dark" to Color(0xFF3A2A24), "brown" to Color(0xFF6E4628), "hazel" to Color(0xFF86703E), "grey" to Color(0xFF6E7A86))
        fun skin(look: Look) = skinTones[look.skin.coerceIn(0, skinTones.size - 1)]
        fun hair(look: Look) = hairColors[look.hairColor] ?: hairColors.getValue("black")
        fun cloth(look: Look) = outfitColors[look.outfitColor] ?: outfitColors.getValue("navy")
        fun eyes(look: Look) = eyeColors[look.eyes] ?: eyeColors.getValue("dark")
    }
}
