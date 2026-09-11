package com.trancong.dexworkspacetouch.license.ui

import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.runtime.LicenseGateUiState
import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LicenseActivationPresentationTest {
    @Test fun unactivatedLicenseUsesInformationPresentation() {
        val state = LicenseGateUiState.ActivationRequired(LicenseState.Unactivated)
        val presentation = licenseActivationPresentation(state)

        assertEquals(DwtStatusTone.Info, presentation.tone)
        assertEquals("Cần kích hoạt", presentation.title)
    }

    @Test fun networkRequiredUsesWarningPresentation() {
        val state = LicenseGateUiState.ActivationRequired(LicenseState.NetworkRequired(null))
        assertEquals(DwtStatusTone.Warning, licenseActivationPresentation(state).tone)
    }

    @Test fun validationAndActivationFailuresUseErrorWithoutExposingLicenseKey() {
        val state = LicenseGateUiState.ActivationRequired(
            licenseState = LicenseState.Unactivated,
            licenseKeyInput = "DWT-SECRET-LICENSE",
            activationFailure = LicenseFailure.NetworkUnavailable,
        )
        val presentation = licenseActivationPresentation(state)

        assertEquals(DwtStatusTone.Error, presentation.tone)
        assertFalse(presentation.message.contains("DWT-SECRET-LICENSE"))
    }
}
