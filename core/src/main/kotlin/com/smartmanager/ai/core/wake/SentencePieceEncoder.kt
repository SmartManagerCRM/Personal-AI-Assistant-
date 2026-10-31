package com.smartmanager.ai.core.wake

import java.text.Normalizer

/**
 * Minimal SentencePiece encoder for icefall's `bpe.model` files (which are *unigram* models despite the name); reads the binary protobuf. It lets the app turn *any* typed wake phrase
 * into the token sequence that a sherpa-onnx keyword-spotting model expects, entirely on the device.
 * Validated against the reference `sentencepiece` library in WakeWordTokenizerTest.
 */
class SentencePieceEncoder(modelBytes: ByteArray) {
    private class Piece(val text: String, val score: Float, val type: Int)
    private val pieces = ArrayList<Piece>()
    private val index = HashMap<String, Int>()

    init {
        // ModelProto: repeated SentencePiece pieces = 1  { string piece = 1; float score = 2; enum type = 3 }
        val r = Reader(modelBytes)
        while (r.hasMore()) {
            val tag = r.varint(); val field = (tag ushr 3).toInt(); val wire = (tag and 7).toInt()
            if (field == 1 && wire == 2) {
                val sub = Reader(r.bytes())
                var text = ""; var score = 0f; var type = 1
                while (sub.hasMore()) {
                    val t = sub.varint(); val f = (t ushr 3).toInt(); val w = (t and 7).toInt()
                    when {
                        f == 1 && w == 2 -> text = String(sub.bytes(), Charsets.UTF_8)
                        f == 2 && w == 5 -> score = java.lang.Float.intBitsToFloat(sub.fixed32())
                        f == 3 && w == 0 -> type = sub.varint().toInt()
                        else -> sub.skip(w)
                    }
                }
                index.putIfAbsent(text, pieces.size); pieces += Piece(text, score, type)
            } else r.skip(wire)
        }
        require(pieces.isNotEmpty()) { "not a SentencePiece model" }
    }

    val vocabSize get() = pieces.size
    fun contains(token: String) = index[token]?.let { pieces[it].type == NORMAL || pieces[it].type == USER_DEFINED } == true

    /** Viterbi segmentation maximising the sum of piece log-probabilities (SentencePiece *unigram* encoding, as used by icefall "bpe" models). */
    fun encode(text: String): List<String> {
        val norm = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("\u2581", prefix = "\u2581")
        val cps = norm.codePoints().toArray()
        val n = cps.size
        val unkScore = (pieces.filter { it.type == NORMAL }.minOfOrNull { it.score } ?: -20f) - 10f
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }; val from = IntArray(n + 1) { -1 }; val isUnk = BooleanArray(n + 1)
        best[0] = 0f
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            val sb = StringBuilder()
            var j = i
            while (j < n && j - i < MAX_PIECE_CODEPOINTS) {
                sb.appendCodePoint(cps[j]); j++
                val id = index[sb.toString()] ?: continue
                val pc = pieces[id]
                if (pc.type != NORMAL && pc.type != USER_DEFINED) continue
                val sc = best[i] + pc.score
                if (sc > best[j]) { best[j] = sc; from[j] = i; isUnk[j] = false }
            }
            val sc = best[i] + unkScore
            if (sc > best[i + 1] && from[i + 1] == -1) { best[i + 1] = sc; from[i + 1] = i; isUnk[i + 1] = true }
        }
        val out = ArrayList<String>()
        var pos = n
        while (pos > 0) { val f = from[pos]; out += if (isUnk[pos]) "<unk>" else String(cps, f, pos - f); pos = f }
        return out.reversed()
    }

    /** One line for sherpa-onnx `keywords.txt`: "▁HE Y ▁S M AR T :1.5 #0.25 @HEY_SMART". Returns null if a piece is not in the model. */
    fun keywordLine(phrase: String, boost: Float, threshold: Float): String? {
        val upper = phrase.trim().uppercase()
        val toks = encode(upper)
        if (toks.isEmpty() || toks.any { !contains(it) }) return null
        val label = upper.replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        return toks.joinToString(" ") + " :$boost #$threshold @$label"
    }

    private class Reader(val b: ByteArray) {
        var p = 0
        fun hasMore() = p < b.size
        fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[p++].toInt() and 0xff; r = r or ((x and 0x7f).toLong() shl s); if (x and 0x80 == 0) return r; s += 7 } }
        fun bytes(): ByteArray { val n = varint().toInt(); val o = b.copyOfRange(p, p + n); p += n; return o }
        fun fixed32(): Int { val v = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8) or ((b[p + 2].toInt() and 0xff) shl 16) or ((b[p + 3].toInt() and 0xff) shl 24); p += 4; return v }
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> p += 8; 2 -> { val n = varint().toInt(); p += n }; 5 -> p += 4; else -> error("bad wire type $wire") } }
    }

    private companion object { const val NORMAL = 1; const val USER_DEFINED = 4; const val MAX_PIECE_CODEPOINTS = 16 }
}
