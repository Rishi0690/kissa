package com.kissa.app.game

import org.junit.Assert.*
import org.junit.Test

class ConditionAndIntentTest {
    private val vars = mapOf("affection" to V.Num(12.0), "trust" to V.Num(5.0), "helped_isha" to V.Bool(false), "rank_name" to V.Str("Bold"), "has(rose)" to V.Bool(true), "obj.o2" to V.Str("done"))
    private val scope = Condition.Scope { vars[it] }
    private fun t(c: String) = Condition.eval(c, scope)

    @Test fun conditions() {
        assertTrue(t("affection >= 10 and trust >= 5"))
        assertFalse(t("affection >= 10 and trust > 5"))
        assertTrue(t("helped_isha == false"))
        assertTrue(t("helped_isha = false"))
        assertTrue(t("not helped_isha"))
        assertTrue(t("!helped_isha && trust == 5"))
        assertTrue(t("trust < 0 or (affection > 11 and rank_name == \"Bold\")"))
        assertTrue(t("rank_name == 'bold'"))
        assertTrue(t("has(rose)"))
        assertTrue(t("obj.o2 == \"done\""))
        assertFalse(t("unknown_flag"))
        assertTrue(t("unknown_flag == false"))
        assertTrue(t("true"))
        assertTrue(t("trust >= -3"))
        assertEquals(setOf("affection", "trust"), Condition.identifiers("affection >= 10 and trust >= 5"))
        try { Condition.parse("affection >= "); fail() } catch (e: Exception) {}
        try { Condition.parse("(a and b"); fail() } catch (e: Exception) {}
    }

    private val intents = listOf(
        Intent("help", listOf("help", "haan", "batao", "kya hua", "madad"), emptyList(), "a", emptyList(), "kind", 5),
        Intent("cold", listOf("nahi", "aukaat", "yaad hai", "revenge"), emptyList(), "b", emptyList(), "cold", 3),
    )
    private fun m(s: String) = IntentMatcher.match(s, intents)?.let { intents[it.index].id }

    @Test fun hinglishVariants() {
        for (s in listOf("haan", "han", "haaan!!", "ha", "yes", "Yeah", "ok", "theek hai", "thik h", "bilkul")) assertEquals(s, "help", m(s))
        for (s in listOf("nahi", "nahin", "nai", "nhi", "no", "nope", "NAHI!!!")) assertEquals(s, "cold", m(s))
        assertEquals("help", m("Kya hua? Batao na"))
        assertEquals("help", m("kia hua btao"))
        assertEquals("cold", m("ab aukat yaad aayi?"))
        assertEquals("cold", m("tumhari aukaat yaad hai mujhe"))
        assertEquals("help", m("I will help you"))
        assertEquals("help", m("मदद"))           // dictated Devanagari
        assertEquals("cold", m("नहीं"))
        assertEquals("help", m("हाँ बताओ"))
        assertNull(m("asdf qwerty"))
        assertNull(m(""))
        assertNull(m("🙂🙂"))
    }

    @Test fun normalisation() {
        assertEquals(listOf("haan", "batao"), IntentMatcher.normalize("Haaaan!! Batao…"))
        assertEquals(listOf("nahi_pata"), IntentMatcher.normalize("pata nahi"))
        assertEquals("mdd", IntentMatcher.romanize("मदद").replace("a", ""))
        assertTrue(IntentMatcher.phraseScore("kya hua", IntentMatcher.normalize("arey kya hua")) > 2.0)
        assertEquals(0.6, IntentMatcher.phraseScore("revenge", IntentMatcher.normalize("revnge")), 1e-9) // one-edit typo
    }
}
