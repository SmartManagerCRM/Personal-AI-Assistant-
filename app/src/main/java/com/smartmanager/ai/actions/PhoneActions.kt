package com.smartmanager.ai.actions

import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.smartmanager.ai.camera.CameraCaptureActivity
import com.smartmanager.ai.camera.CameraResultBus
import com.smartmanager.ai.core.action.*
import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.AppAliases
import com.smartmanager.ai.core.intent.ParsedIntent
import com.smartmanager.ai.core.intent.Slots
import com.smartmanager.ai.core.language.TextNormalizer
import com.smartmanager.ai.data.SettingsStore
import com.smartmanager.ai.perm.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val NUMBER = "number"

@Singleton
class PhoneActions @Inject constructor(
    @ApplicationContext private val ctx: Context, private val launcher: ActivityLauncher, private val contacts: ContactResolver, private val perms: PermissionManager,
    private val settings: SettingsStore, private val reminders: ReminderScheduler,
) {
    // ---------- shared contact preparation -----------------------------------------------------------
    private suspend fun resolveContact(intent: ParsedIntent): Preparation {
        val q = intent.slot(Slots.CONTACT) ?: return Preparation.Failed(ActionResult(ResultStatus.NOT_FOUND, "no name"))
        val m = contacts.find(q)
        if (m.isEmpty()) return Preparation.Failed(ActionResult(ResultStatus.NOT_FOUND, q, data = mapOf("contactName" to q)))
        val top = m.first()
        val strong = m.filter { it.score >= top.score - 0.04 }.distinctBy { it.name }
        if (strong.size > 1) return Preparation.Failed(ActionResult(ResultStatus.AMBIGUOUS, strong.take(4).joinToString("، ") { it.name }))
        return Preparation.Ready(intent.with(NUMBER to top.number, Slots.CONTACT to top.name), subject = top.name)
    }

    // ---------- calls ----------------------------------------------------------------------------------
    val call = object : ActionHandler {
        override suspend fun prepare(intent: ParsedIntent) = resolveContact(intent)
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val number = intent.slot(NUMBER) ?: return ActionResult(ResultStatus.FAILED, "no number")
            val name = intent.slot(Slots.CONTACT).orEmpty()
            val direct = perms.has(android.Manifest.permission.CALL_PHONE)
            val i = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            val watcher = if (perms.has(android.Manifest.permission.READ_PHONE_STATE)) CallWatcher(ctx) else null
            val res = launcher.launch(i, "Call $name")
            if (res == LaunchResult.NEEDS_TAP) return ActionResult(ResultStatus.NEEDS_USER_ACTION, "Tap the notification to place the call", data = mapOf("contactName" to name))
            // Initiation ≠ connection: OFFHOOK only proves the call is being dialled, never that the other side answered.
            val dialling = direct && watcher?.awaitOffhook(6000) == true
            return ActionResult(if (direct) ResultStatus.INITIATED else ResultStatus.NEEDS_USER_ACTION, if (direct) "dialling" else "dialer opened — press the call button", verified = dialling, data = mapOf("contactName" to name))
        }
    }

    // ---------- WhatsApp message (user must press Send) ------------------------------------------------
    val message = object : ActionHandler {
        override suspend fun prepare(intent: ParsedIntent) = resolveContact(intent)
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val body = intent.slot(Slots.BODY).orEmpty(); val name = intent.slot(Slots.CONTACT).orEmpty()
            val digits = toWaNumber(intent.slot(NUMBER).orEmpty(), settings.data.first().defaultCountryCode)
            val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(it) }
            val i = if (pkg != null) Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(body)}")).setPackage(pkg)
            else Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${intent.slot(NUMBER)}")).putExtra("sms_body", body)
            val r = launcher.launch(i, "Message to $name")
            return ActionResult(ResultStatus.NEEDS_USER_ACTION, if (pkg != null) "prepared in WhatsApp — user presses Send" else "WhatsApp not installed — SMS composer opened", data = mapOf("contactName" to name, "channel" to (pkg ?: "sms"), "launch" to r.name))
        }
    }

    // ---------- share last photo -------------------------------------------------------------------------------
    val shareLastPhoto = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val uri = lastImage() ?: return ActionResult(ResultStatus.NOT_FOUND, "no photo")
            val send = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val wa = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(it) }
            if (wa != null && intent.slot(Slots.CONTACT_REF) != "ME") send.setPackage(wa)
            val chooser = Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launcher.launch(chooser, "Share last photo")
            return ActionResult(ResultStatus.NEEDS_USER_ACTION, "share sheet opened — choose the recipient", data = mapOf("uri" to uri.toString()))
        }
    }
    private fun lastImage(): Uri? {
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        return null
    }

    // ---------- open app ------------------------------------------------------------------------------------------------
    private val knownPackages = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"), "youtube" to listOf("com.google.android.youtube"), "facebook" to listOf("com.facebook.katana"),
        "instagram" to listOf("com.instagram.android"), "maps" to listOf("com.google.android.apps.maps"), "chrome" to listOf("com.android.chrome"), "telegram" to listOf("org.telegram.messenger"),
        "messenger" to listOf("com.facebook.orca"), "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "spotify" to listOf("com.spotify.music"), "gmail" to listOf("com.google.android.gm"),
    )
    val openApp = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val key = intent.slot(Slots.APP).orEmpty()
            val pm = ctx.packageManager
            val canonical = AppAliases.resolve(key) ?: key
            val launch: Pair<Intent, String>? = when (canonical) {
                "camera" -> Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) to "Camera"
                "gallery" -> Intent(Intent.ACTION_VIEW).setType("image/*") to "Gallery"
                "settings" -> Intent(Settings.ACTION_SETTINGS) to "Settings"
                "calendar" -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()) to "Calendar"
                "contacts" -> Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI) to "Contacts"
                "phone" -> Intent(Intent.ACTION_DIAL) to "Phone"
                "messages" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING) to "Messages"
                "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS) to "Clock"
                "calculator" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR) to "Calculator"
                else -> null
            }
            val target: Pair<Intent, String>? = launch?.takeIf { pm.resolveActivity(it.first, 0) != null } ?: run {
                val pkgs = knownPackages[canonical].orEmpty()
                pkgs.firstNotNullOfOrNull { p -> pm.getLaunchIntentForPackage(p)?.let { it to (pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()) } }
                    ?: labelMatch(key)
            }
            if (target == null) return ActionResult(ResultStatus.NOT_FOUND, key)
            val r = launcher.launch(target.first, target.second)
            return if (r == LaunchResult.STARTED) ActionResult(ResultStatus.SUCCESS, data = mapOf("label" to target.second)) else ActionResult(ResultStatus.NEEDS_USER_ACTION, "tap the notification to open ${target.second}", data = mapOf("label" to target.second))
        }
    }
    private fun labelMatch(q: String): Pair<Intent, String>? {
        val pm = ctx.packageManager
        val nq = TextNormalizer.normalizeToken(q)
        if (nq.length < 2) return null
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { TextNormalizer.normalizeToken(it.loadLabel(pm).toString()).contains(nq) || nq.contains(TextNormalizer.normalizeToken(it.loadLabel(pm).toString())) && it.loadLabel(pm).length > 2 } ?: return null
        return pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?.let { it to hit.loadLabel(pm).toString() }
    }

    // ---------- camera (CameraX capture screen: verified by reading the saved file back) ---------------------------------
    fun camera(video: Boolean) = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val front = intent.slot(Slots.CAMERA) == "front"
            val i = Intent(ctx, CameraCaptureActivity::class.java).putExtra(CameraCaptureActivity.EXTRA_FRONT, front).putExtra(CameraCaptureActivity.EXTRA_VIDEO, video).putExtra(CameraCaptureActivity.EXTRA_AUTO, true)
            CameraResultBus.reset()
            if (launcher.launch(i, if (video) "Record video" else "Take photo") == LaunchResult.NEEDS_TAP) return ActionResult(ResultStatus.NEEDS_USER_ACTION, "tap the notification to open the camera")
            val saved = withTimeoutOrNull(if (video) 70_000 else 25_000) { CameraResultBus.result.first { it != null } }
            val uri = saved?.uri ?: return ActionResult(ResultStatus.INITIATED, "camera opened, nothing saved yet")
            val ok = runCatching { ctx.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { it.length > 0 } }.getOrNull() == true
            return ActionResult(if (ok) ResultStatus.SUCCESS else ResultStatus.FAILED, uri, verified = ok, data = mapOf("uri" to uri))
        }
    }

    // ---------- reminders / alarms ---------------------------------------------------------------------------------------------
    val reminder = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val at = triggerTime(intent) ?: return ActionResult(ResultStatus.FAILED, "no time")
            val text = intent.slot(Slots.WHAT).orEmpty().ifBlank { "⏰" }
            val (id, exact) = reminders.add(at, text)
            val armed = reminders.isArmed(id)   // read back from AlarmManager
            val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))
            return ActionResult(if (armed) ResultStatus.SUCCESS else ResultStatus.FAILED, whenStr + if (exact) "" else " (approximate: exact alarms not allowed)", verified = armed, data = mapOf("when" to whenStr, "exact" to exact.toString()))
        }
    }
    val alarm = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val at = Calendar.getInstance().apply { timeInMillis = triggerTime(intent) ?: return ActionResult(ResultStatus.FAILED, "no time") }
            val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, at.get(Calendar.HOUR_OF_DAY)).putExtra(AlarmClock.EXTRA_MINUTES, at.get(Calendar.MINUTE)).putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, intent.slot(Slots.WHAT).orEmpty())
            if (ctx.packageManager.resolveActivity(i, 0) == null) return ActionResult(ResultStatus.UNAVAILABLE, "no clock app")
            launcher.launch(i, "Set alarm")
            val whenStr = String.format("%02d:%02d", at.get(Calendar.HOUR_OF_DAY), at.get(Calendar.MINUTE))
            return ActionResult(ResultStatus.INITIATED, whenStr, data = mapOf("when" to whenStr))   // clock app owns the alarm; creation can't be read back
        }
    }
    private fun triggerTime(i: ParsedIntent): Long? {
        val now = Calendar.getInstance()
        i.slot(Slots.MINUTES)?.toIntOrNull()?.let { return now.timeInMillis + it * 60_000L }
        val day = i.slot(Slots.DAY_OFFSET)?.toIntOrNull(); val hour = i.slot(Slots.HOUR)?.toIntOrNull()
        if (day == null && hour == null) return null
        val c = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, day ?: 0); set(Calendar.HOUR_OF_DAY, hour ?: 9); set(Calendar.MINUTE, i.slot(Slots.MINUTE)?.toIntOrNull() ?: 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= now.timeInMillis && day == null) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    // ---------- agenda (calendar read, verified by construction) -----------------------------------------------------------------
    val agenda = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult = withContext(Dispatchers.IO) {
            val off = if (intent.slot(Slots.DAY) == "tomorrow") 1 else 0
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start.timeInMillis); ContentUris.appendId(it, end.timeInMillis) }.build()
            val items = ArrayList<String>()
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                val tf = DateFormat.getTimeInstance(DateFormat.SHORT)
                while (c.moveToNext()) items += (if (c.getInt(2) == 1) "" else tf.format(Date(c.getLong(1))) + " ") + (c.getString(0) ?: "?")
            } ?: return@withContext ActionResult(ResultStatus.FAILED, "calendar unavailable")
            ActionResult(ResultStatus.SUCCESS, items.take(6).joinToString("، "), verified = true, data = mapOf("count" to items.size.toString(), "day" to if (off == 1) "tomorrow" else "today"))
        }
    }

    // ---------- volume --------------------------------------------------------------------------------------------------------------
    val volume = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val am = ctx.getSystemService(AudioManager::class.java); val s = AudioManager.STREAM_MUSIC
            val before = am.getStreamVolume(s); val max = am.getStreamMaxVolume(s)
            try {
                when (intent.slot(Slots.DIRECTION)) {
                    "up" -> am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, 0).also { if (am.getStreamVolume(s) == before && before < max) am.setStreamVolume(s, minOf(max, before + 2), 0) }
                    "down" -> am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, 0)
                    "mute" -> am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    "set" -> am.setStreamVolume(s, (max * (intent.slot(Slots.LEVEL)?.toIntOrNull() ?: 50) / 100), 0)
                }
            } catch (e: SecurityException) { return ActionResult(ResultStatus.FAILED, e.message ?: "volume change not allowed (Do Not Disturb?)") }
            val after = am.getStreamVolume(s)
            val changed = after != before || (intent.slot(Slots.DIRECTION) == "up" && before == max) || (intent.slot(Slots.DIRECTION) in listOf("down", "mute") && before == 0)
            return ActionResult(if (changed) ResultStatus.SUCCESS else ResultStatus.FAILED, "$after/$max", verified = changed)
        }
    }

    // ---------- settings panels (Android requires the user to flip radios) --------------------------------------------------------------------
    val settingsPanel = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val (i, label) = when (intent.slot(Slots.PANEL)) {
                "wifi" -> (if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)) to "Wi-Fi"
                "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "Bluetooth"
                "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS) to "Display"
                else -> Intent(Settings.ACTION_SETTINGS) to "Settings"
            }
            val r = launcher.launch(i, label)
            return ActionResult(if (r == LaunchResult.STARTED) ResultStatus.NEEDS_USER_ACTION else ResultStatus.NEEDS_USER_ACTION, "opened $label — Android needs you to flip the switch", data = mapOf("label" to label))
        }
    }

    // ---------- notifications (local summary) ---------------------------------------------------------------------------------------------
    val notifications = object : ActionHandler {
        override suspend fun execute(intent: ParsedIntent): ActionResult {
            val list = NotificationStore.snapshot()
            if (list.isEmpty()) return ActionResult(ResultStatus.SUCCESS, "", verified = true)
            val byApp = list.groupBy { it.app }
            val text = byApp.entries.take(4).joinToString(". ") { (app, l) ->
                val first = l.first(); val who = first.title.takeIf { it.isNotBlank() }; "$app${if (l.size > 1) " (${l.size})" else ""}${who?.let { ": $it" } ?: ""}${first.text.takeIf { it.isNotBlank() }?.let { " — ${it.take(60)}" } ?: ""}"
            }
            return ActionResult(ResultStatus.SUCCESS, text, verified = true, data = mapOf("count" to list.size.toString()))
        }
    }

    // ---------- helpers ----------------------------------------------------------------------------------------------------------------------------
    private fun installed(pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

    companion object {
        /** wa.me needs international digits only: 00…→strip, local 8-digit → +216…, leading 0 → replaced by country code. */
        fun toWaNumber(raw: String, cc: String): String {
            var d = raw.filter { it.isDigit() || it == '+' }
            if (d.startsWith("+")) return d.drop(1).filter { it.isDigit() }
            d = d.filter { it.isDigit() }
            if (d.startsWith("00")) return d.drop(2)
            if (d.startsWith("0") && d.length in 9..11) return cc + d.drop(1)
            if (d.length in 7..9) return cc + d
            return d
        }
    }
}

/** Observes telephony call state to learn whether the call actually started dialling. */
class CallWatcher(private val ctx: Context) {
    suspend fun awaitOffhook(timeoutMs: Long): Boolean {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= 31) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener { override fun onCallStateChanged(state: Int) { if (state == TelephonyManager.CALL_STATE_OFFHOOK && cont.isActive) cont.resume(true) } }
                    tm.registerTelephonyCallback(ContextCompat.getMainExecutor(ctx), cb); cont.invokeOnCancellation { tm.unregisterTelephonyCallback(cb) }
                } else {
                    @Suppress("DEPRECATION") val l = object : PhoneStateListener() { @Deprecated("old api") override fun onCallStateChanged(state: Int, n: String?) { if (state == TelephonyManager.CALL_STATE_OFFHOOK && cont.isActive) cont.resume(true) } }
                    android.os.Handler(android.os.Looper.getMainLooper()).post { @Suppress("DEPRECATION") tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE) }
                    cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { @Suppress("DEPRECATION") tm.listen(l, PhoneStateListener.LISTEN_NONE) } }
                }
            }
        } ?: false
    }
}
