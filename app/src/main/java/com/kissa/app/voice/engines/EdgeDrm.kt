package com.kissa.app.voice.engines

/**
 * Sec-MS-GEC v2 token for the Microsoft Edge "Read aloud" neural voice service.
 * Straight port of edge-tts 7.3.x `drm.py` (rany2/edge-tts, LGPL/GPL-compatible reference). The service rejects the
 * old v1 (plain SHA-256) token, so this exact algorithm is required. Verified against the Python reference in
 * EdgeDrmTest. All arithmetic is done on Longs and masked to 32 bits, matching Python's arbitrary-precision ints.
 */
object EdgeDrm {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    const val CHROMIUM_MAJOR = "143"
    const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L
    private const val M = 0xFFFFFFFFL

    /** Seconds to add to the device clock (corrected from the server's Date header after a 403). */
    @Volatile var clockSkewSeconds: Double = 0.0

    private val INITIAL = longArrayOf(0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19)
    private val STATE = longArrayOf(0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A, 0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2)
    private val SCHEDULE = longArrayOf(0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA, 0xA622EE8C, 0xD1DD9491, 0xC0DD25A0,
        0xF9E70AB6, 0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33, 0xD258B060, 0x72766564)
    private val K = longArrayOf(
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
        0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
        0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
        0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
    )
    private const val ADJUSTMENT = 0xC9DC06A7L
    private val KB = K.copyOf().also { it[14] = ADJUSTMENT }
    private const val SIGMA0_ADJ = 0x059C0B80L
    private const val SIGMA1_ADJ = 0x000B92C0L
    private val CS64 = arrayOf(
        0xE82E91B1L to 0xB32700D0L, 0x44DE24C6L to 0x88DFF51DL, 0x3BEB65BCL to 0x29514038L, 0xC89C5933L to 0xB328CBDFL,
        0x8D1EC211L to 0xD405B4D5L, 0x314EF5D5L to 0x26E55F3CL, 0xE92491B1L to 0x607801C6L, 0xF0A5B9D5L to 0x82B2C1FBL,
        0x06064CF3L to 0xFA51A3DAL, 0xDA79FA60L to 0x207CC464L, 0xBED20560L to 0x3C3C74F3L, 0x89F9C9F8L to 0xD2D14B61L,
        0x35FA38C6L to 0xBB04A2C1L, 0xCB49E3C6L to 0xC5626167L,
    )
    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJ0 = 0xF2F90B1AL
    private const val CS64_ADJ1 = 0x0B1A0000L

    private fun rol16(v: Long): Long { val x = v and M; return ((x shl 16) or (x ushr 16)) and M }
    private fun rotr(x: Long, n: Int): Long = ((x ushr n) or (x shl (32 - n))) and M
    private fun sigma0(v: Long) = ((v ushr 7) xor (v shl 25) xor (v ushr 18) xor (v shl 14) xor (v ushr 3)) and M
    private fun sigma1(v: Long) = ((v ushr 17) xor (v shl 15) xor (v ushr 19) xor (v shl 13) xor (v ushr 10)) and M

    private fun rounds(state: LongArray, w: LongArray, first: Int): LongArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        val k = if (first == 14) KB else K
        for (i in first until 64) {
            val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val t1 = (h + s1 + (g xor (e and (f xor g))) + k[i] + w[i]) and M
            val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val t2 = (s0 + ((a and b) or (c and (a or b)))) and M
            h = g; g = f; f = e; e = (d + t1) and M; d = c; c = b; b = a; a = (t1 + t2) and M
        }
        return longArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun expand(words: MutableList<Long>, firstWord: Int = 16) {
        for (i in firstWord until 64) {
            words.add((sigma1(words[i - 2]) + words[i - 7] + sigma0(words[i - 15]) + words[i - 16]) and M)
        }
    }

    private fun be(b: ByteArray, off: Int, len: Int): Long { var v = 0L; for (i in 0 until len) v = (v shl 8) or (b[off + i].toLong() and 0xFF); return v }

    private fun sha256Init(data7: ByteArray): LongArray {
        val words = MutableList(14) { 0L }
        words.add(be(data7, 0, 3)); words.add(be(data7, 3, 4))
        for (index in 16 until 30) {
            var value = SCHEDULE[index - 16]
            if (index - 2 >= 14) value += if (index == 16) (sigma1(words[index - 2]) xor SIGMA1_ADJ) else sigma1(words[index - 2])
            if (index - 7 >= 14) value += words[index - 7]
            if (index - 15 >= 14) value += if (index == 29) (sigma0(words[index - 15]) xor SIGMA0_ADJ) else sigma0(words[index - 15])
            words.add(value and M)
        }
        words.add((sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] + ADJUSTMENT - K[14]) and M)
        expand(words, 31)
        val working = rounds(STATE, words.toLongArray(), 14)
        return LongArray(8) { (INITIAL[it] + working[it]) and M }
    }

    fun sha256Digest(data: ByteArray): ByteArray {
        require(data.size >= 7)
        val length = 57 + data.size
        val pad = Math.floorMod(55 - length, 64)
        val padded = java.io.ByteArrayOutputStream().apply {
            write(data, 7, data.size - 7); write(0x80); write(ByteArray(pad))
            val bits = length.toLong() * 8
            for (s in 7 downTo 0) write(((bits ushr (8 * s)) and 0xFF).toInt())
        }.toByteArray()
        var state = sha256Init(data.copyOfRange(0, 7))
        var off = 0
        while (off < padded.size) {
            val words = MutableList(16) { be(padded, off + it * 4, 4) }
            expand(words)
            val working = rounds(state, words.toLongArray(), 0)
            state = LongArray(8) { (state[it] + working[it]) and M }
            off += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = ((state[i] ushr (24 - 8 * j)) and 0xFF).toByte()
        return out
    }

    private fun le32(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun cs64(data: ByteArray, digest: ByteArray): Pair<Long, Long> {
        val keyA = le32(digest, 0) or 1L
        val keyB = le32(digest, 4) or 1L
        val factors = longArrayOf((keyA * CS64_FACTOR) and M, (keyB * CS64_FACTOR) and M)
        val terms = ArrayList<Long>(); val offsets = ArrayList<Long>()
        CS64.forEachIndexed { i, (coef, offset) -> terms.add((coef * factors[i % 2]) and M); offsets.add(offset) }
        val src = byteArrayOf(0) + data
        val padded = src + ByteArray(Math.floorMod(-src.size, 4))
        val words = ArrayList<Long>()
        var o = 0; while (o < padded.size) { words.add(le32(padded, o)); o += 4 }
        if (words.size % 2 == 1) words.add(0L)
        words.forEachIndexed { index, word ->
            var term = word * (if (index % 2 == 0) keyA else keyB)
            var offset = rol16(word) * (if (index % 2 == 0) 0xEF0569FBL else 0xC31713DBL)
            if (index == 0) { term += CS64_ADJ0 * factors[0]; offset += CS64_ADJ1 }
            terms.add(term and M); offsets.add(offset and M)
        }
        var swapAcc = 0L; var swapLast = 0L; var state = 0L; var acc = 0L
        var i = 0
        while (i < terms.size) {
            val x = terms[i]; val y = terms[i + 1]
            var t = rol16((offsets[i] + x) and M)
            var q = (t * 0x76F50F34L) and M
            swapAcc += (rol16(q) * 0xC3EFEA97L + q) and M
            t = rol16((offsets[i + 1] + y) and M)
            q = (t * 0x1881CE3CL) and M
            swapLast = (rol16(q) * 0x35BD1EC9L + q) and M
            swapAcc = (swapAcc + swapLast) and M
            state = (state * keyA + x) and M
            state = (rol16(state) * 0xCF98B111L) and M
            state = (rol16(state) * 0x87085B9FL) and M
            state = (rol16(state) * 0x12CEB96DL) and M
            state = (rol16(state) * 0x257E1D83L) and M
            acc += state
            t = (state * keyB + y) and M
            t = (rol16(t) * 0xA27416F5L) and M
            t = (rol16(t) * 0xD38396FFL) and M
            state = (rol16((rol16(t) * 0x7C932B89L) and M) * 0xBFA49F69L) and M
            acc = (acc + state) and M
            i += 2
        }
        return (swapLast xor state) to (swapAcc xor acc)
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** The Sec-MS-GEC value for [unixSeconds] (defaults to now + clock skew). */
    fun secMsGec(unixSeconds: Double = System.currentTimeMillis() / 1000.0 + clockSkewSeconds): String {
        var secs = Math.floor(unixSeconds).toLong() + WIN_EPOCH
        secs -= secs % 300
        val ticks = secs * 10_000_000L
        val data = "$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII)
        val digest = sha256Digest(data)
        val (c1, c2) = cs64(data, digest)
        val cs = ByteArray(8)
        for (j in 0 until 4) { cs[j] = ((c1 ushr (8 * j)) and 0xFF).toByte(); cs[4 + j] = ((c2 ushr (8 * j)) and 0xFF).toByte() }
        return "${hex(cs)}-${hex(digest)}"
    }
}
