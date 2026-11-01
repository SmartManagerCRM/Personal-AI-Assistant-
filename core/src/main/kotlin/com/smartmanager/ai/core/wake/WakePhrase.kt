package com.smartmanager.ai.core.wake

import com.smartmanager.ai.core.language.TextNormalizer

/**
 * The bundled keyword spotter is trained on English phonetics, so a wake phrase must be written with Latin letters.
 * Arabic-script words (and Arabizi digits) are therefore converted to an approximate *phonetic* Latin spelling:
 * «عسلامة سمارت» → "ASLAMA SMART". It is a heuristic (Tunisian drops short vowels), not a pronunciation dictionary:
 * the UI shows the result so the user can type a better Latin spelling directly.
 */
object WakePhrase {
    private val letters = mapOf(
        'ا' to "A", 'ب' to "B", 'ت' to "T", 'ث' to "TH", 'ج' to "J", 'ح' to "H", 'خ' to "KH", 'د' to "D", 'ذ' to "TH", 'ر' to "R", 'ز' to "Z", 'س' to "S", 'ش' to "SH",
        'ص' to "S", 'ض' to "D", 'ط' to "T", 'ظ' to "Z", 'ع' to "A", 'غ' to "GH", 'ف' to "F", 'ق' to "K", 'ڨ' to "G", 'ك' to "K", 'ل' to "L", 'م' to "M", 'ن' to "N",
        'ه' to "H", 'و' to "OU", 'ي' to "Y", 'ة' to "A", 'ى' to "A", 'ء' to "", 'پ' to "B", 'ڤ' to "V", 'چ' to "CH",
    )
    private val vowelLetters = setOf('ا', 'و', 'ي', 'ع', 'ة', 'ى', 'ء')
    private val arabizi = mapOf('2' to "A", '3' to "A", '5' to "KH", '7' to "H", '8' to "GH", '9' to "K")

    fun toLatin(phrase: String): String = phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
        if (word.any { TextNormalizer.isArabicChar(it) }) arabicWord(word) else latinWord(word)
    }.trim()

    private fun latinWord(w: String): String {
        val sb = StringBuilder()
        for (c in TextNormalizer.normalizeLatinToken(w)) when { c in arabizi -> sb.append(arabizi[c]); c.isLetter() && c.code < 128 -> sb.append(c.uppercaseChar()) }
        val out = sb.toString().replace(Regex("(.)\\1{2,}"), "$1$1")
        return if (w.any { it in arabizi }) out.replace(Regex("A{2,}"), "A") else out
    }

    private fun arabicWord(w: String): String {
        val norm = TextNormalizer.normalizeArabicToken(w.replace('ة', 'ا')).filter { it in letters || it == 'ء' }
        val sb = StringBuilder(); var consonantRun = 0
        for (c in norm) {
            val isVowel = c in vowelLetters
            // Tunisian speech drops short vowels, so only break up clusters of three consonants ("SKR" → "SKER")
            if (!isVowel && consonantRun == 2) { sb.append('E'); consonantRun = 0 }
            sb.append(letters[c].orEmpty()); consonantRun = if (isVowel) 0 else consonantRun + 1
        }
        return sb.toString().replace(Regex("A{2,}"), "A")
    }
}
