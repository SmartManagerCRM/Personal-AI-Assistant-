package com.smartmanager.ai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.smartmanager.ai.core.conversation.SpeechOutput
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class VoiceInfo(val name: String, val locale: String, val offline: Boolean, val quality: Int)
data class TtsStatus(val ready: Boolean, val engine: String = "", val arabicAvailable: Boolean = false, val tunisianAvailable: Boolean = false, val message: String = "")

/** Replaceable TTS provider based on Android TextToSpeech: voice discovery, per-language availability, rate/pitch, audio focus. */
@Singleton
class AndroidTts @Inject constructor(@ApplicationContext private val ctx: Context, private val settings: SettingsStore) : SpeechOutput {
    private var tts: TextToSpeech? = null
    private var ready = CompletableDeferred<Boolean>()
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    @Volatile private var currentUtterance: String? = null
    @Volatile private var onDone: ((Boolean) -> Unit)? = null

    @Synchronized private fun init() {
        if (tts != null) return
        ready = CompletableDeferred()
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (id == currentUtterance) onDone?.invoke(true) }
                    @Deprecated("deprecated") override fun onError(id: String?) { if (id == currentUtterance) onDone?.invoke(false) }
                    override fun onStop(id: String?, interrupted: Boolean) { if (id == currentUtterance) onDone?.invoke(false) }
                })
                ready.complete(true)
            } else { tts = null; ready.complete(false) }
        }
    }

    suspend fun status(): TtsStatus {
        init()
        if (!ready.await()) return TtsStatus(false, message = "No text-to-speech engine available")
        val t = tts!!
        fun ok(l: Locale) = t.isLanguageAvailable(l).let { it == TextToSpeech.LANG_AVAILABLE || it == TextToSpeech.LANG_COUNTRY_AVAILABLE || it == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE }
        return TtsStatus(true, t.defaultEngine ?: "", ok(Locale("ar")), ok(Locale("ar", "TN")))
    }

    suspend fun voices(): List<VoiceInfo> { init(); if (!ready.await()) return emptyList(); return tts!!.voices.orEmpty().map { VoiceInfo(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired, it.quality) }.sortedBy { it.locale } }

    override suspend fun speak(text: String, lang: Lang) {
        init()
        if (!ready.await()) return
        val t = tts!!; val s = settings.data.first()
        t.setSpeechRate(s.ttsRate); t.setPitch(s.ttsPitch)
        // Tunisian text is spoken with the best available Arabic voice; wording stays Derja.
        val candidates = when (lang) { Lang.TUNISIAN, Lang.MIXED_TN_FR -> listOf(Locale("ar", "TN"), Locale("ar"), Locale.FRENCH); Lang.MSA -> listOf(Locale("ar"), Locale("ar", "SA")); Lang.FRENCH -> listOf(Locale.FRANCE, Locale.FRENCH); else -> listOf(Locale.US, Locale.ENGLISH) }
        val loc = candidates.firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: candidates.last()
        t.language = loc
        if (s.ttsVoice.isNotBlank()) t.voices?.firstOrNull { it.name == s.ttsVoice && it.locale.language == loc.language }?.let { t.voice = it }
        requestFocus()
        val id = UUID.randomUUID().toString(); currentUtterance = id
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    onDone = { if (cont.isActive) cont.resume(Unit) }
                    cont.invokeOnCancellation { t.stop() }
                    val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                    if (r != TextToSpeech.SUCCESS) cont.resume(Unit)
                }
            }
        } finally { onDone = null; abandonFocus() }
    }

    override fun stop() { tts?.stop(); abandonFocus() }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
        focus = req; audio.requestAudioFocus(req)
    }
    private fun abandonFocus() { focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null }
    fun shutdown() { tts?.shutdown(); tts = null }
}
