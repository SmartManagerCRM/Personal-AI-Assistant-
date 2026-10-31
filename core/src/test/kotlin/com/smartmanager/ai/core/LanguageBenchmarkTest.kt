package com.smartmanager.ai.core

import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.ActionName.*
import com.smartmanager.ai.core.intent.IntentParser
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.core.language.Lang.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tunisian / multilingual intent benchmark (Phase 6). Expectations are written from the *meaning* of each sentence,
 * independent of the parser implementation. Text-level only: it does NOT measure speech recognition accuracy.
 */
class LanguageBenchmarkTest {
    private data class Case(
        val text: String, val action: ActionName, val slots: Map<String, String> = emptyMap(),
        val lang: Lang? = null, val missing: List<String> = emptyList(), val bodyContains: String? = null, val group: String = "",
    )

    private fun c(text: String, a: ActionName, group: String, lang: Lang? = null, missing: List<String> = emptyList(), body: String? = null, vararg s: Pair<String, String>) =
        Case(text, a, s.toMap(), lang, missing, body, group)

    private val cases = listOf(
        // ---- Tunisian Derja, Arabic script ----
        c("عيّط لأحمد", CALL_CONTACT, "TN", TUNISIAN, s = arrayOf("contact" to "أحمد")),
        c("عيط لأحمد بن علي", CALL_CONTACT, "TN", TUNISIAN, s = arrayOf("contact" to "أحمد بن علي")),
        c("ابعثلو مساج", SEND_MESSAGE, "TN", TUNISIAN, missing = listOf("body"), s = arrayOf("contactRef" to "CONTEXT")),
        c("حلّ الواتساب", OPEN_APP, "TN", TUNISIAN, s = arrayOf("app" to "whatsapp")),
        c("صوّرلي", TAKE_PHOTO, "TN", null, s = arrayOf("camera" to "rear")),
        c("فكّرني غدوة", SET_REMINDER, "TN", TUNISIAN, s = arrayOf("dayOffset" to "1")),
        c("شنوة عندي اليوم؟", READ_AGENDA, "TN", TUNISIAN, s = arrayOf("day" to "today")),
        c("ابعثلي التصويرة", SHARE_LAST_PHOTO, "TN", TUNISIAN, s = arrayOf("contactRef" to "ME")),
        c("نحب نعيّط لفلان", CALL_CONTACT, "TN", TUNISIAN, s = arrayOf("contact" to "فلان")),
        c("قلّو نستحق عشرين دقيقة", SEND_MESSAGE, "TN", TUNISIAN, body = "عشرين", s = arrayOf("contactRef" to "CONTEXT")),
        c("صوّرلي سيلفي", TAKE_PHOTO, "TN", null, s = arrayOf("camera" to "front")),
        c("حل الكاميرا", TAKE_PHOTO, "TN"),
        c("زيدلي الصوت", SET_VOLUME, "TN", s = arrayOf("direction" to "up")),
        c("نقص الصوت", SET_VOLUME, "TN", s = arrayOf("direction" to "down")),
        c("حل البلوتوث", OPEN_SETTINGS_PANEL, "TN", s = arrayOf("panel" to "bluetooth")),
        c("حل الواي فاي", OPEN_SETTINGS_PANEL, "TN", s = arrayOf("panel" to "wifi")),
        c("فكرني بعد عشرين دقيقة نكلم ماما", SET_REMINDER, "TN", s = arrayOf("minutes" to "20")),
        c("فكرني بعد ساعة نعيط لبابا", SET_REMINDER, "TN", s = arrayOf("minutes" to "60")),
        c("فكرني غدوة على الساعة 8 نمشي للخدمة", SET_REMINDER, "TN", s = arrayOf("dayOffset" to "1", "hour" to "8")),
        c("اقرالي الإشعارات", READ_NOTIFICATIONS, "TN"),
        c("تكلم بالفرنسية", SET_LANGUAGE, "TN", s = arrayOf("language" to "FRENCH")),
        c("احكي معايا بالتونسي", SET_LANGUAGE, "TN", s = arrayOf("language" to "TUNISIAN")),
        c("اسكت", STOP_SPEAKING, "TN"),
        c("إي", CONFIRM_YES, "TN"),
        c("لا", CONFIRM_NO, "TN"),
        c("باهي", CONFIRM_YES, "TN"),
        c("موش توا", CONFIRM_NO, "TN"),
        c("حل اليوتيوب", OPEN_APP, "TN", s = arrayOf("app" to "youtube")),
        c("عيط لماما", CALL_CONTACT, "TN", s = arrayOf("contact" to "ماما")),
        c("ابعث لسارة مساج نجي بعد شوية", SEND_MESSAGE, "TN", body = "نجي", s = arrayOf("contact" to "سارة")),
        c("صوّرلي فيديو", RECORD_VIDEO, "TN"),
        c("شنوة عندي غدوة", READ_AGENDA, "TN", s = arrayOf("day" to "tomorrow")),
        c("حل الإعدادات", OPEN_SETTINGS_PANEL, "TN", s = arrayOf("panel" to "main")),
        c("اطفي المساعد", SHUTDOWN_ASSISTANT, "TN"),
        c("ما تسمعنيش ثلاثين دقيقة", QUIET_MODE, "TN", s = arrayOf("quietMinutes" to "30")),
        c("عيطلو", CALL_CONTACT, "TN", s = arrayOf("contactRef" to "CONTEXT")),
        c("عسلامة", GREETING, "TN"),
        c("حل واتساب", OPEN_APP, "TN", s = arrayOf("app" to "whatsapp")),
        // ---- Modern Standard Arabic ----
        c("اتصل بأحمد", CALL_CONTACT, "MSA", s = arrayOf("contact" to "أحمد")),
        c("اتصل بماما", CALL_CONTACT, "MSA", s = arrayOf("contact" to "ماما")),
        c("افتح الكاميرا", TAKE_PHOTO, "MSA"),
        c("أرسل رسالة إلى سارة", SEND_MESSAGE, "MSA", missing = listOf("body"), s = arrayOf("contact" to "سارة")),
        c("ذكرني بعد ساعتين بالدواء", SET_REMINDER, "MSA", s = arrayOf("minutes" to "120")),
        c("ماذا لدي اليوم", READ_AGENDA, "MSA", s = arrayOf("day" to "today")),
        c("ارفع الصوت", SET_VOLUME, "MSA", s = arrayOf("direction" to "up")),
        c("اخفض الصوت", SET_VOLUME, "MSA", s = arrayOf("direction" to "down")),
        c("اقرأ الإشعارات", READ_NOTIFICATIONS, "MSA"),
        c("تحدث بالإنجليزية", SET_LANGUAGE, "MSA", s = arrayOf("language" to "ENGLISH")),
        c("التقط صورة", TAKE_PHOTO, "MSA"),
        c("افتح الإعدادات", OPEN_SETTINGS_PANEL, "MSA", s = arrayOf("panel" to "main")),
        c("افتح واتساب", OPEN_APP, "MSA", s = arrayOf("app" to "whatsapp")),
        c("أطفئ المساعد", SHUTDOWN_ASSISTANT, "MSA"),
        c("نعم", CONFIRM_YES, "MSA"),
        c("ألغي", CONFIRM_NO, "MSA"),
        c("اعرض مواعيدي غدا", READ_AGENDA, "MSA", s = arrayOf("day" to "tomorrow")),
        c("ضع منبها الساعة 7", SET_ALARM, "MSA", s = arrayOf("hour" to "7")),
        // ---- French ----
        c("appelle Ahmed", CALL_CONTACT, "FR", FRENCH, s = arrayOf("contact" to "Ahmed")),
        c("appelle Ahmed Ben Ali", CALL_CONTACT, "FR", FRENCH, s = arrayOf("contact" to "Ahmed Ben Ali")),
        c("ouvre WhatsApp", OPEN_APP, "FR", FRENCH, s = arrayOf("app" to "whatsapp")),
        c("envoie un message à Sarah", SEND_MESSAGE, "FR", FRENCH, missing = listOf("body"), s = arrayOf("contact" to "Sarah")),
        c("dis à Sarah que j'arrive dans dix minutes", SEND_MESSAGE, "FR", FRENCH, body = "arrive", s = arrayOf("contact" to "Sarah")),
        c("prends une photo", TAKE_PHOTO, "FR", FRENCH, s = arrayOf("camera" to "rear")),
        c("prends un selfie", TAKE_PHOTO, "FR", FRENCH, s = arrayOf("camera" to "front")),
        c("rappelle-moi demain à 8h de prendre mes médicaments", SET_REMINDER, "FR", FRENCH, s = arrayOf("dayOffset" to "1", "hour" to "8")),
        c("rappelle-moi dans 10 minutes", SET_REMINDER, "FR", FRENCH, s = arrayOf("minutes" to "10")),
        c("quel est mon agenda aujourd'hui", READ_AGENDA, "FR", FRENCH, s = arrayOf("day" to "today")),
        c("monte le volume", SET_VOLUME, "FR", FRENCH, s = arrayOf("direction" to "up")),
        c("baisse le son", SET_VOLUME, "FR", FRENCH, s = arrayOf("direction" to "down")),
        c("coupe le son", SET_VOLUME, "FR", FRENCH, s = arrayOf("direction" to "mute")),
        c("lis mes notifications", READ_NOTIFICATIONS, "FR", FRENCH),
        c("parle en anglais", SET_LANGUAGE, "FR", FRENCH, s = arrayOf("language" to "ENGLISH")),
        c("tais-toi", STOP_SPEAKING, "FR", FRENCH),
        c("oui", CONFIRM_YES, "FR"),
        c("non", CONFIRM_NO, "FR"),
        c("ouvre la caméra", TAKE_PHOTO, "FR", FRENCH),
        c("ouvre les paramètres", OPEN_SETTINGS_PANEL, "FR", FRENCH, s = arrayOf("panel" to "main")),
        c("active le wifi", OPEN_SETTINGS_PANEL, "FR", FRENCH, s = arrayOf("panel" to "wifi")),
        c("éteins l'assistant", SHUTDOWN_ASSISTANT, "FR", FRENCH),
        c("envoie la photo à Karim", SHARE_LAST_PHOTO, "FR", FRENCH, s = arrayOf("contact" to "Karim")),
        c("mets une alarme à 7h", SET_ALARM, "FR", FRENCH, s = arrayOf("hour" to "7")),
        c("réveille-moi à 6h30 demain", SET_ALARM, "FR", FRENCH, s = arrayOf("hour" to "6", "minute" to "30", "dayOffset" to "1")),
        c("enregistre une vidéo", RECORD_VIDEO, "FR", FRENCH),
        c("bonjour", GREETING, "FR"),
        c("dis-lui que je suis en retard", SEND_MESSAGE, "FR", FRENCH, body = "retard", s = arrayOf("contactRef" to "CONTEXT")),
        // ---- English ----
        c("call Ahmed", CALL_CONTACT, "EN", ENGLISH, s = arrayOf("contact" to "Ahmed")),
        c("call my mom", CALL_CONTACT, "EN", ENGLISH, s = arrayOf("contact" to "mom")),
        c("open WhatsApp", OPEN_APP, "EN", ENGLISH, s = arrayOf("app" to "whatsapp")),
        c("send a message to Sarah saying I'm late", SEND_MESSAGE, "EN", ENGLISH, body = "late", s = arrayOf("contact" to "Sarah")),
        c("tell him I'll be there in ten minutes", SEND_MESSAGE, "EN", ENGLISH, body = "there", s = arrayOf("contactRef" to "CONTEXT")),
        c("take a picture", TAKE_PHOTO, "EN", ENGLISH, s = arrayOf("camera" to "rear")),
        c("take a selfie", TAKE_PHOTO, "EN", ENGLISH, s = arrayOf("camera" to "front")),
        c("remind me tomorrow at 8", SET_REMINDER, "EN", ENGLISH, s = arrayOf("dayOffset" to "1", "hour" to "8")),
        c("remind me in 20 minutes to call mom", SET_REMINDER, "EN", ENGLISH, s = arrayOf("minutes" to "20")),
        c("what's on my schedule today", READ_AGENDA, "EN", ENGLISH, s = arrayOf("day" to "today")),
        c("turn the volume up", SET_VOLUME, "EN", ENGLISH, s = arrayOf("direction" to "up")),
        c("turn down the volume", SET_VOLUME, "EN", ENGLISH, s = arrayOf("direction" to "down")),
        c("mute the volume", SET_VOLUME, "EN", ENGLISH, s = arrayOf("direction" to "mute")),
        c("read my notifications", READ_NOTIFICATIONS, "EN", ENGLISH),
        c("speak French", SET_LANGUAGE, "EN", ENGLISH, s = arrayOf("language" to "FRENCH")),
        c("stop", STOP_SPEAKING, "EN"),
        c("yes", CONFIRM_YES, "EN"),
        c("no", CONFIRM_NO, "EN"),
        c("open settings", OPEN_SETTINGS_PANEL, "EN", ENGLISH, s = arrayOf("panel" to "main")),
        c("turn on bluetooth", OPEN_SETTINGS_PANEL, "EN", ENGLISH, s = arrayOf("panel" to "bluetooth")),
        c("record a video", RECORD_VIDEO, "EN", ENGLISH),
        c("set an alarm for 7 am", SET_ALARM, "EN", ENGLISH, s = arrayOf("hour" to "7")),
        c("send me the last photo", SHARE_LAST_PHOTO, "EN", ENGLISH, s = arrayOf("contactRef" to "ME")),
        c("shut down", SHUTDOWN_ASSISTANT, "EN"),
        c("turn off the assistant", SHUTDOWN_ASSISTANT, "EN", ENGLISH),
        // ---- Tunisian in Latin letters (Arabizi) ----
        c("3ayet l ahmed", CALL_CONTACT, "ARABIZI", TUNISIAN, s = arrayOf("contact" to "ahmed")),
        c("ab3athlou message", SEND_MESSAGE, "ARABIZI", TUNISIAN, missing = listOf("body"), s = arrayOf("contactRef" to "CONTEXT")),
        c("7el lwatsap", OPEN_APP, "ARABIZI", TUNISIAN, s = arrayOf("app" to "whatsapp")),
        c("sawerli", TAKE_PHOTO, "ARABIZI", null, s = arrayOf("camera" to "rear")),
        c("sawerli selfie", TAKE_PHOTO, "ARABIZI", null, s = arrayOf("camera" to "front")),
        c("fakarni ghodwa", SET_REMINDER, "ARABIZI", TUNISIAN, s = arrayOf("dayOffset" to "1")),
        c("chnowa 3andi lyoum", READ_AGENDA, "ARABIZI", TUNISIAN, s = arrayOf("day" to "today")),
        c("nheb n3ayet l sarra", CALL_CONTACT, "ARABIZI", TUNISIAN, s = arrayOf("contact" to "sarra")),
        c("zid sawt", SET_VOLUME, "ARABIZI", s = arrayOf("direction" to "up")),
        c("na9es sawt", SET_VOLUME, "ARABIZI", s = arrayOf("direction" to "down")),
        c("kallemni bel inglizi", SET_LANGUAGE, "ARABIZI", s = arrayOf("language" to "ENGLISH")),
        c("9olou nesta7a9 20 d9i9a", SEND_MESSAGE, "ARABIZI", TUNISIAN, body = "20", s = arrayOf("contactRef" to "CONTEXT")),
        c("ab3athli tswira", SHARE_LAST_PHOTO, "ARABIZI", TUNISIAN, s = arrayOf("contactRef" to "ME")),
        c("7el el kamira", TAKE_PHOTO, "ARABIZI", TUNISIAN),
        c("fakarni ba3d 20 d9i9a", SET_REMINDER, "ARABIZI", TUNISIAN, s = arrayOf("minutes" to "20")),
        // ---- Tunisian/French code-switching ----
        c("عيط لأحمد tout de suite", CALL_CONTACT, "MIXED", MIXED_TN_FR, s = arrayOf("contact" to "أحمد")),
        // "message" is a loanword used in French AND English, so alone it is not a code-switching signal; reply language is Tunisian either way.
        c("نحب نبعث message لسارة", SEND_MESSAGE, "MIXED", TUNISIAN, s = arrayOf("contact" to "سارة")),
        c("نحب نبعث message لسارة demain matin", SEND_MESSAGE, "MIXED", MIXED_TN_FR, s = arrayOf("contact" to "سارة")),
        c("ouvre le واتساب", OPEN_APP, "MIXED", MIXED_TN_FR, s = arrayOf("app" to "whatsapp")),
        c("فكرني demain à 8h", SET_REMINDER, "MIXED", MIXED_TN_FR, s = arrayOf("dayOffset" to "1", "hour" to "8")),
        c("3ayet l ahmed s'il te plait", CALL_CONTACT, "MIXED", MIXED_TN_FR, s = arrayOf("contact" to "ahmed")),
        // ---- Open-ended input must fall through to the LLM (UNKNOWN) ----
        c("شنوة رأيك في الطقس", ActionName.UNKNOWN, "LLM"),
        c("what is the capital of France", ActionName.UNKNOWN, "LLM", ENGLISH),
        c("raconte moi une blague", ActionName.UNKNOWN, "LLM", FRENCH),
        c("كيفاش حالك", ActionName.UNKNOWN, "LLM"),
        c("hey how are you today", ActionName.UNKNOWN, "LLM", ENGLISH),
    )

    @Test
    fun benchmark() {
        val parser = IntentParser()
        val failures = mutableListOf<String>()
        val perGroup = linkedMapOf<String, IntArray>()
        for (cs in cases) {
            val p = parser.parse(cs.text)
            val errs = mutableListOf<String>()
            if (p.action != cs.action) errs += "action ${p.action} != ${cs.action}"
            cs.slots.forEach { (k, v) -> if (p.slots[k] != v) errs += "slot $k='${p.slots[k]}' != '$v'" }
            cs.missing.forEach { m -> if (m !in p.missing) errs += "missing lacks $m (got ${p.missing})" }
            if (cs.bodyContains != null && p.slots["body"]?.contains(cs.bodyContains) != true) errs += "body='${p.slots["body"]}' lacks '${cs.bodyContains}'"
            if (cs.lang != null && p.language != cs.lang) errs += "lang ${p.language} != ${cs.lang}"
            val g = perGroup.getOrPut(cs.group) { IntArray(2) }
            g[1]++
            if (errs.isEmpty()) g[0]++ else failures += "[${cs.group}] \"${cs.text}\" → ${errs.joinToString("; ")}"
        }
        val total = cases.size; val ok = total - failures.size
        val report = buildString {
            appendLine("LANGUAGE/INTENT BENCHMARK: $ok/$total passed (${"%.1f".format(100.0 * ok / total)}%)")
            perGroup.forEach { (g, v) -> appendLine("  $g: ${v[0]}/${v[1]}") }
            if (failures.isNotEmpty()) { appendLine("FAILURES:"); failures.forEach { appendLine("  $it") } }
        }
        println(report)
        File("build/benchmark-report.txt").apply { parentFile.mkdirs(); writeText(report) }
        assertTrue("benchmark has at least 100 sentences", total >= 100)
        assertTrue("benchmark pass rate\n$report", failures.isEmpty())
    }
}
