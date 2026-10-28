package com.kissa.app.ai

/**
 * Lightweight on-device content filter for user-created stories and player messages.
 * The Gemini safety filters are the second layer; this blocks the obvious cases early.
 */
object Moderation {
    private val blocked = listOf(
        "rape", "child porn", "minor sex", "underage sex", "incest", "bestiality",
        "kill myself", "suicide method", "how to make a bomb", "nazi", "terrorist attack plan",
        "nude", "nudes", "sex scene", "porn", "chudai", "randi", "bhosdi", "madarchod", "behenchod",
    )
    private val selfHarm = listOf("kill myself", "end my life", "suicide", "khudkushi", "mar jaana chahta", "mar jana chahti")

    data class Verdict(val allowed: Boolean, val reason: String? = null, val selfHarm: Boolean = false)

    fun check(text: String): Verdict {
        val t = " " + text.lowercase().replace(Regex("[^a-z\\u0900-\\u097F ]"), " ").replace(Regex("\\s+"), " ") + " "
        if (selfHarm.any { t.contains(it) }) return Verdict(false, "self-harm", selfHarm = true)
        val hit = blocked.firstOrNull { t.contains(" ${it.trim()} ") || (it.contains(' ') && t.contains(it)) }
        return if (hit != null) Verdict(false, "not allowed in Kissa stories") else Verdict(true)
    }
}
