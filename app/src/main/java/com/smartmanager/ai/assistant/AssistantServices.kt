package com.smartmanager.ai.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.smartmanager.ai.R
import com.smartmanager.ai.core.conversation.ConvState
import com.smartmanager.ai.di.ControllerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

object SessionBridge { @Volatile var session: SmartVoiceInteractionSession? = null }

/** System-managed digital assistant. Selecting the app as default assistant enables the assist gesture / power-button long press and lets the session start activities from the background legally. */
class SmartVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() { super.onReady(); instance = this }
    override fun onShutdown() { if (instance === this) instance = null; super.onShutdown() }
    override fun onLaunchVoiceAssistFromKeyguard() { showSessionSafely() }
    fun showSessionSafely() { try { showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST) } catch (_: Exception) { /* not the active assistant */ } }
    companion object { @Volatile var instance: SmartVoiceInteractionService? = null }
}

class SmartVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = SmartVoiceInteractionSession(this)
}

/** Lightweight overlay (plain Views: sessions have no Compose lifecycle owner) mirroring the conversation state with a stop button. */
class SmartVoiceInteractionSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView; private lateinit var text: TextView

    private val controller get() = EntryPointAccessors.fromApplication(ctx.applicationContext, ControllerEntryPoint::class.java).controller()

    override fun onCreateContentView(): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F00B0F1A")); setPadding(48, 48, 48, 48); gravity = Gravity.BOTTOM }
        status = TextView(ctx).apply { setTextColor(Color.parseColor("#B9A8FF")); textSize = 20f }
        text = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 16f; maxLines = 6 }
        val stop = Button(ctx).apply { setText(R.string.emergency_stop); setOnClickListener { controller.emergencyStop("Stopped"); hide() } }
        root.addView(status); root.addView(text); root.addView(stop)
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        SessionBridge.session = this
        controller.init(); controller.talk()
        scope.launch {
            controller.ui.collect { u ->
                status.setText(when (u.conv) { ConvState.LISTENING -> R.string.state_listening; ConvState.SPEAKING -> R.string.state_speaking; ConvState.THINKING -> R.string.state_thinking; ConvState.EXECUTING -> R.string.state_executing; ConvState.PROCESSING -> R.string.state_processing; ConvState.WAITING_CONFIRMATION -> R.string.state_confirm; else -> R.string.state_standby })
                text.text = u.transcript.takeLast(2).joinToString("\n") { (if (it.fromUser) "› " else "‹ ") + it.text }
            }
        }
    }

    override fun onHide() { SessionBridge.session = null; scope.coroutineContext.cancelChildren(); super.onHide() }
}

/** Required by the assistant declaration. Third-party apps asking for recognition get a clear "not supported" error; the assistant itself uses sherpa-onnx / the system recognizer directly. */
class SmartRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback) { try { callback.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) } catch (_: Exception) {} }
    override fun onCancel(callback: Callback) {}
    override fun onStopListening(callback: Callback) {}
}

object AssistRole {
    fun isDefault(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    else Settings.Secure.getString(ctx.contentResolver, "assistant").orEmpty().contains(ctx.packageName)

    /** Android 10+: role request dialog. Earlier versions have no role API: open Settings → Default apps → Assist & voice input. */
    fun requestIntent(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) { val rm = ctx.getSystemService(RoleManager::class.java); if (rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT) }
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }
}
