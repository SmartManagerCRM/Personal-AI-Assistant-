package com.smartmanager.ai.core.language

import java.text.Normalizer

/**
 * Normalization that never changes the meaning of what the user said: it only unifies spelling
 * variants (diacritics, alef/ya/ta-marbuta forms, elongation, Arabic-Indic digits, Latin accents).
 */
object TextNormalizer {
    private val arabicDiacritics = Regex("[ً-ٰٟۖ-ۭـ]") // tashkeel, shadda, tatweel
    private val repeated = Regex("(.)\\1{2,}")
    private val doubled = Regex("(.)\\1")

    fun isArabicChar(c: Char) = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'

    fun normalizeArabicToken(t: String): String {
        var s = Normalizer.normalize(t, Normalizer.Form.NFKC)
        s = arabicDiacritics.replace(s, "")
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    'ک' -> 'ك'
                    'ی' -> 'ي'
                    'گ' -> 'ق' // Tunisian written "g" for qaf
                    'پ' -> 'ب'
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    'ء' -> '\u0000'
                    else -> c
                },
            )
        }
        // elongation ("عيييط" -> "عيط"), 3+ repeats collapse to one
        return repeated.replace(sb.toString().replace("\u0000", ""), "$1")
    }

    /** Latin / Arabizi: lowercase, strip accents, keep Arabizi digits, collapse elongation. */
    fun normalizeLatinToken(t: String): String {
        var s = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        s = s.replace("œ", "oe").replace("æ", "ae")
        s = repeated.replace(s, "$1")
        return s
    }

    /** Collapse doubled letters too (Arabizi "3ayyet" ~ "3ayet"); used only as a matching key. */
    fun matchKeyLatin(t: String): String = doubled.replace(normalizeLatinToken(t), "$1")

    fun normalizeToken(t: String): String =
        if (t.any { isArabicChar(it) }) normalizeArabicToken(t) else normalizeLatinToken(t)

    /** Split on whitespace and punctuation, keeping alignment; apostrophes/hyphens split ("dis-lui", "j'veux"). */
    fun tokenize(raw: String): List<String> =
        raw.split(Regex("[\\s\\-'’‘`\"“”«»(),.;:!?؟،؛…]+")).filter { it.isNotBlank() }
}
