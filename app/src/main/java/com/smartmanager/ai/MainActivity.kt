package com.smartmanager.ai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.smartmanager.ai.actions.AppVisibility
import com.smartmanager.ai.engine.AssistantService
import com.smartmanager.ai.ui.AppNav
import com.smartmanager.ai.ui.SmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val micThenStart = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) AssistantService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartTheme { AppNav(onStartStandby = { startStandby() }) } }
        handle(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    override fun onStart() { super.onStart(); AppVisibility.started++ }
    override fun onStop() { super.onStop(); AppVisibility.started-- }

    private fun handle(i: Intent?) {
        when (i?.action) {
            ACTION_RESUME_STANDBY -> startStandby()
            Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND -> startStandbyOrTalk()
        }
    }

    private fun startStandbyOrTalk() { if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) AssistantService.start(this).also { startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_TALK)) } else startStandby() }

    /** Foreground start (allowed on every Android version): ask for mic + notification permission first. */
    fun startStandby() {
        val need = buildList { add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
        if (need.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) AssistantService.start(this) else micThenStart.launch(need.toTypedArray())
    }

    companion object { const val ACTION_RESUME_STANDBY = "com.smartmanager.ai.RESUME_STANDBY" }
}
