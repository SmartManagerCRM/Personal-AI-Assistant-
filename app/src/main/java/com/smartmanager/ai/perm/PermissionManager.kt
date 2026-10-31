package com.smartmanager.ai.perm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartmanager.ai.core.action.AppPermission
import com.smartmanager.ai.core.action.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Granular, per-category permission handling (Android runtime permissions + special accesses). */
@Singleton
class PermissionManager @Inject constructor(@ApplicationContext private val ctx: Context) : PermissionChecker {

    /** Runtime permissions to request for a category on the running API level (empty = none needed / special access). */
    fun runtimePermissions(p: AppPermission, sdk: Int = Build.VERSION.SDK_INT): List<String> = when (p) {
        AppPermission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        AppPermission.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        AppPermission.PHONE -> listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        AppPermission.NOTIFICATIONS -> if (sdk >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        AppPermission.CAMERA -> listOf(Manifest.permission.CAMERA)
        AppPermission.MEDIA -> if (sdk >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        AppPermission.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        AppPermission.BLUETOOTH -> if (sdk >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()
        AppPermission.NOTIFICATION_ACCESS, AppPermission.WRITE_SETTINGS, AppPermission.LOCATION -> emptyList()
    }

    /** Core permissions that are *required* (READ_PHONE_STATE is optional: only used to observe call state). */
    private fun required(p: AppPermission) = runtimePermissions(p).filterNot { it == Manifest.permission.READ_PHONE_STATE }

    override fun isGranted(p: AppPermission): Boolean = when (p) {
        AppPermission.NOTIFICATION_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        AppPermission.LOCATION, AppPermission.WRITE_SETTINGS -> true // no feature requires them; never requested
        AppPermission.MEDIA -> if (Build.VERSION.SDK_INT >= 33) has(Manifest.permission.READ_MEDIA_IMAGES) else has(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> required(p).all { has(it) }
    }

    fun has(perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** Categories shown in the Permissions screen. */
    val manageable = listOf(AppPermission.MICROPHONE, AppPermission.CONTACTS, AppPermission.PHONE, AppPermission.NOTIFICATIONS, AppPermission.NOTIFICATION_ACCESS,
        AppPermission.CAMERA, AppPermission.MEDIA, AppPermission.CALENDAR, AppPermission.BLUETOOTH)
}
