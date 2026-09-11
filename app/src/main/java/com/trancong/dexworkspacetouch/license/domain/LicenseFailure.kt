package com.trancong.dexworkspacetouch.license.domain

enum class LicenseErrorCode(val wireValue: String) {
    INVALID_REQUEST("INVALID_REQUEST"),
    INVALID_DEVICE_PUBLIC_KEY("INVALID_DEVICE_PUBLIC_KEY"),
    APPLICATION_NOT_ALLOWED("APPLICATION_NOT_ALLOWED"),
    LICENSE_INVALID("LICENSE_INVALID"),
    LICENSE_REVOKED("LICENSE_REVOKED"),
    LICENSE_EXPIRED("LICENSE_EXPIRED"),
    DEVICE_MISMATCH("DEVICE_MISMATCH"),
    DEVICE_REVOKED("DEVICE_REVOKED"),
    UNKNOWN_KEY("UNKNOWN_KEY"),
    TOKEN_INVALID("TOKEN_INVALID"),
    TOKEN_EXPIRED("TOKEN_EXPIRED"),
    CHALLENGE_INVALID("CHALLENGE_INVALID"),
    CHALLENGE_EXPIRED("CHALLENGE_EXPIRED"),
    CHALLENGE_USED("CHALLENGE_USED"),
    PROOF_INVALID("PROOF_INVALID"),
    RATE_LIMITED("RATE_LIMITED"),
    DEVICE_LIMIT_REACHED("DEVICE_LIMIT_REACHED"),
    SERVER_ERROR("SERVER_ERROR");

    companion object {
        fun fromWireValue(value: String): LicenseErrorCode? =
            entries.firstOrNull { it.wireValue == value }
    }
}

sealed interface LicenseFailure {
    data object NetworkUnavailable : LicenseFailure
    data class Rejected(val code: LicenseErrorCode) : LicenseFailure
    data class Server(val retryable: Boolean) : LicenseFailure
    data object InvalidResponse : LicenseFailure
    data object LocalDataCorrupted : LicenseFailure
    data object Unexpected : LicenseFailure
}
