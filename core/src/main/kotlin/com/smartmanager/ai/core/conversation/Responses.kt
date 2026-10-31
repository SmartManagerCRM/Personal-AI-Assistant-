package com.smartmanager.ai.core.conversation

import com.smartmanager.ai.core.language.Lang

enum class R {
    LISTENING, ASK_CONTACT, ASK_BODY, ASK_WHEN, ASK_WHAT, ASK_APP, CONFIRM_CALL, CONFIRM_MESSAGE, CONFIRM_SHARE, CANCELLED, NOT_UNDERSTOOD, NEED_PERMISSION,
    REFUSED_RESTRICTED, CALL_STARTED, CONTACT_NOT_FOUND, CONTACT_AMBIGUOUS, APP_OPENED, APP_MISSING, CAMERA_OPENED, LANG_SWITCHED, QUIET_ON, SHUTDOWN, ERROR, NO_AI,
    WHATSAPP_PRESS_SEND, REMINDER_SET, ALARM_SET, AGENDA_EMPTY, AGENDA_ITEMS, NOTIF_NONE, NOTIF_ITEMS, DONE, VOLUME_DONE, SETTINGS_OPENED, PHOTO_NONE, SHARE_READY,
    VIDEO_OPENED, GOODBYE, ONLINE_USED, ACTION_FAILED, NEED_NOTIF_ACCESS, UNAVAILABLE,
}

/** Offline, deterministic templates. Tunisian replies use Derja wording (not translated into formal Arabic). */
object Responses {
    private val tn = mapOf(
        R.LISTENING to "إي، نسمعك.", R.ASK_CONTACT to "لمن؟", R.ASK_BODY to "شنوة تحب نكتبلو؟", R.ASK_WHEN to "وقتاش؟", R.ASK_WHAT to "نفكرك بشنوة؟", R.ASK_APP to "أنهي تطبيق؟",
        R.CONFIRM_CALL to "لقيت %s. تحب نعيّطلو؟", R.CONFIRM_MESSAGE to "نبعث لـ%s: «%s». نبعث؟", R.CONFIRM_SHARE to "نبعث آخر تصويرة لـ%s؟",
        R.CANCELLED to "باهي، ما عملت شي.", R.NOT_UNDERSTOOD to "ما فهمتش مليح، تنجم تعاود؟", R.NEED_PERMISSION to "نحتاج إذن %s باش نعمل هالحاجة. تنجم تعطيهولي من الإعدادات.",
        R.REFUSED_RESTRICTED to "هاذي حاجة حساسة (فلوس/حساب)، ما نعملهاش وحدي. اعملها إنت بيدك.", R.CALL_STARTED to "حليت الاتصال بـ%s. ما نجمش نتأكد إذا ردّ.",
        R.CONTACT_NOT_FOUND to "ما لقيتش حد اسمو %s في جهات الاتصال.", R.CONTACT_AMBIGUOUS to "لقيت برشا ناس: %s. شكون تقصد؟",
        R.APP_OPENED to "حليت %s.", R.APP_MISSING to "%s موش موجود في التليفون.", R.CAMERA_OPENED to "حليت الكاميرا، اضغط على الزر باش تصوّر.", R.LANG_SWITCHED to "باهي، نحكي معاك %s.",
        R.QUIET_ON to "باهي، نسكت.", R.SHUTDOWN to "طفيت المساعد. باش نرجع، حلّ التطبيق.", R.ERROR to "صار مشكل، عاود حاول.", R.NO_AI to "ما عنديش نموذج ذكاء مثبّت باش نجاوبك على هالسؤال. تنجم تثبّتو من إدارة النماذج.",
        R.WHATSAPP_PRESS_SEND to "حضّرت الميساج في واتساب. اضغط إرسال باش يتبعث.", R.REMINDER_SET to "باهي، نفكرك %s.", R.ALARM_SET to "حطيت المنبّه %s.",
        R.AGENDA_EMPTY to "ما عندك حتى موعد %s.", R.AGENDA_ITEMS to "عندك %s: %s.", R.NOTIF_NONE to "ما عندك حتى إشعار جديد.", R.NOTIF_ITEMS to "الإشعارات: %s.",
        R.DONE to "تمّ.", R.VOLUME_DONE to "باهي، تبدّل الصوت.", R.SETTINGS_OPENED to "حليت %s.", R.PHOTO_NONE to "ما لقيتش تصويرة.", R.SHARE_READY to "حضّرت التصويرة. اختار واتساب ولا غيرو وابعثها.",
        R.VIDEO_OPENED to "حليت الكاميرا للفيديو، اضغط تسجيل.", R.GOODBYE to "بالسلامة.", R.ONLINE_USED to "استعملت %s (أونلاين).", R.ACTION_FAILED to "ما نجمتش نعملها: %s",
        R.NEED_NOTIF_ACCESS to "لازم تفعّل إذن قراءة الإشعارات من الإعدادات.", R.UNAVAILABLE to "هالميزة موش متوفرة على تليفونك.",
    )
    private val msa = mapOf(
        R.LISTENING to "نعم، أنا أسمعك.", R.ASK_CONTACT to "لمن؟", R.ASK_BODY to "ماذا تريد أن أكتب؟", R.ASK_WHEN to "متى؟", R.ASK_WHAT to "بماذا أذكّرك؟", R.ASK_APP to "أي تطبيق؟",
        R.CONFIRM_CALL to "وجدت %s. هل تريد أن أتصل به؟", R.CONFIRM_MESSAGE to "سأرسل إلى %s: «%s». هل أرسل؟", R.CONFIRM_SHARE to "هل أرسل آخر صورة إلى %s؟",
        R.CANCELLED to "حسنًا، لم أفعل شيئًا.", R.NOT_UNDERSTOOD to "لم أفهم جيدًا، هل يمكنك أن تعيد؟", R.NEED_PERMISSION to "أحتاج إلى إذن %s لتنفيذ ذلك. يمكنك منحه من الإعدادات.",
        R.REFUSED_RESTRICTED to "هذه عملية حساسة (مال/حساب) ولا أنفذها تلقائيًا. يرجى تنفيذها بنفسك.", R.CALL_STARTED to "فتحت الاتصال بـ%s. لا أستطيع التأكد من الرد.",
        R.CONTACT_NOT_FOUND to "لم أجد جهة اتصال باسم %s.", R.CONTACT_AMBIGUOUS to "وجدت عدة جهات: %s. أيهم تقصد؟",
        R.APP_OPENED to "فتحت %s.", R.APP_MISSING to "%s غير مثبت على الهاتف.", R.CAMERA_OPENED to "فتحت الكاميرا، اضغط الزر للتصوير.", R.LANG_SWITCHED to "حسنًا، سأتحدث معك بـ%s.",
        R.QUIET_ON to "حسنًا، سأصمت.", R.SHUTDOWN to "أوقفت المساعد. افتح التطبيق لتشغيله من جديد.", R.ERROR to "حدث خطأ، حاول مجددًا.", R.NO_AI to "لا يوجد نموذج ذكاء اصطناعي مثبت للإجابة عن هذا السؤال. يمكنك تثبيته من مدير النماذج.",
        R.WHATSAPP_PRESS_SEND to "جهّزت الرسالة في واتساب. اضغط إرسال لإرسالها.", R.REMINDER_SET to "حسنًا، سأذكّرك %s.", R.ALARM_SET to "ضبطت المنبّه %s.",
        R.AGENDA_EMPTY to "لا توجد مواعيد %s.", R.AGENDA_ITEMS to "لديك %s: %s.", R.NOTIF_NONE to "لا توجد إشعارات جديدة.", R.NOTIF_ITEMS to "الإشعارات: %s.",
        R.DONE to "تم.", R.VOLUME_DONE to "تم تغيير مستوى الصوت.", R.SETTINGS_OPENED to "فتحت %s.", R.PHOTO_NONE to "لم أجد صورة.", R.SHARE_READY to "جهّزت الصورة. اختر التطبيق وأرسلها.",
        R.VIDEO_OPENED to "فتحت الكاميرا للفيديو، اضغط تسجيل.", R.GOODBYE to "إلى اللقاء.", R.ONLINE_USED to "استخدمت %s (عبر الإنترنت).", R.ACTION_FAILED to "تعذّر التنفيذ: %s",
        R.NEED_NOTIF_ACCESS to "يجب تفعيل إذن قراءة الإشعارات من الإعدادات.", R.UNAVAILABLE to "هذه الميزة غير متاحة على هاتفك.",
    )
    private val fr = mapOf(
        R.LISTENING to "Oui, je t'écoute.", R.ASK_CONTACT to "Pour qui ?", R.ASK_BODY to "Qu'est-ce que je lui écris ?", R.ASK_WHEN to "Quand ?", R.ASK_WHAT to "Je te rappelle quoi ?", R.ASK_APP to "Quelle application ?",
        R.CONFIRM_CALL to "J'ai trouvé %s. Je l'appelle ?", R.CONFIRM_MESSAGE to "J'envoie à %s : « %s ». J'envoie ?", R.CONFIRM_SHARE to "J'envoie la dernière photo à %s ?",
        R.CANCELLED to "D'accord, je n'ai rien fait.", R.NOT_UNDERSTOOD to "Je n'ai pas bien compris, tu peux répéter ?", R.NEED_PERMISSION to "J'ai besoin de l'autorisation %s pour faire ça. Tu peux l'accorder dans les paramètres.",
        R.REFUSED_RESTRICTED to "C'est une opération sensible (argent/compte), je ne la fais jamais automatiquement. Fais-la toi-même.", R.CALL_STARTED to "J'ai lancé l'appel vers %s. Je ne peux pas vérifier s'il a décroché.",
        R.CONTACT_NOT_FOUND to "Je n'ai trouvé aucun contact nommé %s.", R.CONTACT_AMBIGUOUS to "J'ai trouvé plusieurs contacts : %s. Lequel ?",
        R.APP_OPENED to "J'ai ouvert %s.", R.APP_MISSING to "%s n'est pas installé.", R.CAMERA_OPENED to "Caméra ouverte, appuie sur le bouton pour photographier.", R.LANG_SWITCHED to "D'accord, je parle %s.",
        R.QUIET_ON to "D'accord, je me tais.", R.SHUTDOWN to "Assistant arrêté. Ouvre l'application pour le relancer.", R.ERROR to "Une erreur s'est produite, réessaie.", R.NO_AI to "Aucun modèle d'IA n'est installé pour répondre à cette question. Tu peux l'installer depuis le gestionnaire de modèles.",
        R.WHATSAPP_PRESS_SEND to "Message préparé dans WhatsApp. Appuie sur Envoyer pour l'envoyer.", R.REMINDER_SET to "D'accord, je te rappellerai %s.", R.ALARM_SET to "Alarme réglée %s.",
        R.AGENDA_EMPTY to "Aucun rendez-vous %s.", R.AGENDA_ITEMS to "Tu as %s : %s.", R.NOTIF_NONE to "Aucune nouvelle notification.", R.NOTIF_ITEMS to "Notifications : %s.",
        R.DONE to "C'est fait.", R.VOLUME_DONE to "Volume modifié.", R.SETTINGS_OPENED to "J'ai ouvert %s.", R.PHOTO_NONE to "Je n'ai trouvé aucune photo.", R.SHARE_READY to "Photo prête. Choisis l'application et envoie-la.",
        R.VIDEO_OPENED to "Caméra ouverte pour la vidéo, appuie sur enregistrer.", R.GOODBYE to "À bientôt.", R.ONLINE_USED to "J'ai utilisé %s (en ligne).", R.ACTION_FAILED to "Impossible : %s",
        R.NEED_NOTIF_ACCESS to "Active l'accès aux notifications dans les paramètres.", R.UNAVAILABLE to "Cette fonction n'est pas disponible sur ton téléphone.",
    )
    private val en = mapOf(
        R.LISTENING to "Yes, I'm listening.", R.ASK_CONTACT to "Who for?", R.ASK_BODY to "What should I write?", R.ASK_WHEN to "When?", R.ASK_WHAT to "What should I remind you about?", R.ASK_APP to "Which app?",
        R.CONFIRM_CALL to "I found %s. Shall I call?", R.CONFIRM_MESSAGE to "I'll send to %s: \"%s\". Send it?", R.CONFIRM_SHARE to "Send the last photo to %s?",
        R.CANCELLED to "Okay, I didn't do anything.", R.NOT_UNDERSTOOD to "I didn't quite get that, could you repeat?", R.NEED_PERMISSION to "I need the %s permission to do that. You can grant it in Settings.",
        R.REFUSED_RESTRICTED to "That's a sensitive operation (money/account) and I never do those automatically. Please do it yourself.", R.CALL_STARTED to "I started the call to %s. I can't verify whether they picked up.",
        R.CONTACT_NOT_FOUND to "I couldn't find a contact named %s.", R.CONTACT_AMBIGUOUS to "I found several contacts: %s. Which one?",
        R.APP_OPENED to "Opened %s.", R.APP_MISSING to "%s isn't installed.", R.CAMERA_OPENED to "Camera is open, press the shutter to take it.", R.LANG_SWITCHED to "Okay, I'll speak %s.",
        R.QUIET_ON to "Okay, going quiet.", R.SHUTDOWN to "Assistant stopped. Open the app to start it again.", R.ERROR to "Something went wrong, please try again.", R.NO_AI to "No AI model is installed to answer that. You can install one from the model manager.",
        R.WHATSAPP_PRESS_SEND to "I prepared the message in WhatsApp. Press Send to deliver it.", R.REMINDER_SET to "Okay, I'll remind you %s.", R.ALARM_SET to "Alarm set %s.",
        R.AGENDA_EMPTY to "No events %s.", R.AGENDA_ITEMS to "You have %s: %s.", R.NOTIF_NONE to "No new notifications.", R.NOTIF_ITEMS to "Notifications: %s.",
        R.DONE to "Done.", R.VOLUME_DONE to "Volume changed.", R.SETTINGS_OPENED to "Opened %s.", R.PHOTO_NONE to "I couldn't find a photo.", R.SHARE_READY to "Photo ready. Pick the app and send it.",
        R.VIDEO_OPENED to "Camera open for video, press record.", R.GOODBYE to "Goodbye.", R.ONLINE_USED to "Used %s (online).", R.ACTION_FAILED to "I couldn't do it: %s",
        R.NEED_NOTIF_ACCESS to "Please enable notification access in Settings.", R.UNAVAILABLE to "That isn't available on your phone.",
    )

    fun table(lang: Lang): Map<R, String> = when (lang) {
        Lang.TUNISIAN, Lang.MIXED_TN_FR -> tn
        Lang.MSA -> msa
        Lang.FRENCH -> fr
        Lang.ENGLISH, Lang.UNKNOWN -> en
    }

    fun get(key: R, lang: Lang, vararg args: Any): String = String.format(table(lang)[key] ?: en.getValue(key), *args)

    fun languageName(target: Lang, inLang: Lang): String = when (target) {
        Lang.TUNISIAN, Lang.MIXED_TN_FR -> when (inLang) { Lang.FRENCH -> "tunisien"; Lang.ENGLISH -> "Tunisian"; else -> "بالتونسي" }
        Lang.MSA -> when (inLang) { Lang.FRENCH -> "arabe littéraire"; Lang.ENGLISH -> "Modern Standard Arabic"; else -> "بالفصحى" }
        Lang.FRENCH -> when (inLang) { Lang.FRENCH -> "français"; Lang.ENGLISH -> "French"; else -> "بالفرنسية" }
        Lang.ENGLISH, Lang.UNKNOWN -> when (inLang) { Lang.FRENCH -> "anglais"; Lang.ENGLISH -> "English"; else -> "بالإنجليزية" }
    }
}
