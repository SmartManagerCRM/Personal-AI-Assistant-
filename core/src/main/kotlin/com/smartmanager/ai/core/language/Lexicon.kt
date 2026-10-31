package com.smartmanager.ai.core.language

/**
 * Multilingual concept lexicon: Tunisian Derja (Arabic script + Arabizi), MSA, French, English.
 * Every entry is stored in normalized form and matched against normalized tokens.
 */
enum class Concept {
    CALL, SEND, TELL, OPEN, PHOTO_VERB, PHOTO_NOUN, SELFIE, VIDEO, REMIND, ALARM, CALENDAR, WHAT_Q, HAVE, TODAY,
    TOMORROW, VOLUME, BRIGHTNESS, UP, DOWN, MUTE, WIFI, BLUETOOTH, SETTINGS, NOTIFICATIONS, READ, SPEAK_LANG,
    LANG_FR, LANG_EN, LANG_MSA, LANG_TN, YES, NO, STOP_SPEAKING, SHUTDOWN, QUIET, MESSAGE_NOUN, ME_PRON, HIM_PRON,
    GREETING, GALLERY, LAST, STOPWORD,
}

object Lexicon {
    private fun a(vararg s: String) = s.map { TextNormalizer.normalizeArabicToken(it) }
    private fun l(vararg s: String) = s.map { TextNormalizer.matchKeyLatin(it) }

    private val entries: Map<Concept, List<String>> = buildMap {
        put(Concept.CALL, a("عيط", "عيّط", "اتصل", "اتصال", "هاتف", "كلم", "رن") + l("3ayet", "3ayat", "3ayt", "3aye", "3ayit", "aayet", "ta9al", "call", "phone", "ring", "dial", "appelle", "appeler", "appel", "telephone", "telephoner", "passe un appel", "coup de fil") )
        put(Concept.SEND, a("ابعث", "ابعت", "بعث", "ارسل", "رسل", "ابعثلو", "ابعثلي") + l("ab3ath", "ab3at", "ab3eth", "b3ath", "b3at", "b3eth", "ab3th", "ab3ath", "send", "text", "envoie", "envoyer", "envoi", "ecris", "write"))
        put(Concept.TELL, a("قلو", "قول", "قله", "قللو", "قلها", "خبرو", "اعلمو") + l("9olo", "9ollou", "9olou", "qolou", "9elou", "9olha", "9ol", "dis", "tell", "say", "dites"))
        put(Concept.OPEN, a("حل", "حلّ", "افتح", "شغل", "شغّل", "لانسي", "ادخل") + l("7el", "hel", "7ell", "ftah", "aftah", "ouvre", "ouvrir", "open", "lance", "lancer", "launch", "start", "demarre", "chaghel", "shaghel"))
        put(Concept.PHOTO_VERB, a("صور", "صوّر", "صورلي", "التقط", "خذ") + l("sawer", "sawwer", "sawerli", "tsawer", "swer", "take", "prends", "prend", "shoot", "snap", "capture"))
        put(Concept.PHOTO_NOUN, a("تصويره", "تصويرة", "صوره", "صور", "صورة", "تصاور", "صوّر") + l("tswira", "taswira", "tsawer", "sora", "soura", "photo", "photos", "picture", "pictures", "pic", "image", "images", "selfie"))
        put(Concept.SELFIE, a("سيلفي", "كاميرا امامية", "امامية") + l("selfie", "sylfi", "silfi", "front camera", "camera frontale", "frontale"))
        put(Concept.VIDEO, a("فيديو", "فيديوهات", "فيلم", "تصوير فيديو") + l("video", "videos", "vidéo", "film", "filmer", "record", "enregistre"))
        put(Concept.REMIND, a("فكرني", "فكرلي", "ذكرني", "فكّرني", "نبهني", "ذكرلي") + l("fakarni", "fakerni", "fakkarni", "fekerni", "fakkerni", "dakkarni", "dkarni", "rappelle", "rappel", "remind", "reminder", "rappeler"))
        put(Concept.ALARM, a("منبه", "المنبه", "صحيني", "فيقني", "فيّقني", "نبه", "ساعه منبهه") + l("sa7ini", "sa7ni", "fi9ni", "fayi9ni", "reveil", "reveille", "alarm", "alarme", "wake"))
        put(Concept.CALENDAR, a("اجندا", "اجندة", "روزنامه", "رزنامه", "تقويم", "موعد", "مواعيد", "برنامجي", "اجتماع") + l("agenda", "calendrier", "calendar", "schedule", "rdv", "rendezvous", "meeting", "meetings", "rendez", "planning", "programme", "appointments", "appointment", "rouznama"))
        put(Concept.WHAT_Q, a("شنوه", "شنو", "ماذا", "ما", "واش", "اش", "شنيه") + l("chnowa", "chnouwa", "chnou", "chnoua", "shnowa", "shnouwa", "quoi", "que", "what", "qu", "est"))
        put(Concept.HAVE, a("عندي", "عندى", "لدي", "عندنا", "عندك", "عندو") + l("3andi", "3andy", "3andek", "have", "ai", "ay", "got", "on", "il"))
        put(Concept.TODAY, a("اليوم", "اليوم", "الليله", "الليلة") + l("lyoum", "elyoum", "lyom", "aujourdhui", "aujourd", "today", "tonight", "ellila"))
        put(Concept.TOMORROW, a("غدوه", "غدوة", "غدا", "غدًا", "بكري", "صباح") + l("ghodwa", "ghodoua", "ghodwa", "ghodwe", "ghodoua", "ghdwa", "demain", "tomorrow", "ghoudwa", "ghodouwa"))
        put(Concept.VOLUME, a("صوت", "الصوت", "مستوي الصوت") + l("sawt", "volume", "son", "sound", "audio"))
        put(Concept.BRIGHTNESS, a("ضوء", "الضوء", "السطوع", "ضو") + l("luminosite", "brightness", "luminosité", "lumiere", "dhaw", "dhaw2"))
        put(Concept.UP, a("زيد", "ارفع", "اعلي", "زيدلي", "اكثر", "عالي", "كبر", "علي") + l("zid", "zidli", "up", "louder", "increase", "monte", "augmente", "plus", "higher", "raise", "ar3a", "a3la"))
        put(Concept.DOWN, a("نقص", "نقّص", "اخفض", "هبط", "اقل", "صغر", "نقصلي", "وطي") + l("na9es", "na9a9", "na9asli", "down", "lower", "decrease", "baisse", "diminue", "moins", "reduce", "w6i", "weti"))
        put(Concept.MUTE, a("اكتم", "اسكت الصوت", "صامت", "كتم") + l("mute", "muet", "couper le son", "coupe", "coupez", "couper", "silencieux", "silent", "silence"))
        put(Concept.WIFI, a("واي فاي", "وايفاي", "الواي فاي", "وي في") + l("wifi", "wi fi", "wi-fi", "wai fai", "waifai"))
        put(Concept.BLUETOOTH, a("بلوتوث", "البلوتوث", "بلوتوس", "بلوتوت") + l("bluetooth", "blututh", "bluetoth", "blutooth", "bluto"))
        put(Concept.SETTINGS, a("اعدادات", "الاعدادات", "البارامترات", "باراميتر", "ضبط", "اعدادت", "خيارات") + l("settings", "setting", "parametres", "parametre", "reglages", "paramtrat", "parametrat", "options"))
        put(Concept.NOTIFICATIONS, a("اشعارات", "الاشعارات", "اشعار", "تنبيهات", "نوتيفيكاسيون", "نوتيف") + l("notification", "notifications", "notifs", "notif", "notifs", "alerts", "ichaarat", "ichaarate"))
        put(Concept.READ, a("اقرا", "اقرالي", "اقرأ", "قرالي", "اسمعني", "قراءه", "اعطيني", "عرض", "وريني", "ورّيني", "قولي") + l("9ra", "9rali", "9raly", "read", "lis", "lire", "lit", "show", "montre", "consulte", "wrini", "wriny", "donne", "give"))
        put(Concept.SPEAK_LANG, a("تكلم", "اتكلم", "احكي", "حكي", "كلمني", "كلّمني", "تحدث", "كلم") + l("parle", "parler", "speak", "talk", "kallemni", "kalemni", "7keni", "hki", "ehki", "switch", "reponds", "answer", "reply", "kalem", "kallem", "en"))
        put(Concept.LANG_FR, a("فرنسي", "فرنسيه", "بالفرنسيه", "الفرنسيه", "بالفرنسي", "فرانسي", "فرونسيه") + l("francais", "french", "fransawi", "frensi", "fransi", "bel fransi", "fr", "fransiya"))
        put(Concept.LANG_EN, a("انجليزي", "انجليزيه", "بالانجليزيه", "انقليزي", "بالانقليزي", "انقليزيه", "بالانجليزي") + l("anglais", "english", "inglizi", "ingliziya", "anglaise", "bel inglizi", "ang", "englizi", "englisi"))
        put(Concept.LANG_MSA, a("فصحي", "فصحى", "بالفصحي", "الفصحي", "عربيه فصحي", "العربيه الفصحي", "العربيه") + l("fos7a", "fus7a", "arabe classique", "arabe litteraire", "msa", "modern standard", "classical arabic", "fousha", "arabe"))
        put(Concept.LANG_TN, a("تونسي", "بالتونسي", "دارجه", "الدارجه", "تونسيه", "بالدارجه", "درجه") + l("tounsi", "derja", "darja", "tunisien", "tunisian", "bel tounsi", "tounsia", "tunisian arabic", "bedarja"))
        put(Concept.YES, a("اي", "ايه", "اه", "نعم", "باهي", "اكيد", "تمام", "ايوه", "هيا", "ياسر", "بهي", "موافق", "حاضر", "طبعا", "ماشي") + l("ey", "eyh", "iyeh", "eyyeh", "accord", "ouais", "oui", "ok", "okay", "yes", "yeah", "yep", "sure", "behi", "bahi", "nam", "aham", "d accord", "dac", "daccord", "bien sur", "aya", "yalla", "confirme", "go"))
        put(Concept.NO, a("لا", "لاء", "لأ", "لا لا", "موش", "ما", "الغي", "ألغي", "لغي", "وقف", "بلاش", "منحبش", "ماناش") + l("non", "no", "nope", "nah", "la", "laa", "mouch", "moush", "ma n7ebch", "mn7ebch", "annule", "annuler", "cancel", "abort", "balesh", "nevermind", "laisse tomber", "ma ta3melch", "matekhdemch"))
        put(Concept.STOP_SPEAKING, a("اسكت", "بس", "وقف", "كفى", "كفي", "يكفي", "باهي برا") + l("stop", "tais", "tais toi", "silence", "chut", "shut up", "ska3", "oskot", "uskut", "enough", "arrete", "arrete toi", "yeffi", "barra", "ya9fi"))
        put(Concept.SHUTDOWN, a("اطفي", "اطفئ", "اقفل", "طفي", "طفّي", "اغلق", "ايقاف", "وقف المساعد") + l("eteins", "eteint", "shutdown", "shut down", "shut", "turn off", "power off", "tfi", "otfi", "tafi", "desactive", "deactivate", "disable", "arrete l assistant"))
        put(Concept.QUIET, a("وضع الصمت", "صمت", "ما تسمعنيش", "تسمعنيش", "هدوء", "لا تزعجني", "ماتسمعنيش", "وضع صامت") + l("quiet", "quiet mode", "do not disturb", "dnd", "ne m ecoute pas", "ne m ecoute pas", "mode silencieux", "mode silence", "mode discret", "pause", "ma tesme3nich", "ma tsme3nich", "tsme3nich"))
        put(Concept.MESSAGE_NOUN, a("مساج", "رساله", "رسالة", "ميساج", "مسج", "ميسج", "مسيج", "كلمه") + l("message", "msg", "mssg", "mess", "messag", "sms", "texto", "mesaj", "msj", "mesage", "missage", "messaj", "massage", "misaj"))
        put(Concept.ME_PRON, a("لي", "ليا", "لياي", "لانا", "مني") + l("li", "ly", "lia", "moi", "me", "m", "moi meme"))
        put(Concept.HIM_PRON, a("لو", "له", "ليه", "لها", "لهم", "لهما", "ليها", "يه") + l("lou", "lu", "lih", "leh", "leha", "lhom", "lui", "le", "la", "him", "her", "them", "it", "leur", "leurs"))
        put(Concept.GREETING, a("مرحبا", "اهلا", "سلام", "عسلامه", "صباح الخير", "مساء الخير", "هاي") + l("salut", "bonjour", "bonsoir", "hello", "hi", "hey", "salam", "aslema", "3aslema", "cc", "coucou", "slm"))
        put(Concept.GALLERY, a("معرض", "المعرض", "الصور", "البوم", "ألبوم", "الالبوم", "غاليري", "كاليري") + l("galerie", "gallery", "album", "photos", "galery", "galeri", "pictures"))
        put(Concept.LAST, a("اخر", "الاخيره", "الاخير", "اخرها", "آخر", "الاخرى", "هذيكا", "هاذي") + l("derniere", "dernier", "last", "latest", "recent", "lekhra", "lekher", "a5er", "akher", "akhir", "akhira"))
        put(Concept.STOPWORD, a("ل", "لـ", "الي", "الى", "الى", "من", "فضلك", "يعيشك", "بالله", "برك", "توا", "نحب", "نحبش", "ريد", "اريد", "ابغي", "نبغي", "ممكن", "تنجم", "ينجم", "تجم", "لو", "سمحت", "لازم", "عاود", "باهي", "اني", "انا", "نحب", "نحبو", "هل", "في", "علي", "على", "ب", "و", "ثم", "وبعد", "بعد", "بش", "باش", "كيما", "شويه", "شويا", "الان", "حالا", "فورا", "رجاء", "بلاتي", "برشا") + l("l", "el", "lel", "ll", "d", "j", "m", "n", "s", "t", "c", "qu", "to", "a", "au", "pour", "le", "la", "les", "de", "du", "des", "un", "une", "the", "please", "plz", "stp", "svp", "s il", "te", "plait", "vous", "plait", "je", "j", "veux", "voudrais", "want", "would", "like", "i", "me", "my", "mon", "ma", "mes", "can", "could", "you", "now", "tout", "suite", "tout de suite", "right", "nheb", "n7eb", "nhib", "7ab", "nheb", "tawa", "tawwa", "yaatik", "ya3tik", "ya3tikesaha", "3aslema", "3a", "ala", "b", "w", "bech", "besh", "barcha", "barsha", "chwaya", "chwayya", "ena", "ani", "ya", "dir", "3mel", "a3mel", "ta3mel", "peux", "pouvez", "pouvoir", "faire", "fais", "moi", "m", "est", "ce", "que", "qu", "c", "ca", "ce", "cet", "cette", "ces", "do", "does", "up", "his", "her", "their", "our", "us", "we", "pls"))
    }

    private val cache = HashMap<Concept, Set<String>>()
    fun stems(c: Concept): Set<String> = cache.getOrPut(c) { entries[c].orEmpty().toSet() }

    private val clitics = setOf("ن", "ت", "ي", "ب", "و", "ف", "س", "ا")

    /** Token matches a stem exactly, or as stem + clitic suffix, or after a one-letter verb prefix (n/t/y/b…). */
    fun matches(token: String, c: Concept, exactOnly: Boolean = false): Boolean {
        if (token.isEmpty()) return false
        val set = stems(c)
        val key = if (token.any { TextNormalizer.isArabicChar(it) }) token else TextNormalizer.matchKeyLatin(token)
        if (key in set) return true
        if (exactOnly) return false
        for (s in set) {
            if (s.length < 3) continue
            if (key.startsWith(s) && key.length - s.length <= 6) return true
            if (key.length > s.length && key.length > 3 && key[0].toString().let { it in clitics || it in "ntyb" } && key.substring(1).startsWith(s) && key.length - 1 - s.length <= 6) return true
        }
        return false
    }

    private fun keyOf(t: String) = if (t.any { TextNormalizer.isArabicChar(it) }) t else TextNormalizer.matchKeyLatin(t)
    private val multiCache = HashMap<Concept, Set<String>>()
    private fun multi(c: Concept) = multiCache.getOrPut(c) { stems(c).filter { ' ' in it }.toSet() }

    /** Index of the first token (or first token of a 2–3 word phrase) matching the concept, or -1. */
    fun indexOf(tokens: List<String>, c: Concept, exactOnly: Boolean = false): Int {
        val mw = multi(c)
        for (i in tokens.indices) {
            if (matches(tokens[i], c, exactOnly)) return i
            if (mw.isNotEmpty()) for (n in 2..3) if (i + n <= tokens.size && tokens.subList(i, i + n).joinToString(" ") { keyOf(it) } in mw) return i
        }
        return -1
    }
    fun has(tokens: List<String>, c: Concept, exactOnly: Boolean = false) = indexOf(tokens, c, exactOnly) >= 0
    fun isStop(token: String) = matches(token, Concept.STOPWORD, exactOnly = true)

    // Marker words for language detection ---------------------------------------------------------
    val tunisianArabicMarkers: Set<String> = a(
        "شنوه", "شنو", "نحب", "نحبش", "باهي", "برشا", "توا", "عيط", "حل", "علاش", "كيفاش", "فمه", "مازال", "ملا", "نعمل", "نجم", "بش", "باش",
        "ماني", "هاك", "موش", "يعيشك", "عسلامه", "غدوه", "فكرني", "نستحق", "ياسر", "ابعثلو", "ابعثلي", "قلو", "صورلي", "عندي", "عيطلو",
        "شكون", "وين", "قداش", "برا", "دبر", "نجي", "تو", "اللي", "بلي", "انو", "نيشان", "مرا", "راجل", "كان", "نبي", "نخدم", "نطلع", "هكا",
        "هكه", "ديما", "حاجه", "حاجة", "فيسع", "تعدي", "عاود", "ماذابيك", "وقتاش", "كيفه", "شبيك", "شنيه", "بالله", "فما", "اشكون",
        "خوي", "صاحبي", "بابا", "ماما", "عزيزي", "يا", "حبيبي", "ولدي", "نعيط", "نبعث", "نحل", "نفكر", "مانيش", "ماناش", "منحبش", "بلاش", "ساهل", "بالسيف",
    ).toSet()
    val msaMarkers: Set<String> = a(
        "ماذا", "اريد", "هل", "الان", "سوف", "لماذا", "كيف", "حالك", "افتح", "اتصل", "ارسل", "ذكرني", "من فضلك", "رجاء", "لدي", "ماهي", "اين", "متي",
        "هذا", "هذه", "الي", "التي", "الذي", "لكن", "ايضا", "فقط", "جدا", "يمكنك", "يرجي", "الرجاء", "اقرأ", "اخبرني", "مرحبا", "شكرا", "عفوا", "تحدث", "كم",
    ).toSet()
    val tunisianLatinMarkers: Set<String> = l(
        "chnowa", "chnouwa", "chnou", "nheb", "n7eb", "nhib", "barcha", "behi", "ghodwa", "tawa", "bech", "besh", "3ayet", "3ayat", "3aytelou", "ab3ath",
        "ab3athli", "ab3athlou", "9olou", "9olo", "sawerli", "fakarni", "fakerni", "3andi", "3aslema", "yaatik", "ya3tik", "ya3tikesaha", "winek", "kifech", "kifeh",
        "3lech", "mouch", "moush", "lyoum", "7el", "ma n7ebch", "yesser", "yaser", "ya7ki", "enti", "inti", "ena", "houa", "hiya", "mel", "nesta7a9", "nesta9",
        "tswira", "sa7bi", "sahbi", "khouya", "5ouya", "5oya", "nokhrej", "n5arej", "nodkhol", "famma", "fama", "mazel", "mazelt", "ye3tik", "9olli", "chbik", "chniya", "chkoun",
    ).toSet()
    val frenchMarkers: Set<String> = l(
        "le", "la", "les", "un", "une", "des", "je", "tu", "il", "elle", "nous", "vous", "ils", "mon", "ma", "mes", "ton", "ta", "est", "pas", "pour", "avec", "dans", "sur",
        "appelle", "ouvre", "envoie", "rappelle", "prends", "photo", "demain", "aujourd", "veux", "voudrais", "peux", "peut", "merci", "bonjour", "salut", "oui", "non", "stp", "svp",
        "tout", "suite", "message", "heure", "heures", "minutes", "minute", "dis", "lui", "moi", "quoi", "quel", "quelle", "mes", "agenda", "rendez", "vous", "reunion", "ce", "que",
        "qu", "dit", "fais", "faire", "maintenant", "apres", "avant", "puis", "ensuite", "et", "ou", "mais", "donc", "parle", "francais", "reveille", "lance", "ecris", "montre", "lis", "lire",
        "ai", "de", "du", "au", "aux", "en", "a", "d", "l", "j", "m", "n", "s", "t", "c", "qu", "toi", "tais", "coupe", "monte", "baisse", "active", "mets", "eteins", "enregistre", "arrete", "camera", "parametres", "ecoute", "assistant", "bien", "tres", "ca", "va", "comment", "pourquoi", "ou", "quand", "combien",
    ).toSet()
    val englishMarkers: Set<String> = l(
        "the", "a", "an", "to", "and", "or", "my", "me", "you", "your", "is", "are", "what", "where", "when", "how", "why", "call", "open", "send", "remind", "take", "picture",
        "photo", "message", "please", "tomorrow", "today", "tonight", "minutes", "hours", "hour", "minute", "in", "at", "on", "for", "with", "have", "do", "does", "can", "could", "would",
        "want", "volume", "turn", "speak", "tell", "him", "her", "that", "this", "it", "of", "from", "show", "read", "set", "up", "down", "wake", "then", "after", "before", "now", "i", "i'm", "im", "hello", "hi",
        "record", "video", "alarm", "mute", "shut", "off", "stop", "yes", "no", "thanks", "thank", "schedule", "calendar", "notifications", "say", "ask", "last", "latest", "next",
    ).toSet()
}
