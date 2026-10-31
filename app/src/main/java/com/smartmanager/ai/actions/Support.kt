package com.smartmanager.ai.actions

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.smartmanager.ai.R
import com.smartmanager.ai.SmartManagerApp
import com.smartmanager.ai.assistant.SessionBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether any of our activities is on screen (Android blocks background activity starts from Android 10). */
object AppVisibility { @Volatile var started = 0; val foreground get() = started > 0 }

enum class LaunchResult { STARTED, NEEDS_TAP }

/**
 * Starts activities the Android-sanctioned way: through the assistant session when one is showing, directly when the app is
 * visible, otherwise via a heads-up notification the user taps (background starts are blocked by the OS; we never try to bypass that).
 */
@Singleton
class ActivityLauncher @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun launch(intent: Intent, label: String): LaunchResult {
        SessionBridge.session?.let { s -> return try { s.startAssistantActivity(intent); LaunchResult.STARTED } catch (e: Exception) { notify(intent, label) } }
        if (AppVisibility.foreground) return try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); LaunchResult.STARTED } catch (e: Exception) { notify(intent, label) }
        return notify(intent, label)
    }

    private fun notify(intent: Intent, label: String): LaunchResult {
        val pi = PendingIntent.getActivity(ctx, label.hashCode(), intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, SmartManagerApp.CH_REMINDERS).setSmallIcon(R.drawable.ic_stat_mic).setContentTitle(label).setContentText("Tap to continue")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_CALL).setContentIntent(pi).setAutoCancel(true).build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(label.hashCode(), n) } catch (_: SecurityException) {}
        return LaunchResult.NEEDS_TAP
    }
}
