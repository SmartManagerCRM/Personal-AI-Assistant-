package com.smartmanager.ai.audio

import kotlin.math.sqrt

/** Adaptive-noise-floor energy detector with hysteresis. Cheap enough to run permanently; Silero can replace it later. */
class EnergyVad(private val minRms: Double = 350.0, private val onFactor: Double = 3.0, private val offFactor: Double = 1.8) {
    private var floor = 200.0
    var speaking = false; private set
    var lastRms = 0.0; private set

    fun process(s: ShortArray, n: Int): Boolean {
        var sum = 0.0
        for (i in 0 until n) { val v = s[i].toDouble(); sum += v * v }
        val rms = sqrt(sum / maxOf(n, 1)); lastRms = rms
        if (!speaking) floor = floor * 0.95 + rms * 0.05            // only learn the floor from non-speech
        val on = maxOf(minRms, floor * onFactor); val off = maxOf(minRms * 0.6, floor * offFactor)
        speaking = if (speaking) rms > off else rms > on
        return speaking
    }
    fun reset() { speaking = false }
}

/**
 * Collects one utterance: waits for speech (≤ [noSpeechTimeoutMs]), keeps a pre-roll so the first syllable is not clipped,
 * ends after [endSilenceMs] of silence or [maxMs] total.
 */
class UtteranceCollector(
    private val noSpeechTimeoutMs: Long, private val endSilenceMs: Long = 900, private val maxMs: Long = 15_000,
    private val preRollChunks: Int = 4, private val vad: EnergyVad = EnergyVad(),
) {
    enum class Result { COLLECTING, DONE, NO_SPEECH }
    private val pre = ArrayDeque<ShortArray>()
    private val out = ArrayList<ShortArray>()
    private var started = false; private var elapsed = 0L; private var silence = 0L; private var speechMs = 0L
    var result = Result.COLLECTING; private set
    val peakRms get() = vad.lastRms

    fun feed(s: ShortArray, n: Int): Result {
        if (result != Result.COLLECTING) return result
        val chunkMs = n * 1000L / MicHub.SAMPLE_RATE
        elapsed += chunkMs
        val speech = vad.process(s, n)
        val copy = s.copyOf(n)
        if (!started) {
            pre.addLast(copy); if (pre.size > preRollChunks) pre.removeFirst()
            if (speech) { started = true; out.addAll(pre); pre.clear(); speechMs += chunkMs }
            else if (elapsed >= noSpeechTimeoutMs) result = Result.NO_SPEECH
        } else {
            out.add(copy)
            if (speech) { silence = 0; speechMs += chunkMs } else silence += chunkMs
            if (silence >= endSilenceMs || elapsed >= maxMs) result = if (speechMs >= 200) Result.DONE else Result.NO_SPEECH
        }
        return result
    }

    fun samples(): FloatArray {
        val total = out.sumOf { it.size }; val f = FloatArray(total); var k = 0
        for (c in out) for (v in c) f[k++] = v / 32768f
        return f
    }
}
