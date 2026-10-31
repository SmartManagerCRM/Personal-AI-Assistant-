package com.smartmanager.ai.core.ai

import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.ParsedIntent

/**
 * Parses the model's structured action request:
 *   {"action":"CALL_CONTACT","slots":{"contact":"أحمد"},"say":"نعيّطلو توا"}
 * Anything that is not a well-formed request for a *registered* action is treated as plain text, so the model
 * can never trigger an arbitrary operation.
 */
object StructuredOutput {
    fun parse(output: String, allowed: Set<ActionName>): AiResponse {
        val start = output.indexOf('{'); val end = output.lastIndexOf('}')
        if (start < 0) return AiResponse.Text(output.trim())
        val candidates = buildList { if (end > start) add(output.substring(start, end + 1)); add(repair(output.substring(start))) }
        val obj = candidates.firstNotNullOfOrNull { c -> try { MiniJson(c).parseObject() } catch (e: Exception) { null } } ?: run {
            // Truncated / malformed JSON must never be spoken aloud: salvage the "say" sentence if present, otherwise stay silent.
            val say = Regex("\"say\"\\s*:\\s*\"([^\"]*)").find(output)?.groupValues?.get(1)
            return AiResponse.Text(if (output.trimStart().startsWith("{")) say.orEmpty() else output.trim())
        }
        val actionStr = obj["action"] as? String ?: return AiResponse.Text(output.trim())
        val action = ActionName.entries.firstOrNull { it.name == actionStr } ?: return AiResponse.Text((obj["say"] as? String) ?: "")
        if (action !in allowed) return AiResponse.Text((obj["say"] as? String) ?: "")
        @Suppress("UNCHECKED_CAST")
        val slots = (obj["slots"] as? Map<String, Any?>)?.mapNotNull { (k, v) -> (v as? String ?: (v as? Number)?.toString())?.let { k to it } }?.toMap() ?: emptyMap()
        return AiResponse.Action(ParsedIntent(action, slots, emptyList(), 0.6), obj["say"] as? String)
    }
}

/** Closes an unterminated string / object / array produced by a truncated generation. */
private fun repair(partial: String): String {
    val sb = StringBuilder(partial.trimEnd()); var inStr = false; var esc = false; val stack = ArrayDeque<Char>()
    for (c in sb) { if (esc) { esc = false; continue }; if (c == '\\') { esc = true; continue }; if (c == '"') inStr = !inStr
        if (!inStr) when (c) { '{' -> stack.addLast('}'); '[' -> stack.addLast(']'); '}', ']' -> if (stack.isNotEmpty()) stack.removeLast() } }
    if (inStr) sb.append('"')
    while (stack.isNotEmpty()) sb.append(stack.removeLast())
    return sb.toString()
}

/** Minimal JSON reader (objects, strings, numbers, booleans, null, arrays) — avoids a library dependency in core. */
internal class MiniJson(private val s: String) {
    private var i = 0
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun parseObject(): Map<String, Any?> { val v = value(); @Suppress("UNCHECKED_CAST") return v as? Map<String, Any?> ?: error("not an object") }
    private fun value(): Any? {
        ws()
        return when (s[i]) {
            '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
                while (true) { ws(); val k = string(); ws(); expect(':'); m[k] = value(); ws(); if (s[i] == ',') { i++; continue }; expect('}'); break }; m }
            '[' -> { i++; val l = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return l }
                while (true) { l += value(); ws(); if (s[i] == ',') { i++; continue }; expect(']'); break }; l }
            '"' -> string()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; s.substring(st, i).toDouble().let { d -> if (d % 1.0 == 0.0) d.toLong() else d } }
        }
    }
    private fun expect(c: Char) { if (s[i] != c) error("expected $c at $i"); i++ }
    private fun string(): String {
        expect('"'); val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') { i++; when (val c = s[i]) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> sb.append(c) } } else sb.append(s[i])
            i++
        }
        i++; return sb.toString()
    }
}
