package com.smartmanager.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.*
import com.smartmanager.ai.data.MemoryRepository
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SmartManagerApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LISTENING, getString(R.string.notif_channel_listening), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_HIGH))
        // Daily retention purge (conversation turns older than the user's retention setting are deleted).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("retention", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MemoryRepository.RetentionWorker>(1, TimeUnit.DAYS).build())
    }

    companion object { const val CH_LISTENING = "listening"; const val CH_REMINDERS = "reminders" }
}
