package com.smartmanager.license

import org.junit.Assert.*
import org.junit.Test

class LicenseTest {
    private val keys = LicenseKeys.generate()
    private val signer = LicenseSigner(keys.first)
    private val verifier = LicenseVerifier(keys.second)
    private val now = 1_800_000_000L
    private fun payload(type: LicenseType = LicenseType.LIFETIME, exp: Long = 0, dev: String = "DEV-AAAA") = LicensePayload("L1", type, dev, now - 10, exp, "Test Customer")

    @Test fun `lifetime license validates on its device`() {
        val lic = signer.issue(payload())
        assertTrue(verifier.verify(lic, "DEV-AAAA", now) is LicenseStatus.Valid)
        assertTrue("lifetime never expires", verifier.verify(lic, "DEV-AAAA", now + 10 * 365 * 86400L) is LicenseStatus.Valid)
    }
    @Test fun `bound to a single device`() { assertTrue(verifier.verify(signer.issue(payload()), "DEV-BBBB", now) is LicenseStatus.WrongDevice) }
    @Test fun `timed license expires`() {
        val lic = signer.issue(payload(LicenseType.TIMED, now + 100))
        assertTrue(verifier.verify(lic, "DEV-AAAA", now + 50) is LicenseStatus.Valid)
        assertTrue(verifier.verify(lic, "DEV-AAAA", now + 100) is LicenseStatus.Expired)
    }
    @Test fun `tampering is detected`() {
        val lic = signer.issue(payload(LicenseType.TIMED, now + 100))
        val forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload(LicenseType.LIFETIME, 0).canonical()) + "." + lic.substringAfter('.')
        assertEquals(LicenseStatus.BadSignature, verifier.verify(forged, "DEV-AAAA", now))
    }
    @Test fun `license signed by another key is rejected`() {
        val other = LicenseSigner(LicenseKeys.generate().first)
        assertEquals(LicenseStatus.BadSignature, verifier.verify(other.issue(payload()), "DEV-AAAA", now))
    }
    @Test fun `garbage and plain text are rejected`() {
        assertEquals(LicenseStatus.Malformed, verifier.verify("hello", "DEV-AAAA", now))
        assertEquals(LicenseStatus.Malformed, verifier.verify("a.b.c", "DEV-AAAA", now))
        assertTrue(verifier.verify("!!!.???", "DEV-AAAA", now) is LicenseStatus.Malformed)
    }
    @Test fun `clock rollback is detected`() {
        val lic = signer.issue(payload(LicenseType.TIMED, now + 1_000_000))
        assertEquals(LicenseStatus.ClockTampered, verifier.verify(lic, "DEV-AAAA", now, lastSeenSec = now + 86400))
    }
}
