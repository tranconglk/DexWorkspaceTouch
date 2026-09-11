package com.trancong.dexworkspacetouch.license.ui

import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.domain.LicenseTokenClaims
import com.trancong.dexworkspacetouch.license.runtime.LicenseGateUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseGatePolicyTest {
    @Test fun everyBlockedLicenseStateExposesOnlyActivationAndPublicUpdate() {
        val blocked = listOf(
            LicenseState.Unactivated,
            LicenseState.Expired(null),
            LicenseState.DeviceMismatch(null),
            LicenseState.DeviceRevoked(null),
            LicenseState.Revoked(null),
            LicenseState.NetworkRequired(null),
            LicenseState.Error(LicenseFailure.Unexpected),
        )
        blocked.forEach { licenseState ->
            val policy = licenseGateContentPolicy(LicenseGateUiState.ActivationRequired(licenseState))
            assertFalse(policy.rendersMainApplication)
            assertTrue(policy.exposesActivation)
            assertTrue(policy.exposesPublicUpdate)
        }
    }

    @Test fun checkingDoesNotRenderMainOrPublicUtility() {
        val policy = licenseGateContentPolicy(LicenseGateUiState.Checking)
        assertFalse(policy.rendersMainApplication)
        assertFalse(policy.exposesPublicUpdate)
    }

    @Test fun licensedStateRendersMainWithoutBlockedGate() {
        val policy = licenseGateContentPolicy(LicenseGateUiState.Allowed(LicenseState.Active(claims())))
        assertTrue(policy.rendersMainApplication)
        assertFalse(policy.exposesActivation)
        assertFalse(policy.exposesPublicUpdate)
    }

    private fun claims() = LicenseTokenClaims(
        licenseId = "license", installationId = "installation",
        issuedAtEpochSeconds = 1, expiresAtEpochSeconds = 3,
        offlineValidUntilEpochSeconds = 2, packageName = "app", tokenVersion = 1,
    )
}
