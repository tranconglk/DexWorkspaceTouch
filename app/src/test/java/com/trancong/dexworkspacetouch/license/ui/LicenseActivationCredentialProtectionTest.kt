package com.trancong.dexworkspacetouch.license.ui

import androidx.compose.ui.text.AnnotatedString
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.runtime.LicenseGateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LicenseActivationCredentialProtectionTest {
    @Test fun activationFieldMasksEveryLicenseKeyCharacter() {
        val key = "DWT-ABCD-EFGH-JKLM"
        val transformed = licenseKeyVisualTransformation.filter(AnnotatedString(key)).text.text

        assertEquals(key.length, transformed.length)
        assertFalse(transformed.contains(key))
        assertEquals(setOf('\u2022'), transformed.toSet())
    }

    @Test fun activationStateDiagnosticsNeverExposeLicenseKeyInput() {
        val key = "DWT-ABCD-EFGH-JKLM"
        val state = LicenseGateUiState.ActivationRequired(
            licenseState = LicenseState.Unactivated,
            licenseKeyInput = key,
        )

        assertFalse(state.toString().contains(key))
        assertFalse(state.toString().contains("ABCD"))
    }
}
