package com.smartmanager.ai.engine

import com.smartmanager.ai.asr.*
import com.smartmanager.ai.audio.*
import com.smartmanager.ai.core.conversation.BargeInSource
import com.smartmanager.ai.core.conversation.SpeechInput
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class PerfSnapshot(val asrLastMs: Long = -1, val asrAvgMs: Long = -1, val asrEngine: String = "", val whisperLoadMs: Long = -1, val lastDetectedLang: String = "", val utterances: Int = 0, val llmTokPerSec: Double = 0.0, val replyMs: Long = -1, val lastTranscript: String = "", val lastError: String = "")

@Singleton
class PerfStats @Inject constructor() {
    private val _s = MutableStateFlow(PerfSnapshot()); val snapshot: StateFlow<PerfSnapshot> = _s
    private val lat = ArrayDeque<Long>()
    fun asr(r: AsrResult, loadMs: Long) { lat.addLast(r.latencyMs); if (lat.size > 20) lat.removeFirst(); _s.update { it.copy(asrLastMs = r.latencyMs, asrAvgMs = lat.average().toLong(), asrEngine = r.engine, whisperLoadMs = loadMs, lastDetectedLang = r.language.orEmpty(), utterances = it.utterances + 1) } }
    fun llm(tps: Double) = _s.update { it.copy(llmTokPerSec = tps) }
    fun reply(ms: Long) = _s.update { it.copy(replyMs = ms) }
    fun transcript(t: String) = _s.update { it.copy(lastTranscript = t, lastError = "") }
    fun error(e: String?) = _s.update { it.copy(lastError = e.orEmpty()) }
}

/** Speech input: Whisper (offline, shares the microphone hub with VAD) when installed and selected; otherwise Android's recognizer. */
@Singleton
class VoiceInput @Inject constructor(
    private val mic: MicHub, private val whisper: SherpaWhisperAsr, private val android: AndroidSpeechAsr, private val settings: SettingsStore, private val perf: PerfStats,
) : SpeechInput {
    @Volatile var hint: () -> Lang = { Lang.TUNISIAN }
    @Volatile var lastUsedEngine: String = ""; private set

    /** activeAsr holds a catalog id ("android" = system recognizer). Returns true when that sherpa model is installed and now selected. */
    private fun selectWhisper(active: String): Boolean {
        if (active == "android" || com.smartmanager.ai.models.ModelCatalog.byId(active)?.kind != com.smartmanager.ai.models.ModelKind.ASR) return false
        whisper.selectedId = active
        return whisper.isAvailable()
    }

    /** Null when speech recognition can work, otherwise a human-readable reason shown in the UI. */
    suspend fun unavailableReason(): String? {
        val s = settings.data.first()
        return if (selectWhisper(s.activeAsr) || android.isAvailable()) null else "No speech recognizer: install the Whisper model (Settings → AI models) or an on-device Android speech service"
    }

    override suspend fun listen(timeoutMs: Long): String? {
        val s = settings.data.first()
        return if (selectWhisper(s.activeAsr)) listenWhisper(timeoutMs) else listenAndroid(timeoutMs, preferOffline = !(s.networkAllowed && s.hybridMode))
    }

    private suspend fun listenWhisper(timeoutMs: Long): String? {
        if (!mic.running.value && !mic.start()) return null
        val collector = UtteranceCollector(noSpeechTimeoutMs = timeoutMs)
        val done = CompletableDeferred<Boolean>()
        val consumer = AudioConsumer { s, n, _ -> if (!done.isCompleted) { val r = collector.feed(s, n); if (r != UtteranceCollector.Result.COLLECTING) done.complete(r == UtteranceCollector.Result.DONE) } }
        mic.add(consumer)
        val got = try { withTimeoutOrNull(timeoutMs + 20_000) { done.await() } ?: false } finally { mic.remove(consumer) }
        if (!got) return null
        val r = whisper.transcribe(collector.samples(), MicHub.SAMPLE_RATE) ?: return null
        lastUsedEngine = r.engine; perf.asr(r, whisper.lastLoadMs); perf.transcript(r.text)
        return r.text.takeIf { it.isNotBlank() && !isHallucination(it) }
    }

    /** Warms the selected offline model while the greeting plays. */
    suspend fun warmUp() { val s = settings.data.first(); if (selectWhisper(s.activeAsr)) runCatching { whisper.warmUp() } }
    val lastError: String? get() = android.lastError

    private suspend fun listenAndroid(timeoutMs: Long, preferOffline: Boolean): String? {
        if (!android.isAvailable()) return null
        mic.stop()                                 // SpeechRecognizer needs exclusive microphone access
        val r = android.listen(timeoutMs, hint(), preferOffline) ?: run { perf.error(android.lastError); return null }
        lastUsedEngine = r.engine; perf.asr(r, -1); perf.transcript(r.text)
        return r.text.takeIf { it.isNotBlank() }
    }

    /** Whisper emits stock phrases on near-silence; ignore those so noise does not become a command. */
    private fun isHallucination(t: String): Boolean {
        val n = t.trim().lowercase().trim('.', '!', '?', ' ')
        return n.length < 2 || n in setOf("you", "thank you", "thanks for watching", "merci", "merci d'avoir regardé cette vidéo", "شكرا", "شكراً للمشاهدة", "اشتركوا في القناة", "اشترك في القناة")
    }
}

/** Detects the user speaking over the assistant (needs the mic hub running; uses a self-calibrating threshold against TTS echo). */
class EnergyBargeIn @Inject constructor(private val mic: MicHub, private val settings: SettingsStore) : BargeInSource {
    override val events: Flow<Unit> = callbackFlow {
        if (!settings.data.first().bargeIn) { awaitClose { }; return@callbackFlow }
        if (!mic.running.value) mic.start()
        val vad = EnergyVad(minRms = 1000.0, onFactor = 3.5)
        var baseline = 0.0; var seen = 0; var run = 0
        val c = AudioConsumer { s, n, _ ->
            val speech = vad.process(s, n); val rms = vad.lastRms
            if (seen < 4) { baseline = max(baseline, rms); seen++ ; return@AudioConsumer }   // first 400 ms = assistant's own echo level
            if (speech && rms > baseline * 2.2) { if (++run >= 3) trySend(Unit) } else run = 0
        }
        mic.add(c)
        awaitClose { mic.remove(c) }
    }
}
