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

data class PerfSnapshot(val asrLastMs: Long = -1, val asrAvgMs: Long = -1, val asrEngine: String = "", val whisperLoadMs: Long = -1, val lastDetectedLang: String = "", val utterances: Int = 0, val llmTokPerSec: Double = 0.0)

@Singleton
class PerfStats @Inject constructor() {
    private val _s = MutableStateFlow(PerfSnapshot()); val snapshot: StateFlow<PerfSnapshot> = _s
    private val lat = ArrayDeque<Long>()
    fun asr(r: AsrResult, loadMs: Long) { lat.addLast(r.latencyMs); if (lat.size > 20) lat.removeFirst(); _s.update { it.copy(asrLastMs = r.latencyMs, asrAvgMs = lat.average().toLong(), asrEngine = r.engine, whisperLoadMs = loadMs, lastDetectedLang = r.language.orEmpty(), utterances = it.utterances + 1) } }
    fun llm(tps: Double) = _s.update { it.copy(llmTokPerSec = tps) }
}

/** Speech input: Whisper (offline, shares the microphone hub with VAD) when installed and selected; otherwise Android's recognizer. */
@Singleton
class VoiceInput @Inject constructor(
    private val mic: MicHub, private val whisper: SherpaWhisperAsr, private val android: AndroidSpeechAsr, private val settings: SettingsStore, private val perf: PerfStats,
) : SpeechInput {
    @Volatile var hint: () -> Lang = { Lang.TUNISIAN }
    @Volatile var lastUsedEngine: String = ""; private set

    override suspend fun listen(timeoutMs: Long): String? {
        val s = settings.data.first()
        val useWhisper = (s.activeAsr == whisper.id || s.activeAsr == "auto") && whisper.isAvailable()
        return if (useWhisper) listenWhisper(timeoutMs) else listenAndroid(timeoutMs)
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
        lastUsedEngine = r.engine; perf.asr(r, whisper.lastLoadMs)
        return r.text.takeIf { it.isNotBlank() && !isHallucination(it) }
    }

    private suspend fun listenAndroid(timeoutMs: Long): String? {
        if (!android.isAvailable()) return null
        mic.stop()                                 // SpeechRecognizer needs exclusive microphone access
        val r = android.listen(timeoutMs, hint()) ?: return null
        lastUsedEngine = r.engine; perf.asr(r, -1)
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
