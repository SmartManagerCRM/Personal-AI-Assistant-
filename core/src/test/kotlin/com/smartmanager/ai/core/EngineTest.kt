package com.smartmanager.ai.core

import com.smartmanager.ai.core.action.*
import com.smartmanager.ai.core.ai.*
import com.smartmanager.ai.core.conversation.*
import com.smartmanager.ai.core.intent.*
import com.smartmanager.ai.core.language.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class FakeInput(private val script: ArrayDeque<String?>) : SpeechInput {
    override suspend fun listen(timeoutMs: Long): String? = if (script.isEmpty()) null else script.removeFirst()
}

class FakeOutput : SpeechOutput {
    val spoken = mutableListOf<String>(); var stops = 0
    var speakDelayMs = 0L
    override suspend fun speak(text: String, lang: Lang) { spoken += text; if (speakDelayMs > 0) delay(speakDelayMs) }
    override fun stop() { stops++ }
}

class RecordingHandler(val result: ActionResult = ActionResult(ResultStatus.SUCCESS), val prep: Preparation? = null) : ActionHandler {
    val executed = mutableListOf<ParsedIntent>()
    override suspend fun prepare(intent: ParsedIntent) = prep ?: Preparation.Ready(intent)
    override suspend fun execute(intent: ParsedIntent): ActionResult { executed += intent; return result }
}

class EngineTest {
    private fun registry(granted: Set<AppPermission> = AppPermission.entries.toSet(), call: RecordingHandler = RecordingHandler(ActionResult(ResultStatus.INITIATED, data = mapOf("contactName" to "أحمد بن علي")), Preparation.Ready(ParsedIntent(ActionName.CALL_CONTACT, mapOf("contact" to "أحمد بن علي")), "أحمد بن علي")),
                         msg: RecordingHandler = RecordingHandler(ActionResult(ResultStatus.NEEDS_USER_ACTION)), app: RecordingHandler = RecordingHandler(ActionResult(ResultStatus.SUCCESS, data = mapOf("label" to "WhatsApp"))),
                         audit: MutableList<AuditEntry> = mutableListOf()) =
        ActionRegistry({ it in granted }, { audit += it }).apply {
            register(ActionDefinition(ActionName.CALL_CONTACT, setOf(Slots.CONTACT), setOf(AppPermission.CONTACTS, AppPermission.PHONE), RiskLevel.CONFIRM, handler = call))
            register(ActionDefinition(ActionName.SEND_MESSAGE, setOf(Slots.CONTACT, Slots.BODY), setOf(AppPermission.CONTACTS), RiskLevel.CONFIRM, handler = msg))
            register(ActionDefinition(ActionName.OPEN_APP, setOf(Slots.APP), emptySet(), RiskLevel.AUTOMATIC, handler = app))
            register(ActionDefinition(ActionName.SET_VOLUME, emptySet(), emptySet(), RiskLevel.AUTOMATIC, handler = RecordingHandler()))
        }

    private fun engine(inp: List<String?>, out: FakeOutput = FakeOutput(), reg: ActionRegistry = registry(), router: ProviderRouter? = null, barge: BargeInSource? = null) =
        ConversationEngine(FakeInput(ArrayDeque(inp)), out, reg, router, barge, config = EngineConfig(maxSilences = 2))

    @Test fun `spec example dialogue - call with confirmation in Tunisian`() = runTest {
        val out = FakeOutput(); val call = RecordingHandler(ActionResult(ResultStatus.INITIATED, data = mapOf("contactName" to "أحمد بن علي")), Preparation.Ready(ParsedIntent(ActionName.CALL_CONTACT, mapOf("contact" to "أحمد بن علي")), "أحمد بن علي"))
        val e = engine(listOf("باه، عيّط لأحمد", "إي"), out, registry(call = call))
        e.runSession()
        assertEquals("إي، نسمعك.", out.spoken[0])
        assertEquals("لقيت أحمد بن علي. تحب نعيّطلو؟", out.spoken[1])
        assertEquals(1, call.executed.size)
        assertTrue("call result is reported as started, never as connected", out.spoken[2].contains("ما نجمش نتأكد"))
        assertEquals(ConvState.STANDBY, e.state.value)
        assertTrue(e.machine.violations.isEmpty())
    }

    @Test fun `no confirmation means no call`() = runTest {
        val call = RecordingHandler(ActionResult(ResultStatus.INITIATED), Preparation.Ready(ParsedIntent(ActionName.CALL_CONTACT, mapOf("contact" to "x")), "x"))
        val out = FakeOutput(); engine(listOf("عيط لأحمد", "لا"), out, registry(call = call)).runSession()
        assertEquals(0, call.executed.size); assertTrue(out.spoken.any { it.startsWith("باهي، ما عملت") })
    }

    @Test fun `conversation continues without wake word and times out to standby`() = runTest {
        val out = FakeOutput(); val e = engine(listOf("حل الواتساب", "حل اليوتيوب", null, null), out); e.runSession(greet = false)
        assertEquals(2, out.spoken.count { it.startsWith("حليت") }); assertEquals(ConvState.STANDBY, e.state.value)
    }

    @Test fun `missing slot is asked then filled - message body`() = runTest {
        val msg = RecordingHandler(ActionResult(ResultStatus.NEEDS_USER_ACTION)); val out = FakeOutput()
        engine(listOf("ابعث لسارة مساج", "نجي بعد شوية", "إي"), out, registry(msg = msg)).runSession(greet = false)
        assertEquals("شنوة تحب نكتبلو؟", out.spoken[0]); assertEquals("نجي بعد شوية", msg.executed.single().slot("body"))
        assertTrue("must say user has to press Send", out.spoken.last().contains("اضغط إرسال"))
    }

    @Test fun `multi-step plan resolves him from the previous contact`() = runTest {
        val call = RecordingHandler(ActionResult(ResultStatus.INITIATED, data = mapOf("contactName" to "Ahmed")), Preparation.Ready(ParsedIntent(ActionName.CALL_CONTACT, mapOf("contact" to "Ahmed")), "Ahmed"))
        val msg = RecordingHandler(ActionResult(ResultStatus.NEEDS_USER_ACTION)); val out = FakeOutput()
        engine(listOf("call Ahmed then tell him I'm late", "yes", "yes"), out, registry(call = call, msg = msg)).runSession(greet = false)
        assertEquals(1, call.executed.size); assertEquals("Ahmed", msg.executed.single().slot(Slots.CONTACT)); assertTrue(msg.executed.single().slot("body")!!.contains("late"))
    }

    @Test fun `denied permission never reaches handler`() = runTest {
        val app = RecordingHandler(); val msg = RecordingHandler(); val out = FakeOutput()
        val reg = registry(granted = emptySet(), msg = msg, app = app)
        engine(listOf("ابعث لسارة مساج جاي"), out, reg).runSession(greet = false)
        assertEquals(0, msg.executed.size); assertTrue(out.spoken.any { it.contains("نحتاج إذن") })
    }

    @Test fun `level 3 financial requests are refused in every language`() = runTest {
        for (t in listOf("حول فلوس لأحمد", "transfer money to Ahmed", "fais un virement", "7awel flous")) {
            val out = FakeOutput(); val call = RecordingHandler()
            engine(listOf(t), out, registry(call = call)).runSession(greet = false)
            assertEquals(t, 0, call.executed.size); assertTrue(t, out.spoken.first().let { it.contains("حساسة") || it.contains("sensitive") || it.contains("sensible") })
        }
    }

    @Test fun `unknown action from the model is rejected and restricted actions cannot run`() = runTest {
        val reg = registry()
        reg.register(ActionDefinition(ActionName.SHUTDOWN_ASSISTANT, risk = RiskLevel.RESTRICTED, handler = RecordingHandler()))
        val o = reg.run(ParsedIntent(ActionName.SHUTDOWN_ASSISTANT), confirmed = true)
        assertTrue(o is Outcome.Refused)
        assertTrue(reg.run(ParsedIntent(ActionName.TAKE_PHOTO), true) is Outcome.Invalid)
        val parsed = StructuredOutput.parse("""{"action":"DELETE_EVERYTHING","say":"ok"}""", reg.registered())
        assertTrue(parsed is AiResponse.Text)
    }

    @Test fun `audit log records confirmed execution`() = runTest {
        val audit = mutableListOf<AuditEntry>()
        engine(listOf("حل الواتساب"), reg = registry(audit = audit)).runSession(greet = false)
        assertEquals("OPEN_APP", audit.single().action); assertEquals("SUCCESS", audit.single().outcome)
    }

    @Test fun `llm text and structured action both pass through the gate`() = runTest {
        val app = RecordingHandler(ActionResult(ResultStatus.SUCCESS, data = mapOf("label" to "WhatsApp")))
        val provider = object : AIProvider {
            override val id = "fake"; override val displayName = "Fake"; override val isOnline = false
            override suspend fun isAvailable() = true
            override suspend fun generate(request: AiRequest): AiResponse =
                if (request.userText.contains("weather")) AiResponse.Text("It is sunny.") else StructuredOutput.parse("""{"action":"OPEN_APP","slots":{"app":"whatsapp"},"say":"Sure"}""", request.allowedActions)
        }
        val out = FakeOutput(); val router = ProviderRouter(listOf(provider), emptyList(), { false }, { emptySet() })
        engine(listOf("how is the weather like", "please do the thing everyone does every morning"), out, registry(app = app), router).runSession(greet = false)
        assertTrue(out.spoken.contains("It is sunny.")); assertEquals(1, app.executed.size)
    }

    @Test fun `without a model the assistant says so and never invents an answer`() = runTest {
        val out = FakeOutput(); engine(listOf("what is the capital of France"), out).runSession(greet = false)
        assertTrue(out.spoken.single().contains("No AI model"))
    }

    @Test fun `explicit language switch is honoured and replies follow`() = runTest {
        val out = FakeOutput(); engine(listOf("parle en anglais", "open WhatsApp"), out).runSession(greet = false)
        assertEquals("Okay, I'll speak English.", out.spoken[0]); assertEquals("Opened WhatsApp.", out.spoken[1])
    }

    @Test fun `barge-in stops speech and goes straight to listening`() = runTest {
        val out = FakeOutput().apply { speakDelayMs = 10_000 }
        val bargeFlow = MutableSharedFlow<Unit>()
        val e = engine(listOf(null, null), out, barge = object : BargeInSource { override val events: Flow<Unit> = bargeFlow })
        val job = launch { e.runSession(greet = true) }
        runCurrent(); bargeFlow.emit(Unit); advanceTimeBy(1); runCurrent()
        job.join()
        assertTrue("TTS must have been stopped", out.stops >= 1)
        assertEquals(ConvState.STANDBY, e.state.value)
    }

    @Test fun `shutdown by voice calls emergency hook`() = runTest {
        var down = false; val out = FakeOutput()
        val e = ConversationEngine(FakeInput(ArrayDeque(listOf("اطفي المساعد"))), out, registry(), onShutdown = { down = true })
        e.runSession(greet = false); assertTrue(down); assertEquals(ConvState.SHUTDOWN, e.state.value)
    }

    @Test fun `offline router never picks online provider unless allowed and enabled`() = runTest {
        val online = object : AIProvider { override val id = "cloud"; override val displayName = "Cloud"; override val isOnline = true
            override suspend fun isAvailable() = true; override suspend fun generate(request: AiRequest) = AiResponse.Text("x") }
        assertNull(ProviderRouter(emptyList(), listOf(online), { false }, { setOf("cloud") }).pick())
        assertNull(ProviderRouter(emptyList(), listOf(online), { true }, { emptySet() }).pick())
        assertNotNull(ProviderRouter(emptyList(), listOf(online), { true }, { setOf("cloud") }).pick())
    }

    @Test fun `state machine rejects illegal transitions but allows emergency fallback`() {
        val m = ConversationStateMachine()
        assertFalse(m.transition(ConvState.SPEAKING)); assertEquals(1, m.violations.size)
        assertTrue(m.transition(ConvState.WAKE_DETECTED)); assertTrue(m.transition(ConvState.LISTENING)); assertTrue(m.transition(ConvState.SHUTDOWN)); assertTrue(m.transition(ConvState.STANDBY))
    }

    @Test fun `planner splits multi-step phrases`() {
        val p = { s: String -> Planner.split(TextNormalizer.tokenize(s)).map { it.joinToString(" ") } }
        assertEquals(listOf("عيط لأحمد", "ابعثلو مساج"), p("عيط لأحمد وبعد ابعثلو مساج"))
        assertEquals(listOf("ouvre WhatsApp", "prends une photo"), p("ouvre WhatsApp puis prends une photo"))
        assertEquals(listOf("open camera", "take a selfie"), p("open camera and then take a selfie"))
        assertEquals(1, p("فكرني بعد عشرين دقيقة").size)
    }

    @Test fun `structured output parser handles model json`() {
        val r = StructuredOutput.parse("""Sure! {"action":"SET_VOLUME","slots":{"direction":"up","level":5},"say":"ok"} thanks""", setOf(ActionName.SET_VOLUME)) as AiResponse.Action
        assertEquals("up", r.intent.slot("direction")); assertEquals("5", r.intent.slot("level"))
        assertTrue(StructuredOutput.parse("{broken", setOf(ActionName.SET_VOLUME)) is AiResponse.Text)
    }

    @Test fun `truncated model json is repaired and raw json is never spoken`() {
        val r = StructuredOutput.parse("""{"action":"TAKE_PHOTO","slots":{"camera":"front"},"say":"باهي، نصور روحي"""", setOf(ActionName.TAKE_PHOTO)) as AiResponse.Action
        assertEquals("front", r.intent.slot("camera")); assertEquals("باهي، نصور روحي", r.say)
        val broken = StructuredOutput.parse("""{"action": TAKE_PHOTO, "say":"hello""", setOf(ActionName.TAKE_PHOTO)) as AiResponse.Text
        assertFalse(broken.text.contains("{")); assertEquals("hello", broken.text)
    }

    @Test fun `time parser`() {
        fun p(s: String) = TimeParser.parse(TextNormalizer.tokenize(s))
        assertEquals(20, p("بعد عشرين دقيقة").relativeMinutes); assertEquals(30, p("بعد نص ساعة").relativeMinutes)
        assertEquals(10, p("dans dix minutes").relativeMinutes); assertEquals(120, p("in 2 hours").relativeMinutes)
        assertEquals(2, p("بعد غدوة").dayOffset); assertEquals(8, p("demain à 8h").hour); assertEquals(30, p("à 6h30").minute)
        assertEquals(15, p("ba3d rob3 sa3a").relativeMinutes); assertEquals(20, p("baad 3chrin d9aye9").relativeMinutes)
    }

    @Test fun `language detection`() {
        val pr = TunisianLanguageProcessor()
        assertEquals(Lang.TUNISIAN, pr.process("شنوة عندي اليوم").language); assertEquals(Lang.MSA, pr.process("ماذا لدي اليوم من فضلك").language)
        assertEquals(Lang.FRENCH, pr.process("appelle-moi un taxi s'il te plaît").language); assertEquals(Lang.ENGLISH, pr.process("what is on my calendar tomorrow").language)
        assertEquals(Lang.TUNISIAN, pr.process("chnowa 3andi lyoum").language); assertEquals(Lang.MIXED_TN_FR, pr.process("نحب نعيط لأحمد tout de suite s'il te plait").language)
        assertEquals("original text preserved", "عيّط لأحمد", pr.process("عيّط لأحمد").raw); assertEquals("عيط لاحمد", pr.process("عيّـــط لأحمد").normalized)
    }
}
