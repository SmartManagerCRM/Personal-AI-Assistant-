package com.smartmanager.ai.ui

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartmanager.ai.actions.NotificationStore
import com.smartmanager.ai.ai.LlamaCppProvider
import com.smartmanager.ai.ai.OpenAiCompatibleProvider
import com.smartmanager.ai.data.*
import com.smartmanager.ai.engine.*
import com.smartmanager.ai.license.LicenseManager
import com.smartmanager.ai.models.ModelEntry
import com.smartmanager.ai.models.ModelManager
import com.smartmanager.ai.perm.PermissionManager
import com.smartmanager.ai.tts.AndroidTts
import com.smartmanager.ai.tts.TtsStatus
import com.smartmanager.ai.tts.VoiceInfo
import com.smartmanager.ai.wake.WakeWordManager
import com.smartmanager.license.LicenseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemInfo(val ramFreeMb: Long, val ramTotalMb: Long, val appPssMb: Long, val batteryPct: Int, val batteryTempC: Float, val charging: Boolean, val thermal: String, val batteryOptIgnored: Boolean)

@HiltViewModel
class AppViewModel @Inject constructor(
    app: Application, val controller: AssistantController, val settings: SettingsStore, val wake: WakeWordManager, val models: ModelManager, val perms: PermissionManager,
    val memory: MemoryRepository, val db: AppDatabase, val tts: AndroidTts, val license: LicenseManager, val perf: PerfStats, val llama: LlamaCppProvider, val openai: OpenAiCompatibleProvider,
) : AndroidViewModel(app) {
    val ui = controller.ui
    val cfg = settings.data.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val wakeMetrics = wake.metrics
    val perfStats = perf.snapshot
    val audit = db.dao().observeAudit(200)
    val memoryItems = memory.observe()
    val aliases = memory.aliases()

    private val _tts = MutableStateFlow(TtsStatus(false)); val ttsStatus: StateFlow<TtsStatus> = _tts
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList()); val voices: StateFlow<List<VoiceInfo>> = _voices
    private val _licenseStatus = MutableStateFlow<LicenseStatus?>(null); val licenseStatus: StateFlow<LicenseStatus?> = _licenseStatus
    private val _sys = MutableStateFlow<SystemInfo?>(null); val sys: StateFlow<SystemInfo?> = _sys

    init { controller.init() }

    fun refreshTts() = viewModelScope.launch { _tts.value = tts.status(); _voices.value = tts.voices() }
    fun speakTest(text: String, lang: com.smartmanager.ai.core.language.Lang) = viewModelScope.launch { tts.speak(text, lang) }
    fun update(block: (Settings) -> Settings) = viewModelScope.launch { settings.update(block) }
    fun refreshLicense() = viewModelScope.launch { _licenseStatus.value = license.status() }
    fun activate(text: String) = viewModelScope.launch { _licenseStatus.value = license.activate(text) }
    fun download(e: ModelEntry) = viewModelScope.launch { models.download(e) }
    fun notificationsAvailable() = NotificationStore.snapshot().size

    fun refreshSystem() = viewModelScope.launch {
        val c: Context = getApplication()
        val am = c.getSystemService(ActivityManager::class.java); val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val bi = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pm = c.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) when (pm.currentThermalStatus) { 0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"; 4 -> "critical"; 5 -> "emergency"; else -> "shutdown" } else "n/a (API<29)"
        val pss = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss?.toLong()?.div(1024) ?: -1
        _sys.value = SystemInfo(mi.availMem / 1_048_576, mi.totalMem / 1_048_576, pss, if (level >= 0) level * 100 / scale else -1, (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0, thermal, pm.isIgnoringBatteryOptimizations(c.packageName))
    }
}
