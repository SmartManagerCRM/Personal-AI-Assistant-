package com.smartmanager.ai.core.intent

import com.smartmanager.ai.core.language.Lang

/** Names of every action the assistant can perform. The AI can only request these — never arbitrary code. */
enum class ActionName {
    CALL_CONTACT, SEND_MESSAGE, SHARE_LAST_PHOTO, OPEN_APP, TAKE_PHOTO, RECORD_VIDEO, SET_REMINDER, SET_ALARM, READ_AGENDA,
    SET_VOLUME, OPEN_SETTINGS_PANEL, READ_NOTIFICATIONS, SET_LANGUAGE, STOP_SPEAKING, QUIET_MODE, SHUTDOWN_ASSISTANT,
    /** Conversation control (not phone actions). */
    CONFIRM_YES, CONFIRM_NO, GREETING, UNKNOWN,
}

/** A parsed user request with validated string slots. */
data class ParsedIntent(
    val action: ActionName,
    val slots: Map<String, String> = emptyMap(),
    /** Required slots that are still missing → the assistant asks a follow-up question. */
    val missing: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val language: Lang = Lang.UNKNOWN,
    val rawText: String = "",
) {
    fun slot(k: String) = slots[k]
    fun with(vararg kv: Pair<String, String>) = copy(slots = slots + kv)
}

object Slots {
    const val CONTACT = "contact"
    /** "CONTEXT" (him/her: last mentioned contact) or "ME". */
    const val CONTACT_REF = "contactRef"
    const val BODY = "body"
    const val APP = "app"
    const val CAMERA = "camera"          // front | rear
    const val WHAT = "what"
    const val MINUTES = "minutes"        // relative minutes from now
    const val DAY_OFFSET = "dayOffset"
    const val HOUR = "hour"
    const val MINUTE = "minute"
    const val DIRECTION = "direction"    // up | down | mute | set
    const val LEVEL = "level"            // 0..100
    const val PANEL = "panel"            // wifi | bluetooth | brightness | main
    const val LANGUAGE = "language"      // TUNISIAN | MSA | FRENCH | ENGLISH
    const val DAY = "day"                // today | tomorrow
    const val MINUTES_QUIET = "quietMinutes"
}
