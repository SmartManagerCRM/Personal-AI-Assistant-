package com.smartmanager.ai.actions

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartmanager.ai.R
import com.smartmanager.ai.SmartManagerApp
import com.smartmanager.ai.data.AppDatabase
import com.smartmanager.ai.data.ReminderEntity
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Local reminders: Room + AlarmManager, re-armed after reboot. Exact when the user allows exact alarms, inexact (idle-aware) otherwise. */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val ctx: Context, private val db: AppDatabase) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    fun canExact() = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun pi(id: Long, text: String, flags: Int) = PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text), flags or PendingIntent.FLAG_IMMUTABLE)

    /** @return true when delivery will be exact. */
    suspend fun add(atMs: Long, text: String): Pair<Long, Boolean> {
        val id = db.dao().addReminder(ReminderEntity(triggerAtMs = atMs, text = text))
        return id to arm(id, atMs, text)
    }
    private fun arm(id: Long, atMs: Long, text: String): Boolean {
        val p = pi(id, text, PendingIntent.FLAG_UPDATE_CURRENT)
        return if (canExact()) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p); true } else { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p); false }
    }
    /** Read-back check used to *verify* a reminder was really armed. */
    fun isArmed(id: Long) = PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    suspend fun rescheduleAll() { db.dao().pendingReminders(System.currentTimeMillis()).forEach { arm(it.id, it.triggerAtMs, it.text) } }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text").orEmpty(); val id = intent.getLongExtra("id", 0)
        val n = NotificationCompat.Builder(ctx, SmartManagerApp.CH_REMINDERS).setSmallIcon(R.drawable.ic_stat_mic).setContentTitle(ctx.getString(R.string.notif_channel_reminders))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(("r$id").hashCode(), n) } catch (_: SecurityException) {}
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(ctx.applicationContext, com.smartmanager.ai.di.ControllerEntryPoint::class.java)
                ep.db().dao().markDone(id)
                ep.controller().speakIfActive(text)   // spoken only when the assistant is currently in standby
            } finally { pending.finish() }
        }
    }
}
