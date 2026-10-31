package com.smartmanager.license

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class LicenseType { LIFETIME, TIMED }

/** Canonical, signed license content. Independent from the app's business logic. */
data class LicensePayload(
    val id: String,
    val type: LicenseType,
    /** Device code shown in the app (hash of device identifiers) — single-device activation. */
    val deviceCode: String,
    val issuedAtSec: Long,
    /** 0 for lifetime licenses. */
    val expiresAtSec: Long,
    val customer: String = "",
) {
    internal fun canonical(): ByteArray = buildString {
        append("v=1\n"); append("id=$id\n"); append("type=${type.name}\n"); append("device=$deviceCode\n")
        append("issued=$issuedAtSec\n"); append("expires=$expiresAtSec\n"); append("customer=${customer.replace("\n", " ")}\n")
    }.toByteArray(Charsets.UTF_8)

    companion object {
        internal fun parse(bytes: ByteArray): LicensePayload? {
            val m = String(bytes, Charsets.UTF_8).lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            if (m["v"] != "1") return null
            return try {
                LicensePayload(m.getValue("id"), LicenseType.valueOf(m.getValue("type")), m.getValue("device"), m.getValue("issued").toLong(), m.getValue("expires").toLong(), m["customer"].orEmpty())
            } catch (e: Exception) { null }
        }
    }
}

sealed interface LicenseStatus {
    data class Valid(val payload: LicensePayload) : LicenseStatus
    data object Malformed : LicenseStatus
    data object BadSignature : LicenseStatus
    data class WrongDevice(val expected: String) : LicenseStatus
    data class Expired(val payload: LicensePayload) : LicenseStatus
    /** System clock was moved back before the last time this license was seen. */
    data object ClockTampered : LicenseStatus
}

private val b64 = Base64.getUrlEncoder().withoutPadding()
private val unb64 = Base64.getUrlDecoder()
private const val ALG = "SHA256withECDSA"

object LicenseCodec {
    fun encode(payload: LicensePayload, signature: ByteArray): String = b64.encodeToString(payload.canonical()) + "." + b64.encodeToString(signature)
}

/** Offline issuer (used only by the separate generator tool, never shipped in the app). */
class LicenseSigner(privateKeyPkcs8: ByteArray) {
    private val key: PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8))
    fun issue(payload: LicensePayload): String {
        val sig = Signature.getInstance(ALG).apply { initSign(key); update(payload.canonical()) }.sign()
        return LicenseCodec.encode(payload, sig)
    }
}

object LicenseKeys {
    fun generate(): Pair<ByteArray, ByteArray> {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return kp.private.encoded to kp.public.encoded
    }
}

/** Offline validator embedded in the app: signature, device binding, expiry, clock-rollback guard. */
class LicenseVerifier(publicKeyX509: ByteArray) {
    private val key: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyX509))

    /** [lastSeenSec] is the highest "now" previously recorded by the app (monotonic clock guard). */
    fun verify(license: String, thisDeviceCode: String, nowSec: Long, lastSeenSec: Long = 0): LicenseStatus {
        val parts = license.trim().split('.')
        if (parts.size != 2) return LicenseStatus.Malformed
        val payloadBytes = try { unb64.decode(parts[0]) } catch (e: Exception) { return LicenseStatus.Malformed }
        val sig = try { unb64.decode(parts[1]) } catch (e: Exception) { return LicenseStatus.Malformed }
        val ok = try { Signature.getInstance(ALG).apply { initVerify(key); update(payloadBytes) }.verify(sig) } catch (e: Exception) { false }
        if (!ok) return LicenseStatus.BadSignature
        val p = LicensePayload.parse(payloadBytes) ?: return LicenseStatus.Malformed
        if (!MessageDigest.isEqual(p.deviceCode.toByteArray(), thisDeviceCode.toByteArray())) return LicenseStatus.WrongDevice(p.deviceCode)
        if (lastSeenSec > 0 && nowSec + 300 < lastSeenSec) return LicenseStatus.ClockTampered
        if (p.type == LicenseType.TIMED && nowSec >= p.expiresAtSec) return LicenseStatus.Expired(p)
        return LicenseStatus.Valid(p)
    }
}
