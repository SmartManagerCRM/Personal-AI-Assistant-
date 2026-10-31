package com.smartmanager.ai.core.language

/** Languages the assistant understands and replies in. */
enum class Lang(val tag: String, val ttsLocaleTag: String) {
    TUNISIAN("aeb", "ar-TN"),
    MSA("ar", "ar"),
    FRENCH("fr", "fr-FR"),
    ENGLISH("en", "en-US"),
    /** Tunisian Arabic (script or Arabizi) mixed with French insertions. */
    MIXED_TN_FR("aeb-fr", "ar-TN"),
    UNKNOWN("und", "en-US");

    val isArabicFamily get() = this == TUNISIAN || this == MSA || this == MIXED_TN_FR
}

enum class Script { ARABIC, LATIN, MIXED, OTHER }

/** Result of processing one transcription. [raw] is always preserved for debugging and memory. */
data class Utterance(
    val raw: String,
    val normalized: String,
    /** Raw tokens (alignment preserved with [normTokens]). */
    val rawTokens: List<String>,
    val normTokens: List<String>,
    val language: Lang,
    val script: Script,
    val arabizi: Boolean,
    val codeSwitched: Boolean,
)
