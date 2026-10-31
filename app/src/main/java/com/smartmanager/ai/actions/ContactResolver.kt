package com.smartmanager.ai.actions

import android.content.Context
import android.provider.ContactsContract
import com.smartmanager.ai.core.language.TextNormalizer
import com.smartmanager.ai.data.MemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactMatch(val name: String, val number: String, val score: Double)

/** Finds contacts by spoken name: aliases, Arabic/Latin cross-script matching (consonant skeletons), prefix clitics, fuzzy distance. */
@Singleton
class ContactResolver @Inject constructor(@ApplicationContext private val ctx: Context, private val memory: MemoryRepository) {

    suspend fun find(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        val aliases = memory.aliasMap()
        val q0 = query.trim()
        val q = aliases[q0.lowercase()] ?: q0
        val all = loadAll()
        val variants = nameVariants(q)
        val scored = all.map { c -> c to variants.maxOf { v -> score(v, c.first) } }.filter { it.second >= 0.75 }
            .groupBy { it.first.first }.map { (name, list) -> ContactMatch(name, list.first().first.second, list.maxOf { it.second }) }
        scored.sortedByDescending { it.score }
    }

    private fun loadAll(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_PRIMARY), null, null, null)?.use { c ->
            val seen = HashSet<String>()
            while (c.moveToNext()) { val n = c.getString(0) ?: continue; val num = c.getString(1) ?: continue; if (seen.add(n + num)) out += n to num }
        }
        return out
    }

    companion object {
        /** Spoken forms: as heard, minus Arabic "ل"/"ب" prepositions glued to the name. */
        fun nameVariants(q: String): List<String> {
            val l = mutableListOf(q)
            if (q.length >= 4 && q[0] == 'ل' && q.any { TextNormalizer.isArabicChar(it) }) l += q.substring(1)
            if (q.length >= 4 && q[0] == 'ب' && q.any { TextNormalizer.isArabicChar(it) }) l += q.substring(1)
            return l.distinct()
        }

        /** Consonant skeleton shared by Arabic script and Latin/Arabizi so "أحمد" ≈ "Ahmed", "كريم" ≈ "Karim", "يوسف" ≈ "Youssef". */
        fun skeleton(s: String): String {
            val sb = StringBuilder()
            val norm = TextNormalizer.normalizeToken(s)
            for (ch in norm) {
                val m = when (ch) {
                    'ب' -> "b"; 'ت', 'ط' -> "t"; 'ث' -> "s"; 'ج' -> "j"; 'ح', 'ه' -> "h"; 'خ' -> "x"; 'د', 'ض', 'ذ' -> "d"; 'ر' -> "r"; 'ز', 'ظ' -> "z"; 'س', 'ص' -> "s"
                    'ش' -> "c"; 'غ' -> "g"; 'ف' -> "f"; 'ق', 'ك', 'ڨ' -> "k"; 'ل' -> "l"; 'م' -> "m"; 'ن' -> "n"
                    'a', 'e', 'i', 'o', 'u', 'y', 'ا', 'و', 'ي', 'ع', 'ء', 'ى', 'ة' -> ""
                    '3', '2' -> ""; '7' -> "h"; '9' -> "k"; '5' -> "x"; '8' -> "g"
                    'p' -> "b"; 'v' -> "f"; 'q' -> "k"; 'c' -> "k"; 'w' -> ""
                    else -> if (ch.isLetter() && ch.code < 128) ch.toString() else ""
                }
                sb.append(m)
            }
            return sb.toString().replace(Regex("(.)\\1+"), "$1").replace("sh", "c").replace("kh", "x").replace("th", "t").replace("gh", "g")
        }

        fun score(spoken: String, contactName: String): Double {
            val a = TextNormalizer.tokenize(spoken).map { TextNormalizer.normalizeToken(it) }
            val b = TextNormalizer.tokenize(contactName).map { TextNormalizer.normalizeToken(it) }
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a.joinToString(" ") == b.joinToString(" ")) return 1.0
            val sa = a.map { skeleton(it) }.filter { it.isNotEmpty() }; val sb = b.map { skeleton(it) }.filter { it.isNotEmpty() }
            if (sa.isEmpty() || sb.isEmpty()) return 0.0
            if (sa == sb) return 0.97
            // every spoken word matches some contact word (exact skeleton → 0.92, prefix/fuzzy → ≤0.85)
            var total = 0.0
            for (w in sa) total += sb.maxOf { x -> if (w == x) 1.0 else if (x.startsWith(w) && w.length >= 3) 0.85 else { val d = lev(w, x); if (maxOf(w.length, x.length) >= 4 && d <= 1) 0.8 else 0.0 } }
            val cover = total / sa.size
            return if (cover >= 0.8) 0.7 + 0.22 * cover - (if (sb.size > sa.size) 0.03 * (sb.size - sa.size) else 0.0) else 0.0
        }

        private fun lev(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) { var prev = dp[0]; dp[0] = i
                for (j in 1..b.length) { val t = dp[j]; dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = t } }
            return dp[b.length]
        }
    }
}
