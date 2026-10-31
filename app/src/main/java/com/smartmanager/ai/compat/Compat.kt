package com.smartmanager.ai.compat

import android.os.Build

enum class Support { FULL, WITH_PERMISSION, USER_INTERACTION, MANUFACTURER_LIMITED, UNAVAILABLE }

data class CapabilityRow(val feature: String, val support: Support, val note: String)

/** Per-API capability matrix (also mirrored in ANDROID_COMPATIBILITY.md). */
object Compat {
    val sdk get() = Build.VERSION.SDK_INT

    fun capabilities(api: Int = sdk): List<CapabilityRow> = listOf(
        CapabilityRow("Default assistant role", if (api >= 29) Support.USER_INTERACTION else Support.USER_INTERACTION,
            if (api >= 29) "RoleManager.ROLE_ASSISTANT request dialog" else "Settings → Default apps → Assist & voice input (no role API before Android 10)"),
        CapabilityRow("Always-on microphone service", when { api >= 34 -> Support.MANUFACTURER_LIMITED; else -> Support.WITH_PERMISSION },
            when { api >= 34 -> "Foreground service type microphone; cannot be started from the background on Android 14+/15 without an exemption — started while the app/assistant UI is visible"
                api >= 29 -> "Foreground service type microphone"; else -> "Plain foreground service" }),
        CapabilityRow("Start after reboot", if (api >= 35) Support.MANUFACTURER_LIMITED else Support.WITH_PERMISSION,
            if (api >= 35) "Android 15 forbids starting microphone foreground services from BOOT_COMPLETED; the app posts a notification to resume with one tap"
            else "BOOT_COMPLETED receiver (may also need the OEM auto-start toggle)"),
        CapabilityRow("Notification permission", if (api >= 33) Support.WITH_PERMISSION else Support.FULL, if (api >= 33) "POST_NOTIFICATIONS runtime permission" else "Granted at install"),
        CapabilityRow("Media access", Support.WITH_PERMISSION, if (api >= 33) "READ_MEDIA_IMAGES/VIDEO" else "READ_EXTERNAL_STORAGE"),
        CapabilityRow("Bluetooth control", Support.USER_INTERACTION, "Opens Bluetooth settings; apps cannot toggle Bluetooth silently on API 33+"),
        CapabilityRow("Wi-Fi control", Support.USER_INTERACTION, if (api >= 29) "Opens the Internet connectivity panel" else "Opens Wi-Fi settings"),
        CapabilityRow("Exact reminders", when { api >= 31 -> Support.USER_INTERACTION; else -> Support.FULL }, if (api >= 31) "SCHEDULE_EXACT_ALARM must be allowed by the user, otherwise inexact delivery" else "AlarmManager exact"),
        CapabilityRow("Calls", Support.WITH_PERMISSION, "CALL_PHONE for direct dial, otherwise opens the dialer"),
        CapabilityRow("WhatsApp send", Support.USER_INTERACTION, "Prepares the message; the user must press Send"),
        CapabilityRow("Read notifications", Support.USER_INTERACTION, "Notification access is granted in system settings"),
        CapabilityRow("On-device system recognizer", if (api >= 33) Support.FULL else Support.UNAVAILABLE, "createOnDeviceSpeechRecognizer needs API 33+ (used only as fallback; sherpa-onnx is the main offline engine)"),
        CapabilityRow("Thermal status API", if (api >= 29) Support.FULL else Support.UNAVAILABLE, "PowerManager.currentThermalStatus"),
    )
}
