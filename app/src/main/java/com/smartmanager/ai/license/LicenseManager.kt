package com.smartmanager.ai.license

import android.content.Context
import android.provider.Settings
import com.smartmanager.ai.data.SettingsStore
import com.smartmanager.license.LicenseStatus
import com.smartmanager.license.LicenseVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Offline, device-bound license validation (ECDSA P-256). Independent from the assistant logic; no server involved. */
@Singleton
class LicenseManager @Inject constructor(@ApplicationContext private val ctx: Context, private val settings: SettingsStore) {
    private val verifier: LicenseVerifier by lazy { LicenseVerifier(Base64.getDecoder().decode(ctx.assets.open("license_public_key.b64").bufferedReader().readText().trim())) }

    /** Shown to the user; they send it to the vendor to get a license bound to this phone. */
    val deviceCode: String by lazy {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val h = MessageDigest.getInstance("SHA-256").digest("$id|${ctx.packageName.removeSuffix(".debug")}|smartmanager".toByteArray()).joinToString("") { "%02X".format(it) }.take(20)
        "SM-" + h.chunked(5).joinToString("-")
    }

    suspend fun status(): LicenseStatus {
        val s = settings.data.first()
        if (s.licenseText.isBlank()) return LicenseStatus.Malformed
        val now = System.currentTimeMillis() / 1000
        val st = verifier.verify(s.licenseText, deviceCode, now, s.licenseLastSeenSec)
        if (st is LicenseStatus.Valid) settings.update { it.copy(licenseLastSeenSec = maxOf(it.licenseLastSeenSec, now)) }
        return st
    }

    suspend fun activate(text: String): LicenseStatus {
        val st = verifier.verify(text.trim(), deviceCode, System.currentTimeMillis() / 1000, settings.data.first().licenseLastSeenSec)
        if (st is LicenseStatus.Valid) settings.update { it.copy(licenseText = text.trim()) }
        return st
    }
}
