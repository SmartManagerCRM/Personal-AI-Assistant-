package com.smartmanager.ai.engine

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.smartmanager.ai.MainActivity
import com.smartmanager.ai.R
import com.smartmanager.ai.SmartManagerApp
import com.smartmanager.ai.actions.ReminderScheduler
import com.smartmanager.ai.data.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Foreground service (type: microphone) that keeps the wake-word engine alive. The notification is always visible while the mic is open and has Stop / Quiet / Talk controls. */
@AndroidEntryPoint
class AssistantService : Service() {
    @Inject lateinit var controller: AssistantController
    @Inject lateinit var settings: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.init()
        when (intent?.action) {
            ACTION_STOP -> { controller.emergencyStop("Stopped"); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_QUIET -> { controller.quiet(30); refresh(); return START_STICKY }
            ACTION_TALK -> { controller.talk(); return START_STICKY }
        }
        val ok = try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0); true
        } catch (e: Exception) { false }   // Android 14/15 may refuse a microphone FGS started from the background
        if (!ok) { postResumeNotification(this, "Android blocked background microphone start — tap to resume"); stopSelf(); return START_NOT_STICKY }
        scope.launch {
            val err = controller.startStandby()
            if (err != null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } else { launch { controller.ui.collect { refresh() } } }
        }
        return START_STICKY
    }

    private fun refresh() { runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification()) } }

    private fun notification(): Notification {
        val ui = controller.ui.value
        val phrase = runBlocking { settings.data.first().wakePhrase }
        fun pi(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AssistantService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val quiet = ui.quietUntilMs > System.currentTimeMillis()
        return NotificationCompat.Builder(this, SmartManagerApp.CH_LISTENING).setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(if (quiet) getString(R.string.notif_title_quiet) else getString(R.string.notif_title_standby, phrase))
            .setContentText(getString(R.string.notif_text_mic)).setOngoing(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_stop), pi(ACTION_STOP, 1)).addAction(0, getString(R.string.notif_quiet), pi(ACTION_QUIET, 2)).addAction(0, getString(R.string.notif_talk), pi(ACTION_TALK, 3)).build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "start"; const val ACTION_STOP = "stop"; const val ACTION_QUIET = "quiet"; const val ACTION_TALK = "talk"; const val NOTIF_ID = 1001
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java).setAction(ACTION_START))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, AssistantService::class.java).setAction(ACTION_STOP))

        fun postResumeNotification(ctx: Context, text: String) {
            val pi = PendingIntent.getActivity(ctx, 77, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_RESUME_STANDBY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, SmartManagerApp.CH_REMINDERS).setSmallIcon(R.drawable.ic_stat_mic).setContentTitle(ctx.getString(R.string.app_name)).setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
            try { ctx.getSystemService(NotificationManager::class.java).notify(1002, n) } catch (_: SecurityException) {}
        }
    }
}

/** After reboot / app update: re-arm reminders; resume standby when Android still allows it, otherwise ask for one tap (Android 15 forbids mic services from BOOT_COMPLETED). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(ctx.applicationContext, com.smartmanager.ai.di.ControllerEntryPoint::class.java)
                ReminderScheduler(ctx.applicationContext, ep.db()).rescheduleAll()
                val settings = SettingsStore(ctx.applicationContext)
                if (settings.data.first().standbyEnabled) {
                    if (Build.VERSION.SDK_INT >= 35 || !runCatching { AssistantService.start(ctx.applicationContext) }.isSuccess) AssistantService.postResumeNotification(ctx.applicationContext, "Tap to resume wake-word listening")
                }
            } finally { pending.finish() }
        }
    }
}
