package com.trancong.dexworkspacetouch.license.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicenseStateTest {
    @Test
    fun stateModelRepresentsEveryFoundationState() {
        val claims = LicenseTokenClaims(
            licenseId = "license-1",
            installationId = "installation-1",
            issuedAtEpochSeconds = 100L,
            expiresAtEpochSeconds = 2_000L,
            offlineValidUntilEpochSeconds = 1_000L,
            packageName = "com.trancong.dexworkspacetouch",
            tokenVersion = 1,
        )
        val states = listOf(
            LicenseState.Unactivated,
            LicenseState.Activating,
            LicenseState.Active(claims),
            LicenseState.OfflineGrace(claims),
            LicenseState.Expired("license-1"),
            LicenseState.DeviceMismatch("license-1"),
            LicenseState.Revoked("license-1"),
            LicenseState.NetworkRequired(123L),
            LicenseState.Error(LicenseFailure.NetworkUnavailable),
        )

        assertEquals(9, states.size)
        assertEquals(LicenseState.Active(claims), states[2])
        assertEquals(LicenseState.Error(LicenseFailure.NetworkUnavailable), states.last())
    }

    @Test
    fun errorCodesRoundTripByStableWireValue() {
        LicenseErrorCode.entries.forEach { code ->
            assertEquals(code, LicenseErrorCode.fromWireValue(code.wireValue))
        }
        assertNull(LicenseErrorCode.fromWireValue("unknown"))
    }

    @Test
    fun networkFailureIsNotRevocation() {
        val network = LicenseState.Error(LicenseFailure.NetworkUnavailable)
        val revoked = LicenseState.Revoked("license-1")
        assert(network != revoked)
    }
}
