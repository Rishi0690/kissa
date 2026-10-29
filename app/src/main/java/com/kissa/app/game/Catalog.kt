package com.kissa.app.game

/**
 * The shared v1 asset kit (spec §5). Games may only reference IDs listed here; the validator rejects anything else.
 * The renderer (rig/) draws every one of these on both base rigs.
 */
object Catalog {
    val rigs = listOf("female", "male")

    /** 20 expressions = face swaps (drawn as layered face parts on the shared head). */
    val expressions = listOf(
        "neutral", "smile", "happy", "laugh", "angry", "furious", "sad", "crying", "shocked", "surprised",
        "shy", "blushing", "smug", "worried", "scared", "disgusted", "bored", "thinking", "sleepy", "determined",
    )

    /** 20 named move clips (keyframe animations on the shared skeleton). */
    val moves = listOf(
        "enter", "exit", "walk", "walk_away", "idle", "kneel", "hold_phone", "look_down", "nod", "shake_head",
        "point", "cross_arms", "wave", "sit", "turn", "laugh", "cry", "shrug", "hands_on_hips", "step_back",
    )

    /** 10 layered backgrounds (back / mid / front). Day, night, dusk and rain are filters, not new artwork. */
    val backgrounds = listOf(
        "college_corridor", "classroom", "library", "cafe", "street", "park", "bedroom", "office", "rooftop", "forest",
    )

    /** Spec §6 example ids like "college_day" resolve to a background + time-of-day filter. */
    val backgroundAliases = mapOf(
        "college_day" to ("college_corridor" to "day"),
        "college_night" to ("college_corridor" to "night"),
        "college" to ("college_corridor" to "day"),
        "canteen" to ("cafe" to "day"),
    )

    val times = listOf("day", "dusk", "night")
    val weathers = listOf("clear", "rain")

    /** 20 props, each snapping to an anchor. */
    val props = listOf(
        "phone", "rose", "bag", "coffee_cup", "chai_glass", "book", "notebook", "laptop", "umbrella", "guitar",
        "earphones", "id_card", "file_folder", "gift_box", "letter", "water_bottle", "keys", "cap", "mic", "ring_box",
    )
    val anchors = listOf("hand_l", "hand_r", "head", "back")
    /** Default anchor per prop when a step does not name one. */
    fun defaultAnchor(prop: String) = when (prop) {
        "cap" -> "head"
        "bag", "guitar" -> "back"
        else -> "hand_r"
    }

    // ---- character parts (mix-and-match layers on the shared skeleton) ----
    val skins = 5 // skin tone indices 0..4
    val hairs = listOf(
        "long_straight", "long_wavy", "ponytail", "bob", "bun", "braid",
        "short_messy", "side_part", "curly", "buzz", "undercut", "man_bun",
    )
    val hairColors = listOf("black", "dark_brown", "brown", "auburn", "burgundy", "ash")
    val outfits = listOf(
        "college_blazer", "hoodie", "kurta", "tshirt_jeans", "formal_shirt", "denim_jacket", "sweater", "party_dress",
    )
    val outfitColors = listOf("navy", "blue", "maroon", "black", "white", "olive", "mustard", "pink", "teal", "grey")
    val faces = listOf("soft", "sharp", "round")
    val eyeColors = listOf("dark", "brown", "hazel", "grey")
    val accessories = listOf("glasses_round", "glasses_square", "earrings", "bindi", "watch", "scarf", "backpack", "beard")

    val music = listOf("calm_campus", "tension", "night_city", "romance", "sad_piano", "upbeat")

    val styles = listOf("timid", "confident", "rude", "funny", "kind", "cold", "neutral")

    /** Hair `long_black` (spec example) = style long_straight + colour black. */
    fun splitHair(id: String): Pair<String, String?> {
        if (id in hairs) return id to null
        val c = hairColors.firstOrNull { id.endsWith("_$it") }
        if (c != null) {
            val base = id.removeSuffix("_$c")
            val style = when (base) { "long" -> "long_straight"; "short" -> "short_messy"; else -> base }
            if (style in hairs) return style to c
        }
        return id to null
    }

    /** Outfit `college_blue` (spec example) = outfit + colour. */
    fun splitOutfit(id: String): Pair<String, String?> {
        if (id in outfits) return id to null
        val c = outfitColors.firstOrNull { id.endsWith("_$it") }
        if (c != null) {
            val base = id.removeSuffix("_$c")
            val o = if (base == "college") "college_blazer" else base
            if (o in outfits) return o to c
        }
        return id to null
    }
}
