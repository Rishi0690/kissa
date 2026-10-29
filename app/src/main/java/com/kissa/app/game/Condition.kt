package com.kissa.app.game

/**
 * Condition expressions used by endings, choice gates and `if` steps, e.g.
 * `affection >= 10 and trust >= 5`, `helped_isha == false`, `helped_isha = false`, `rank >= 25`, `rank_name == "Pro"`,
 * `has(rose)`, `obj.o2 == "done"`, `ending.good`, `visited.s3`, `not (a or b)`, `true`.
 * Grammar: or := and ( (or|'||') and )* ; and := not ( (and|'&&') not )* ; not := (not|'!') not | cmp ;
 * cmp := atom ( op atom )? ; atom := number | string | true | false | ident | ident '(' ident ')' | '(' or ')'.
 */
object Condition {
    sealed class Node {
        data class Lit(val v: V) : Node()
        data class Id(val name: String) : Node()
        data class Call(val fn: String, val arg: String) : Node()
        data class Not(val n: Node) : Node()
        data class Bin(val op: String, val a: Node, val b: Node) : Node()
    }

    class ParseException(msg: String) : Exception(msg)

    /** Resolves identifiers against game state. Unknown identifiers resolve to false. */
    fun interface Scope { fun lookup(name: String): V? }

    private val cache = HashMap<String, Node>()

    fun parse(src: String): Node = synchronized(cache) { cache.getOrPut(src) { Parser(tokenize(src)).parseAll() } }

    fun eval(src: String, scope: Scope): Boolean = value(parse(src), scope).truthy()

    /** All identifiers referenced (for the validator). */
    fun identifiers(src: String): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(n: Node) { when (n) { is Node.Id -> out += n.name; is Node.Call -> out += "${n.fn}(${n.arg})"; is Node.Not -> walk(n.n); is Node.Bin -> { walk(n.a); walk(n.b) }; else -> {} } }
        walk(parse(src)); return out
    }

    fun value(n: Node, s: Scope): V = when (n) {
        is Node.Lit -> n.v
        is Node.Id -> s.lookup(n.name) ?: V.Bool(false)
        is Node.Call -> s.lookup("${n.fn}(${n.arg})") ?: V.Bool(false)
        is Node.Not -> V.Bool(!value(n.n, s).truthy())
        is Node.Bin -> when (n.op) {
            "and" -> V.Bool(value(n.a, s).truthy() && value(n.b, s).truthy())
            "or" -> V.Bool(value(n.a, s).truthy() || value(n.b, s).truthy())
            else -> V.Bool(compare(n.op, value(n.a, s), value(n.b, s)))
        }
    }

    private fun compare(op: String, a: V, b: V): Boolean {
        if (a is V.Num && b is V.Num) return when (op) {
            "==" -> a.v == b.v; "!=" -> a.v != b.v; ">=" -> a.v >= b.v; "<=" -> a.v <= b.v; ">" -> a.v > b.v; "<" -> a.v < b.v; else -> false
        }
        if (op == "==" || op == "!=") {
            val eq = when {
                a is V.Bool || b is V.Bool -> a.truthy() == b.truthy()
                else -> a.toString().equals(b.toString(), ignoreCase = true)
            }
            return if (op == "==") eq else !eq
        }
        val c = a.toString().compareTo(b.toString())
        return when (op) { ">=" -> c >= 0; "<=" -> c <= 0; ">" -> c > 0; "<" -> c < 0; else -> false }
    }

    private data class Tok(val kind: Char, val text: String) // n=number s=string i=ident o=operator p=paren
    private fun tokenize(src: String): List<Tok> {
        val out = ArrayList<Tok>(); var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c == '(' || c == ')' -> { out += Tok('p', c.toString()); i++ }
                c == '"' || c == '\'' -> { val j = src.indexOf(c, i + 1); if (j < 0) throw ParseException("unterminated string in '$src'"); out += Tok('s', src.substring(i + 1, j)); i = j + 1 }
                c.isDigit() || (c == '-' && i + 1 < src.length && src[i + 1].isDigit() && (out.isEmpty() || out.last().kind == 'o' || out.last().text == "(")) -> {
                    var j = i + 1; while (j < src.length && (src[j].isDigit() || src[j] == '.')) j++
                    out += Tok('n', src.substring(i, j)); i = j
                }
                c.isLetter() || c == '_' -> { var j = i; while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '_' || src[j] == '.')) j++; out += Tok('i', src.substring(i, j)); i = j }
                else -> {
                    val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
                    when {
                        two in setOf(">=", "<=", "==", "!=", "&&", "||") -> { out += Tok('o', two); i += 2 }
                        c in "<>!=" -> { out += Tok('o', if (c == '=') "==" else c.toString()); i++ }
                        else -> throw ParseException("unexpected '$c' in '$src'")
                    }
                }
            }
        }
        return out
    }

    private class Parser(val t: List<Tok>) {
        var p = 0
        fun peek() = t.getOrNull(p)
        fun parseAll(): Node { if (t.isEmpty()) throw ParseException("empty condition"); val n = or(); if (p != t.size) throw ParseException("unexpected '${t[p].text}'"); return n }
        fun isWord(vararg w: String) = peek()?.let { (it.kind == 'i' || it.kind == 'o') && it.text.lowercase() in w } == true
        fun or(): Node { var a = and(); while (isWord("or", "||")) { p++; a = Node.Bin("or", a, and()) }; return a }
        fun and(): Node { var a = not(); while (isWord("and", "&&")) { p++; a = Node.Bin("and", a, not()) }; return a }
        fun not(): Node = if (isWord("not", "!")) { p++; Node.Not(not()) } else cmp()
        fun cmp(): Node {
            val a = atom()
            val op = peek()
            if (op != null && op.kind == 'o' && op.text in setOf(">=", "<=", "==", "!=", ">", "<")) { p++; return Node.Bin(op.text, a, atom()) }
            return a
        }
        fun atom(): Node {
            val k = peek() ?: throw ParseException("unexpected end")
            p++
            return when (k.kind) {
                'n' -> Node.Lit(V.Num(k.text.toDouble()))
                's' -> Node.Lit(V.Str(k.text))
                'p' -> if (k.text == "(") { val n = or(); if (peek()?.text != ")") throw ParseException("missing )"); p++; n } else throw ParseException("unexpected )")
                'i' -> when (k.text.lowercase()) {
                    "true" -> Node.Lit(V.Bool(true)); "false" -> Node.Lit(V.Bool(false))
                    else -> if (peek()?.text == "(") {
                        p++; val arg = peek() ?: throw ParseException("missing argument"); p++
                        if (peek()?.text != ")") throw ParseException("missing ) after ${k.text}("); p++
                        Node.Call(k.text, arg.text)
                    } else Node.Id(k.text)
                }
                else -> throw ParseException("unexpected '${k.text}'")
            }
        }
    }
}
