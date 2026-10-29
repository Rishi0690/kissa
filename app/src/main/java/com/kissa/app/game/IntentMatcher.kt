package com.kissa.app.game

/**
 * Open-reply intent matching without live AI (spec §3). The typed (or dictated) reply is normalised — lower case,
 * punctuation/emoji stripped, Devanagari transliterated to Roman, stretched letters collapsed ("haaaan" -> "han"),
 * common Roman-Hinglish spellings folded to one canonical form (haan/han/ha -> haan, nahi/nahin/nai/nhi -> nahi) —
 * then scored against each intent's keyword phrases. Multi-word phrases score higher; near-misses on long words
 * (one edit) score partially. The best intent above the threshold fires; otherwise the mandatory fallback.
 */
object IntentMatcher {
    data class Match(val index: Int, val score: Double, val matched: List<String>)

    /** canonical form <- spelling variants (Roman Hinglish + English). */
    private val variants: Map<String, String> = buildMap {
        fun v(canon: String, vararg forms: String) { put(canon, canon); forms.forEach { put(it, canon) } }
        v("haan", "han", "ha", "haa", "haanji", "hanji", "haji", "hn", "hmm", "hm", "yes", "yeah", "yep", "ya", "yup", "ok", "okay", "okk", "theek", "thik", "thick", "thk", "sure", "bilkul", "zaroor", "jarur", "jaroor", "zarur")
        v("nahi", "naheen", "nahin", "nai", "nhi", "nahee", "nahii", "na", "naa", "no", "nope", "nah", "never", "kabhi")
        v("madad", "help", "helps", "helping", "madat", "sahayata", "support")
        v("kya", "kia", "kyaa", "what", "wat")
        v("kyun", "kyu", "kyon", "kyoon", "why", "y")
        v("main", "mai", "mein", "me", "mujhe", "mujhko", "i", "im")
        v("tum", "tu", "tumhe", "tumko", "tujhe", "you", "u", "aap", "ap", "apko", "aapko")
        v("acha", "accha", "achha", "achcha", "acchha", "good", "nice", "fine")
        v("sorry", "sry", "sorri", "soryy", "maaf", "maafi", "mafi", "apologies", "apologize")
        v("please", "plz", "pls", "plis", "plss", "pleaseee")
        v("dost", "friend", "friends", "yaar", "yar", "bro", "bhai", "bestie", "buddy")
        v("pyaar", "pyar", "love", "luv", "ishq", "mohabbat")
        v("gussa", "gusa", "angry", "naraz", "naraaz", "mad")
        v("batao", "bata", "bataao", "btao", "bolo", "tell", "say", "kaho")
        v("chalo", "chal", "chalte", "lets", "come")
        v("abhi", "abi", "now", "turant", "jaldi")
        v("kal", "tomorrow", "kl")
        v("thanks", "thank", "thanku", "thankyou", "shukriya", "dhanyavaad", "dhanyawad", "thx", "ty")
        v("bye", "byee", "tata", "alvida", "goodbye", "chalta")
        v("hai", "he", "h", "hain", "hei", "hay", "is")
        v("nahi_pata", "pata_nahi", "idk", "dunno")
        v("bakwas", "bakwaas", "nonsense", "rubbish", "bs")
        v("aukaat", "aukat", "okaat", "aukad", "auqat")
        v("badla", "badlaa", "revenge", "bdla")
        v("block", "blocked", "blok")
        v("pakka", "pakka", "promise", "vaada", "wada", "waada", "vada")
        v("sach", "sacchi", "sachi", "true", "truth", "really", "seriously")
        v("jhooth", "jhoot", "jhuth", "lie", "liar", "jhoothi", "jhoota")
        v("darr", "dar", "dara", "scared", "afraid", "fear", "darpok")
        v("himmat", "courage", "guts", "dare")
        v("chup", "chupp", "quiet", "shut", "silent", "shush")
        v("koi_baat_nahi", "kbn", "its_ok", "no_problem", "np")
        v("mil", "milo", "milte", "milna", "meet", "milenge", "milungi", "milunga")
        v("aaunga", "aaungi", "aunga", "aungi", "aa_raha", "aa_rahi", "coming", "omw")
        v("paisa", "paise", "money", "cash", "rupees", "rs")
        v("police", "polis", "cops", "complaint")
        v("pagal", "paagal", "crazy", "mental")
        v("sundar", "beautiful", "pretty", "cute", "khoobsurat", "handsome")
    }

    /** Multi-word phrases folded to a single token before matching. */
    private val phrases = listOf(
        "pata nahi" to "nahi_pata", "nahi pata" to "nahi_pata", "i dont know" to "nahi_pata", "dont know" to "nahi_pata",
        "koi baat nahi" to "koi_baat_nahi", "its ok" to "its_ok", "it's ok" to "its_ok", "no problem" to "no_problem",
        "aa raha" to "aa_raha", "aa rahi" to "aa_rahi", "on my way" to "omw", "thank you" to "thankyou", "shut up" to "chup",
    )

    private val devanagari: Map<Char, String> = buildMap {
        val m = mapOf(
            'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ri",
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
            'ष' to "sh", 'स' to "s", 'ह' to "h", 'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'े' to "e", 'ै' to "ai",
            'ो' to "o", 'ौ' to "au", 'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", 'ृ' to "ri", '़' to "", 'ॉ' to "o",
        )
        putAll(m)
    }
    private val consonants = "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसह".toSet()
    private val matras = "ािीुूेैोौंँः्ृॉ़".toSet()

    /** Romanises Devanagari (schwa added between consonants, dropped word-finally) so dictated Hindi matches Roman rules. */
    fun romanize(s: String): String {
        if (s.none { it in '\u0900'..'\u097F' }) return s
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            val r = devanagari[c]
            if (r == null) { sb.append(c); continue }
            sb.append(r)
            if (c in consonants) {
                val n = s.getOrNull(i + 1)
                if (n != null && n !in matras && (n in consonants || n in devanagari.keys)) sb.append('a')
            }
        }
        return sb.toString()
    }

    private fun collapse(w: String): String {
        val sb = StringBuilder()
        for (c in w) { if (sb.length >= 2 && sb[sb.length - 1] == c && sb[sb.length - 2] == c) continue; sb.append(c) }
        // "haaan" -> "haan" (max 2), then for variant lookup also try single
        return sb.toString()
    }

    fun canonical(word: String): String {
        val w = collapse(word)
        variants[w]?.let { return it }
        val single = w.replace(Regex("(.)\\1+"), "$1")
        variants[single]?.let { return it }
        // common Roman-Hinglish suffix folding: z->j, w->v, 'ee'->'i', trailing 'h' noise
        val folded = w.replace('z', 'j').replace('w', 'v').replace("ee", "i").replace("oo", "u")
        variants.entries.firstOrNull { (k, _) -> k.replace('z', 'j').replace('w', 'v').replace("ee", "i").replace("oo", "u").replace(Regex("(.)\\1+"), "$1") == folded }?.let { return it.value }
        return w
    }

    fun normalize(text: String): List<String> {
        var t = romanize(text).lowercase()
        t = t.replace(Regex("[^a-z0-9\\s']"), " ").replace("'", "")
        t = " " + t.replace(Regex("\\s+"), " ").trim() + " "
        for ((p, r) in phrases) t = t.replace(" $p ", " $r ")
        return t.trim().split(' ').filter { it.isNotBlank() }.map { canonical(it) }
    }

    private fun lev1(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /** Scores one keyword phrase against normalised tokens: exact = token count * 1.0, fuzzy (1 edit, words >= 5 chars) = 0.6 each. */
    fun phraseScore(phrase: String, tokens: List<String>): Double {
        val p = normalize(phrase)
        if (p.isEmpty()) return 0.0
        if (p.size == 1) {
            val w = p[0]
            if (tokens.any { it == w }) return 1.0
            val raw = phrase.lowercase().trim()
            if (tokens.any { it.length >= 4 && ((w.length >= 5 && lev1(it, w)) || (raw.length >= 5 && lev1(it, raw))) }) return 0.6
            return 0.0
        }
        // multi-word: contiguous run = full credit (+bonus), all words present in order = 0.8 credit
        for (s in 0..tokens.size - p.size) if (p.indices.all { tokens[s + it] == p[it] }) return p.size * 1.25
        var k = 0
        for (t in tokens) if (k < p.size && t == p[k]) k++
        return if (k == p.size) p.size * 0.8 else 0.0
    }

    /** Best intent for [text], or null -> fallback. [threshold] is the minimum score to fire. */
    fun match(text: String, intents: List<Intent>, threshold: Double = 0.6): Match? {
        val tokens = normalize(text)
        if (tokens.isEmpty()) return null
        var best: Match? = null
        intents.forEachIndexed { i, intent ->
            var score = 0.0; val hits = ArrayList<String>()
            for (ph in intent.match) { val s = phraseScore(ph, tokens); if (s > 0) { score += s; hits += ph } }
            // negation guard: "nahi" in the reply weakens intents that don't themselves list a negative word
            val neg = tokens.contains("nahi") && intent.match.none { normalize(it).contains("nahi") }
            if (neg && score > 0) score *= 0.5
            if (score >= threshold && (best == null || score > best!!.score)) best = Match(i, score, hits)
        }
        return best
    }
}
