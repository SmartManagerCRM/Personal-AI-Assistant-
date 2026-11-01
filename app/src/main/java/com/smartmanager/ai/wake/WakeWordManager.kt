package com.smartmanager.ai.wake

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.smartmanager.ai.audio.AudioConsumer
import com.smartmanager.ai.audio.MicHub
import com.smartmanager.ai.core.wake.SentencePieceEncoder
import com.smartmanager.ai.core.wake.WakePhrase
import com.smartmanager.ai.data.SettingsStore
import com.smartmanager.ai.models.ModelCatalog
import com.smartmanager.ai.models.ModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class WakeMetrics(
    val engine: String = "none", val ready: Boolean = false, val reason: String = "", val detections: Int = 0, val suppressed: Int = 0,
    val falsePositives: Int = 0, val lastLatencyMs: Long = -1, val avgLatencyMs: Long = -1, val phrase: String = "", val threshold: Float = 0f,
)

/**
 * Local wake-word detection with sherpa-onnx keyword spotting (no cloud, no audio leaves the device).
 * The phrase typed by the user is tokenised with the model's own sentencepiece file and written to keywords.txt.
 * NOTE: Changing the phrase does NOT train a model; it re-targets an open-vocabulary spotter, so accuracy per phrase must be checked.
 */
@Singleton
class WakeWordManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val mic: MicHub, private val models: ModelManager, private val settings: SettingsStore,
) : AudioConsumer {
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private val _metrics = MutableStateFlow(WakeMetrics())
    val metrics: StateFlow<WakeMetrics> = _metrics
    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val detections: SharedFlow<Long> = _detections
    @Volatile private var active = false
    @Volatile private var cooldownUntil = 0L
    private var latencies = ArrayDeque<Long>()
    private var chunkCounter = 0

    /** Builds the spotter for the current phrase/sensitivity. Returns null on success, or a human-readable reason. */
    @Synchronized fun prepare(phrase: String, sensitivity: Float): String? {
        release()
        val entry = ModelCatalog.byId("kws-gigaspeech-3m")!!
        if (!models.isInstalled(entry)) return fail("Wake-word model not installed (Settings → AI models)")
        val dir = models.dir(entry.id)
        val bpe = try { SentencePieceEncoder(File(dir, "bpe.model").readBytes()) } catch (e: Exception) { return fail("bpe.model unreadable: ${e.message}") }
        val threshold = (0.5f - 0.4f * sensitivity.coerceIn(0f, 1f)).coerceIn(0.1f, 0.5f)   // higher sensitivity → lower threshold
        val boost = 1.0f + 1.0f * sensitivity.coerceIn(0f, 1f)
        val latin = WakePhrase.toLatin(phrase)   // Arabic-script words → phonetic Latin spelling (the model only knows English letters)
        val line = (if (latin.isBlank()) null else bpe.keywordLine(latin, boost, threshold)) ?: return fail("\"$phrase\" (heard as \"$latin\") cannot be written with this model's vocabulary — try a Latin spelling")
        val kwFile = File(dir, "keywords.txt").apply { writeText(line + "\n") }
        return try {
            fun f(prefix: String) = dir.listFiles()!!.filter { it.name.startsWith(prefix) && it.name.endsWith(".onnx") }.minByOrNull { if (it.name.contains("int8")) 0 else 1 }!!.absolutePath
            val cfg = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(encoder = f("encoder"), decoder = f("decoder"), joiner = f("joiner")),
                    tokens = File(dir, "tokens.txt").absolutePath, numThreads = 1, debug = false, provider = "cpu",
                ),
                maxActivePaths = 4, keywordsFile = kwFile.absolutePath, keywordsScore = boost, keywordsThreshold = threshold, numTrailingBlanks = 1,
            )
            spotter = KeywordSpotter(config = cfg); stream = spotter!!.createStream()
            _metrics.update { it.copy(engine = "sherpa-onnx KWS", ready = true, reason = "", phrase = phrase, threshold = threshold) }
            null
        } catch (t: Throwable) { Log.e(TAG, "KWS init failed", t); fail("KWS init failed: ${t.message}") }
    }

    private fun fail(reason: String): String { _metrics.update { it.copy(engine = "sherpa-onnx KWS", ready = false, reason = reason) }; return reason }

    @Synchronized fun start(): Boolean { if (spotter == null) return false; active = true; mic.add(this); return mic.start() }
    @Synchronized fun stopListening() { active = false; mic.remove(this) }
    @Synchronized fun suppressFor(ms: Long) { cooldownUntil = System.currentTimeMillis() + ms }
    fun markFalsePositive() = _metrics.update { it.copy(falsePositives = it.falsePositives + 1) }

    @Synchronized fun release() { active = false; mic.remove(this); runCatching { stream?.release() }; runCatching { spotter?.release() }; stream = null; spotter = null }

    override fun onAudio(samples: ShortArray, count: Int, captureTimeMs: Long) {
        val sp = spotter ?: return; val st = stream ?: return
        if (!active) return
        val f = FloatArray(count) { samples[it] / 32768f }
        try {
            st.acceptWaveform(f, 16000)
            var hit = false
            while (sp.isReady(st)) { sp.decode(st); val r = sp.getResult(st); if (r.keyword.isNotEmpty()) { hit = true; sp.reset(st) } }
            if (hit) {
                val now = System.currentTimeMillis(); val lat = now - captureTimeMs
                if (now < cooldownUntil) { _metrics.update { it.copy(suppressed = it.suppressed + 1) }; return }
                cooldownUntil = now + 2500
                latencies.addLast(lat); if (latencies.size > 20) latencies.removeFirst()
                _metrics.update { it.copy(detections = it.detections + 1, lastLatencyMs = lat, avgLatencyMs = latencies.average().toLong()) }
                _detections.tryEmit(now)
            }
        } catch (t: Throwable) { Log.e(TAG, "KWS decode", t) }
        if (++chunkCounter % 600 == 0) runCatching { sp.reset(st) } // periodic reset keeps decoder state bounded
    }

    private companion object { const val TAG = "WakeWord" }
}
