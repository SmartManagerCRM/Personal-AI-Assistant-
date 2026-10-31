package com.smartmanager.ai.core.language

/** Time expression found in an utterance. Exactly one of the relative/absolute forms is meaningful. */
data class TimeSpec(
    val relativeMinutes: Int? = null,
    val dayOffset: Int? = null,
    val hour: Int? = null,
    val minute: Int = 0,
    /** Indices of tokens consumed by the time expression. */
    val consumed: Set<Int> = emptySet(),
) {
    val isSet get() = relativeMinutes != null || dayOffset != null || hour != null
}

/** Parses Tunisian/MSA/French/English time expressions ("بعد عشرين دقيقة", "demain à 8h", "in 2 hours"). */
object TimeParser {
    private fun a(s: String) = TextNormalizer.normalizeArabicToken(s)

    val numbers: Map<String, Int> = buildMap {
        fun put(n: Int, vararg w: String) = w.forEach { this[TextNormalizer.normalizeToken(it)] = n }
        put(1, "واحد", "وحده", "وحدة", "واحده", "un", "une", "one", "wahed", "weh", "wehed", "wa7da", "wa7ed")
        put(2, "اثنين", "زوز", "جوج", "اثنان", "ثنين", "deux", "two", "zouz", "zouj", "jouj")
        put(3, "ثلاثه", "ثلاثة", "ثلاث", "تلاثه", "تلاثة", "تلات", "trois", "three", "tleta", "tlata", "tlet")
        put(4, "اربعه", "اربعة", "اربع", "quatre", "four", "arba3a", "arb3a", "arba3")
        put(5, "خمسه", "خمسة", "خمس", "cinq", "five", "khamsa", "5amsa", "khamsa")
        put(6, "سته", "ستة", "ست", "six", "setta", "sitta", "sta")
        put(7, "سبعه", "سبعة", "سبع", "sept", "seven", "seb3a", "sab3a", "seb3")
        put(8, "ثمانيه", "ثمانية", "تمنيه", "تمنية", "ثماني", "huit", "eight", "tmenya", "tmanya", "thmanya")
        put(9, "تسعه", "تسعة", "تسع", "neuf", "nine", "tes3a", "tis3a", "tes3")
        put(10, "عشره", "عشرة", "عشر", "dix", "ten", "3achra", "3ashra", "achra", "3chra", "3achr")
        put(11, "احداش", "احدعش", "onze", "eleven", "a7dech", "ahdech")
        put(12, "اثناش", "طناش", "douze", "twelve", "tnach", "ttnech", "tnash", "atnach")
        put(13, "ثلطاش", "تلطاش", "treize", "thirteen", "tleta3ch", "tlet3ch")
        put(14, "اربعطاش", "quatorze", "fourteen", "arba3tach")
        put(15, "خمسطاش", "خمستاش", "quinze", "fifteen", "khamstach", "5amstach")
        put(20, "عشرين", "vingt", "twenty", "3chrin", "3echrin", "achrin", "3ashrin", "3chrine", "3ichrin")
        put(30, "ثلاثين", "تلاثين", "trente", "thirty", "tletin", "tlatin", "tlatine")
        put(40, "اربعين", "quarante", "forty", "arb3in", "arba3in", "arb3in")
        put(45, "خمسه واربعين", "quarante cinq")
        put(50, "خمسين", "cinquante", "fifty", "khamsin", "5amsin")
        put(60, "ستين", "soixante", "sixty", "sittin", "setin")
    }

    private fun num(t: String): Int? {
        val n = TextNormalizer.normalizeToken(t)
        return n.toIntOrNull() ?: numbers[n] ?: numbers[TextNormalizer.matchKeyLatin(n)]
    }

    private val minuteWords = setOf("دقيقه", "دقايق", "دقائق", "دقيقتين", "د", "minute", "minutes", "min", "mins", "d9aye9", "da9ayek", "d9i9a", "da9i9a", "d9ay9", "da9a2e9", "dqayeq", "d9ayek", "dqiqa", "dakika").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val hourWords = setOf("ساعه", "ساعات", "ساعتين", "heure", "heures", "hour", "hours", "h", "hr", "sa3a", "sa3at", "saa3a", "sa3tin", "saa3at", "sa3a").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val afterWords = setOf("بعد", "dans", "in", "after", "baad", "ba3d", "b3ad", "ba3ed", "apres", "within").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val halfWords = setOf("نص", "نصف", "demi", "demie", "half", "nos", "nass", "nousf").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val quarterWords = setOf("ربع", "quart", "quarter", "rob3", "robe3", "ro3b").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val atWords = setOf("الساعه", "على", "علي", "à", "a", "at", "ala", "3la", "3ala", "essa3a", "sa3a", "lsa3a", "vers", "around", "for", "pour").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val pmWords = setOf("pm", "مساء", "العشية", "lasher", "aashiya", "3chiya", "soir", "evening", "afternoon", "night", "ليلا", "الليل", "ellil").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()
    private val amWords = setOf("am", "صباحا", "الصباح", "sbeh", "sbah", "matin", "morning", "الصبح", "صباح", "essba7").map { TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(it)) }.toSet()

    private fun key(t: String) = TextNormalizer.matchKeyLatin(TextNormalizer.normalizeToken(t))

    fun parse(tokens: List<String>): TimeSpec {
        val toks = tokens.map { key(it) }
        val consumed = mutableSetOf<Int>()
        var dayOffset: Int? = null
        var relMin: Int? = null
        var hour: Int? = null
        var minute = 0

        // day words
        for ((i, t) in toks.withIndex()) {
            if (Lexicon.matches(t, Concept.TOMORROW, exactOnly = true) && t != "صباح") {
                // "بعد غدوة" = day after tomorrow
                val afterTomorrow = i > 0 && toks[i - 1] in afterWords
                dayOffset = if (afterTomorrow) 2 else 1
                consumed += i; if (afterTomorrow) consumed += i - 1
            } else if (Lexicon.matches(t, Concept.TODAY, exactOnly = true) && t != "الليله") {
                dayOffset = dayOffset ?: 0; consumed += i
            } else if (t == "الليله" || t == "tonight") {
                dayOffset = 0; hour = hour ?: 21; consumed += i
            }
        }

        // relative durations: [after] N unit | half/quarter hour | unit-dual
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            val hasAfter = i > 0 && toks[i - 1] in afterWords
            val prevIsNumber = i > 0 && num(toks[i - 1]) != null
            when {
                (t == key("دقيقتين")) -> { relMin = (relMin ?: 0) + 2; consumed += i; if (hasAfter) consumed += i - 1 }
                (t == key("ساعتين") || t == "sa3tin") -> { relMin = (relMin ?: 0) + 120; consumed += i; if (hasAfter) consumed += i - 1 }
                t in minuteWords && prevIsNumber && (i < 2 || toks[i - 2] !in atWords) -> {
                    val n = num(toks[i - 1])!!
                    relMin = (relMin ?: 0) + n; consumed += setOf(i, i - 1)
                    if (i >= 2 && toks[i - 2] in afterWords) consumed += i - 2
                }
                t in hourWords && t != "h" && prevIsNumber && !(i >= 2 && toks[i - 2] in atWords) -> {
                    val n = num(toks[i - 1])!!
                    relMin = (relMin ?: 0) + n * 60; consumed += setOf(i, i - 1)
                    if (i >= 2 && toks[i - 2] in afterWords) consumed += i - 2
                }
                t in hourWords && t != "h" && i >= 1 && toks[i - 1] in halfWords -> { relMin = (relMin ?: 0) + 30; consumed += setOf(i, i - 1); if (i >= 2 && toks[i - 2] in afterWords) consumed += i - 2 }
                t in hourWords && t != "h" && i >= 1 && toks[i - 1] in quarterWords -> { relMin = (relMin ?: 0) + 15; consumed += setOf(i, i - 1); if (i >= 2 && toks[i - 2] in afterWords) consumed += i - 2 }
                t in hourWords && t != "h" && hasAfter && relMin == null -> { relMin = 60; consumed += setOf(i, i - 1) }
                t in minuteWords && t != "d" && hasAfter && relMin == null -> { relMin = 1; consumed += setOf(i, i - 1) }
            }
            i++
        }

        // absolute clock: "8h", "8h30", "at 8", "à 8h", "الساعة 8", "ala essa3a 8"
        val clock = Regex("^(\\d{1,2})(?:h|:|ح)(\\d{2})?$")
        for ((idx, t) in toks.withIndex()) {
            if (relMin != null && idx in consumed) continue
            val m = clock.find(t)
            if (m != null) {
                hour = m.groupValues[1].toInt(); minute = m.groupValues[2].ifEmpty { "0" }.toInt(); consumed += idx; continue
            }
            if (num(t) != null && num(t)!! in 1..12 && toks.getOrNull(idx + 1)?.let { it in pmWords || it in amWords } == true && idx !in consumed) { hour = num(t); consumed += idx; continue }
            if (idx > 0 && toks[idx - 1] in atWords && idx !in consumed) {
                val n = num(t)
                if (n != null && n in 0..24 && (idx + 1 >= toks.size || toks[idx + 1] !in minuteWords)) { hour = n; consumed += idx; consumed += idx - 1
                    if (idx >= 2 && toks[idx - 2] in atWords) consumed += idx - 2
                    // optional minutes: "8 et demie", "8 30"
                    val nxt = toks.getOrNull(idx + 1)
                    if (nxt != null && nxt in halfWords || nxt == "et" && toks.getOrNull(idx + 2) in halfWords) { minute = 30; consumed += idx + 1 }
                    else if (nxt != null && nxt in quarterWords) { minute = 15; consumed += idx + 1 }
                    else if (nxt != null) { val mm = num(nxt); if (mm != null && mm in 5..59 && toks.getOrNull(idx + 2)?.let { it in minuteWords } != true) { minute = mm; consumed += idx + 1 } }
                }
            }
        }
        if (hour != null) {
            val pm = toks.any { it in pmWords }
            val am = toks.any { it in amWords }
            if (pm && hour in 1..11) hour += 12
            if (am && hour == 12) hour = 0
            if (hour == 24) hour = 0
            toks.forEachIndexed { k, t -> if (t in pmWords || t in amWords) consumed += k }
            if (dayOffset == null) dayOffset = null
        }
        return TimeSpec(relMin, dayOffset, hour, minute, consumed)
    }
}
