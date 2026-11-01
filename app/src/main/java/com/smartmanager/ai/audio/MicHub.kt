package com.smartmanager.ai.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

fun interface AudioConsumer { fun onAudio(samples: ShortArray, count: Int, captureTimeMs: Long) }

/** Single owner of the microphone (16 kHz mono PCM16). Wake word, VAD, ASR and barge-in all read from here; nothing is ever sent off-device. */
@Singleton
class MicHub @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val consumers = CopyOnWriteArrayList<AudioConsumer>()
    @Volatile private var record: AudioRecord? = null
    @Volatile private var thread: Thread? = null
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    @Volatile var lastError: String? = null; private set
    /** RMS (0..32767) of the latest 100 ms chunk — shown live in Settings → Wake word to diagnose low microphone gain. */
    @Volatile var level = 0.0; private set
    @Volatile var peak = 0; private set

    fun add(c: AudioConsumer) { consumers.add(c) }
    fun remove(c: AudioConsumer) { consumers.remove(c) }

    @SuppressLint("MissingPermission")
    @Synchronized fun start(): Boolean {
        if (_running.value) return true
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { lastError = "RECORD_AUDIO not granted"; return false }
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { lastError = "AudioRecord unsupported"; return false }
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, SAMPLE_RATE * 2))
        } catch (e: Exception) { lastError = e.message; return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); lastError = "AudioRecord not initialised (microphone busy?)"; return false }
        // Echo cancellation / noise suppression when the device offers them (helps barge-in while the assistant speaks).
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        try { rec.startRecording() } catch (e: Exception) { rec.release(); lastError = e.message; return false }
        record = rec; lastError = null; _running.value = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ShortArray(CHUNK)
            while (_running.value) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var sum = 0.0; var pk = 0
                    for (i in 0 until n) { val v = buf[i].toInt(); sum += v.toDouble() * v; if (kotlin.math.abs(v) > pk) pk = kotlin.math.abs(v) }
                    level = kotlin.math.sqrt(sum / n); peak = pk
                    val t = System.currentTimeMillis(); for (c in consumers) c.onAudio(buf, n, t)
                }
                else if (n < 0) { lastError = "read error $n"; break }
            }
        }, "mic-hub").also { it.start() }
        return true
    }

    @Synchronized fun stop() {
        _running.value = false
        thread?.let { runCatching { it.join(500) } }; thread = null
        record?.let { runCatching { it.stop() }; it.release() }; record = null
    }

    companion object { const val SAMPLE_RATE = 16_000; const val CHUNK = 1_600 /* 100 ms */ }
}
