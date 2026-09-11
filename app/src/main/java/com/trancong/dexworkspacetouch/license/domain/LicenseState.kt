package com.trancong.dexworkspacetouch.license.domain

sealed interface LicenseState {
    data object Unactivated : LicenseState
    data object Activating : LicenseState
    data class Active(val claims: LicenseTokenClaims) : LicenseState
    data class OfflineGrace(val claims: LicenseTokenClaims) : LicenseState
    data class Expired(val licenseId: String?) : LicenseState
    data class DeviceMismatch(val licenseId: String?) : LicenseState
    data class DeviceRevoked(val licenseId: String?) : LicenseState
    data class Revoked(val licenseId: String?) : LicenseState
    data class NetworkRequired(val lastVerifiedAtEpochMillis: Long?) : LicenseState
    data class Error(val failure: LicenseFailure) : LicenseState
}
