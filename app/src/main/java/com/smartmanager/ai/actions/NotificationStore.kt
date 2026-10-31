package com.smartmanager.ai.actions

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentLinkedDeque

data class Notif(val pkg: String, val app: String, val title: String, val text: String, val timeMs: Long)

/** In-memory only (never persisted, never leaves the device). Filled while the user has granted notification access. */
object NotificationStore {
    private val items = ConcurrentLinkedDeque<Notif>()
    fun snapshot(): List<Notif> = items.toList().sortedByDescending { it.timeMs }
    fun add(n: Notif) { items.removeIf { it.pkg == n.pkg && it.title == n.title && it.text == n.text }; items.addFirst(n); while (items.size > 50) items.removeLast() }
    fun clear() = items.clear()
}

class SmartNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { runCatching { activeNotifications?.forEach { handle(it) } } }
    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)
    override fun onListenerDisconnected() { NotificationStore.clear() }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(); val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: PackageManager.NameNotFoundException) { sbn.packageName }
        NotificationStore.add(Notif(sbn.packageName, label, title, text, sbn.postTime))
    }
}
