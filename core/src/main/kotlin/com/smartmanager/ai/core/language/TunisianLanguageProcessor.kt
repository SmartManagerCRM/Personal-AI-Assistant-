package com.smartmanager.ai.core.language

/**
 * Tunisian-first language processing: normalization, Arabic-script / Arabizi / French code-switching
 * handling and language detection. The original text is always kept in [Utterance.raw].
 */
class TunisianLanguageProcessor {

    fun process(raw: String, preferred: Lang = Lang.TUNISIAN): Utterance {
        val rawTokens = TextNormalizer.tokenize(raw)
        val norm = rawTokens.map { TextNormalizer.normalizeToken(it) }
        val (lang, script, arabizi, mixed) = detect(rawTokens, norm, preferred)
        return Utterance(raw, norm.joinToString(" "), rawTokens, norm, lang, script, arabizi, mixed)
    }

    private data class Detection(val lang: Lang, val script: Script, val arabizi: Boolean, val mixed: Boolean)

    private fun isArabizi(t: String): Boolean {
        if (t.any { TextNormalizer.isArabicChar(it) }) return false
        val k = TextNormalizer.matchKeyLatin(t)
        if (k in Lexicon.tunisianLatinMarkers) return true
        // digit-for-letter convention inside a word: 3 (ع) 7 (ح) 9 (ق) 5 (خ) 2 (ء) 8 (غ)
        return t.length >= 2 && t.any { it.isLetter() } && t.any { it in "23578 9".trim() && it != ' ' } && t.all { it.isLetterOrDigit() } &&
            Regex("[a-z][23579][a-z]|^[23579][a-z]|[a-z][23579]$").containsMatchIn(k) && !Regex("^\\d+(h\\d*|am|pm)?$").matches(k)
    }

    private fun detect(raw: List<String>, norm: List<String>, preferred: Lang): Detection {
        if (norm.isEmpty()) return Detection(Lang.UNKNOWN, Script.OTHER, false, false)
        var arabicTokens = 0; var latinTokens = 0
        var tnAr = 0; var msa = 0; var tnLat = 0; var fr = 0; var en = 0; var arabiziCount = 0
        for ((i, n) in norm.withIndex()) {
            val isAr = raw[i].any { TextNormalizer.isArabicChar(it) }
            if (isAr) {
                arabicTokens++
                if (n in Lexicon.tunisianArabicMarkers || Lexicon.matches(n, Concept.CALL) && n.startsWith("عيط")) tnAr++
                if (n in Lexicon.msaMarkers) msa++
            } else if (n.any { it.isLetter() }) {
                latinTokens++
                val k = TextNormalizer.matchKeyLatin(n)
                val ar = isArabizi(raw[i])
                if (ar) { arabiziCount++; tnLat++ }
                else {
                    if (k in Lexicon.frenchMarkers && k.length > 1 && k !in FR_EN_AMBIGUOUS) fr++
                    if (k in Lexicon.englishMarkers && k.length > 1 && k !in FR_EN_AMBIGUOUS) en++
                    if (k in FR_EN_AMBIGUOUS) { fr += 0; en += 0 }
                }
                if (raw[i].any { it in "éèêàâùûôîçëï" }) fr += 2
            }
        }
        val script = when {
            arabicTokens > 0 && latinTokens > 0 -> Script.MIXED
            arabicTokens > 0 -> Script.ARABIC
            latinTokens > 0 -> Script.LATIN
            else -> Script.OTHER
        }
        val arabizi = arabiziCount > 0
        val tunisianSignal = tnAr + tnLat
        val frSignal = fr
        val mixed: Boolean
        val lang: Lang = when {
            // Arabic script
            arabicTokens > 0 && arabicTokens >= latinTokens -> {
                mixed = latinTokens > 0 && frSignal > 0
                when {
                    mixed -> Lang.MIXED_TN_FR
                    msa > 0 && tnAr == 0 -> Lang.MSA
                    tnAr > 0 -> Lang.TUNISIAN
                    preferred == Lang.MSA -> Lang.MSA
                    else -> Lang.TUNISIAN
                }
            }
            // Arabizi dominant
            tnLat > 0 && tnLat >= maxOf(fr, en) - 1 && tnLat >= 1 -> {
                mixed = frSignal > 0
                if (mixed) Lang.MIXED_TN_FR else Lang.TUNISIAN
            }
            tnLat > 0 && frSignal > 0 -> { mixed = true; Lang.MIXED_TN_FR }
            arabicTokens > 0 -> { mixed = frSignal > 0; if (mixed) Lang.MIXED_TN_FR else Lang.TUNISIAN }
            frSignal > en -> { mixed = false; Lang.FRENCH }
            en > frSignal -> { mixed = false; Lang.ENGLISH }
            frSignal == en && frSignal > 0 -> { mixed = false; if (preferred == Lang.FRENCH) Lang.FRENCH else Lang.ENGLISH }
            else -> { mixed = false; preferred }
        }
        return Detection(lang, script, arabizi, mixed)
    }

    private companion object {
        val FR_EN_AMBIGUOUS = listOf("a", "i", "me", "ce", "on", "en", "est", "ai", "no", "non", "message", "photo", "minute", "minutes", "volume", "agenda", "hi", "salut", "or", "ou", "an", "up", "d", "l", "j", "m", "n", "s", "t", "c", "qu", "y", "stop", "assistant", "toi").map { TextNormalizer.matchKeyLatin(it) }.toSet()
    }
}
