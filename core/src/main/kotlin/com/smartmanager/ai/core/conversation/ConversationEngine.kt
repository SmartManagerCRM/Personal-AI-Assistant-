package com.smartmanager.ai.core.conversation

import com.smartmanager.ai.core.action.*
import com.smartmanager.ai.core.ai.*
import com.smartmanager.ai.core.intent.*
import com.smartmanager.ai.core.language.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Ports implemented by the Android layer (ASR + VAD, TTS, barge-in detector). */
interface SpeechInput {
    /** Listens until end-of-speech; returns the transcription, or null when nothing was said within [timeoutMs]. */
    suspend fun listen(timeoutMs: Long): String?
}

interface SpeechOutput {
    suspend fun speak(text: String, lang: Lang)
    fun stop()
}

interface BargeInSource { val events: Flow<Unit> }

data class EngineConfig(
    val listenTimeoutMs: Long = 8_000,
    /** Consecutive silent listen windows before the conversation times out and returns to standby. */
    val maxSilences: Int = 2,
    val maxHistory: Int = 8,
    val aiMinConfidence: Double = 0.6,
)

data class TranscriptLine(val fromUser: Boolean, val text: String, val lang: Lang, val timeMs: Long)

class ConversationEngine(
    private val input: SpeechInput,
    private val output: SpeechOutput,
    private val registry: ActionRegistry,
    private val router: ProviderRouter? = null,
    private val barge: BargeInSource? = null,
    private val processor: TunisianLanguageProcessor = TunisianLanguageProcessor(),
    private val parser: IntentParser = IntentParser(processor),
    private val config: EngineConfig = EngineConfig(),
    private val now: () -> Long = System::currentTimeMillis,
    initialLanguage: Lang = Lang.TUNISIAN,
    /** Called when the assistant must stop listening completely (voice "shut down" command). */
    private val onShutdown: () -> Unit = {},
    private val onQuiet: (minutes: Int) -> Unit = {},
    private val onLanguagePreference: (Lang) -> Unit = {},
) {
    val machine = ConversationStateMachine()
    val state: StateFlow<ConvState> get() = machine.state
    private val _transcript = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 64)
    val transcript: SharedFlow<TranscriptLine> = _transcript

    private sealed interface Pending {
        data class Confirm(val intent: ParsedIntent, val subject: String?, val rest: List<ParsedIntent>) : Pending
        data class Fill(val intent: ParsedIntent, val rest: List<ParsedIntent>) : Pending
    }

    private var pending: Pending? = null
    private var lastDetected: Lang = initialLanguage
    private var pinned: Lang? = null
    private var lastContact: String? = null
    private val history = ArrayDeque<ChatMessage>()
    private var sessionEnded = false
    private var bargedIn = false

    val preferredLanguage get() = pinned ?: lastDetected
    fun pinLanguage(l: Lang?) { pinned = l }

    /** Runs one conversation: greeting → listen/respond loop → timeout back to standby. */
    suspend fun runSession(greet: Boolean = true) {
        sessionEnded = false; pending = null; bargedIn = false
        try {
            machine.transition(ConvState.WAKE_DETECTED)
            if (greet) say(R.LISTENING, preferredLang())
            var silences = 0
            while (!sessionEnded) {
                machine.transition(ConvState.LISTENING)
                bargedIn = false
                val text = input.listen(config.listenTimeoutMs)
                if (text.isNullOrBlank()) {
                    if (++silences >= config.maxSilences) break
                    continue
                }
                silences = 0
                handle(text)
            }
        } finally {
            output.stop()
            if (machine.state.value != ConvState.QUIET && machine.state.value != ConvState.SHUTDOWN) machine.transition(ConvState.STANDBY)
        }
    }

    /** Feeds one utterance (used by tests and by the text field on the main screen). */
    suspend fun handle(text: String) {
        machine.transition(ConvState.PROCESSING)
        val u = processor.process(text, lastDetected)
        if (u.language != Lang.UNKNOWN) lastDetected = if (u.language == Lang.MIXED_TN_FR) Lang.TUNISIAN else u.language
        val lang = replyLang(u)
        emitUser(text, u.language)

        val p = pending
        pending = null
        if (p is Pending.Confirm) {
            when (parser.parse(u).action) {
                ActionName.CONFIRM_YES -> { if (execute(p.intent, confirmed = true, rest = p.rest, lang = lang) && p.rest.isNotEmpty()) runPlan(p.rest, lang); return }
                ActionName.CONFIRM_NO -> { say(R.CANCELLED, lang); return }
                else -> {} // new request replaces the pending confirmation
            }
        }
        if (p is Pending.Fill) {
            if (parser.parse(u).action == ActionName.CONFIRM_NO) { say(R.CANCELLED, lang); return }
            val filled = fill(p.intent, u)
            runPlan(listOf(filled) + p.rest, lang); return
        }

        if (RestrictedTopics.matches(text)) { say(R.REFUSED_RESTRICTED, lang); return }

        val segments = Planner.split(u.rawTokens)
        val intents = segments.map { parser.parse(it.joinToString(" "), lastDetected).let { i -> if (i.language == Lang.UNKNOWN) i.copy(language = u.language) else i } }
        if (intents.size == 1 && (intents[0].action == ActionName.UNKNOWN || intents[0].confidence < 0.5 && intents[0].missing.isNotEmpty() && intents[0].action == ActionName.OPEN_APP)) {
            askAi(text, lang); return
        }
        runPlan(intents, lang)
    }

    // ---------------------------------------------------------------------------------------------------

    private fun preferredLang() = pinned ?: lastDetected
    private fun replyLang(u: Utterance): Lang = pinned ?: when (u.language) { Lang.UNKNOWN -> lastDetected; Lang.MIXED_TN_FR -> Lang.TUNISIAN; else -> u.language }

    private suspend fun runPlan(plan: List<ParsedIntent>, lang: Lang) {
        var queue = plan
        while (queue.isNotEmpty() && !sessionEnded && !bargedIn) {
            var intent = queue.first(); val rest = queue.drop(1)
            queue = rest
            // contextual reference: "عيط لأحمد وبعد ابعثلو مساج" → him = Ahmed
            if (intent.slot(Slots.CONTACT_REF) == "CONTEXT") {
                intent = lastContact?.let { c -> intent.copy(slots = intent.slots - Slots.CONTACT_REF + (Slots.CONTACT to c), missing = intent.missing - Slots.CONTACT) }
                    ?: intent.copy(missing = (intent.missing + Slots.CONTACT).distinct())
            }
            when (intent.action) {
                ActionName.UNKNOWN -> { say(R.NOT_UNDERSTOOD, lang); return }
                ActionName.GREETING -> { say(R.LISTENING, lang); continue }
                ActionName.STOP_SPEAKING -> { output.stop(); continue }
                ActionName.CONFIRM_YES, ActionName.CONFIRM_NO -> { say(R.NOT_UNDERSTOOD, lang); return }
                ActionName.SET_LANGUAGE -> {
                    val target = Lang.valueOf(intent.slot(Slots.LANGUAGE) ?: "TUNISIAN")
                    pinned = target; onLanguagePreference(target)
                    say(R.LANG_SWITCHED, target, Responses.languageName(target, target)); continue
                }
                ActionName.QUIET_MODE -> {
                    val mins = intent.slot(Slots.MINUTES_QUIET)?.toIntOrNull() ?: 30
                    say(R.QUIET_ON, lang); machine.transition(ConvState.QUIET); sessionEnded = true; onQuiet(mins); return
                }
                ActionName.SHUTDOWN_ASSISTANT -> { say(R.SHUTDOWN, lang); machine.transition(ConvState.SHUTDOWN); sessionEnded = true; onShutdown(); return }
                else -> {}
            }
            if (intent.missing.isNotEmpty()) { ask(intent, rest, lang); return }
            if (!execute(intent, confirmed = false, rest = rest, lang = lang)) return
        }
    }

    /** Executes one step. Returns true when the plan may continue with the next step. */
    private suspend fun execute(intent: ParsedIntent, confirmed: Boolean, rest: List<ParsedIntent>, lang: Lang): Boolean {
        machine.transition(ConvState.EXECUTING)
        when (val o = registry.run(intent, confirmed)) {
            is Outcome.NeedsSlots -> { ask(intent.copy(missing = o.missing), rest, lang); return false }
            is Outcome.NeedsPermission -> {
                say(if (o.permissions.contains(AppPermission.NOTIFICATION_ACCESS)) R.NEED_NOTIF_ACCESS else R.NEED_PERMISSION, lang, *(if (o.permissions.contains(AppPermission.NOTIFICATION_ACCESS)) emptyArray() else arrayOf<Any>(o.permissions.joinToString(", ") { it.name.lowercase().replace('_', ' ') })))
                return false
            }
            is Outcome.Refused -> { say(R.REFUSED_RESTRICTED, lang); return false }
            is Outcome.Invalid -> { say(R.NOT_UNDERSTOOD, lang); return false }
            is Outcome.NeedsConfirmation -> {
                pending = Pending.Confirm(o.intent, o.subject, rest)
                machine.transition(ConvState.WAITING_CONFIRMATION)
                val who = o.subject ?: o.intent.slot(Slots.CONTACT) ?: "?"
                val prompt = when (o.intent.action) {
                    ActionName.CALL_CONTACT -> Responses.get(R.CONFIRM_CALL, lang, who)
                    ActionName.SEND_MESSAGE -> Responses.get(R.CONFIRM_MESSAGE, lang, who, o.intent.slot(Slots.BODY) ?: "")
                    ActionName.SHARE_LAST_PHOTO -> Responses.get(R.CONFIRM_SHARE, lang, who)
                    else -> Responses.get(R.DONE, lang)
                }
                sayRaw(prompt, lang); return false
            }
            is Outcome.Executed -> {
                o.result.data["contactName"]?.let { lastContact = it }
                    ?: o.intent.slot(Slots.CONTACT)?.takeIf { o.result.isOk }?.let { lastContact = it }
                sayRaw(describe(o.intent, o.result, lang), lang)
                if (!o.result.isOk) return false
            }
        }
        return true
    }

    private suspend fun ask(intent: ParsedIntent, rest: List<ParsedIntent>, lang: Lang) {
        pending = Pending.Fill(intent, rest)
        val key = when (intent.missing.first()) {
            Slots.CONTACT -> R.ASK_CONTACT; Slots.BODY -> R.ASK_BODY; "when" -> R.ASK_WHEN; Slots.WHAT -> R.ASK_WHAT; Slots.APP -> R.ASK_APP; else -> R.NOT_UNDERSTOOD
        }
        say(key, lang)
    }

    private fun fill(intent: ParsedIntent, u: Utterance): ParsedIntent {
        val slot = intent.missing.first()
        val slots = intent.slots.toMutableMap()
        when (slot) {
            Slots.CONTACT -> slots[Slots.CONTACT] = u.rawTokens.filterNot { Lexicon.isStop(TextNormalizer.normalizeToken(it)) }.joinToString(" ").ifBlank { u.raw }.let { n -> if (n.length >= 4 && n[0] == 'ل' && n.any { TextNormalizer.isArabicChar(it) }) n.substring(1) else n }
            "when" -> { val t = TimeParser.parse(u.rawTokens); t.relativeMinutes?.let { slots[Slots.MINUTES] = it.toString() }; t.dayOffset?.let { slots[Slots.DAY_OFFSET] = it.toString() }; t.hour?.let { slots[Slots.HOUR] = it.toString(); slots[Slots.MINUTE] = t.minute.toString() } }
            else -> slots[slot] = u.raw.trim()
        }
        val stillMissing = intent.missing.drop(1).filter { it !in slots || slots[it].isNullOrBlank() }.toMutableList()
        if (slot == "when" && !(slots.containsKey(Slots.MINUTES) || slots.containsKey(Slots.DAY_OFFSET) || slots.containsKey(Slots.HOUR))) stillMissing.add(0, "when")
        return intent.copy(slots = slots, missing = stillMissing)
    }

    private suspend fun askAi(text: String, lang: Lang) {
        val provider = router?.pick()
        if (provider == null) { say(R.NO_AI, lang); return }
        machine.transition(ConvState.THINKING)
        val resp = try {
            provider.generate(AiRequest(history.toList(), text, lang, registry.registered().toSet()))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { AiResponse.Unavailable(e.message ?: "error") }
        when (resp) {
            is AiResponse.Text -> { remember(text, resp.text); sayRaw(resp.text.ifBlank { Responses.get(R.NOT_UNDERSTOOD, lang) }, lang) }
            is AiResponse.Action -> {
                resp.say?.takeIf { it.isNotBlank() }?.let { sayRaw(it, lang) }
                // The model's action goes through the same policy gate as any rule-parsed action.
                val missing = registry.definition(resp.intent.action)?.requiredSlots?.filter { resp.intent.slot(it).isNullOrBlank() && !(it == Slots.CONTACT && resp.intent.slot(Slots.CONTACT_REF) != null) }.orEmpty()
                runPlan(listOf(resp.intent.copy(missing = missing, language = lang, rawText = text)), lang)
            }
            is AiResponse.Unavailable -> say(R.NO_AI, lang)
        }
    }

    private fun remember(user: String, assistant: String) {
        history += ChatMessage(ChatMessage.Role.USER, user); history += ChatMessage(ChatMessage.Role.ASSISTANT, assistant)
        while (history.size > config.maxHistory) history.removeFirst()
    }

    private fun describe(intent: ParsedIntent, r: ActionResult, lang: Lang): String {
        val name = r.data["contactName"] ?: intent.slot(Slots.CONTACT) ?: ""
        if (!r.isOk) return when (r.status) {
            ResultStatus.NOT_FOUND -> when (intent.action) {
                ActionName.OPEN_APP -> Responses.get(R.APP_MISSING, lang, intent.slot(Slots.APP) ?: "")
                ActionName.SHARE_LAST_PHOTO -> Responses.get(R.PHOTO_NONE, lang)
                else -> Responses.get(R.CONTACT_NOT_FOUND, lang, name)
            }
            ResultStatus.AMBIGUOUS -> Responses.get(R.CONTACT_AMBIGUOUS, lang, r.detail)
            ResultStatus.UNAVAILABLE -> Responses.get(R.UNAVAILABLE, lang)
            else -> Responses.get(R.ACTION_FAILED, lang, r.detail)
        }
        return when (intent.action) {
            ActionName.CALL_CONTACT -> Responses.get(R.CALL_STARTED, lang, name)
            ActionName.SEND_MESSAGE -> if (r.status == ResultStatus.NEEDS_USER_ACTION) Responses.get(R.WHATSAPP_PRESS_SEND, lang) else Responses.get(R.DONE, lang)
            ActionName.SHARE_LAST_PHOTO -> Responses.get(R.SHARE_READY, lang)
            ActionName.OPEN_APP -> Responses.get(R.APP_OPENED, lang, r.data["label"] ?: intent.slot(Slots.APP) ?: "")
            ActionName.TAKE_PHOTO -> Responses.get(R.CAMERA_OPENED, lang)
            ActionName.RECORD_VIDEO -> Responses.get(R.VIDEO_OPENED, lang)
            ActionName.SET_REMINDER -> Responses.get(R.REMINDER_SET, lang, r.data["when"] ?: "")
            ActionName.SET_ALARM -> Responses.get(R.ALARM_SET, lang, r.data["when"] ?: "")
            ActionName.READ_AGENDA -> if (r.data["count"] == "0") Responses.get(R.AGENDA_EMPTY, lang, r.data["day"] ?: "") else Responses.get(R.AGENDA_ITEMS, lang, r.data["count"] ?: "", r.detail)
            ActionName.READ_NOTIFICATIONS -> if (r.detail.isBlank()) Responses.get(R.NOTIF_NONE, lang) else Responses.get(R.NOTIF_ITEMS, lang, r.detail)
            ActionName.SET_VOLUME -> Responses.get(R.VOLUME_DONE, lang)
            ActionName.OPEN_SETTINGS_PANEL -> Responses.get(R.SETTINGS_OPENED, lang, r.data["label"] ?: "")
            else -> Responses.get(R.DONE, lang)
        }
    }

    // ---- speaking with barge-in ---------------------------------------------------------------------------

    private suspend fun say(key: R, lang: Lang, vararg args: Any) = sayRaw(Responses.get(key, lang, *args), lang)

    private suspend fun sayRaw(text: String, lang: Lang) {
        if (text.isBlank()) return
        machine.transition(ConvState.SPEAKING)
        _transcript.tryEmit(TranscriptLine(false, text, lang, now()))
        if (history.lastOrNull()?.role != ChatMessage.Role.ASSISTANT || history.lastOrNull()?.content != text) {
            history += ChatMessage(ChatMessage.Role.ASSISTANT, text); while (history.size > config.maxHistory) history.removeFirst()
        }
        coroutineScope {
            val speaking = launch { output.speak(text, lang) }
            val watcher = barge?.let { b ->
                launch { b.events.first(); bargedIn = true; output.stop(); speaking.cancel() }
            }
            speaking.join()
            watcher?.cancel()
        }
        if (!sessionEnded) machine.transition(if (pending is Pending.Confirm) ConvState.WAITING_CONFIRMATION else ConvState.LISTENING)
    }

    private fun emitUser(text: String, lang: Lang) {
        _transcript.tryEmit(TranscriptLine(true, text, lang, now()))
        history += ChatMessage(ChatMessage.Role.USER, text); while (history.size > config.maxHistory) history.removeFirst()
    }
}
