package com.smartmanager.ai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("settings")

data class Settings(
    val wakePhrase: String = "Hey Smart",
    val wakeSensitivity: Float = 0.5f,
    val standbyEnabled: Boolean = false,
    /** "auto" or a core Lang name (TUNISIAN, MSA, FRENCH, ENGLISH). */
    val language: String = "auto",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoice: String = "",
    val bargeIn: Boolean = true,
    /** Master switch: when false NO network request is ever made by the app (also blocks model downloads). */
    val networkAllowed: Boolean = false,
    /** Hybrid mode: optional online AI providers may be used. Off by default. */
    val hybridMode: Boolean = false,
    val enabledOnline: Set<String> = emptySet(),
    val onlineEndpoint: String = "",
    val onlineModel: String = "",
    val retentionDays: Int = 30,
    val quietUntilMs: Long = 0,
    val activeAsr: String = "android",
    val activeLlm: String = "",
    val activeKws: String = "",
    val defaultCountryCode: String = "216",
    val licenseText: String = "",
    val licenseLastSeenSec: Long = 0,
    val keepScreenOnMain: Boolean = false,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val wake = stringPreferencesKey("wake"); val sens = floatPreferencesKey("sens"); val standby = booleanPreferencesKey("standby")
        val lang = stringPreferencesKey("lang"); val rate = floatPreferencesKey("rate"); val pitch = floatPreferencesKey("pitch"); val voice = stringPreferencesKey("voice")
        val barge = booleanPreferencesKey("barge"); val net = booleanPreferencesKey("net"); val hybrid = booleanPreferencesKey("hybrid")
        val online = stringSetPreferencesKey("online"); val endpoint = stringPreferencesKey("endpoint"); val omodel = stringPreferencesKey("omodel")
        val retention = intPreferencesKey("retention"); val quiet = longPreferencesKey("quiet"); val asr = stringPreferencesKey("asr")
        val llm = stringPreferencesKey("llm"); val kws = stringPreferencesKey("kws"); val cc = stringPreferencesKey("cc")
        val lic = stringPreferencesKey("lic"); val licSeen = longPreferencesKey("licseen")
    }
    val data: Flow<Settings> = ctx.store.data.map { p ->
        val d = Settings()
        Settings(p[K.wake] ?: d.wakePhrase, p[K.sens] ?: d.wakeSensitivity, p[K.standby] ?: d.standbyEnabled, p[K.lang] ?: d.language, p[K.rate] ?: d.ttsRate, p[K.pitch] ?: d.ttsPitch,
            p[K.voice] ?: d.ttsVoice, p[K.barge] ?: d.bargeIn, p[K.net] ?: d.networkAllowed, p[K.hybrid] ?: d.hybridMode, p[K.online] ?: d.enabledOnline,
            p[K.endpoint] ?: d.onlineEndpoint, p[K.omodel] ?: d.onlineModel, p[K.retention] ?: d.retentionDays, p[K.quiet] ?: d.quietUntilMs, p[K.asr] ?: d.activeAsr,
            p[K.llm] ?: d.activeLlm, p[K.kws] ?: d.activeKws, p[K.cc] ?: d.defaultCountryCode, p[K.lic] ?: d.licenseText, p[K.licSeen] ?: d.licenseLastSeenSec)
    }

    suspend fun update(block: (Settings) -> Settings) {
        ctx.store.edit { p ->
            val cur = Settings(p[K.wake] ?: "Hey Smart", p[K.sens] ?: 0.5f, p[K.standby] ?: false, p[K.lang] ?: "auto", p[K.rate] ?: 1f, p[K.pitch] ?: 1f, p[K.voice] ?: "", p[K.barge] ?: true,
                p[K.net] ?: false, p[K.hybrid] ?: false, p[K.online] ?: emptySet(), p[K.endpoint] ?: "", p[K.omodel] ?: "", p[K.retention] ?: 30, p[K.quiet] ?: 0, p[K.asr] ?: "android",
                p[K.llm] ?: "", p[K.kws] ?: "", p[K.cc] ?: "216", p[K.lic] ?: "", p[K.licSeen] ?: 0)
            val n = block(cur)
            p[K.wake] = n.wakePhrase; p[K.sens] = n.wakeSensitivity; p[K.standby] = n.standbyEnabled; p[K.lang] = n.language; p[K.rate] = n.ttsRate; p[K.pitch] = n.ttsPitch
            p[K.voice] = n.ttsVoice; p[K.barge] = n.bargeIn; p[K.net] = n.networkAllowed; p[K.hybrid] = n.hybridMode; p[K.online] = n.enabledOnline; p[K.endpoint] = n.onlineEndpoint
            p[K.omodel] = n.onlineModel; p[K.retention] = n.retentionDays; p[K.quiet] = n.quietUntilMs; p[K.asr] = n.activeAsr; p[K.llm] = n.activeLlm; p[K.kws] = n.activeKws
            p[K.cc] = n.defaultCountryCode; p[K.lic] = n.licenseText; p[K.licSeen] = n.licenseLastSeenSec
        }
    }
}
