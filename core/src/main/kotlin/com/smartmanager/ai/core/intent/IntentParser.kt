package com.smartmanager.ai.core.intent

import com.smartmanager.ai.core.language.*
import com.smartmanager.ai.core.language.Concept as C

/**
 * Deterministic, offline intent parser for simple commands in Tunisian (Arabic script and Arabizi), MSA,
 * French, English and code-switched speech. Unknown / open-ended input returns [ActionName.UNKNOWN] so the
 * conversation engine can hand it to the local LLM.
 */
class IntentParser(private val processor: TunisianLanguageProcessor = TunisianLanguageProcessor()) {

    fun parse(text: String, preferred: Lang = Lang.TUNISIAN): ParsedIntent = parse(processor.process(text, preferred))

    fun parse(u: Utterance): ParsedIntent {
        val t = u.normTokens
        val lang = u.language
        fun result(a: ActionName, conf: Double, missing: List<String> = emptyList(), vararg s: Pair<String, String>) =
            ParsedIntent(a, s.toMap(), missing, conf, lang, u.raw)
        if (t.isEmpty()) return result(ActionName.UNKNOWN, 0.0)

        val has = { c: C -> Lexicon.has(t, c) }
        val hasX = { c: C -> Lexicon.has(t, c, exactOnly = true) }
        val short = t.size <= 3

        // ---- conversation control ---------------------------------------------------------------
        if (hasX(C.SHUTDOWN) && (t.any { isAssistantWord(it) } || t.size <= 2)) return result(ActionName.SHUTDOWN_ASSISTANT, 0.9)
        if (has(C.QUIET) && !has(C.VOLUME)) {
            val mins = TimeParser.parse(u.rawTokens).relativeMinutes
            return result(ActionName.QUIET_MODE, 0.85, emptyList(), *(mins?.let { arrayOf(Slots.MINUTES_QUIET to it.toString()) } ?: emptyArray()))
        }
        if (short && has(C.YES) && t.all { Lexicon.matches(it, C.YES, true) || Lexicon.isStop(it) }) return result(ActionName.CONFIRM_YES, 0.95)
        if (short && hasX(C.NO) && t.all { Lexicon.matches(it, C.NO, true) || Lexicon.isStop(it) || it.length <= 3 }) return result(ActionName.CONFIRM_NO, 0.95)
        if (short && hasX(C.STOP_SPEAKING) && !has(C.VOLUME)) return result(ActionName.STOP_SPEAKING, 0.9)
        if (short && t.all { Lexicon.matches(it, C.GREETING, true) }) return result(ActionName.GREETING, 0.9)

        // ---- language switching ---------------------------------------------------------------------
        languageRequest(t)?.let { return result(ActionName.SET_LANGUAGE, 0.9, emptyList(), Slots.LANGUAGE to it.name) }

        // ---- share last photo / send picture ----------------------------------------------------------
        val sendIdx = Lexicon.indexOf(t, C.SEND)
        val photoNounIdx = t.indexOfFirst { it in PHOTO_ONLY }
        if (sendIdx >= 0 && photoNounIdx >= 0) {
            val (name, ref) = recipientAfter(u, sendIdx, skipIdx = setOf(photoNounIdx))
            val slots = mutableListOf<Pair<String, String>>()
            name?.let { slots += Slots.CONTACT to it }
            ref?.let { slots += Slots.CONTACT_REF to it }
            if (name == null && ref == null) slots += Slots.CONTACT_REF to "ME"
            return result(ActionName.SHARE_LAST_PHOTO, 0.85, emptyList(), *slots.toTypedArray())
        }

        // ---- reminder / alarm ---------------------------------------------------------------------------
        if (has(C.REMIND)) return reminder(u, ActionName.SET_REMINDER)
        if (has(C.ALARM) && !has(C.OPEN)) return reminder(u, ActionName.SET_ALARM)

        // ---- agenda -----------------------------------------------------------------------------------------
        if (has(C.CALENDAR) || (has(C.WHAT_Q) && has(C.HAVE) && (has(C.TODAY) || has(C.TOMORROW)))) {
            val day = if (Lexicon.has(t, C.TOMORROW, true)) "tomorrow" else "today"
            return result(ActionName.READ_AGENDA, 0.85, emptyList(), Slots.DAY to day)
        }

        // ---- notifications ----------------------------------------------------------------------------------
        if (has(C.NOTIFICATIONS)) return result(ActionName.READ_NOTIFICATIONS, 0.85)

        // ---- messaging / telling ------------------------------------------------------------------------------
        val tellIdx = Lexicon.indexOf(t, C.TELL)
        if (sendIdx >= 0 || tellIdx >= 0) return messageIntent(u, if (sendIdx >= 0) sendIdx else tellIdx)

        // ---- calls ------------------------------------------------------------------------------------------------
        val callIdx = Lexicon.indexOf(t, C.CALL)
        if (callIdx >= 0 && !has(C.OPEN)) return callIntent(u, callIdx)

        // ---- volume / brightness / radios ----------------------------------------------------------------------------
        if (has(C.VOLUME)) return volume(u)
        if (has(C.BRIGHTNESS)) return result(ActionName.OPEN_SETTINGS_PANEL, 0.8, emptyList(), Slots.PANEL to "brightness")
        if (has(C.WIFI)) return result(ActionName.OPEN_SETTINGS_PANEL, 0.85, emptyList(), Slots.PANEL to "wifi")
        if (has(C.BLUETOOTH)) return result(ActionName.OPEN_SETTINGS_PANEL, 0.85, emptyList(), Slots.PANEL to "bluetooth")
        if (has(C.MUTE) && short) return result(ActionName.SET_VOLUME, 0.8, emptyList(), Slots.DIRECTION to "mute")

        // ---- camera / gallery / apps ---------------------------------------------------------------------------------------
        val openIdx = Lexicon.indexOf(t, C.OPEN)
        val cameraWord = t.indexOfFirst { it in CAMERA_WORDS }
        val photoNoun = t.any { Lexicon.matches(it, C.PHOTO_NOUN, true) }
        val photoVerb = t.indexOfFirst { Lexicon.matches(it, C.PHOTO_VERB, false) && (it != "take" && it != "خذ" || photoNoun || cameraWord >= 0) }
        if (has(C.VIDEO) && (photoVerb >= 0 || openIdx >= 0 || t.any { it in RECORD_WORDS })) {
            return result(ActionName.RECORD_VIDEO, 0.85, emptyList(), Slots.CAMERA to if (has(C.SELFIE)) "front" else "rear")
        }
        if (has(C.SELFIE)) return result(ActionName.TAKE_PHOTO, 0.9, emptyList(), Slots.CAMERA to "front")
        if (photoVerb >= 0 && openIdx < 0) return result(ActionName.TAKE_PHOTO, 0.85, emptyList(), Slots.CAMERA to "rear")
        if (openIdx >= 0 && cameraWord >= 0) return result(ActionName.TAKE_PHOTO, 0.75, emptyList(), Slots.CAMERA to "rear")
        if (openIdx >= 0 && has(C.SETTINGS)) return result(ActionName.OPEN_SETTINGS_PANEL, 0.85, emptyList(), Slots.PANEL to "main")
        if (openIdx >= 0) {
            val app = appName(t, openIdx)
            return if (app == null) result(ActionName.OPEN_APP, 0.5, listOf(Slots.APP)) else result(ActionName.OPEN_APP, 0.9, emptyList(), Slots.APP to app)
        }
        if (t.size == 1) AppAliases.resolve(t[0])?.let { return result(ActionName.OPEN_APP, 0.6, emptyList(), Slots.APP to it) }

        return result(ActionName.UNKNOWN, 0.0)
    }

    // -----------------------------------------------------------------------------------------------------

    private fun isAssistantWord(t: String) = t in setOf("المساعد", "مساعد", "assistant", "smart", "سمارت", "نفسك", "toi", "yourself", "الاستماع", "listening", "ecoute", "ecouter")

    private fun languageRequest(t: List<String>): Lang? {
        val which = when {
            Lexicon.has(t, C.LANG_TN) -> Lang.TUNISIAN
            Lexicon.has(t, C.LANG_FR) -> Lang.FRENCH
            Lexicon.has(t, C.LANG_EN) -> Lang.ENGLISH
            Lexicon.has(t, C.LANG_MSA) -> Lang.MSA
            else -> return null
        }
        return if (Lexicon.has(t, C.SPEAK_LANG) || (t.size <= 3 && t.any { it in setOf("en", "in", "ب") })) which else null
    }

    private fun appName(t: List<String>, openIdx: Int): String? {
        for (i in t.indices) { if (i == openIdx) continue; AppAliases.resolve(t[i])?.let { return it } }
        val rest = t.drop(openIdx + 1).filterNot { Lexicon.isStop(it) || it in setOf("تطبيق", "application", "app", "l", "el", "al") }
        return rest.firstOrNull()?.removePrefix("ال")?.takeIf { it.length >= 2 }
    }

    /** Name / pronoun following the verb at [verbIdx]. Returns (contactName, contactRef). */
    private fun recipientAfter(u: Utterance, verbIdx: Int, skipIdx: Set<Int> = emptySet(), takeAll: Boolean = false): Pair<String?, String?> {
        val t = u.normTokens
        cliticRef(t[verbIdx])?.let { return null to it }
        val msaCall = t[verbIdx].startsWith("اتصل")
        var i = verbIdx + 1
        val names = mutableListOf<String>()
        while (i < t.size) {
            val x = t[i]
            if (names.isEmpty() && x in ME_WORDS) return null to "ME"
            if (names.isEmpty() && (x in PRONOUNS_CTX && !(x == "le" || x == "la") || (x == "le" || x == "la") && i == t.size - 1)) return null to "CONTEXT"
            if (names.isNotEmpty()) {
                // a name continues until a trailer / filler / Latin stopword ("tout de suite", "s'il te plait"); "علي" is a name, not "on"
                if (x in CALL_TRAILERS || x in FILLER || Lexicon.isStop(x) && x != "علي" && !(x.any { TextNormalizer.isArabicChar(it) } && false)) { if (!takeAll || x in CALL_TRAILERS || x in FILLER || x.none { TextNormalizer.isArabicChar(it) } || Lexicon.isStop(x) && x != "علي") break }
                if (Lexicon.matches(x, C.MESSAGE_NOUN, true)) break
                names += u.rawTokens[i]
                if (!takeAll) break
                i++; continue
            }
            if (i in skipIdx || Lexicon.isStop(x) || x in FILLER || Lexicon.matches(x, C.LAST, true) || x in PHOTO_ONLY || Lexicon.matches(x, C.MESSAGE_NOUN, true)) { i++; continue }
            names += stripNamePrefix(u.rawTokens[i], first = true, msaCall = msaCall)
            if (!takeAll) break
            i++
        }
        return (names.joinToString(" ").takeIf { it.isNotBlank() }) to null
    }

    private fun cliticRef(verbTok: String): String? {
        val key = if (verbTok.any { TextNormalizer.isArabicChar(it) }) verbTok else TextNormalizer.matchKeyLatin(verbTok)
        if (key in EMBEDDED_CTX) return "CONTEXT"
        val stems = (Lexicon.stems(C.CALL) + Lexicon.stems(C.SEND) + Lexicon.stems(C.TELL) + Lexicon.stems(C.REMIND)).filter { it.length >= 3 }
        for (s in stems.sortedByDescending { it.length }) {
            val k = when {
                key.startsWith(s) -> key.removePrefix(s)
                key.length > 3 && key.substring(1).startsWith(s) -> key.substring(1).removePrefix(s)
                else -> continue
            }
            if (k.isEmpty()) continue
            val rem = k.removePrefix("e").removePrefix("t").removePrefix("a")
            val pron = listOf("لو", "له", "ليه", "لها", "ليها", "لهم", "lou", "lih", "leh", "leha", "lhom")
            val me = listOf("لي", "li", "ly")
            if (pron.any { k == it || rem == it }) return "CONTEXT"
            if (me.any { k == it || rem == it }) return "ME"
        }
        return null
    }

    private fun stripNamePrefix(raw: String, first: Boolean, msaCall: Boolean = false): String {
        var n = raw.trim()
        // MSA "اتصل بـ<name>": the preposition ب is glued to the name (بأحمد → أحمد)
        if (msaCall && n.length >= 4 && n[0] == 'ب' && n.any { TextNormalizer.isArabicChar(it) }) return n.substring(1)
        // Arabic preposition "ل" glued to the name: لأحمد → أحمد
        if (first && n.length >= 4 && n[0] == 'ل' && n.any { TextNormalizer.isArabicChar(it) }) n = n.substring(1)
        return n
    }

    private fun callIntent(u: Utterance, callIdx: Int): ParsedIntent {
        val (name, ref) = recipientAfter(u, callIdx, takeAll = true)
        val slots = mutableMapOf<String, String>()
        name?.let { slots[Slots.CONTACT] = it }
        ref?.let { slots[Slots.CONTACT_REF] = it }
        val missing = if (name == null && ref == null) listOf(Slots.CONTACT) else emptyList()
        return ParsedIntent(ActionName.CALL_CONTACT, slots, missing, if (missing.isEmpty()) 0.9 else 0.7, u.language, u.raw)
    }

    private fun messageIntent(u: Utterance, verbIdx: Int): ParsedIntent {
        val t = u.normTokens
        val (name, ref) = recipientAfter(u, verbIdx)
        // body = tokens after the recipient token (name / pronoun), minus markers and message nouns
        var bodyStart = verbIdx + 1
        if (cliticRef(t[verbIdx]) == null) {
            val recipIdx = (verbIdx + 1 until t.size).firstOrNull { i ->
                if (Lexicon.isStop(t[i]) && t[i] !in PRONOUNS || t[i] in FILLER) false
                else !(Lexicon.matches(t[i], C.MESSAGE_NOUN, true))
            }
            if (recipIdx != null && (name != null || ref != null)) bodyStart = recipIdx + 1
        }
        val body = (bodyStart until t.size).filter { i ->
            val x = t[i]
            !(Lexicon.matches(x, C.MESSAGE_NOUN, true) || x in BODY_MARKERS)
        }.map { u.rawTokens[it] }.joinToString(" ").trim().takeIf { it.isNotBlank() }
        val slots = mutableMapOf<String, String>()
        name?.let { slots[Slots.CONTACT] = it }
        ref?.let { slots[Slots.CONTACT_REF] = it }
        body?.let { slots[Slots.BODY] = it }
        val missing = buildList {
            if (name == null && ref == null) add(Slots.CONTACT)
            if (body == null) add(Slots.BODY)
        }
        return ParsedIntent(ActionName.SEND_MESSAGE, slots, missing, if (missing.isEmpty()) 0.9 else 0.75, u.language, u.raw)
    }

    private fun reminder(u: Utterance, action: ActionName): ParsedIntent {
        val t = u.normTokens
        val time = TimeParser.parse(u.rawTokens)
        val slots = mutableMapOf<String, String>()
        time.relativeMinutes?.let { slots[Slots.MINUTES] = it.toString() }
        time.dayOffset?.let { slots[Slots.DAY_OFFSET] = it.toString() }
        time.hour?.let { slots[Slots.HOUR] = it.toString(); slots[Slots.MINUTE] = time.minute.toString() }
        val what = t.indices.filter { i ->
            i !in time.consumed && !Lexicon.isStop(t[i]) && t[i] !in FILLER && !Lexicon.matches(t[i], C.TOMORROW, true) && !Lexicon.matches(t[i], C.TODAY, true) &&
                !Lexicon.matches(t[i], C.REMIND) && !Lexicon.matches(t[i], C.ALARM) && t[i] !in ME_WORDS && t[i] !in MINUTE_UNIT_WORDS
        }.map { u.rawTokens[it] }.joinToString(" ").trim()
        if (what.isNotBlank()) slots[Slots.WHAT] = what
        val missing = buildList {
            if (!time.isSet) add("when")
            if (action == ActionName.SET_REMINDER && what.isBlank()) add(Slots.WHAT)
        }
        return ParsedIntent(action, slots, missing, if (missing.isEmpty()) 0.88 else 0.7, u.language, u.raw)
    }

    private fun volume(u: Utterance): ParsedIntent {
        val t = u.normTokens
        val pct = t.firstNotNullOfOrNull { it.removeSuffix("%").toIntOrNull()?.takeIf { n -> n in 0..100 } }
        val dir = when {
            Lexicon.has(t, C.MUTE) -> "mute"
            Lexicon.has(t, C.UP) && !Lexicon.has(t, C.DOWN) -> "up"
            Lexicon.has(t, C.DOWN) -> "down"
            pct != null -> "set"
            else -> null
        }
        val slots = mutableMapOf<String, String>()
        dir?.let { slots[Slots.DIRECTION] = it }
        pct?.let { slots[Slots.LEVEL] = it.toString() }
        return ParsedIntent(ActionName.SET_VOLUME, slots, if (dir == null) listOf(Slots.DIRECTION) else emptyList(), if (dir == null) 0.6 else 0.88, u.language, u.raw)
    }

    private companion object {
        val PRONOUNS_CTX = setOf("lui", "le", "la", "him", "her", "them", "it", "leur", "لو", "له", "لها", "ليه", "لهم", "ليها", "lou", "lih", "leh", "leha", "lhom")
        val PRONOUNS = PRONOUNS_CTX
        /** Verb forms that already contain the pronoun "him/her" (قلّو = tell him). */
        val EMBEDDED_CTX = listOf("قلو", "قله", "قللو", "قلها", "9olo", "9ollou", "9olou", "qolou", "9elou", "9olha").map { TextNormalizer.normalizeToken(it).let { k -> if (k.any { c -> TextNormalizer.isArabicChar(c) }) k else TextNormalizer.matchKeyLatin(k) } }.toSet()
        val FILLER = setOf("يا", "ya", "حبيبي", "habibi", "sahbi", "sa7bi", "صاحبي", "khouya", "خويا", "smart", "سمارت", "sil", "plait", "s", "svp", "stp", "pls", "e", "eh", "euh", "hmm", "alors", "donc", "bon", "il", "te", "plz", "please", "ya3tik", "yaatik")
        val ME_WORDS = setOf("me", "moi", "ليا", "لياي", "مني", "li", "lia")
        val BODY_MARKERS = setOf("بلي", "انو", "اللي", "bli", "inou", "enou", "que", "saying", "that", "dire")
        val PHOTO_ONLY = setOf("التصويره", "الصوره", "التصاور", "tswira", "tswiratek", "sora", "soura", "taswira", "photo", "picture", "image", "pic", "photos", "pictures", "selfie", "الصور", "images")
        val CAMERA_WORDS = setOf("كاميرا", "camera", "kamira", "cam", "الكاميرا", "كاميره")
        val RECORD_WORDS = setOf("record", "enregistre", "film", "filmer", "صور")
        val CALL_TRAILERS = setOf("توا", "tawa", "tout", "suite", "now", "maintenant", "tawwa", "الان", "فورا", "right")
        val MINUTE_UNIT_WORDS = setOf("دقيقه", "دقايق", "minutes", "minute", "heures", "heure", "hours", "hour", "ساعه", "ساعات", "min", "mins", "h", "d9aye9", "da9ayek", "sa3a", "sa3at", "ساعتين", "دقيقتين", "بعد", "dans", "in", "after", "apres", "baad", "ba3d")
    }
}

/** Canonical application keys for common spoken names. The Android layer maps keys to installed packages. */
object AppAliases {
    private val map: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("واتساب", "واتس", "whatsapp", "watsap", "watsapp", "whatsap", "wattsap", "الواتساب", "الواتس", "واتسب", "wtsp", "whats"),
        "camera" to listOf("كاميرا", "camera", "kamira", "الكاميرا"),
        "gallery" to listOf("معرض", "galerie", "gallery", "الصور", "غاليري", "photos", "البوم"),
        "settings" to listOf("اعدادات", "الاعدادات", "settings", "parametres", "reglages", "paramtrat"),
        "youtube" to listOf("يوتيوب", "youtube", "youtub", "يوتوب", "اليوتيوب"),
        "facebook" to listOf("فيسبوك", "facebook", "fb", "فايسبوك", "الفيسبوك", "feisbouk", "fesbouk"),
        "instagram" to listOf("انستغرام", "instagram", "insta", "انستا", "انستقرام", "الانستغرام"),
        "maps" to listOf("خرائط", "maps", "gps", "الخرائط"),
        "chrome" to listOf("كروم", "chrome", "المتصفح", "navigateur", "browser", "الكروم"),
        "clock" to listOf("الساعه", "horloge", "clock"),
        "calendar" to listOf("روزنامه", "رزنامه", "تقويم", "calendrier", "calendar", "agenda", "الروزنامه"),
        "contacts" to listOf("جهات الاتصال", "contacts", "الاسماء", "repertoire", "annuaire"),
        "phone" to listOf("الهاتف", "telephone", "dialer", "phone", "clavier"),
        "messages" to listOf("الرسائل", "messages", "sms"),
        "telegram" to listOf("تلغرام", "telegram", "تيليغرام", "تلقرام"),
        "messenger" to listOf("ماسنجر", "messenger", "ميسنجر"),
        "tiktok" to listOf("تيك توك", "tiktok", "تيكتوك"),
        "spotify" to listOf("سبوتيفاي", "spotify"),
        "gmail" to listOf("جيميل", "gmail", "جي ميل", "mail", "email", "البريد"),
        "calculator" to listOf("الة حاسبه", "calculatrice", "calculator", "كلكيلاتور"),
    ).mapValues { (_, v) -> v.map { TextNormalizer.normalizeToken(it) } }

    fun resolve(token: String): String? {
        val key = TextNormalizer.normalizeToken(token)
        val noAl = key.removePrefix("ال")
        val latin = TextNormalizer.matchKeyLatin(key)
        val noLatinArticle = listOf("el", "l").firstNotNullOfOrNull { a -> latin.takeIf { it.startsWith(a) && it.length > a.length + 2 }?.removePrefix(a) }
        return map.entries.firstOrNull { (_, v) -> key in v || noAl in v || v.any { it.removePrefix("ال") == noAl } || noLatinArticle != null && v.any { TextNormalizer.matchKeyLatin(it) == noLatinArticle } }?.key
    }
}
