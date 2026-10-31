package com.smartmanager.ai.core.conversation

import com.smartmanager.ai.core.language.TextNormalizer

/** Splits a multi-step instruction ("عيط لأحمد وبعد ابعثلو مساج", "ouvre WhatsApp puis prends une photo"). */
object Planner {
    private val single = setOf("ثم", "وبعدها", "وبعدين", "وبعد", "puis", "ensuite", "then", "apres", "ثمّ", "baadha", "w3ad", "wba3d", "wbaad", "wba3dha", "baadin", "ba3dhaka", "ba3dli")
        .map { TextNormalizer.normalizeToken(it) }.toSet()
    private val pairs = setOf("w baad", "w ba3d", "w b3ad", "و بعد", "و بعدين", "et puis", "et apres", "and then", "after that", "w baadin", "w ba3dha", "و بعدها", "baad ma", "ba3d ma")
        .map { TextNormalizer.tokenize(it).map(TextNormalizer::normalizeToken) }

    fun split(rawTokens: List<String>): List<List<String>> {
        val norm = rawTokens.map { TextNormalizer.normalizeToken(it) }
        val segments = mutableListOf<MutableList<String>>(mutableListOf())
        var i = 0
        while (i < rawTokens.size) {
            val pair = pairs.firstOrNull { p -> i + p.size <= norm.size && norm.subList(i, i + p.size) == p }
            when {
                pair != null && segments.last().isNotEmpty() -> { segments.add(mutableListOf()); i += pair.size }
                norm[i] in single && segments.last().isNotEmpty() && i + 1 < rawTokens.size -> { segments.add(mutableListOf()); i++ }
                else -> { segments.last() += rawTokens[i]; i++ }
            }
        }
        return segments.filter { it.isNotEmpty() }
    }
}
