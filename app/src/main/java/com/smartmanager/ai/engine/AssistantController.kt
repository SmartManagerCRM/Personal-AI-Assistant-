package com.smartmanager.ai.engine

import android.content.Context
import android.util.Log
import com.smartmanager.ai.BuildConfig
import com.smartmanager.ai.actions.ActionRegistryFactory
import com.smartmanager.ai.ai.LlamaCppProvider
import com.smartmanager.ai.ai.OpenAiCompatibleProvider
import com.smartmanager.ai.asr.SherpaWhisperAsr
import com.smartmanager.ai.assistant.SmartVoiceInteractionService
import com.smartmanager.ai.audio.MicHub
import com.smartmanager.ai.core.ai.ProviderRouter
import com.smartmanager.ai.core.conversation.*
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.data.MemoryRepository
import com.smartmanager.ai.data.Settings
import com.smartmanager.ai.data.SettingsStore
import com.smartmanager.ai.license.LicenseManager
import com.smartmanager.ai.perm.PermissionManager
import com.smartmanager.ai.tts.AndroidTts
import com.smartmanager.license.LicenseStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

data class UiState(
    val conv: ConvState = ConvState.STANDBY, val standby: Boolean = false, val micOn: Boolean = false, val quietUntilMs: Long = 0,
    val message: String = "", val transcript: List<TranscriptLine> = emptyList(), val providerLabel: String = "",
)

/** Orchestrates wake word → conversation → standby, resource lifecycle (heavy models are released after use) and the emergency stop. */
@Singleton
class AssistantController @Inject constructor(
    @ApplicationContext private val ctx: Context, private val settings: SettingsStore, private val mic: MicHub, private val wake: com.smartmanager.ai.wake.WakeWordManager,
    private val voice: VoiceInput, private val tts: AndroidTts, registryFactory: ActionRegistryFactory, private val llama: LlamaCppProvider, private val openai: OpenAiCompatibleProvider,
    private val memory: MemoryRepository, private val perms: PermissionManager, private val perf: PerfStats, private val barge: EnergyBargeIn,
    private val whisper: SherpaWhisperAsr, private val license: LicenseManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    @Volatile private var cfg = Settings()
    private val sessionLock = Mutex()
    private var sessionJob: Job? = null
    private var resumeJob: Job? = null
    private var idleJob: Job? = null
    private var started = false

    private val router = ProviderRouter(listOf(llama), listOf(openai), { cfg.networkAllowed && cfg.hybridMode }, { cfg.enabledOnline })
    private val engine: ConversationEngine by lazy {
        ConversationEngine(
            input = voice, output = tts, registry = registryFactory.create(), router = router, barge = barge,
            onShutdown = { emergencyStop("Stopped by voice command") }, onQuiet = { quiet(it) },
            onLanguagePreference = { l -> scope.launch { settings.update { it.copy(language = l.name) } } },
        )
    }

    /** Called once from the service/UI; idempotent. */
    fun init() {
        if (started) return; started = true
        voice.hint = { engine.preferredLanguage }
        scope.launch { settings.data.collect { s -> cfg = s; engine.pinLanguage(if (s.language == "auto") null else runCatching { Lang.valueOf(s.language) }.getOrNull()) } }
        scope.launch { engine.state.collect { c -> _ui.update { it.copy(conv = c) } } }
        scope.launch { mic.running.collect { r -> _ui.update { it.copy(micOn = r) } } }
        scope.launch { engine.transcript.collect { l -> _ui.update { it.copy(transcript = (it.transcript + l).takeLast(60), providerLabel = router.lastUsed?.let { p -> if (p.isOnline) "${p.displayName} · online" else p.displayName }.orEmpty()) }; memory.recordTurn(l.fromUser, l.text, l.lang.tag, l.timeMs) } }
        scope.launch { wake.detections.collect { onWake() } }
    }

    private suspend fun licenseOk(): Boolean {
        if (!BuildConfig.ENFORCE_LICENSE) return true
        return license.status() is LicenseStatus.Valid
    }

    // ---- standby (wake word) ---------------------------------------------------------------------------
    /** @return null when listening for the wake word, otherwise the reason it cannot. */
    suspend fun startStandby(): String? {
        init()
        if (!licenseOk()) return msg("License required")
        if (!perms.isGranted(com.smartmanager.ai.core.action.AppPermission.MICROPHONE)) return msg("Microphone permission missing")
        val s = settings.data.first()
        wake.prepare(s.wakePhrase, s.wakeSensitivity)?.let { return msg(it) }
        if (!wake.start()) return msg("Microphone unavailable: ${mic.lastError ?: "busy"}")
        settings.update { it.copy(standbyEnabled = true, quietUntilMs = 0) }
        _ui.update { it.copy(standby = true, quietUntilMs = 0, message = "") }
        return null
    }

    fun isStandby() = _ui.value.standby

    private fun msg(m: String): String { _ui.update { it.copy(message = m, standby = false) }; return m }

    // ---- conversation -------------------------------------------------------------------------------------
    fun onWake() { if (sessionJob?.isActive != true && _ui.value.quietUntilMs < System.currentTimeMillis()) converse(greet = true) }
    fun talk() { if (sessionJob?.isActive != true) converse(greet = true) }

    private fun converse(greet: Boolean) {
        idleJob?.cancel()
        sessionJob = scope.launch {
            if (!sessionLock.tryLock()) return@launch
            try {
                if (!licenseOk()) { _ui.update { it.copy(message = "License required") }; return@launch }
                if (!perms.isGranted(com.smartmanager.ai.core.action.AppPermission.MICROPHONE)) { _ui.update { it.copy(message = "Microphone permission missing") }; return@launch }
                voice.unavailableReason()?.let { r -> _ui.update { it.copy(message = r) }; return@launch }
                _ui.update { it.copy(message = "") }
                wake.stopListening()
                SmartVoiceInteractionService.instance?.showSessionSafely()
                engine.runSession(greet)
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { Log.e(TAG, "session failed", e); _ui.update { it.copy(message = e.message.orEmpty()) } }
            finally {
                sessionLock.unlock()
                com.smartmanager.ai.assistant.SessionBridge.session?.let { s -> withContext(Dispatchers.Main) { runCatching { s.hide() } } }
                afterSession()
            }
        }
    }

    /** Back to standby: release heavy models after a short idle, keep only the wake-word engine alive. */
    private fun afterSession() {
        if (_ui.value.standby && _ui.value.conv != ConvState.QUIET && _ui.value.conv != ConvState.SHUTDOWN) { if (!wake.start()) mic.start() } else if (!_ui.value.standby) mic.stop()
        idleJob = scope.launch { delay(IDLE_UNLOAD_MS); whisper.unload(); llama.unload() }
    }

    /** Typed command from the main screen (same pipeline as voice). */
    fun typed(text: String) { scope.launch { runCatching { engine.handle(text) } } }

    fun speakIfActive(text: String) { if (_ui.value.standby && sessionJob?.isActive != true) scope.launch { runCatching { tts.speak(text, engine.preferredLanguage) } } }

    // ---- quiet mode / emergency stop -----------------------------------------------------------------------
    fun quiet(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        sessionJob?.cancel(); wake.stopListening(); mic.stop()
        _ui.update { it.copy(quietUntilMs = until, conv = ConvState.QUIET) }
        scope.launch { settings.update { it.copy(quietUntilMs = until) } }
        resumeJob?.cancel()
        resumeJob = scope.launch { delay(minutes * 60_000L); _ui.update { it.copy(quietUntilMs = 0) }; engine.machine.transition(ConvState.STANDBY); if (_ui.value.standby) startStandby() }
    }

    /** Immediate shutdown of every audio path (notification button, UI button, voice command). */
    fun emergencyStop(reason: String = "") {
        sessionJob?.cancel(); resumeJob?.cancel(); idleJob?.cancel()
        tts.stop(); wake.stopListening(); wake.release(); mic.stop(); whisper.unload(); llama.unload()
        engine.machine.transition(ConvState.STANDBY)
        _ui.update { it.copy(standby = false, micOn = false, quietUntilMs = 0, message = reason) }
        scope.launch { settings.update { it.copy(standbyEnabled = false, quietUntilMs = 0) } }
    }

    private companion object { const val TAG = "Assistant"; const val IDLE_UNLOAD_MS = 45_000L }
}
