package com.trancong.dexworkspacetouch.license.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LicenseTokenClaimsTest {
    @Test
    fun validClaims_haveValueSemantics() {
        val claims = validClaims()
        assertEquals(claims, validClaims())
        assertEquals(1_000L, claims.offlineValidUntilEpochSeconds)
    }

    @Test
    fun claims_rejectBlankIdentityAndInvalidVersion() {
        assertThrows(IllegalArgumentException::class.java) { validClaims(licenseId = " ") }
        assertThrows(IllegalArgumentException::class.java) { validClaims(installationId = "") }
        assertThrows(IllegalArgumentException::class.java) { validClaims(packageName = "\t") }
        assertThrows(IllegalArgumentException::class.java) { validClaims(tokenVersion = 0) }
    }

    @Test
    fun claims_rejectInvalidTimeOrdering() {
        assertThrows(IllegalArgumentException::class.java) {
            validClaims(issuedAt = 101L, expiresAt = 100L)
        }
        assertThrows(IllegalArgumentException::class.java) { validClaims(offlineValidUntil = 99L) }
        assertThrows(IllegalArgumentException::class.java) { validClaims(offlineValidUntil = 2_001L) }
    }

    private fun validClaims(
        licenseId: String = "license-1",
        installationId: String = "installation-1",
        issuedAt: Long = 100L,
        expiresAt: Long = 2_000L,
        offlineValidUntil: Long = 1_000L,
        packageName: String = "com.trancong.dexworkspacetouch",
        tokenVersion: Int = 1,
    ) = LicenseTokenClaims(
        licenseId = licenseId,
        installationId = installationId,
        issuedAtEpochSeconds = issuedAt,
        expiresAtEpochSeconds = expiresAt,
        offlineValidUntilEpochSeconds = offlineValidUntil,
        packageName = packageName,
        tokenVersion = tokenVersion,
    )
}
