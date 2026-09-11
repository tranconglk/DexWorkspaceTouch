package com.trancong.dexworkspacetouch.license.runtime

import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseState

sealed interface LicenseGateUiState {
    data object Checking : LicenseGateUiState

    data class ActivationRequired(
        val licenseState: LicenseState,
        val licenseKeyInput: String = "",
        val validationMessage: String? = null,
        val activationFailure: LicenseFailure? = null,
        val requestId: String? = null,
        val activating: Boolean = false,
    ) : LicenseGateUiState {
        /** License Key input must never be disclosed by state diagnostics or accidental logging. */
        override fun toString(): String = "ActivationRequired(" +
            "licenseState=$licenseState, licenseKeyInput=<redacted>, " +
            "validationMessage=$validationMessage, activationFailure=$activationFailure, " +
            "requestId=$requestId, activating=$activating)"
    }

    data class Allowed(val licenseState: LicenseState) : LicenseGateUiState
}

fun LicenseState.allowsMainApplication(): Boolean =
    this is LicenseState.Active || this is LicenseState.OfflineGrace
