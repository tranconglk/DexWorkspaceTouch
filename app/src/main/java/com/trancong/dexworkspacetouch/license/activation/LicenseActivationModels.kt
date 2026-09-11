package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.domain.LicenseTokenClaims

data class VerifiedLicenseToken(
    val encoded: String,
    val claims: LicenseTokenClaims,
    val deviceId: String,
    val deviceHash: String,
)

sealed interface LicenseActivationResult {
    data class Success(val state: LicenseState.Active, val requestId: String?) : LicenseActivationResult
    data class Failure(val failure: LicenseFailure, val requestId: String?) : LicenseActivationResult
}

sealed interface LicenseTokenVerificationResult {
    data class Valid(val token: VerifiedLicenseToken) : LicenseTokenVerificationResult
    data class Invalid(val reason: LicenseTokenInvalidReason) : LicenseTokenVerificationResult
}

enum class LicenseTokenInvalidReason {
    MALFORMED, UNSUPPORTED_HEADER, UNKNOWN_KEY, INVALID_SIGNATURE, INVALID_CLAIMS,
    TOKEN_EXPIRED, DEVICE_MISMATCH, APPLICATION_MISMATCH,
}
