package com.smartmanager.ai.asr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.k2fsa.sherpa.onnx.*
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.models.ModelCatalog
import com.smartmanager.ai.models.ModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class AsrResult(val text: String, /** ISO code reported by the engine ("ar","fr","en") or null */ val language: String?, val latencyMs: Long, val engine: String)

/** Replaceable speech-recognition back ends. */
interface AsrProvider { val id: String; val displayName: String; fun isAvailable(): Boolean; val note: String get() = "" }
interface WaveformAsr : AsrProvider { suspend fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult?; fun unload() }
interface MicrophoneAsr : AsrProvider { suspend fun listen(timeoutMs: Long, hint: Lang): AsrResult? }

/** Whisper (multilingual, INT8) through sherpa-onnx. Any ASR entry of the catalog that uses the sherpa Whisper layout can be selected. Language is auto-detected per utterance. Heavy → loaded on demand, released after idle. */
@Singleton
class SherpaWhisperAsr @Inject constructor(private val models: ModelManager) : WaveformAsr {
    override val id = "sherpa-whisper"; override val displayName = "Whisper (offline, sherpa-onnx)"
    private var rec: OfflineRecognizer? = null
    private var loadedId: String? = null
    private val lock = Any()
    /** Catalog id of the ASR model to use (set from settings). */
    @Volatile var selectedId: String = ModelCatalog.entries.first { it.kind == com.smartmanager.ai.models.ModelKind.ASR }.id
    @Volatile var lastLoadMs = -1L; private set
    private val entry get() = ModelCatalog.byId(selectedId)
    override fun isAvailable() = entry?.let { models.isInstalled(it) } == true
    override val note get() = if (isAvailable()) "" else "Install a Whisper model in AI models"

    override suspend fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult? = withContext(Dispatchers.Default) {
        val t0 = System.currentTimeMillis()
        val r = synchronized(lock) { if (loadedId != selectedId) { rec?.release(); rec = null }; rec ?: load().also { rec = it; loadedId = selectedId } } ?: return@withContext null
        val s = r.createStream()
        try {
            s.acceptWaveform(samples, sampleRate); r.decode(s)
            val res = r.getResult(s)
            AsrResult(res.text.trim(), res.lang.takeIf { it.isNotBlank() }?.trim('<', '|', '>', '/'), System.currentTimeMillis() - t0, selectedId)
        } finally { s.release() }
    }

    private fun load(): OfflineRecognizer? {
        val e = entry ?: return null
        val d = models.dir(e.id); val t0 = System.currentTimeMillis()
        fun pick(token: String): String? = d.listFiles()?.filter { it.name.contains(token) && it.name.endsWith(".onnx") }?.minByOrNull { if (it.name.contains("int8")) 0 else 1 }?.absolutePath
        val enc = pick("encoder") ?: return null; val dec = pick("decoder") ?: return null
        val tokens = d.listFiles()?.firstOrNull { it.name.endsWith("tokens.txt") }?.absolutePath ?: return null
        val cfg = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = enc, decoder = dec, language = e.language, task = "transcribe", tailPaddings = 1000),
                tokens = tokens, numThreads = 4, debug = false, provider = "cpu", modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(config = cfg).also { lastLoadMs = System.currentTimeMillis() - t0 }
    }
    override fun unload() = synchronized(lock) { rec?.release(); rec = null; loadedId = null }
}

/** Android's SpeechRecognizer as a fallback when no offline model is installed. On-device on API 33+ when a language pack exists; some devices fall back to Google servers (disclosed in the UI). */
@Singleton
class AndroidSpeechAsr @Inject constructor(@ApplicationContext private val ctx: Context) : MicrophoneAsr {
    override val id = "android"; override val displayName = "Android speech recognizer"
    override fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(ctx)
    override val note get() = "May use Google servers on devices without an offline language pack; the offline Whisper model avoids this."
    private val main = Handler(Looper.getMainLooper())

    override suspend fun listen(timeoutMs: Long, hint: Lang): AsrResult? = withContext(Dispatchers.Main) {
        val t0 = System.currentTimeMillis()
        withTimeoutOrNull(timeoutMs + 12_000) {
            suspendCancellableCoroutine<AsrResult?> { cont ->
                val recognizer = if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx)
                val tag = when (hint) { Lang.FRENCH -> "fr-FR"; Lang.ENGLISH -> "en-US"; Lang.MSA -> "ar"; else -> "ar-TN" }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                    if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra("android.speech.extra.ADDITIONAL_LANGUAGES", arrayListOf("ar-TN", "fr-FR", "en-US", "ar"))
                }
                fun finish(r: AsrResult?) { if (cont.isActive) cont.resume(r); runCatching { recognizer.destroy() } }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) { val t = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); finish(t?.let { AsrResult(it, tag.take(2), System.currentTimeMillis() - t0, id) }) }
                    override fun onError(error: Int) = finish(null)
                    override fun onReadyForSpeech(p: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(r: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onPartialResults(p: Bundle?) {}; override fun onEvent(t: Int, p: Bundle?) {}
                })
                cont.invokeOnCancellation { main.post { runCatching { recognizer.destroy() } } }
                recognizer.startListening(intent)
            }
        }
    }
}
